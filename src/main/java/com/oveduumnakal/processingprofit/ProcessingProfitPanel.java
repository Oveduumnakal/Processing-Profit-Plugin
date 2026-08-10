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
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
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
 * line, a filter/sort bar (search, skill, sort key, density), and four tabs &mdash; Browse, On-hand,
 * Watchlist and Shopping. Each tab holds the full {@link RecipeRow} set pushed by the plugin and renders
 * a filtered, sorted, display-capped view locally so sorting and searching are instant. Uses the
 * RuneLite fonts, colour scheme and themed scrollbar so it scales with the client.
 */
@Singleton
public class ProcessingProfitPanel extends PluginPanel
{
	private static final int MAX_DISPLAY = 100;
	private static final Color STALE_DOT = new Color(0xF0, 0xA3, 0x30);
	private static final String STALE_TOOLTIP =
			"Price may be stale (from the 1h fallback or a low-volume item)";
	private static final String[] MODE_LABELS = {"On-hand", "Buy to order", "Hybrid"};
	private static final SourcingMode[] MODES =
			{SourcingMode.ON_HAND, SourcingMode.BUY_TO_ORDER, SourcingMode.HYBRID};
	private static final String[] SORT_LABELS =
			{"Profit/ea", "GP/hr", "XP/hr", "ROI %", "Success %", "Level", "Volume", "Makeable"};
	private static final String ALL_SKILLS = "All skills";

	private final JComboBox<String> modeSelector = new JComboBox<>(MODE_LABELS);
	private final JComboBox<String> valuationSelector =
			new JComboBox<>(new String[]{"GE market", "Ironman (v2)"});
	private final JComboBox<String> skillSelector = new JComboBox<>(new String[]{ALL_SKILLS});
	private final JComboBox<String> sortSelector = new JComboBox<>(SORT_LABELS);
	private final JComboBox<String> densitySelector = new JComboBox<>(new String[]{"Cards", "Compact"});
	private final JTextField searchField = new JTextField();
	private final JLabel modifierLine = new JLabel("Modifiers: none");

	private final JPanel browseRows = listPanel();
	private final JPanel onHandRows = listPanel();
	private final JPanel watchlistRows = listPanel();
	private final JPanel shoppingRows = listPanel();

	private List<RecipeRow> browseData = Collections.emptyList();
	private List<RecipeRow> onHandData = Collections.emptyList();
	private List<RecipeRow> watchlistData = Collections.emptyList();
	private List<RecipeRow> shoppingData = Collections.emptyList();

	private Consumer<SourcingMode> modeListener;
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

		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(new EmptyBorder(8, 0, 6, 0));
		MaterialTab browse = new MaterialTab("Browse", tabGroup, scroll(browseRows));
		MaterialTab onHand = new MaterialTab("On-hand", tabGroup, scroll(onHandRows));
		MaterialTab watchlist = new MaterialTab("Watch", tabGroup, scroll(watchlistRows));
		MaterialTab shopping = new MaterialTab("Shop", tabGroup, scroll(shoppingRows));
		tabGroup.addTab(browse);
		tabGroup.addTab(onHand);
		tabGroup.addTab(watchlist);
		tabGroup.addTab(shopping);

		add(header(tabGroup), BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		wireControls();
		tabGroup.select(browse);
		renderAll();
	}

