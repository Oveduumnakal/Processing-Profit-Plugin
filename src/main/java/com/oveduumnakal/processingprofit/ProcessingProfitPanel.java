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
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ScrollBarUI;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.ui.laf.RuneLiteScrollBarUI;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * The Processing Profit sidebar: an active-modifier status line, a filter/sort bar (search, skill, sort
 * key, density &mdash; sourcing, valuation, gating and members live in the plugin settings), and four
 * tabs
 * &mdash; On-hand, Watchlist, Browse and Shopping. The skill filter lists only the skills present in the
 * active tab. Each tab holds the full {@link RecipeRow} set pushed by the plugin and renders
 * a filtered, sorted, display-capped view locally so sorting and searching are instant. Uses the
 * RuneLite fonts, colour scheme and themed scrollbar so it scales with the client.
 */
public class ProcessingProfitPanel extends PluginPanel
{
	private static final int MAX_DISPLAY = 100;
	private static final Color STALE_DOT = new Color(0xF0, 0xA3, 0x30);
	private static final Color WARN_TRIANGLE = new Color(0xD0, 0x42, 0x42);
	private static final Font ICON_FONT = FontManager.getRunescapeSmallFont().deriveFont(8f);
	private static final Color HOVER_TINT = new Color(0x46, 0x4B, 0x50);
	private static final Color SP_HIGH = new Color(100, 220, 100);
	private static final Color SP_LOW = new Color(220, 100, 100);
	private static final Color SP_GOLD = new Color(255, 200, 0);
	private static final Color SP_DIVIDER = new Color(80, 80, 80);
	private static final Color SP_MUTED = new Color(150, 150, 150);
	private static final int TREE_INDENT = 14;
	private static final int TREE_TOGGLE_W = 12;
	private static final int TREE_ICON_W = 20;
	private static final int TREE_ROW_H = 18;
	private static final Color STAR_ON = new Color(0xF0, 0xC0, 0x40);
	private static final Color LOCKED_FG = new Color(0x80, 0x80, 0x80);
	private static final String STALE_TOOLTIP =
			"Price is from the last recorded trade (no recent 5m/1h average) and may be out of date";
	private static final String[] SORT_LABELS =
			{"Profit/ea", "GP/hr", "XP/hr", "ROI %", "Success %", "Level", "Volume", "Makeable"};
	private static final String MISC_SKILL = "Miscellaneous";

	private static final String K_SORT_INDEX = "panelFilterSortIndex";
	private static final String K_SORT_REVERSED = "panelFilterSortReversed";
	private static final String K_COMPACT = "panelFilterCompact";
	private static final String K_DISABLED_SKILLS = "panelFilterDisabledSkills";
	private static final String K_SHOW_LOW_VOLUME = "panelFilterShowLowVolume";
	private static final String K_SHOW_STALE = "panelFilterShowStale";
	private static final String K_SHOW_THROTTLED = "panelFilterShowThrottled";
	private static final String K_PRESETS = "presets";

	private final ProcessingProfitConfig config;
	private final ConfigManager configManager;
	private final ItemManager itemManager;
	private final JLabel sortToggle = new JLabel("⇅", SwingConstants.CENTER);
	private final JLabel filterToggle = new JLabel();
	private final JLabel compactToggle = new JLabel("≣", SwingConstants.CENTER);
	private final JLabel presetToggle = new JLabel();
	private final JTextField searchField = new PlaceholderTextField("Search ...");
	private final JLabel modifierLine = new JLabel("Modifiers: none");

	private int sortIndex;
	private boolean sortReversed;
	private boolean compact;
	private final List<String> skillOptions = new ArrayList<>();
	private final Set<String> disabledSkills = new HashSet<>();
	private boolean showLowVolume = true;
	private boolean showStale = true;
	private boolean showThrottled = true;

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
	private JPanel activeRows = onHandRows;
	private ShoppingList shoppingList = new ShoppingList(Collections.emptyList(), 0L, 0, true);
	private List<String> shoppingSummary = Collections.emptyList();

	private final JPanel center = new JPanel(new CardLayout());
	private final JPanel detailHolder = new JPanel(new BorderLayout());

	private JPanel headerPanel;
	private MaterialTabGroup tabGroup;
	private MaterialTab shoppingTab;

	private Consumer<RecipeRow> selectionListener;
	private Consumer<RecipeRow> popOutListener;
	private Consumer<ShoppingRequest> shoppingAddListener;
	private Consumer<RecipeRow> pinToggleListener;
	private Runnable shoppingClearListener;
	private Set<String> pinnedIds = Collections.emptySet();
	private RecipeRow detailRow;
	private RecipeDetail currentDetail;

	/**
	 * Builds the panel layout.
	 *
	 * @param config        the plugin config, read for the gating and members display filters
	 * @param configManager persists the toolbar filter/sort/compact state across sessions
	 * @param itemManager   supplies item icons for the detail view
	 */
	public ProcessingProfitPanel(ProcessingProfitConfig config, ConfigManager configManager,
			ItemManager itemManager)
	{
		super(false);
		this.config = config;
		this.configManager = configManager;
		this.itemManager = itemManager;
		loadFilterState();
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
		onHand.setOnSelectEvent(() -> selectTab(onHandRows));
		watchlist.setOnSelectEvent(() -> selectTab(watchlistRows));
		browse.setOnSelectEvent(() -> selectTab(browseRows));
		shoppingTab.setOnSelectEvent(() -> selectTab(shoppingRows));
		tabGroup.addTab(onHand);
		tabGroup.addTab(watchlist);
		tabGroup.addTab(browse);
		tabGroup.addTab(shoppingTab);

		detailHolder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.setBackground(ColorScheme.DARK_GRAY_COLOR);
		center.add(display, "list");
		center.add(detailHolder, "detail");

		headerPanel = header(tabGroup);
		add(headerPanel, BorderLayout.NORTH);
		add(center, BorderLayout.CENTER);

		wireControls();
		tabGroup.select(onHand);
		renderAll();
		renderShopping();
	}

	private void wireControls()
	{
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

		styleSearch(searchField);

		header.add(searchToolbarRow());

		tabGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(tabGroup);
		return header;
	}

