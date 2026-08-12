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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

/**
 * Bank overlay: while the bank is open it lists the top few products the player can make right now from
 * banked and carried stock, plus the total achievable profit if every makeable product were made. It
 * reuses the plugin's On-hand engine result (a snapshot supplier) rather than recomputing, so it simply
 * reflects whatever the On-hand tab last built. Drawn only when enabled in config and the bank is open.
 */
public class ProcessingProfitOverlay extends OverlayPanel
{
	/** How many top products the overlay lists. */
	private static final int TOP_N = 5;
	private static final Color PROFIT = new Color(100, 220, 100);
	private static final Color LOSS = new Color(220, 100, 100);
	private static final Color MUTED = new Color(170, 170, 170);
	private static final Color TITLE = new Color(255, 200, 0);

	private final ProcessingProfitConfig config;
	private final Supplier<List<RecipeRow>> snapshot;
	private final BooleanSupplier bankOpen;

	/**
	 * @param config   the plugin config (bank-overlay toggle)
	 * @param snapshot supplies the latest On-hand rows
	 * @param bankOpen whether the bank interface is currently open
	 */
	public ProcessingProfitOverlay(ProcessingProfitConfig config, Supplier<List<RecipeRow>> snapshot,
			BooleanSupplier bankOpen)
	{
		this.config = config;
		this.snapshot = snapshot;
		this.bankOpen = bankOpen;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showBankOverlay() || !bankOpen.getAsBoolean())
			return null;

		List<RecipeRow> rows = snapshot.get();
		if (rows == null || rows.isEmpty())
			return null;

		List<RecipeRow> top = new ArrayList<>(rows);
		top.sort((a, b) -> Long.compare(b.getProfitEach(), a.getProfitEach()));

		panelComponent.getChildren().add(TitleComponent.builder()
				.text("Processing Profit")
				.color(TITLE)
				.build());

		int shown = Math.min(TOP_N, top.size());
		for (int i = 0; i < shown; i++)
		{
			RecipeRow row = top.get(i);
			panelComponent.getChildren().add(LineComponent.builder()
					.left(row.getProduct())
					.leftColor(Color.WHITE)
					.right(signed(row.getProfitEach()))
					.rightColor(row.getProfitEach() >= 0 ? PROFIT : LOSS)
					.build());
		}

		if (top.size() > shown)
		{
			panelComponent.getChildren().add(LineComponent.builder()
					.left("… and " + (top.size() - shown) + " more")
					.leftColor(MUTED)
					.build());
		}

		panelComponent.getChildren().add(LineComponent.builder()
				.left("Total achievable")
				.leftColor(MUTED)
				.right(signed(totalAchievable(rows)))
				.rightColor(PROFIT)
				.build());

		return super.render(graphics);
	}

	static long totalAchievable(List<RecipeRow> rows)
	{
		long total = 0L;
		for (RecipeRow row : rows)
		{
			int makeable = Math.max(0, row.getMakeableNow());
			if (row.getProfitEach() > 0)
				total += row.getProfitEach() * (long) makeable;
		}

		return total;
	}

	private static String signed(long value)
	{
		String magnitude = QuantityFormatter.quantityToStackSize(Math.abs(value));
		return (value >= 0 ? "+" : "-") + magnitude;
	}
}