	private void wireControls()
	{
		modeSelector.addActionListener(e -> fireModeChanged());
		sortSelector.addActionListener(e -> renderAll());
		densitySelector.addActionListener(e -> renderAll());
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

	private static JScrollPane scroll(JPanel rows)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.add(rows, BorderLayout.NORTH);

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
			renderTab(onHandRows, onHandData, true);
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
			renderTab(watchlistRows, watchlistData, false);
		});
	}

	/**
	 * Replaces the Shopping tab data.
	 *
	 * @param rows the rows to show
	 */
	public void setShoppingRows(List<RecipeRow> rows)
	{
		SwingUtilities.invokeLater(() ->
		{
			shoppingData = rows == null ? Collections.emptyList() : rows;
			renderTab(shoppingRows, shoppingData, true);
		});
	}

	private void renderAll()
	{
		renderTab(browseRows, browseData, false);
		renderTab(onHandRows, onHandData, true);
		renderTab(watchlistRows, watchlistData, false);
		renderTab(shoppingRows, shoppingData, true);
	}

	private void renderTab(JPanel container, List<RecipeRow> data, boolean showMakeable)
	{
		container.removeAll();
		List<RecipeRow> view = filterAndSort(data);
		if (view.isEmpty())
		{
			container.add(placeholder(data.isEmpty() ? "Nothing to show yet." : "No matches."));
		}
		else
		{
			boolean compact = densitySelector.getSelectedIndex() == 1;
			int shown = Math.min(view.size(), MAX_DISPLAY);
			for (int i = 0; i < shown; i++)
				container.add(rowComponent(view.get(i), showMakeable, compact));

			if (view.size() > shown)
				container.add(placeholder("… and " + (view.size() - shown) + " more"));
		}

		container.revalidate();
		container.repaint();
	}

	private List<RecipeRow> filterAndSort(List<RecipeRow> data)
	{
		String rawQuery = searchField.getText().trim();
		String query = rawQuery.toLowerCase();
		String skill = (String) skillSelector.getSelectedItem();
		boolean allSkills = skill == null || ALL_SKILLS.equals(skill);

		List<RecipeRow> out = new ArrayList<>();
		for (RecipeRow row : data)
		{
			if (!allSkills && !skill.equalsIgnoreCase(row.getSkill()))
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

	private static JPanel rowComponent(RecipeRow row, boolean showMakeable, boolean compact)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 1));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(0, 0, compact ? 2 : 4, 0),
				new EmptyBorder(compact ? 3 : 5, 7, compact ? 3 : 5, 7)));
		if (row.isStale())
			panel.setToolTipText(STALE_TOOLTIP);

		JLabel name = new JLabel(row.getProduct());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel profit = new JLabel(signed(row.getProfitEach()));
		profit.setFont(FontManager.getRunescapeSmallFont());
		profit.setForeground(row.getProfitEach() >= 0
				? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR);
		profit.setHorizontalAlignment(SwingConstants.RIGHT);

		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		if (row.isStale())
			top.add(staleDot(), BorderLayout.WEST);

		top.add(name, BorderLayout.CENTER);
		top.add(profit, BorderLayout.EAST);
		panel.add(top, BorderLayout.NORTH);

		if (!compact)
		{
			JLabel meta = new JLabel(metaText(row, showMakeable));
			meta.setFont(FontManager.getRunescapeSmallFont());
			meta.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			panel.add(meta, BorderLayout.SOUTH);
		}

		capHeight(panel);
		return panel;
	}

	private static String metaText(RecipeRow row, boolean showMakeable)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("gp/hr ");
		sb.append(row.isThroughputKnown()
				? QuantityFormatter.quantityToStackSize(row.getGpPerHour()) : "n/a");
		sb.append("  xp/hr ");
		sb.append(row.isThroughputKnown()
				? QuantityFormatter.quantityToStackSize(Math.round(row.getXpPerHour())) : "n/a");
		sb.append("  roi ");
		sb.append(Math.round(row.getRoi() * 100));
		sb.append('%');
		sb.append("  vol ");
		sb.append(QuantityFormatter.quantityToStackSize(row.getVolume()));
		if (row.getSuccessPercent() != null)
		{
			long pct = Math.round(row.getSuccessPercent() * 100);
			sb.append("  succ ");
			sb.append(pct);
			sb.append('%');
		}

		sb.append("  lvl ");
		sb.append(row.getLevelReq());
		if (showMakeable && row.getMakeableNow() >= 0)
		{
			sb.append("  x");
			sb.append(row.getMakeableNow());
		}

		return sb.toString();
	}

	private static String signed(long value)
	{
		String magnitude = QuantityFormatter.quantityToStackSize(Math.abs(value));
		return (value >= 0 ? "+" : "-") + magnitude;
	}

	private static JLabel staleDot()
	{
		JLabel dot = new JLabel("●");
		dot.setFont(FontManager.getRunescapeSmallFont());
		dot.setForeground(STALE_DOT);
		dot.setToolTipText(STALE_TOOLTIP);
		return dot;
	}
}