	/**
	 * Builds the single header control row: the search bar (no label) filling the width, followed by the
	 * three toolbar toggles on the right.
	 *
	 * @return the search + toolbar row
	 */
	private JPanel searchToolbarRow()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 0, 0, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(searchField, BorderLayout.CENTER);
		row.add(toolbar(), BorderLayout.EAST);
		capHeight(row);
		return row;
	}

	/**
	 * Builds the Stockpile-style toolbar: sort (⇅ / direction arrow), filter (funnel, by skill), and
	 * compact (≣) toggles laid out horizontally. Each is a glyph/icon that hover-tints grey↔gold and
	 * highlights gold while active.
	 *
	 * @return the toolbar toggles panel
	 */
	private JPanel toolbar()
	{
		styleToggle(presetToggle, "Presets", this::showPresetMenu);
		styleToggle(sortToggle, "Sort", this::showSortMenu);
		styleToggle(filterToggle, "Filter by skill", this::showSkillMenu);
		styleToggle(compactToggle, "Toggle compact view", this::toggleCompact);
		sortToggle.setFont(FontManager.getRunescapeBoldFont());
		compactToggle.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));

		installToggleHover(presetToggle, () -> false,
				color -> presetToggle.setIcon(presetIcon(color)), this::updatePresetToggle);
		installToggleHover(sortToggle, () -> sortIndex != 0 || sortReversed,
				sortToggle::setForeground, this::updateSortToggle);
		installToggleHover(filterToggle, this::filterActive,
				color -> filterToggle.setIcon(filterIcon(color)), this::updateFilterToggle);
		installToggleHover(compactToggle, () -> compact,
				compactToggle::setForeground, this::updateCompactToggle);

		updatePresetToggle();
		updateSortToggle();
		updateFilterToggle();
		updateCompactToggle();

		JPanel toggles = new JPanel();
		toggles.setLayout(new BoxLayout(toggles, BoxLayout.X_AXIS));
		toggles.setBackground(ColorScheme.DARK_GRAY_COLOR);
		toggles.add(presetToggle);
		toggles.add(sortToggle);
		toggles.add(filterToggle);
		toggles.add(compactToggle);
		return toggles;
	}

	private static void styleToggle(JLabel toggle, String tooltip, Runnable onClick)
	{
		toggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		toggle.setBorder(new EmptyBorder(2, 3, 2, 3));
		toggle.setToolTipText(tooltip);
		toggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}
		});
	}

	/**
	 * Opens the presets menu: each saved preset applies on click; a "Save current as…" entry snapshots the
	 * live filter/sort/mode/lens; and, when any exist, a Delete submenu removes one.
	 */
	private void showPresetMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		List<Preset> presets = loadPresets();

		if (presets.isEmpty())
		{
			JMenuItem empty = new JMenuItem("No saved presets");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setEnabled(false);
			menu.add(empty);
		}
		else
		{
			for (Preset preset : presets)
			{
				JMenuItem item = new JMenuItem(preset.getName());
				item.setFont(FontManager.getRunescapeSmallFont());
				item.addActionListener(e -> applyPreset(preset));
				menu.add(item);
			}
		}

		menu.addSeparator();

		JMenuItem save = new JMenuItem("Save current as…");
		save.setFont(FontManager.getRunescapeSmallFont());
		save.addActionListener(e -> saveCurrentAsPreset());
		menu.add(save);

		if (!presets.isEmpty())
		{
			JMenu delete = new JMenu("Delete");
			delete.setFont(FontManager.getRunescapeSmallFont());
			for (Preset preset : presets)
			{
				JMenuItem item = new JMenuItem(preset.getName());
				item.setFont(FontManager.getRunescapeSmallFont());
				item.addActionListener(e -> deletePreset(preset.getName()));
				delete.add(item);
			}

			menu.add(delete);
		}

		menu.show(presetToggle, 0, presetToggle.getHeight());
	}

	/**
	 * Prompts for a name and saves the current filter/sort/mode/lens as a preset, replacing any existing
	 * preset of the same name. A cancelled or blank name is ignored.
	 */
	private void saveCurrentAsPreset()
	{
		String name = JOptionPane.showInputDialog(this, "Preset name:", "Save preset",
				JOptionPane.PLAIN_MESSAGE);
		if (name == null)
			return;

		name = name.trim();
		if (name.isEmpty())
			return;

		Preset preset = new Preset(name, captureValues());
		savePresets(PresetStore.upsert(loadPresets(), preset));
	}

	/**
	 * Removes the named preset from the store.
	 *
	 * @param name the preset name to delete
	 */
	private void deletePreset(String name)
	{
		savePresets(PresetStore.remove(loadPresets(), name));
	}

	/**
	 * Snapshots the live filter/sort/mode/lens state into a value map keyed by the config keys in
	 * {@link PresetStore#PRESET_KEYS}. Config-backed settings are read at their effective values (so a
	 * never-touched default is captured, not {@code null}); the toolbar keys come from the panel fields.
	 *
	 * @return the captured values
	 */
	private Map<String, String> captureValues()
	{
		Map<String, String> values = new LinkedHashMap<>();
		values.put("sourcingMode", config.sourcingMode().name());
		values.put("valuationLens", config.valuationLens().name());
		values.put("gatingMode", config.gatingMode().name());
		values.put("showMembers", Boolean.toString(config.showMembers()));
		values.put("minProfit", Integer.toString(config.minProfit()));
		values.put("minGpPerHour", Integer.toString(config.minGpPerHour()));
		values.put("minVolume", Integer.toString(config.minVolume()));
		values.put(K_SORT_INDEX, Integer.toString(sortIndex));
		values.put(K_SORT_REVERSED, Boolean.toString(sortReversed));
		values.put(K_COMPACT, Boolean.toString(compact));
		values.put(K_DISABLED_SKILLS, String.join(",", disabledSkills));
		values.put(K_SHOW_LOW_VOLUME, Boolean.toString(showLowVolume));
		values.put(K_SHOW_STALE, Boolean.toString(showStale));
		values.put(K_SHOW_THROTTLED, Boolean.toString(showThrottled));
		return values;
	}

	/**
	 * Applies a preset: writes each allowlisted key back through the config (config-backed keys trigger the
	 * plugin's rebuild via {@code onConfigChanged}), then reloads the toolbar state and re-renders so the
	 * panel-side filter keys take effect immediately.
	 *
	 * @param preset the preset to apply
	 */
	private void applyPreset(Preset preset)
	{
		if (configManager == null)
			return;

		String group = ProcessingProfitConfig.GROUP;
		for (Map.Entry<String, String> entry : preset.getValues().entrySet())
		{
			if (entry.getValue() != null && PresetStore.PRESET_KEYS.contains(entry.getKey()))
				configManager.setConfiguration(group, entry.getKey(), entry.getValue());
		}

		loadFilterState();
		updateSortToggle();
		updateFilterToggle();
		updateCompactToggle();
		renderAll();
	}

	private List<Preset> loadPresets()
	{
		if (configManager == null)
			return new ArrayList<>();

		return PresetStore.parse(read(K_PRESETS));
	}

	private void savePresets(List<Preset> presets)
	{
		if (configManager == null)
			return;

		configManager.setConfiguration(ProcessingProfitConfig.GROUP, K_PRESETS, PresetStore.format(presets));
	}

	private void updatePresetToggle()
	{
		presetToggle.setIcon(presetIcon(ColorScheme.LIGHT_GRAY_COLOR));
	}

	/**
	 * Opens the sort menu: each sort key as a checkable entry. Clicking the active key toggles its
	 * direction; clicking another key selects it (default descending).
	 */
	private void showSortMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		for (int i = 0; i < SORT_LABELS.length; i++)
		{
			boolean active = i == sortIndex;
			String arrow = active ? (sortReversed ? "  ↑" : "  ↓") : "";
			JCheckBoxMenuItem entry = new JCheckBoxMenuItem(SORT_LABELS[i] + arrow, active);
			entry.setFont(FontManager.getRunescapeSmallFont());
			final int index = i;
			entry.addActionListener(e ->
			{
				if (index == sortIndex)
				{
					sortReversed = !sortReversed;
				}
				else
				{
					sortIndex = index;
					sortReversed = false;
				}

				updateSortToggle();
				saveFilterState();
				renderAll();
			});
			menu.add(entry);
		}

		menu.show(sortToggle, 0, sortToggle.getHeight());
	}

	/**
	 * Opens the multi-select filter menu: "Select all"/"Select none", a check per skill in the active tab
	 * (all checked = show everything; e.g. uncheck Runecrafting for "all but Runecrafting"), then a warning
	 * section that shows/hides low-volume, stale-priced, and buy-limit-throttled rows. The menu stays open
	 * across individual toggles.
	 */
	private void showSkillMenu()
	{
		JPopupMenu menu = new JPopupMenu();

		JMenuItem all = new JMenuItem("Select all");
		all.setFont(FontManager.getRunescapeSmallFont());
		all.addActionListener(e ->
		{
			disabledSkills.clear();
			updateFilterToggle();
			saveFilterState();
			renderAll();
		});
		menu.add(all);

		JMenuItem none = new JMenuItem("Select none");
		none.setFont(FontManager.getRunescapeSmallFont());
		none.addActionListener(e ->
		{
			disabledSkills.addAll(skillOptions);
			updateFilterToggle();
			saveFilterState();
			renderAll();
		});
		menu.add(none);
		menu.addSeparator();

		for (String option : skillOptions)
			menu.add(skillCheckItem(option));

		menu.addSeparator();
		menu.add(warningCheckItem("Show Low Trade Vol", showLowVolume, v -> showLowVolume = v));
		menu.add(warningCheckItem("Show Stale Pricing", showStale, v -> showStale = v));
		menu.add(warningCheckItem("Show Buy-limit throttled", showThrottled, v -> showThrottled = v));

		menu.show(filterToggle, 0, filterToggle.getHeight());
	}

	/**
	 * Builds a checkbox menu item that keeps the popup open on click (so several can be toggled in one
	 * visit) and, if it maps to state, updates it. Callers add the state wiring via an action listener.
	 *
	 * @param label   the item label
	 * @param checked the initial checked state
	 * @return the stay-open checkbox item
	 */
	private static JCheckBoxMenuItem stayOpenCheck(String label, boolean checked)
	{
		JCheckBoxMenuItem entry = new JCheckBoxMenuItem(label, checked)
		{
			@Override
			protected void processMouseEvent(MouseEvent e)
			{
				if (e.getID() == MouseEvent.MOUSE_RELEASED && contains(e.getPoint()))
				{
					doClick();
					setArmed(true);
				}
				else
				{
					super.processMouseEvent(e);
				}
			}
		};
		entry.setFont(FontManager.getRunescapeSmallFont());
		return entry;
	}

	/**
	 * Builds a skill checkbox for {@link #showSkillMenu()} that toggles {@link #disabledSkills} without
	 * closing the popup.
	 *
	 * @param option the skill name
	 * @return the stay-open checkbox item
	 */
	private JCheckBoxMenuItem skillCheckItem(String option)
	{
		JCheckBoxMenuItem entry = stayOpenCheck(option, !disabledSkills.contains(option));
		entry.addActionListener(e ->
		{
			if (entry.isSelected())
				disabledSkills.remove(option);
			else
				disabledSkills.add(option);

			updateFilterToggle();
			saveFilterState();
			renderAll();
		});
		return entry;
	}

	/**
	 * Builds a warning-filter checkbox that shows or hides rows carrying a warning (stale price, low
	 * volume, buy-limit throttled), keeping the popup open.
	 *
	 * @param label   the item label
	 * @param checked the initial checked state (checked = show)
	 * @param setter  applies the new state to the backing field
	 * @return the stay-open checkbox item
	 */
	private JCheckBoxMenuItem warningCheckItem(String label, boolean checked, Consumer<Boolean> setter)
	{
		JCheckBoxMenuItem entry = stayOpenCheck(label, checked);
		entry.addActionListener(e ->
		{
			setter.accept(entry.isSelected());
			updateFilterToggle();
			saveFilterState();
			renderAll();
		});
		return entry;
	}

	private void toggleCompact()
	{
		compact = !compact;
		updateCompactToggle();
		saveFilterState();
		renderAll();
	}

	private void updateSortToggle()
	{
		if (sortIndex == 0 && !sortReversed)
		{
			sortToggle.setText("⇅");
			sortToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else
		{
			sortToggle.setText(sortReversed ? "↑" : "↓");
			sortToggle.setForeground(SP_GOLD);
		}
	}

	private void updateFilterToggle()
	{
		filterToggle.setIcon(filterIcon(filterActive() ? SP_GOLD : ColorScheme.LIGHT_GRAY_COLOR));
	}

	/**
	 * @return whether any filter is narrowing the list: a skill is unchecked, or a warning class
	 *     (low volume, stale, throttled) is hidden
	 */
	private boolean filterActive()
	{
		return !disabledSkills.isEmpty() || !showLowVolume || !showStale || !showThrottled;
	}

	/**
	 * Loads the persisted toolbar state (sort key/direction, compact density, disabled skills, warning
	 * toggles) from the config so filters survive a client restart. Missing keys keep the field defaults.
	 */
	private void loadFilterState()
	{
		if (configManager == null)
			return;

		sortIndex = clampSort(parseIntOr(read(K_SORT_INDEX), 0));
		sortReversed = "true".equals(read(K_SORT_REVERSED));
		compact = "true".equals(read(K_COMPACT));
		showLowVolume = !"false".equals(read(K_SHOW_LOW_VOLUME));
		showStale = !"false".equals(read(K_SHOW_STALE));
		showThrottled = !"false".equals(read(K_SHOW_THROTTLED));

		disabledSkills.clear();
		String csv = read(K_DISABLED_SKILLS);
		if (csv != null && !csv.isEmpty())
		{
			for (String s : csv.split(","))
				if (!s.trim().isEmpty())
					disabledSkills.add(s.trim());
		}
	}

	/**
	 * Writes the current toolbar state to the config. Called on every filter/sort/compact change so the
	 * next session restores it.
	 */
	private void saveFilterState()
	{
		if (configManager == null)
			return;

		String group = ProcessingProfitConfig.GROUP;
		configManager.setConfiguration(group, K_SORT_INDEX, Integer.toString(sortIndex));
		configManager.setConfiguration(group, K_SORT_REVERSED, Boolean.toString(sortReversed));
		configManager.setConfiguration(group, K_COMPACT, Boolean.toString(compact));
		configManager.setConfiguration(group, K_SHOW_LOW_VOLUME, Boolean.toString(showLowVolume));
		configManager.setConfiguration(group, K_SHOW_STALE, Boolean.toString(showStale));
		configManager.setConfiguration(group, K_SHOW_THROTTLED, Boolean.toString(showThrottled));
		configManager.setConfiguration(group, K_DISABLED_SKILLS, String.join(",", disabledSkills));
	}

	private String read(String key)
	{
		return configManager.getConfiguration(ProcessingProfitConfig.GROUP, key);
	}

	private static int parseIntOr(String value, int fallback)
	{
		if (value == null)
			return fallback;

		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	private static int clampSort(int index)
	{
		if (index < 0)
			return 0;

		if (index >= SORT_LABELS.length)
			return SORT_LABELS.length - 1;

		return index;
	}

	private void updateCompactToggle()
	{
		compactToggle.setForeground(compact ? SP_GOLD : ColorScheme.LIGHT_GRAY_COLOR);
	}

	/**
	 * Installs grey↔gold hover colouring on a toolbar toggle: an inactive (grey) toggle turns gold
	 * while hovered, an active (gold) toggle turns grey, and its resting colour is repainted on exit.
	 *
	 * @param toggle   the toggle to wire
	 * @param selected whether the toggle is currently active
	 * @param apply    paints the toggle a colour ({@code setForeground} for glyphs, {@code setIcon} for icons)
	 * @param restore  repaints the toggle's resting colour
	 */
	private static void installToggleHover(JLabel toggle, BooleanSupplier selected,
			Consumer<Color> apply, Runnable restore)
	{
		toggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				apply.accept(selected.getAsBoolean() ? ColorScheme.LIGHT_GRAY_COLOR : SP_GOLD);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				restore.run();
			}
		});
	}

	/**
	 * Paints a small funnel (filter) icon in the given colour: a wide top bar tapering to a stem.
	 *
	 * @param color the icon colour
	 * @return the funnel icon
	 */
	private static Icon filterIcon(Color color)
	{
		int size = 14;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.fillPolygon(
				new int[]{1, size - 1, size / 2 + 1, size / 2 + 1, size / 2 - 1, size / 2 - 1},
				new int[]{2, 2, size / 2, size - 1, size - 1, size / 2},
				6);
		g.dispose();
		return new ImageIcon(img);
	}

	/**
	 * Paints a small bookmark (preset) icon in the given colour: a ribbon with a notch cut into its
	 * bottom edge.
	 *
	 * @param color the icon colour
	 * @return the bookmark icon
	 */
	private static Icon presetIcon(Color color)
	{
		int size = 14;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.fillPolygon(
				new int[]{3, size - 3, size - 3, size / 2, 3},
				new int[]{1, 1, size - 1, size / 2 + 1, size - 1},
				5);
		g.dispose();
		return new ImageIcon(img);
	}

	private static void styleSearch(JTextField field)
	{
		field.setFont(FontManager.getRunescapeSmallFont());
		field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		field.setForeground(Color.WHITE);
		field.setCaretColor(Color.WHITE);
		field.setBorder(new EmptyBorder(3, 5, 3, 5));
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
	 * A text field that paints faint placeholder text while it is empty (Swing has no built-in
	 * placeholder). The placeholder is painted, not inserted, so {@code getText()} stays empty.
	 */
	private static final class PlaceholderTextField extends JTextField
	{
		private final String placeholder;

		private PlaceholderTextField(String placeholder)
		{
			this.placeholder = placeholder;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (!getText().isEmpty())
				return;

			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(SP_MUTED);
			g2.setFont(getFont());
			Insets insets = getInsets();
			FontMetrics fm = g2.getFontMetrics();
			int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
			g2.drawString(placeholder, insets.left, y);
			g2.dispose();
		}
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
	 * Registers a callback fired when the user clicks a row's pop-out button, to open the standalone
	 * graphical recipe-tree window for that recipe.
	 *
	 * @param listener the callback, invoked with the row
	 */
	public void setPopOutListener(Consumer<RecipeRow> listener)
	{
		this.popOutListener = listener;
	}

	private void firePopOut(RecipeRow row)
	{
		if (popOutListener != null && row != null && row.getRecipe() != null)
			popOutListener.accept(row);
	}

	private boolean selectTab(JPanel rows)
	{
		activeRows = rows;
		rebuildSkillOptions();
		renderAll();
		return true;
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
			rebuildSkillOptions();
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
			rebuildSkillOptions();
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
		JLabel more = new JLabel("… and " + remaining + " more");
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
		boolean skillFilterActive = !disabledSkills.isEmpty();

		boolean hideLocked = config.gatingMode() == GatingMode.HIDE;
		boolean showMembers = config.showMembers();
		List<RecipeRow> out = new ArrayList<>();
		for (RecipeRow row : data)
		{
			if (skillFilterActive && !hasEnabledSkill(row))
				continue;

			if (!showLowVolume && row.isLowVolume())
				continue;

			if (!showStale && row.isStale())
				continue;

			if (!showThrottled && row.isThrottled())
				continue;

			if (hideLocked && row.isLocked())
				continue;

			if (!showMembers && row.isMembers())
				continue;

			if (!query.isEmpty() && !row.getProduct().toLowerCase().contains(query))
				continue;

			out.add(row);
		}

		Comparator<RecipeRow> comparator = comparatorFor(sortIndex);
		if (sortReversed)
			comparator = comparator.reversed();

		out.sort(comparator);
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

	/**
	 * @param row the row to test
	 * @return whether the row has at least one skill that is not disabled in the skill filter. A row
	 *     with no skill is treated as {@link #MISC_SKILL}, so it hides only when Miscellaneous is
	 *     unchecked; a row whose every skill is unchecked is hidden, so "Select none" empties the list.
	 */
	private boolean hasEnabledSkill(RecipeRow row)
	{
		List<String> skills = row.getSkills();
		if (skills != null && !skills.isEmpty())
		{
			for (String s : skills)
				if (!disabledSkills.contains(s))
					return true;

			return false;
		}

		String single = row.getSkill();
		if (single != null && !single.isEmpty())
			return !disabledSkills.contains(single);

		return !disabledSkills.contains(MISC_SKILL);
	}

	private List<RecipeRow> activeTabData()
	{
		if (activeRows == browseRows)
			return browseData;

		if (activeRows == watchlistRows)
			return watchlistData;

		if (activeRows == shoppingRows)
			return Collections.emptyList();

		return onHandData;
	}

	private void rebuildSkillOptions()
	{
		Set<String> skills = new TreeSet<>();
		boolean hasMisc = false;
		for (RecipeRow row : activeTabData())
		{
			if (row.getSkills() != null && !row.getSkills().isEmpty())
				skills.addAll(row.getSkills());
			else if (row.getSkill() != null && !row.getSkill().isEmpty())
				skills.add(row.getSkill());
			else
				hasMisc = true;
		}

		skillOptions.clear();
		skillOptions.addAll(skills);
		if (hasMisc)
			skillOptions.add(MISC_SKILL);

		updateFilterToggle();
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
				new EmptyBorder(0, 0, compact ? 4 : 6, 0),
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
		boolean greyed = row.isLocked() && config.gatingMode() == GatingMode.GREY;
		if (compact)
			fillCompactRow(panel, row, greyed);
		else
			fillCardRow(panel, row, greyed);

		capHeight(panel);
		return panel;
	}

	private void fillCompactRow(JPanel panel, RecipeRow row, boolean greyed)
	{
		JLabel name = nameLabel(row, greyed);
		panel.add(name, BorderLayout.CENTER);

		JPanel trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		trailing.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		trailing.add(profitLabel(row, greyed, false));
		if (row.getRecipe() != null)
		{
			trailing.add(popOutButton(row, greyed));
			trailing.add(pinStar(row, greyed));
		}

		panel.add(trailing, BorderLayout.EAST);
	}

	private void fillCardRow(JPanel panel, RecipeRow row, boolean greyed)
	{
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(nameLabel(row, greyed), BorderLayout.CENTER);
		if (row.getRecipe() != null)
		{
			JPanel starWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			starWrap.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			starWrap.add(popOutButton(row, greyed));
			starWrap.add(pinStar(row, greyed));
			top.add(starWrap, BorderLayout.EAST);
		}

		capHeight(top);

		JPanel bottom = new JPanel(new BorderLayout(6, 0));
		bottom.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
		bottom.setBorder(new EmptyBorder(2, 0, 0, 0));
		bottom.add(profitLabel(row, greyed, true), BorderLayout.WEST);

		JPanel meta = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		meta.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		if (row.getRecipeCount() > 1)
			meta.add(rcpsLabel(row, greyed));

		if (row.isStale())
			meta.add(staleDot(row, greyed));

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

	private JLabel nameLabel(RecipeRow row, boolean greyed)
	{
		JLabel name = new JLabel(row.getProduct());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(greyed ? LOCKED_FG : Color.WHITE);
		name.setToolTipText(nameTooltip(row));
		selectOnClick(name, row);
		return name;
	}

	private static String nameTooltip(RecipeRow row)
	{
		if (row.isLocked() && row.getLockReason() != null)
			return row.getProduct() + " — Locked: " + row.getLockReason();

		return row.getProduct();
	}

	private JLabel profitLabel(RecipeRow row, boolean greyed, boolean range)
	{
		boolean showRange = range && row.getProfitMin() != row.getProfitMax();
		String plain = showRange
				? signed(row.getProfitMin()) + " to " + signed(row.getProfitMax())
				: signed(row.getProfitEach());
		String full = showRange
				? signedFull(row.getProfitMin()) + " to " + signedFull(row.getProfitMax())
				: signedFull(row.getProfitEach());
		JLabel label = new JLabel(plain);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(greyed ? LOCKED_FG : (row.getProfitEach() >= 0
				? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR));
		label.setToolTipText(full);
		hoverHighlight(label, plain, plain, row);
		return label;
	}

	private JLabel rcpsLabel(RecipeRow row, boolean greyed)
	{
		String text = "Rcps: " + row.getRecipeCount();
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(greyed ? LOCKED_FG : ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(0, 0, 0, 8));
		label.setToolTipText(row.getRecipeCount() + " recipes can make this");
		hoverHighlight(label, text, text, row);
		return label;
	}

	private JLabel staleDot(RecipeRow row, boolean greyed)
	{
		JLabel dot = new JLabel("●");
		dot.setFont(ICON_FONT);
		dot.setForeground(greyed ? LOCKED_FG : STALE_DOT);
		dot.setBorder(new EmptyBorder(0, 4, 0, 0));
		dot.setToolTipText(STALE_TOOLTIP);
		hoverHighlight(dot, "●", "●", row);
		return dot;
	}

	private JLabel warnTriangle(RecipeRow row, boolean greyed)
	{
		JLabel triangle = new JLabel("▲");
		triangle.setFont(ICON_FONT);
		triangle.setForeground(greyed ? LOCKED_FG : WARN_TRIANGLE);
		triangle.setBorder(new EmptyBorder(0, 4, 0, 0));
		triangle.setToolTipText(warningText(row));
		hoverHighlight(triangle, "▲", "▲", row);
		return triangle;
	}

	/**
	 * Wires a label so hovering highlights it (a background tint behind {@code hoverText}, matching the
	 * Stockpile style) and clicking still opens the row detail. When {@code hoverText} differs from
	 * {@code plain} the hover also swaps the shown text &mdash; used to reveal a profit's full number.
	 *
	 * @param label     the label to wire
	 * @param plain     the resting text
	 * @param hoverText the text shown highlighted on hover
	 * @param row       the row the label belongs to, opened on click
	 */
	private void hoverHighlight(JLabel label, String plain, String hoverText, RecipeRow row)
	{
		String highlighted = tinted(hoverText);
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				fireSelection(row);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setText(highlighted);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setText(plain);
			}
		});
	}

	private void selectOnClick(JComponent component, RecipeRow row)
	{
		component.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				fireSelection(row);
			}
		});
	}

	private static String tinted(String text)
	{
		return "<html><nobr><span style='background-color:" + hex(HOVER_TINT) + "'>" + text
				+ "</span></nobr></html>";
	}

	private static String hex(Color color)
	{
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private static String signed(long value)
	{
		String magnitude = QuantityFormatter.quantityToStackSize(Math.abs(value));
		return (value >= 0 ? "+" : "-") + magnitude;
	}

	private static String signedFull(long value)
	{
		return (value >= 0 ? "+" : "-") + String.format("%,d", Math.abs(value));
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
				star.setText(pinned ? "☆" : "★");
				star.setForeground(pinned ? ColorScheme.LIGHT_GRAY_COLOR : STAR_ON);
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

	/**
	 * A small "open in new window" button that opens the standalone graphical recipe-tree window for the
	 * row's recipe. Hover-tints gold; greyed for locked rows.
	 *
	 * @param row    the row whose recipe the tree is built for
	 * @param greyed whether the row is gated/dimmed
	 * @return the pop-out button
	 */
	private JLabel popOutButton(RecipeRow row, boolean greyed)
	{
		Color base = greyed ? LOCKED_FG : ColorScheme.LIGHT_GRAY_COLOR;
		JLabel button = new JLabel(popOutIcon(base));
		button.setToolTipText("Pop out recipe tree");
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				firePopOut(row);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setIcon(popOutIcon(SP_GOLD));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setIcon(popOutIcon(base));
			}
		});
		return button;
	}

	/**
	 * Paints a small "open in new window" icon in the given colour: a box with a diagonal arrow leaving
	 * its top-right.
	 *
	 * @param color the icon colour
	 * @return the pop-out icon
	 */
	private static Icon popOutIcon(Color color)
	{
		int size = 14;
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(color);
		g.drawRect(1, 5, 7, 7);
		g.drawLine(6, 7, 12, 1);
		g.drawLine(12, 1, 8, 1);
		g.drawLine(12, 1, 12, 5);
		g.dispose();
		return new ImageIcon(img);
	}

	private static JLabel staleDot(boolean greyed)
	{
		JLabel dot = new JLabel("●");
		dot.setFont(ICON_FONT);
		dot.setForeground(greyed ? LOCKED_FG : STALE_DOT);
		dot.setBorder(new EmptyBorder(0, 4, 0, 0));
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
			headerPanel.setVisible(false);
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
		headerPanel.setVisible(true);
		((CardLayout) center.getLayout()).show(center, "list");
	}

	private JScrollPane detailCard(RecipeDetail d)
	{
		JPanel content = listPanel();
		content.setBorder(new EmptyBorder(2, 2, 2, 2));

		content.add(backButton());
		content.add(itemDetailsHeader(d));
		content.add(throughputSection(d));
		content.add(costBreakdownSection(d));
		ChainTree tree = d.getChainTree();
		if (tree != null && (!tree.getRoots().isEmpty() || !tree.getTotals().isEmpty()))
			content.add(chainTreeSection(tree));

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

	/**
	 * The Stockpile-style item header: the item icon (natural size, spanning both text rows) with the item
	 * name over its signed profit beside it, then the examine description in muted italics.
	 *
	 * @param d the detail
	 * @return the item-details header
	 */
	private JPanel itemDetailsHeader(RecipeDetail d)
	{
		JPanel wrap = listPanel();
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.setBorder(new EmptyBorder(6, 2, 0, 2));

		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel icon = new JLabel();
		icon.setVerticalAlignment(SwingConstants.CENTER);
		if (itemManager != null && d.getItemId() > 0)
		{
			AsyncBufferedImage img = itemManager.getImage(d.getItemId());
			img.onLoaded(() ->
			{
				icon.setIcon(new ImageIcon(img));
				icon.revalidate();
				icon.repaint();
			});
		}

		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel name = new JLabel(d.getProduct());
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel profit = new JLabel(signed(d.getProfitEach()) + " /ea");
		profit.setFont(FontManager.getRunescapeSmallFont());
		profit.setForeground(d.getProfitEach() >= 0 ? SP_HIGH : SP_LOW);
		profit.setAlignmentX(Component.LEFT_ALIGNMENT);

		text.add(name);
		text.add(profit);
		row.add(text, BorderLayout.CENTER);
		capHeight(row);
		wrap.add(row);

		if (d.getDescription() != null && !d.getDescription().isEmpty())
		{
			JTextArea desc = new JTextArea(d.getDescription());
			desc.setWrapStyleWord(true);
			desc.setLineWrap(true);
			desc.setEditable(false);
			desc.setFocusable(false);
			desc.setOpaque(false);
			desc.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.ITALIC));
			desc.setForeground(SP_MUTED);
			desc.setAlignmentX(Component.LEFT_ALIGNMENT);
			desc.setBorder(new EmptyBorder(6, 2, 0, 2));
			wrap.add(desc);
		}

		return wrap;
	}

	/**
	 * The Throughput section: a centered header over a 2&times;2 grid of label-over-value cells
	 * (GP/hr and XP/hr on top, Profit/XP and ROI below), matching the Stockpile layout.
	 *
	 * @param d the detail
	 * @return the throughput section
	 */
	private JPanel throughputSection(RecipeDetail d)
	{
		JPanel panel = listPanel();
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(new EmptyBorder(6, 2, 0, 2));
		panel.add(sectionSeparator());
		panel.add(sectionHeader("Throughput"));

		String gpHr = d.isThroughputKnown() ? num(d.getGpPerHour()) : "n/a";
		String xpHr = d.isThroughputKnown() ? num(Math.round(d.getXpPerHour())) : "n/a";
		String profitXp = Long.toString(Math.round(d.getProfitPerXp()));
		String roi = Math.round(d.getRoi() * 100) + "%";

		JPanel grid = new JPanel(new GridLayout(2, 2, 8, 6));
		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setBorder(new EmptyBorder(4, 0, 0, 0));
		grid.add(metricCell("GP/hr", gpHr));
		grid.add(metricCell("XP/hr", xpHr));
		grid.add(metricCell("Profit/XP", profitXp));
		grid.add(metricCell("ROI", roi));
		capHeight(grid);
		panel.add(grid);
		return panel;
	}

	/**
	 * A single throughput cell: a muted centered label over a white centered value.
	 *
	 * @param label the metric name
	 * @param value the metric value
	 * @return the cell
	 */
	private static JPanel metricCell(String label, String value)
	{
		JPanel cell = new JPanel();
		cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
		cell.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel name = new JLabel(label, SwingConstants.CENTER);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(SP_MUTED);
		name.setAlignmentX(Component.CENTER_ALIGNMENT);

		JLabel val = new JLabel(value, SwingConstants.CENTER);
		val.setFont(FontManager.getRunescapeSmallFont());
		val.setForeground(Color.WHITE);
		val.setAlignmentX(Component.CENTER_ALIGNMENT);

		cell.add(name);
		cell.add(val);
		return cell;
	}

	/**
	 * The Cost breakdown section, split into an Overview block (input cost, output value, gross profit)
	 * and a Details block (per-item input and output lines, GE tax, profit/ea), matching the mockup.
	 *
	 * @param d the detail
	 * @return the cost-breakdown section
	 */
	private JPanel costBreakdownSection(RecipeDetail d)
	{
		JPanel panel = listPanel();
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(new EmptyBorder(6, 2, 0, 2));
		panel.add(sectionSeparator());
		panel.add(sectionHeader("Cost breakdown"));

		panel.add(subHeader("Overview"));
		panel.add(overviewLine("Input cost:", "-" + num(d.getInputCost()), SP_LOW));
		panel.add(overviewLine("Output value:", "+" + num(d.getGrossOutput()), SP_HIGH));
		long gross = d.getGrossOutput() - d.getInputCost();
		panel.add(overviewLine("Gross profit:", signed(gross), gross >= 0 ? SP_HIGH : SP_LOW));

		panel.add(subHeader("Inputs"));
		if (d.getInputs().isEmpty())
			panel.add(whiteLine("—"));

		for (CostLine in : d.getInputs())
			panel.add(whiteLine(costText(in)));

		if (!d.getTools().isEmpty())
			panel.add(whiteLine("Tools: " + String.join(", ", d.getTools())));

		panel.add(subHeader("Outputs"));
		for (CostLine out : d.getOutputs())
			panel.add(whiteLine(costText(out)));

		panel.add(subHeader("Details"));
		panel.add(overviewLine("GE tax:", "-" + num(d.getTax()), SP_LOW));
		if (d.isFailCapable())
			panel.add(whiteLine("Success-weighted net: " + num(d.getExpectedNet())));

		panel.add(overviewLine("Profit/ea:", signed(d.getProfitEach()),
				d.getProfitEach() >= 0 ? SP_HIGH : SP_LOW));

		capHeight(panel);
		return panel;
	}

	/**
	 * A left-aligned muted sub-header used to label a block within a section (e.g. Overview / Details).
	 *
	 * @param title the sub-header text
	 * @return the sub-header label
	 */
	private static JLabel subHeader(String title)
	{
		JLabel label = new JLabel(title);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(new EmptyBorder(6, 2, 0, 0));
		return label;
	}

	/**
	 * An overview line with a muted label and a coloured value (e.g. red input cost, green output value).
	 *
	 * @param label the muted label text
	 * @param value the value text
	 * @param color the value colour
	 * @return the styled line
	 */
	private static JLabel overviewLine(String label, String value, Color color)
	{
		JLabel line = new JLabel("<html>" + label + " <font color='" + hex(color) + "'>" + value
				+ "</font></html>");
		line.setFont(FontManager.getRunescapeSmallFont());
		line.setForeground(Color.WHITE);
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		line.setBorder(new EmptyBorder(2, 6, 0, 0));
		return line;
	}

	/**
	 * A white detail line, used for the cost-breakdown value rows so they read clearly against the muted
	 * sub-headers.
	 *
	 * @param text the line text
	 * @return the white label
	 */
	private static JLabel whiteLine(String text)
	{
		JLabel label = detailLine(text);
		label.setForeground(Color.WHITE);
		return label;
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

		panel.add(sectionSeparator());
		panel.add(sectionHeader("Shopping list"));

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

	/**
	 * A full-width 1px horizontal rule, used above each detail section header (Stockpile style).
	 *
	 * @return a thin divider component sized to fill the panel width
	 */
	private static JComponent sectionSeparator()
	{
		JPanel line = new JPanel();
		line.setBackground(SP_DIVIDER);
		line.setPreferredSize(new Dimension(0, 1));
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		return line;
	}

	/**
	 * A centered, gold, bold section header shown below a {@link #sectionSeparator()} (Stockpile style).
	 *
	 * @param title the section title
	 * @return a full-width row with the title centered
	 */
	private static JComponent sectionHeader(String title)
	{
		JLabel label = new JLabel(title);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(SP_GOLD);
		label.setHorizontalAlignment(SwingConstants.CENTER);

		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(4, 0, 4, 0));
		row.add(label, BorderLayout.CENTER);
		capHeight(row);
		return row;
	}

	private static JPanel section(String title, List<String> lines)
	{
		JPanel panel = listPanel();
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(new EmptyBorder(6, 2, 0, 2));

		panel.add(sectionSeparator());
		panel.add(sectionHeader(title));

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

	private JPanel chainTreeSection(ChainTree tree)
	{
		JPanel panel = listPanel();
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(new EmptyBorder(6, 2, 0, 2));

		if (!tree.getRoots().isEmpty())
		{
			panel.add(sectionSeparator());
			panel.add(sectionHeader("Make-or-buy tree"));
			panel.add(chainLegend());
			for (ChainNode node : tree.getRoots())
				panel.add(chainNodeComponent(node, 0));
		}

		if (!tree.getTotals().isEmpty())
			panel.add(materialsSection("Total materials", tree.getTotals(), true));

		if (!tree.getLeftovers().isEmpty())
			panel.add(materialsSection("Leftovers", tree.getLeftovers(), false));

		return panel;
	}

	private JComponent chainNodeComponent(ChainNode node, int depth)
	{
		JPanel holder = new JPanel();
		holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel children = new JPanel();
		children.setLayout(new BoxLayout(children, BoxLayout.Y_AXIS));
		children.setBackground(ColorScheme.DARK_GRAY_COLOR);
		children.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (ChainNode child : node.getChildren())
			children.add(chainNodeComponent(child, depth + 1));

		holder.add(chainNodeRow(node, depth, children));
		if (node.getViaSkill() != null && !node.isCovered())
			holder.add(chainSkillLine(node.getViaSkill(), depth));

		if (!node.getChildren().isEmpty())
			holder.add(children);

		return holder;
	}

	private JPanel chainNodeRow(ChainNode node, int depth, JPanel children)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(1, 6 + depth * TREE_INDENT, 0, 0));

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
		left.setBackground(ColorScheme.DARK_GRAY_COLOR);
		left.add(toggleSlot(node, children));
		left.add(iconSlot(node.getItemId()));
		left.add(chainNameLabel(node));
		if (!node.isCovered() && node.getHeld() > 0)
		{
			JLabel have = chainCostLabel("  have " + node.getHeld(), SP_MUTED);
			have.setToolTipText("You own " + node.getHeld() + " of the " + node.getQty() + " needed");
			left.add(have);
		}

		row.add(left, BorderLayout.WEST);
		row.add(chainRight(node), BorderLayout.EAST);
		capHeight(row);
		return row;
	}

	private JLabel chainNameLabel(ChainNode node)
	{
		String text = node.getQty() + "× " + node.getLabel();
		if (node.isCovered())
		{
			JLabel name = struckLabel(text, SP_HIGH);
			name.setToolTipText("You already own " + node.getHeld() + " — nothing to buy or make");
			return name;
		}

		JLabel name = new JLabel(text);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setBorder(new EmptyBorder(0, 4, 0, 0));
		name.setForeground(Color.WHITE);
		return name;
	}

	private JLabel chainSkillLine(String skill, int depth)
	{
		JLabel line = new JLabel("made via " + skill);
		line.setFont(FontManager.getRunescapeSmallFont());
		line.setForeground(SP_MUTED);
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		line.setBorder(new EmptyBorder(0, 6 + depth * TREE_INDENT + TREE_TOGGLE_W + TREE_ICON_W + 4, 1, 0));
		return line;
	}

	/**
	 * A fixed-width slot holding the expand/collapse arrow, or blank space when the node has no children,
	 * so every row's icon and name line up in a column regardless of whether the row is expandable.
	 *
	 * @param node     the node
	 * @param children the child panel this arrow toggles
	 * @return the slot label
	 */
	private JLabel toggleSlot(ChainNode node, JPanel children)
	{
		JLabel slot = new JLabel();
		fixSize(slot, TREE_TOGGLE_W);
		if (node.getChildren().isEmpty())
			return slot;

		slot.setText("▾");
		slot.setFont(FontManager.getRunescapeSmallFont());
		slot.setForeground(SP_MUTED);
		slot.setHorizontalAlignment(SwingConstants.LEFT);
		slot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		slot.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				boolean show = !children.isVisible();
				children.setVisible(show);
				slot.setText(show ? "▾" : "▸");
				detailHolder.revalidate();
				detailHolder.repaint();
			}
		});
		return slot;
	}

	/**
	 * A fixed-width, centred item icon slot. The icon is scaled to a uniform height and centred in a
	 * constant-width box so names align in a column even though item sprites vary in width.
	 *
	 * @param itemId the item to show
	 * @return the slot label
	 */
	private JLabel iconSlot(int itemId)
	{
		JLabel icon = new JLabel();
		fixSize(icon, TREE_ICON_W);
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (itemManager != null && itemId > 0)
		{
			AsyncBufferedImage img = itemManager.getImage(itemId);
			img.onLoaded(() ->
			{
				Image scaled = img.getScaledInstance(-1, 16, Image.SCALE_SMOOTH);
				icon.setIcon(new ImageIcon(scaled));
				icon.revalidate();
				icon.repaint();
			});
		}

		return icon;
	}

	private static void fixSize(JLabel label, int width)
	{
		Dimension d = new Dimension(width, TREE_ROW_H);
		label.setPreferredSize(d);
		label.setMinimumSize(d);
		label.setMaximumSize(d);
	}

	private JLabel chainLegend()
	{
		JLabel legend = new JLabel("Quantities scaled to ×1 · green struck = already held");
		legend.setFont(FontManager.getRunescapeSmallFont());
		legend.setForeground(SP_MUTED);
		legend.setAlignmentX(Component.LEFT_ALIGNMENT);
		legend.setBorder(new EmptyBorder(0, 2, 4, 0));
		return legend;
	}

	private static JPanel chainRight(ChainNode node)
	{
		JPanel costs = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		costs.setBackground(ColorScheme.DARK_GRAY_COLOR);

		if (node.isCovered())
			return costs;

		if (node.getViaSkill() != null)
		{
			if (node.getSurplus() > 0L)
			{
				JLabel extra = chainCostLabel("+" + node.getSurplus() + " left", SP_GOLD);
				extra.setToolTipText("This step makes " + node.getSurplus() + " more than the tree needs");
				costs.add(extra);
			}
		}
		else if (node.getBuyUnit() == PriceLookup.UNKNOWN)
		{
			costs.add(chainCostLabel("gather", SP_MUTED));
		}
		else
		{
			JLabel buy = chainCostLabel("buy " + num(node.getBuyUnit()), SP_MUTED);
			buy.setToolTipText("Buy price: " + num(node.getBuyUnit()) + " gp each");
			costs.add(buy);
		}

		if (node.isTruncated())
		{
			JLabel more = chainCostLabel("…", SP_MUTED);
			more.setToolTipText("Chain continues (depth cap or cycle)");
			costs.add(more);
		}

		return costs;
	}

	private JPanel materialsSection(String title, List<MaterialLine> lines, boolean isTotals)
	{
		JPanel panel = listPanel();
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		panel.add(sectionSeparator());
		panel.add(sectionHeader(title));
		for (MaterialLine line : lines)
			panel.add(materialRow(line, isTotals));

		return panel;
	}

	private JPanel materialRow(MaterialLine line, boolean isTotals)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(2, 6, 0, 0));

		JPanel left = new JPanel();
		left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
		left.setBackground(ColorScheme.DARK_GRAY_COLOR);
		left.add(iconSlot(line.getItemId()));

		String text = line.getQty() + "× " + line.getName();
		boolean covered = isTotals && line.isCovered();
		JLabel name;
		if (covered)
		{
			name = struckLabel(text, SP_HIGH);
		}
		else
		{
			name = new JLabel(text);
			name.setFont(FontManager.getRunescapeSmallFont());
			name.setBorder(new EmptyBorder(0, 4, 0, 0));
			name.setForeground(isTotals ? Color.WHITE : SP_GOLD);
		}

		left.add(name);
		row.add(left, BorderLayout.WEST);

		if (isTotals && !covered)
		{
			long obtain = line.toObtain();
			String note = line.getHeld() > 0 ? "have " + line.getHeld() + " · get " + obtain : "get " + obtain;
			row.add(chainCostLabel(note, SP_MUTED), BorderLayout.EAST);
		}

		capHeight(row);
		return row;
	}

	private static JLabel chainCostLabel(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	/**
	 * A label that paints a line-through across the text at its vertical middle. The RuneScape pixel
	 * font places HTML {@code <s>} strikethrough near the baseline, where it reads as an underline, so
	 * the strike is drawn manually instead.
	 */
	private static JLabel struckLabel(String text, Color color)
	{
		JLabel label = new JLabel(text)
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				Insets in = getInsets();
				FontMetrics fm = g.getFontMetrics(getFont());
				int y = getHeight() / 2;
				g.setColor(getForeground());
				g.drawLine(in.left, y, in.left + fm.stringWidth(getText()), y);
			}
		};
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setBorder(new EmptyBorder(0, 4, 0, 0));
		label.setForeground(color);
		return label;
	}

	private static String costText(CostLine line)
	{
		if (line.getItemId() == PriceLookup.COINS_ID)
			return line.getQty() + "× " + line.getLabel();

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
