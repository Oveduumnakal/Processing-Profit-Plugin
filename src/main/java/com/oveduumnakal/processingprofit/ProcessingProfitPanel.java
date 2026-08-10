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
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.QuantityFormatter;

/**
 * The Processing Profit sidebar: a sourcing-mode and valuation-lens selector, an active-modifier status
 * line, and four tabs &mdash; Browse (all conversions), On-hand (makeable from current stock), Watchlist
 * (pinned), and Shopping (mat lists). Each tab renders a compact list of {@link RecipeRow}s. The panel is
 * a dumb view: the plugin builds the rows and pushes them in via the {@code set*Rows} methods.
 */
@Singleton
public class ProcessingProfitPanel extends PluginPanel
{
	private static final Color PROFIT = new Color(76, 175, 80);
	private static final Color LOSS = new Color(244, 67, 54);
	private static final String[] MODE_LABELS = {"On-hand", "Buy to order", "Hybrid"};
	private static final SourcingMode[] MODES =
			{SourcingMode.ON_HAND, SourcingMode.BUY_TO_ORDER, SourcingMode.HYBRID};

	private final JComboBox<String> modeSelector = new JComboBox<>(MODE_LABELS);
	private final JComboBox<String> valuationSelector =
			new JComboBox<>(new String[]{"GE market", "Ironman (v2)"});
	private final JLabel modifierLine = new JLabel("Modifiers: none");

	private final JPanel browseRows = listPanel();
	private final JPanel onHandRows = listPanel();
	private final JPanel watchlistRows = listPanel();
	private final JPanel shoppingRows = listPanel();

	private Consumer<SourcingMode> modeListener;

	/**
	 * Builds the panel layout.
	 */
	@Inject
	public ProcessingProfitPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTabGroup tabGroup = new MaterialTabGroup(display);
		tabGroup.setBorder(new EmptyBorder(8, 0, 4, 0));
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

		modeSelector.addActionListener(e -> fireModeChanged());
		tabGroup.select(browse);
		placeholderAll();
	}

	private JPanel header(MaterialTabGroup tabGroup)
	{
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Processing Profit");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);
		header.add(labelledRow("Sourcing", modeSelector));
		header.add(labelledRow("Valuation", valuationSelector));

		modifierLine.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		modifierLine.setFont(modifierLine.getFont().deriveFont(10f));
		modifierLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		modifierLine.setBorder(new EmptyBorder(6, 0, 0, 0));
		header.add(modifierLine);

		tabGroup.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(tabGroup);
		return header;
	}

	private static JPanel labelledRow(String text, Component field)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(4, 0, 0, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setPreferredSize(new Dimension(64, 20));
		row.add(label, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
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

		JScrollPane scroll = new JScrollPane(wrap);
		scroll.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
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
	 * Replaces the Browse tab rows.
	 *
	 * @param rows the rows to show
	 */
	public void setBrowseRows(List<RecipeRow> rows)
	{
		setRows(browseRows, rows, false);
	}

	/**
	 * Replaces the On-hand tab rows.
	 *
	 * @param rows the rows to show
	 */
	public void setOnHandRows(List<RecipeRow> rows)
	{
		setRows(onHandRows, rows, true);
	}

	/**
	 * Replaces the Watchlist tab rows.
	 *
	 * @param rows the rows to show
	 */
	public void setWatchlistRows(List<RecipeRow> rows)
	{
		setRows(watchlistRows, rows, false);
	}

	/**
	 * Replaces the Shopping tab rows.
	 *
	 * @param rows the rows to show
	 */
	public void setShoppingRows(List<RecipeRow> rows)
	{
		setRows(shoppingRows, rows, true);
	}

	private void setRows(JPanel container, List<RecipeRow> rows, boolean showMakeable)
	{
		SwingUtilities.invokeLater(() ->
		{
			container.removeAll();
			if (rows == null || rows.isEmpty())
			{
				container.add(placeholder("Nothing to show yet."));
			}
			else
			{
				for (RecipeRow row : rows)
					container.add(rowComponent(row, showMakeable));
			}

			container.revalidate();
			container.repaint();
		});
	}

	private void placeholderAll()
	{
		browseRows.add(placeholder("Loading recipes…"));
		onHandRows.add(placeholder("Open your bank or inventory."));
		watchlistRows.add(placeholder("Pin recipes to watch them."));
		shoppingRows.add(placeholder("Add products to build a list."));
	}

	private static JLabel placeholder(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(8, 2, 8, 2));
		return label;
	}

	private static JPanel rowComponent(RecipeRow row, boolean showMakeable)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(4, 6, 4, 6));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel name = new JLabel((row.isStale() ? "• " : "") + row.getProduct());
		name.setForeground(Color.WHITE);
		JLabel profit = new JLabel(signed(row.getProfitEach()));
		profit.setForeground(row.getProfitEach() >= 0 ? PROFIT : LOSS);
		profit.setHorizontalAlignment(SwingConstants.RIGHT);
		top.add(name, BorderLayout.CENTER);
		top.add(profit, BorderLayout.EAST);

		JLabel meta = new JLabel(metaText(row, showMakeable));
		meta.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		meta.setFont(meta.getFont().deriveFont(10f));

		panel.add(top, BorderLayout.NORTH);
		panel.add(meta, BorderLayout.SOUTH);
		panel.setBorder(BorderFactory.createCompoundBorder(
				new EmptyBorder(2, 0, 2, 0),
				new EmptyBorder(4, 6, 4, 6)));
		return panel;
	}

	private static String metaText(RecipeRow row, boolean showMakeable)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("gp/hr ").append(row.isThroughputKnown()
				? QuantityFormatter.quantityToStackSize(row.getGpPerHour()) : "n/a");
		if (row.getSuccessPercent() != null)
		{
			long pct = Math.round(row.getSuccessPercent() * 100);
			sb.append("  succ ");
			sb.append(pct);
			sb.append('%');
		}

		sb.append("  lvl ").append(row.getLevelReq());
		if (showMakeable && row.getMakeableNow() >= 0)
			sb.append("  x").append(row.getMakeableNow());

		return sb.toString();
	}

	private static String signed(long value)
	{
		String magnitude = QuantityFormatter.quantityToStackSize(Math.abs(value));
		return (value >= 0 ? "+" : "-") + magnitude;
	}
}
