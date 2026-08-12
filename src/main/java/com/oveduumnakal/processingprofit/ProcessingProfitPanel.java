/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oveduumnakal.processingprofit;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ScrollBarUI;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.ui.laf.RuneLiteScrollBarUI;
import net.runelite.client.util.QuantityFormatter;

/**
 * The Processing Profit sidebar: sourcing-mode and valuation-lens selectors, an active-modifier status
 * line, a filter/sort bar (search, skill, gating, members/F2P, sort key, density), and four tabs
 * &mdash; On-hand, Watchlist, Browse and Shopping. Each tab holds the full {@link RecipeRow} set pushed
 * by the plugin and renders
 * a filtered, sorted, display-capped view locally so sorting and searching are instant. Uses the
 * RuneLite fonts, colour scheme and themed scrollbar so it scales with the client.
 */
@Singleton
public class ProcessingProfitPanel extends PluginPanel
{
	private static final int MAX_DISPLAY = 100;
	private static final Color STALE_DOT = new Color(0xF0, 0xA3, 0x30);
	private static final Color WARN_TRIANGLE = new Color(0xD0, 0x42, 0x42);
	private static final Color SP_HIGH = new Color(100, 220, 100);
	private static final Color SP_LOW = new Color(220, 100, 100);
	private static final Color SP_GOLD = new Color(255, 200, 0);
	private static final Color SP_MUTED = new Color(150, 150, 150);
	private static final Color STAR_ON = new Color(0xF0, 0xC0, 0x40);
	private static final Color LOCKED_FG = new Color(0x80, 0x80, 0x80);
	private static final String STALE_TOOLTIP =
			"Price may be stale (from the 1h fallback or a low-volume item)";
	private static final String[] MODE_LABELS = {"On-hand", "Buy to order", "Hybrid"};
	private static final SourcingMode[] MODES =
			{SourcingMode.ON_HAND, SourcingMode.BUY_TO_ORDER, SourcingMode.HYBRID};
	private static final String[] SORT_LABELS =
			{"Profit/ea", "GP/hr", "XP/hr", "ROI %", "Success %", "Level", "Volume", "Makeable"};
	private static final String ALL_SKILLS = "All skills";
	private static final int GATING_HIDE = 0;
	private static final int GATING_GREY = 1;
	private static final int MEMBERS_ALL = 0;
	private static final int MEMBERS_F2P = 1;
	private static final int MEMBERS_P2P = 2;

	private final JComboBox<String> modeSelector = new JComboBox<>(MODE_LABELS);
	private final JComboBox<String> valuationSelector =
			new JComboBox<>(new String[]{"GE market", "Ironman (v2)"});
	private final JComboBox<String> skillSelector = new JComboBox<>(new String[]{ALL_SKILLS});
	private final JComboBox<String> gatingSelector =
			new JComboBox<>(new String[]{"Hide locked", "Grey locked", "Show all"});
	private final JComboBox<String> membersSelector =
			new JComboBox<>(new String[]{"All worlds", "F2P only", "Members only"});
	private final JComboBox<String> sortSelector = new JComboBox<>(SORT_LABELS);
	private final JComboBox<String> densitySelector = new JComboBox<>(new String[]{"Cards", "Compact"});
	private final JTextField searchField = new JTextField();
	private final JLabel modifierLine = new JLabel("Modifiers: none");

	private static final int DEFAULT_SHOPPING_QTY = 28;

	private final JPanel browseRows = listPanel();
	private final JPanel onHandRows = listPanel();
	private final JPanel watchlistRows = listPanel();
	private final JPanel shoppingRows = listPanel();

	private final Map<JPanel, WidthTrackingPanel> wrappers = new HashMap<>();
	private final Map<JPanel, Integer> tabLimit = new HashMap<>();

	private List<RecipeRow> browseData = Collections.emptyList();
	private List<RecipeRow> onHandData = Collections.emptyList();
	private List<RecipeRow> watchlistData = Collections.emptyList();
	private ShoppingList shoppingList = new ShoppingList(Collections.emptyList(), 0L, 0, true);
	private List<String> shoppingSummary = Collections.emptyList();

	private final JPanel center = new JPanel(new CardLayout());
	private final JPanel detailHolder = new JPanel(new BorderLayout());

	private MaterialTabGroup tabGroup;
	private MaterialTab shoppingTab;

	private Consumer<SourcingMode> modeListener;
	private Consumer<RecipeRow> selectionListener;
	private Consumer<ShoppingRequest> shoppingAddListener;
	private Consumer<RecipeRow> pinToggleListener;
	private Runnable shoppingClearListener;
	private Set<String> pinnedIds = Collections.emptySet();
	private RecipeRow detailRow;
	private RecipeDetail currentDetail;
	private boolean rebuildingSkills;

	/**
	 * Builds the panel layout.
	 */
	@Inject
	public ProcessingProfitPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(new EmptyBorder(8, 0, 6, 0));
		MaterialTab onHand = new MaterialTab("On-hand", tabGroup, scroll(onHandRows));
		MaterialTab watchlist = new MaterialTab("Watch", tabGroup, scroll(watchlistRows));
		MaterialTab browse = new MaterialTab("Browse", tabGroup, scroll(browseRows));
		shoppingTab = new MaterialTab("Shop", tabGroup, scroll(shoppingRows));
		tabGroup.addTab(onHand);
		tabGroup.addTab(watchlist);
		tabGroup.addTab(browse);
		tabGroup.addTab(shoppingTab);

		detailHolder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.add(display, "list");
		center.add(detailHolder, "detail");

		add(header(tabGroup), BorderLayout.NORTH);
		add(center, BorderLayout.CENTER);

		wireControls();
		tabGroup.select(onHand);
		renderAll();
		renderShopping();
	}

	private void wireControls()
	{
		modeSelector.addActionListener(e -> fireModeChanged());
		sortSelector.addActionListener(e -> renderAll());
		densitySelector.addActionListener(e -> renderAll());
		gatingSelector.addActionListener(e -> renderAll());
		membersSelector.addActionListener(e -> renderAll());
		skillSelector.addActionListener(e ->
		{
			if (!rebuildingSkills)
				renderAll();
		});
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				renderAll();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				renderAll();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				renderAll();
			}
		});
	}

	private JPanel header(MaterialTabGroup tabGroup)
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		styleCombo(modeSelector);
		styleCombo(valuationSelector);
		styleCombo(skillSelector);
		styleCombo(gatingSelector);
		styleCombo(membersSelector);
		styleCombo(sortSelector);
		styleCombo(densitySelector);
		styleSearch(searchField);

		header.add(labelledRow("Sourcing", modeSelector));
		header.add(labelledRow("Valuation", valuationSelector));

		modifierLine.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		modifierLine.setFont(FontManager.getRunescapeSmallFont());
		modifierLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		modifierLine.setBorder(new EmptyBorder(6, 0, 0, 0));
		header.add(modifierLine);

		header.add(labelledRow("Search", searchField));
		header.add(labelledRow("Skill", skillSelector));
		header.add(labelledRow("Gating", gatingSelector));
		header.add(labelledRow("Members", membersSelector));
		header.add(labelledRow("Sort", sortSelector));
		header.add(labelledRow("View", densitySelector));

		tabGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(tabGroup);
		return header;
	}

	private static void styleCombo(JComboBox<String> combo)
	{
		combo.setFont(FontManager.getRunescapeSmallFont());
		combo.setFocusable(false);
		combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		combo.setForeground(Color.WHITE);
	}

	private static void styleSearch(JTextField field)
	{
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setBorder(new EmptyBorder(3, 5, 3, 5));
	}

	private static JPanel labelledRow(String text, JComponent field)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 0, 0, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		row.add(label, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		capHeight(row);
		return row;
	}

	private static JPanel listPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return panel;
	}

	private JScrollPane scroll(JPanel rows)
	{
		WidthTrackingPanel wrap = new WidthTrackingPanel(rows);
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrappers.put(rows, wrap);

		JScrollPane scroll = new JScrollPane(wrap,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.setBorder(BorderFactory.createEmptyBorder());

		JScrollBar vertical = scroll.getVerticalScrollBar();
		vertical.setUI((ScrollBarUI) RuneLiteScrollBarUI.createUI(vertical));
		vertical.setUnitIncrement(16);
		return scroll;
	}

	/**
	 * A scroll view whose width tracks the viewport so rows fill the panel width and their labels
	 * ellipsize inside it instead of overflowing past the right edge (there is no horizontal scrollbar).
	 * It normally pins the row list to the top; when a tab is empty it swaps to a single centered
	 * component and reports that it tracks the viewport height too, so the "Nothing to show" placeholder
	 * centres both horizontally and vertically.
	 */
	private static final class WidthTrackingPanel extends JPanel implements Scrollable
	{
		private final JPanel rows;
		private boolean fillHeight;

		private WidthTrackingPanel(JPanel rows)
		{
			super(new BorderLayout());
			this.rows = rows;
			add(rows, BorderLayout.NORTH);
		}

		private void showList()
		{
			removeAll();
			add(rows, BorderLayout.NORTH);
			fillHeight = false;
		}

		private void showCentered(Component centered)
		{
			removeAll();
			add(centered, BorderLayout.CENTER);
			fillHeight = true;
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return fillHeight;
		}
	}

	/**
	 * Caps a component's maximum height to its preferred (font-scaled) height so a vertical BoxLayout
	 * does not stretch it.
	 *
	 * @param component the component to cap
	 */
	private static void capHeight(JComponent component)
	{
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
	}

	/**
	 * Registers a callback fired when the sourcing mode changes.
	 *
	 * @param listener the callback, invoked with the newly selected mode
	 */
	public void setModeListener(Consumer<SourcingMode> listener)
	{
		this.modeListener = listener;
	}

	/**
	 * Registers a callback fired when a recipe row is clicked, so the plugin can build and push its
	 * {@link RecipeDetail} breakdown.
	 *
	 * @param listener the callback, invoked with the clicked row
	 */
	public void setSelectionListener(Consumer<RecipeRow> listener)
	{
		this.selectionListener = listener;
	}

	/**
	 * The currently selected sourcing mode.
	 *
	 * @return the selected mode
	 */
	public SourcingMode selectedMode()
	{
		return MODES[modeSelector.getSelectedIndex()];
	}

	private void fireModeChanged()
	{
		if (modeListener != null)
			modeListener.accept(selectedMode());
	}

	private void fireSelection(RecipeRow row)
	{
		detailRow = row;
		if (selectionListener != null)
			selectionListener.accept(row);
	}

	/**
	 * Registers a callback fired when the user adds a product to the shopping list from the detail view.
	 *
	 * @param listener the callback, invoked with the product recipe and target quantity
	 */
	public void setShoppingAddListener(Consumer<ShoppingRequest> listener)
	{
		this.shoppingAddListener = listener;
	}

	/**
	 * Registers a callback fired when the user clears the shopping list.
	 *
	 * @param listener the callback
	 */
	public void setShoppingClearListener(Runnable listener)
	{
		this.shoppingClearListener = listener;
	}

	/**
	 * Registers a callback fired when the user pins or unpins a recipe (from a row star or the detail
	 * view). The plugin toggles and persists the watchlist, then pushes the new pinned set back via
	 * {@link #setPinned(Set)}.
	 *
	 * @param listener the callback, invoked with the toggled row
	 */
	public void setPinToggleListener(Consumer<RecipeRow> listener)
	{
		this.pinToggleListener = listener;
	}

	/**
	 * Sets which recipe ids are pinned, so row stars and the detail toggle render the current state.
	 *
	 * @param ids the pinned recipe ids
	 */
	public void setPinned(Set<String> ids)
	{
		SwingUtilities.invokeLater(() ->
		{
			pinnedIds = ids == null ? Collections.emptySet() : ids;
			renderAll();
			renderDetail();
		});
	}

	private void fireTogglePin(RecipeRow row)
	{
		if (pinToggleListener != null && row != null && row.getRecipe() != null)
			pinToggleListener.accept(row);
	}

	private boolean isPinned(RecipeRow row)
	{
		return row.getRecipe() != null && pinnedIds.contains(row.getRecipe().getRecipeId());
	}

	/**
	 * Sets the active-modifier status line text.
	 *
	 * @param text the status text (e.g. "Gauntlets, Hosidius elite")
	 */
	public void setModifierText(String text)
	{
		SwingUtilities.invokeLater(() -> modifierLine.setText("Modifiers: " + text));
	}

	/**
	 * Replaces the Browse tab data.
	 *
	 * @param rows the rows to show
	 */
	public void setBrowseRows(List<RecipeRow> rows)
	{
		SwingUtilities.invokeLater(() ->
		{
			browseData = rows == null ? Collections.emptyList() : rows;
			tabLimit.remove(browseRows);
			rebuildSkillOptions();
			renderAll();
		});
	}

	/**
	 * Replaces the On-hand tab data.
	 *
	 * @param rows the rows to show
	 */
	public void setOnHandRows(List<RecipeRow> rows)
	{
		SwingUtilities.invokeLater(() ->
		{
			onHandData = rows == null ? Collections.emptyList() : rows;
			tabLimit.remove(onHandRows);
			renderTab(onHandRows, onHandData);
		});
	}

	/**
	 * Replaces the Watchlist tab data.
	 *
	 * @param rows the rows to show
	 */
	public void setWatchlistRows(List<RecipeRow> rows)
	{
		SwingUtilities.invokeLater(() ->
		{
			watchlistData = rows == null ? Collections.emptyList() : rows;
			tabLimit.remove(watchlistRows);
			renderTab(watchlistRows, watchlistData);
		});
	}

	/**
	 * Replaces the Shopping tab's aggregated material list and its queued-product summary.
	 *
	 * @param list    the shopping list to show
	 * @param summary one line per queued product (e.g. "28&times; Cannonball")
	 */
	public void setShoppingList(ShoppingList list, List<String> summary)
	{
		SwingUtilities.invokeLater(() ->
		{
			shoppingList = list == null ? new ShoppingList(Collections.emptyList(), 0L, 0, true) : list;
			shoppingSummary = summary == null ? Collections.emptyList() : summary;
			renderShopping();
		});
	}

	private void renderAll()
	{
		renderTab(browseRows, browseData);
		renderTab(onHandRows, onHandData);
		renderTab(watchlistRows, watchlistData);
	}

	private void renderShopping()
	{
		shoppingRows.removeAll();
		shoppingRows.add(shoppingHeaderRow());
		if (!shoppingSummary.isEmpty())
			shoppingRows.add(section("Making", shoppingSummary));

		List<ShoppingLine> lines = shoppingList.getLines();
		if (lines.isEmpty())
		{
			shoppingRows.add(placeholder(shoppingSummary.isEmpty()
					? "No shopping list yet. Open a recipe and press ➕ Add."
					: "Held stock covers the whole list — nothing to buy."));
		}
		else
		{
			for (ShoppingLine line : lines)
				shoppingRows.add(shoppingLineComponent(line));

			shoppingRows.add(shoppingTotalRow());
		}

		shoppingRows.revalidate();
		shoppingRows.repaint();
	}

	private JPanel shoppingHeaderRow()
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 2, 4, 2));

		JLabel title = new JLabel("Shopping list");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		row.add(title, BorderLayout.WEST);

		if (!shoppingSummary.isEmpty())
			row.add(smallButton("Clear", e -> fireShoppingClear()), BorderLayout.EAST);

		capHeight(row);
		return row;
	}

	private JPanel shoppingLineComponent(ShoppingLine line)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 1));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(0, 0, 2, 0),
				new EmptyBorder(3, 7, 3, 7)));

		JLabel name = new JLabel(line.getToBuy() + "× " + line.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel cost = new JLabel(line.isPriced() ? num(line.getTotalCost()) : "?");
		cost.setFont(FontManager.getRunescapeSmallFont());
		cost.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		cost.setHorizontalAlignment(SwingConstants.RIGHT);

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.add(name, BorderLayout.CENTER);
		top.add(cost, BorderLayout.EAST);
		panel.add(top, BorderLayout.NORTH);

		JLabel meta = new JLabel(shoppingMetaText(line));
		meta.setFont(FontManager.getRunescapeSmallFont());
		meta.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		panel.add(meta, BorderLayout.SOUTH);

		capHeight(panel);
		return panel;
	}

	private static String shoppingMetaText(ShoppingLine line)
	{
		StringBuilder sb = new StringBuilder();
		sb.append('@');
		sb.append(line.isPriced() ? num(line.getUnitPrice()) : "?");
		if (line.getHeld() > 0)
		{
			sb.append("  held ");
			sb.append(line.getHeld());
		}

		if (line.getBuyLimit() > 0)
		{
			sb.append("  lim ");
			sb.append(line.getBuyLimit());
			sb.append(" (");
			sb.append(line.getWindows());
			sb.append("w)");
		}

		return sb.toString();
	}

	private JLabel shoppingTotalRow()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Total ");
		sb.append(num(shoppingList.getTotalCost()));
		sb.append(" gp");
		int windows = shoppingList.getMaxWindows();
		if (windows > 0)
		{
			sb.append("  ·  ");
			sb.append(windows);
			sb.append(windows == 1 ? " window (~4h)" : " windows (~" + (windows * 4) + "h)");
		}

		if (!shoppingList.isFullyPriced())
			sb.append("  ·  some prices unknown");

		JLabel label = new JLabel(sb.toString());
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(new EmptyBorder(6, 2, 4, 2));
		return label;
	}

	private void fireShoppingClear()
	{
		if (shoppingClearListener != null)
			shoppingClearListener.run();
	}

	private void fireShoppingAdd(RecipeRow row, int qty)
	{
		if (shoppingAddListener != null && row != null && row.getRecipe() != null)
		{
			shoppingAddListener.accept(new ShoppingRequest(row.getRecipe(), qty));
			showList();
			if (tabGroup != null && shoppingTab != null)
				tabGroup.select(shoppingTab);
		}
	}

	private static int resolveQty(String text, int makeable)
	{
		try
		{
			return Math.max(1, Integer.parseInt(text.trim()));
		}
		catch (NumberFormatException e)
		{
			return makeable > 0 ? makeable : DEFAULT_SHOPPING_QTY;
		}
	}

	private static JButton smallButton(String text, ActionListener listener)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusable(false);
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(Color.WHITE);
		button.setBorder(new EmptyBorder(3, 8, 3, 8));
		button.addActionListener(listener);
		return button;
	}

	private void renderTab(JPanel container, List<RecipeRow> data)
	{
		container.removeAll();
		WidthTrackingPanel wrap = wrappers.get(container);
		List<RecipeRow> view = filterAndSort(data);
		if (view.isEmpty())
		{
			String message = data.isEmpty() ? "Nothing to show yet." : "No matches.";
			if (wrap != null)
				wrap.showCentered(centeredPlaceholder(message));
			else
				container.add(placeholder(message));
		}
		else
		{
			if (wrap != null)
				wrap.showList();

			boolean compact = densitySelector.getSelectedIndex() == 1;
			int limit = tabLimit.getOrDefault(container, MAX_DISPLAY);
			int shown = Math.min(view.size(), limit);
			for (int i = 0; i < shown; i++)
				container.add(rowComponent(view.get(i), compact));

			if (view.size() > shown)
				container.add(moreRow(container, data, view.size() - shown));
		}

		container.revalidate();
		container.repaint();
		if (wrap != null)
		{
			wrap.revalidate();
			wrap.repaint();
		}
	}

	private JComponent moreRow(JPanel container, List<RecipeRow> data, int remaining)
	{
		JLabel more = new JLabel("… and " + remaining + " more — click to show all");
		more.setForeground(ColorScheme.BRAND_ORANGE);
		more.setFont(FontManager.getRunescapeSmallFont());
		more.setBorder(new EmptyBorder(8, 2, 8, 2));
		more.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		more.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				tabLimit.put(container, Integer.MAX_VALUE);
				renderTab(container, data);
			}
		});
		return more;
	}

	private static JPanel centeredPlaceholder(String text)
	{
		JPanel wrap = new JPanel(new GridBagLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.add(placeholder(text));
		return wrap;
	}

	private List<RecipeRow> filterAndSort(List<RecipeRow> data)
	{
		String rawQuery = searchField.getText().trim();
		String query = rawQuery.toLowerCase();
		String skill = (String) skillSelector.getSelectedItem();
		boolean allSkills = skill == null || ALL_SKILLS.equals(skill);

		boolean hideLocked = gatingSelector.getSelectedIndex() == GATING_HIDE;
		int membersFilter = membersSelector.getSelectedIndex();
		List<RecipeRow> out = new ArrayList<>();
		for (RecipeRow row : data)
		{
			if (!allSkills && !skill.equalsIgnoreCase(row.getSkill()))
				continue;

			if (hideLocked && row.isLocked())
				continue;

			if (membersFilter == MEMBERS_F2P && row.isMembers())
				continue;

			if (membersFilter == MEMBERS_P2P && !row.isMembers())
				continue;

			if (!query.isEmpty() && !row.getProduct().toLowerCase().contains(query))
				continue;

			out.add(row);
		}

		out.sort(comparatorFor(sortSelector.getSelectedIndex()));
		return out;
	}

	private static Comparator<RecipeRow> comparatorFor(int sortIndex)
	{
		switch (sortIndex)
		{
			case 1:
				return gpPerHourComparator();
			case 2:
				return (a, b) -> Double.compare(b.getXpPerHour(), a.getXpPerHour());
			case 3:
				return (a, b) -> Double.compare(b.getRoi(), a.getRoi());
			case 4:
				return successComparator();
			case 5:
				return (a, b) -> Integer.compare(a.getLevelReq(), b.getLevelReq());
			case 6:
				return (a, b) -> Long.compare(b.getVolume(), a.getVolume());
			case 7:
				return (a, b) -> Integer.compare(b.getMakeableNow(), a.getMakeableNow());
			case 0:
			default:
				return (a, b) -> Long.compare(b.getProfitEach(), a.getProfitEach());
		}
	}

	private static Comparator<RecipeRow> gpPerHourComparator()
	{
		return (a, b) ->
		{
			if (a.isThroughputKnown() != b.isThroughputKnown())
				return a.isThroughputKnown() ? -1 : 1;

			return Long.compare(b.getGpPerHour(), a.getGpPerHour());
		};
	}

	private static Comparator<RecipeRow> successComparator()
	{
		return (a, b) ->
		{
			double sa = a.getSuccessPercent() == null ? -1.0 : a.getSuccessPercent();
			double sb = b.getSuccessPercent() == null ? -1.0 : b.getSuccessPercent();
			return Double.compare(sb, sa);
		};
	}

	private void rebuildSkillOptions()
	{
		Set<String> skills = new TreeSet<>();
		for (RecipeRow row : browseData)
			if (row.getSkill() != null && !row.getSkill().isEmpty())
				skills.add(row.getSkill());

		Set<String> options = new LinkedHashSet<>();
		options.add(ALL_SKILLS);
		options.addAll(skills);

		String selected = (String) skillSelector.getSelectedItem();
		rebuildingSkills = true;
		skillSelector.removeAllItems();
		for (String option : options)
			skillSelector.addItem(option);

		if (selected != null && options.contains(selected))
			skillSelector.setSelectedItem(selected);

		rebuildingSkills = false;
	}

	private static JLabel placeholder(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(8, 2, 8, 2));
		return label;
	}

	private JPanel rowComponent(RecipeRow row, boolean compact)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(0, 0, compact ? 2 : 3, 0),
				new EmptyBorder(compact ? 3 : 5, 7, compact ? 3 : 5, 7)));
		panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		panel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				fireSelection(row);
			}
		});
		boolean greyed = row.isLocked() && gatingSelector.getSelectedIndex() == GATING_GREY;
		String tip = rowTooltip(row);
		if (tip != null)
			panel.setToolTipText(tip);

		if (compact)
			fillCompactRow(panel, row, greyed);
		else
			fillCardRow(panel, row, greyed);

		capHeight(panel);
		return panel;
	}

	private void fillCompactRow(JPanel panel, RecipeRow row, boolean greyed)
	{
		JLabel name = new JLabel(row.getProduct());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(greyed ? LOCKED_FG : Color.WHITE);
		panel.add(name, BorderLayout.CENTER);

		JPanel trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		trailing.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		trailing.add(profitLabel(row, greyed, false));
		if (row.getRecipe() != null)
			trailing.add(pinStar(row, greyed));

		panel.add(trailing, BorderLayout.EAST);
	}

	private void fillCardRow(JPanel panel, RecipeRow row, boolean greyed)
	{
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel name = new JLabel(row.getProduct());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(greyed ? LOCKED_FG : Color.WHITE);
		top.add(name, BorderLayout.CENTER);
		if (row.getRecipe() != null)
		{
			JPanel starWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
			starWrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			starWrap.add(pinStar(row, greyed));
			top.add(starWrap, BorderLayout.EAST);
		}

		capHeight(top);

		JPanel bottom = new JPanel(new BorderLayout(6, 0));
		bottom.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
		bottom.setBorder(new EmptyBorder(2, 0, 0, 0));
		bottom.add(profitLabel(row, greyed, true), BorderLayout.WEST);

		JPanel meta = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		meta.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		if (row.getRecipeCount() > 1)
			meta.add(rcpsLabel(row, greyed));

		if (row.isStale())
			meta.add(staleDot(greyed));

		if (row.isThrottled() || row.isLowVolume())
			meta.add(warnTriangle(row, greyed));

		bottom.add(meta, BorderLayout.EAST);
		capHeight(bottom);

		JPanel stack = new JPanel();
		stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
		stack.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		stack.add(top);
		stack.add(bottom);
		panel.add(stack, BorderLayout.CENTER);
	}

	private JLabel profitLabel(RecipeRow row, boolean greyed, boolean range)
	{
		String text = range && row.getProfitMin() != row.getProfitMax()
				? signed(row.getProfitMin()) + " to " + signed(row.getProfitMax())
				: signed(row.getProfitEach());
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(greyed ? LOCKED_FG : (row.getProfitEach() >= 0
				? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR));
		return label;
	}

	private static JLabel rcpsLabel(RecipeRow row, boolean greyed)
	{
		JLabel label = new JLabel("Rcps: " + row.getRecipeCount());
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(greyed ? LOCKED_FG : ColorScheme.LIGHT_GRAY_COLOR);
		label.setToolTipText(row.getRecipeCount() + " recipes make this item");
		return label;
	}

	private static JLabel warnTriangle(RecipeRow row, boolean greyed)
	{
		JLabel triangle = new JLabel("▲");
		triangle.setFont(FontManager.getRunescapeSmallFont());
		triangle.setForeground(greyed ? LOCKED_FG : WARN_TRIANGLE);
		triangle.setToolTipText(warningText(row));
		return triangle;
	}

	private static String signed(long value)
	{
		String magnitude = QuantityFormatter.quantityToStackSize(Math.abs(value));
		return (value >= 0 ? "+" : "-") + magnitude;
	}

	private JLabel pinStar(RecipeRow row, boolean greyed)
	{
		boolean pinned = isPinned(row);
		Color base = greyed ? LOCKED_FG : (pinned ? STAR_ON : ColorScheme.LIGHT_GRAY_COLOR);
		JLabel star = new JLabel(pinned ? "★" : "☆");
		star.setFont(FontManager.getRunescapeSmallFont());
		star.setForeground(base);
		star.setToolTipText(pinned ? "Unpin from watchlist" : "Pin to watchlist");
		star.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		star.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				fireTogglePin(row);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				star.setText("★");
				star.setForeground(pinned ? STAR_ON.brighter() : STAR_ON);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				star.setText(pinned ? "★" : "☆");
				star.setForeground(base);
			}
		});
		return star;
	}

	private static JLabel staleDot(boolean greyed)
	{
		JLabel dot = new JLabel("●");
		dot.setFont(FontManager.getRunescapeSmallFont());
		dot.setForeground(greyed ? LOCKED_FG : STALE_DOT);
		dot.setToolTipText(STALE_TOOLTIP);
		return dot;
	}

	private static String warningText(RecipeRow row)
	{
		if (row.isThrottled() && row.isLowVolume())
			return "Buy-limit throttled (~" + row.getBuyLimitPerWindow() + "/4h) and low volume";

		if (row.isThrottled())
			return "Buy-limit throttled (~" + row.getBuyLimitPerWindow() + "/4h)";

		return "Low trade volume";
	}

	private static String rowTooltip(RecipeRow row)
	{
		if (row.isLocked() && row.getLockReason() != null)
			return "Locked — " + row.getLockReason();

		StringBuilder sb = new StringBuilder();
		if (row.isThrottled() || row.isLowVolume())
			sb.append(warningText(row));

		if (row.isStale())
		{
			if (sb.length() > 0)
				sb.append("; ");

			sb.append(STALE_TOOLTIP);
		}

		return sb.length() > 0 ? sb.toString() : null;
	}

	/**
	 * Shows the full breakdown for one recipe in place of the tab list, with a Back button.
	 *
	 * @param detail the breakdown to render
	 */
	public void showDetail(RecipeDetail detail)
	{
		SwingUtilities.invokeLater(() ->
		{
			currentDetail = detail;
			renderDetail();
			((CardLayout) center.getLayout()).show(center, "detail");
		});
	}

	private void renderDetail()
	{
		if (currentDetail == null)
			return;

		detailHolder.removeAll();
		detailHolder.add(detailCard(currentDetail), BorderLayout.CENTER);
		detailHolder.revalidate();
		detailHolder.repaint();
	}

	private void showList()
	{
		((CardLayout) center.getLayout()).show(center, "list");
	}

	private JScrollPane detailCard(RecipeDetail d)
	{
		JPanel content = listPanel();
		content.setBorder(new EmptyBorder(2, 2, 2, 2));

		content.add(backButton());
		content.add(detailTitle(d.getProduct()));
		content.add(headline(signed(d.getProfitEach()) + " /ea",
				d.getProfitEach() >= 0 ? SP_HIGH : SP_LOW));

		List<String> dims = new ArrayList<>();
		dims.add("GP/hr: " + (d.isThroughputKnown() ? num(d.getGpPerHour()) : "n/a"));
		dims.add("XP/hr: " + (d.isThroughputKnown() ? num(Math.round(d.getXpPerHour())) : "n/a"));
		dims.add("Profit/XP: " + Math.round(d.getProfitPerXp()));
		dims.add("ROI: " + Math.round(d.getRoi() * 100) + "%");
		content.add(section("Throughput", dims));

		content.add(section("Cost breakdown", costLines(d)));
		if (d.isFailCapable())
			content.add(section("Success @ level " + d.getLevel(), successLines(d)));

		content.add(section("Break-even buy price", breakEvenLines(d)));
		if (detailRow != null)
			content.add(section("Liquidity", liquidityLines(detailRow)));

		if (d.isStale())
			content.add(section("Note", Collections.singletonList(STALE_TOOLTIP)));

		if (detailRow != null && detailRow.isLocked() && detailRow.getLockReason() != null)
			content.add(section("Locked", Collections.singletonList(detailRow.getLockReason())));

		content.add(watchlistButton());
		content.add(shoppingActions());
		return scroll(content);
	}

	private JButton watchlistButton()
	{
		RecipeRow row = detailRow;
		boolean pinned = row != null && isPinned(row);
		JButton button = smallButton(pinned ? "★ Pinned" : "☆ Watchlist", e -> fireTogglePin(row));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(8, 2, 0, 2),
				new EmptyBorder(3, 8, 3, 8)));
		return button;
	}

	private JPanel shoppingActions()
	{
		JPanel panel = listPanel();
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(new EmptyBorder(8, 2, 0, 2));

		JLabel header = new JLabel("Shopping list");
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(SP_GOLD);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);

		RecipeRow row = detailRow;
		int makeable = row != null ? row.getMakeableNow() : -1;
		JTextField qty = new JTextField(makeable > 0 ? String.valueOf(makeable) : "");
		styleSearch(qty);

		JPanel controls = new JPanel(new BorderLayout(6, 0));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(new EmptyBorder(2, 0, 0, 0));
		controls.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel label = new JLabel("Target qty");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		controls.add(label, BorderLayout.WEST);
		controls.add(qty, BorderLayout.CENTER);
		controls.add(smallButton("➕ Add", e -> fireShoppingAdd(row, resolveQty(qty.getText(), makeable))),
				BorderLayout.EAST);
		capHeight(controls);
		panel.add(controls);

		capHeight(panel);
		return panel;
	}

	private JButton backButton()
	{
		JButton back = new JButton("← Back");
		back.setFont(FontManager.getRunescapeSmallFont());
		back.setFocusable(false);
		back.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		back.setForeground(Color.WHITE);
		back.setBorder(new EmptyBorder(3, 6, 3, 6));
		back.setAlignmentX(Component.LEFT_ALIGNMENT);
		back.addActionListener(e -> showList());
		capHeight(back);
		return back;
	}

	private static JLabel detailTitle(String product)
	{
		JLabel label = new JLabel(product);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(new EmptyBorder(8, 2, 0, 2));
		return label;
	}

	private static JLabel headline(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(color);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(new EmptyBorder(0, 2, 4, 2));
		return label;
	}

	private static JPanel section(String title, List<String> lines)
	{
		JPanel panel = listPanel();
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(new EmptyBorder(6, 2, 0, 2));

		JLabel header = new JLabel(title);
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(SP_GOLD);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(header);

		for (String line : lines)
			panel.add(detailLine(line));

		if (lines.isEmpty())
			panel.add(detailLine("—"));

		capHeight(panel);
		return panel;
	}

	private static JLabel detailLine(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(SP_MUTED);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(new EmptyBorder(2, 6, 0, 0));
		return label;
	}

	private static List<String> costLines(RecipeDetail d)
	{
		List<String> lines = new ArrayList<>();
		for (CostLine in : d.getInputs())
			lines.add(costText(in));

		if (!d.getTools().isEmpty())
			lines.add("Tools: " + String.join(", ", d.getTools()));

		lines.add("Input cost: -" + num(d.getInputCost()));
		for (CostLine out : d.getOutputs())
			lines.add(costText(out));

		lines.add("Gross: +" + num(d.getGrossOutput()));
		lines.add("GE tax: -" + num(d.getTax()));
		if (d.isFailCapable())
			lines.add("Success-weighted net: " + num(d.getExpectedNet()));

		lines.add("Profit/ea: " + signed(d.getProfitEach()));
		return lines;
	}

	private static String costText(CostLine line)
	{
		String unit = line.getUnitPrice() == PriceLookup.UNKNOWN ? "?" : num(line.getUnitPrice());
		String total = line.getTotal() == PriceLookup.UNKNOWN ? "?" : num(line.getTotal());
		return line.getQty() + "× " + line.getLabel() + "  @" + unit + " = " + total;
	}

	private static List<String> successLines(RecipeDetail d)
	{
		List<String> lines = new ArrayList<>();
		if (d.getBaseChance() != null)
			lines.add("Base chance: " + percent(d.getBaseChance()));

		lines.addAll(d.getModifierNotes());
		if (d.getFinalChance() != null)
			lines.add("Final chance: " + percent(d.getFinalChance()));

		if (d.getYieldMult() != 1.0)
			lines.add("Yield: ×" + d.getYieldMult());

		if (d.getFailureLabel() != null)
			lines.add("On failure: " + d.getFailureLabel());

		return lines;
	}

	private static List<String> breakEvenLines(RecipeDetail d)
	{
		List<String> lines = new ArrayList<>();
		for (CostLine be : d.getBreakEven())
			lines.add(be.getLabel() + ": " + num(be.getUnitPrice()));

		return lines;
	}

	private static List<String> liquidityLines(RecipeRow row)
	{
		List<String> lines = new ArrayList<>();
		lines.add("Buy limit: " + (row.getBuyLimitPerWindow() > 0
				? num(row.getBuyLimitPerWindow()) + " / 4h window" : "unlimited"));
		lines.add("Volume: " + num(row.getVolume()));
		if (row.isThrottled())
			lines.add("▲ Buy-limit throttled — small realistic throughput");

		if (row.isLowVolume())
			lines.add("▲ Low trade volume — may be slow to buy/sell");

		return lines;
	}

	private static String percent(double chance)
	{
		return Math.round(chance * 100) + "%";
	}

	private static String num(long value)
	{
		return String.format("%,d", value);
	}
}
