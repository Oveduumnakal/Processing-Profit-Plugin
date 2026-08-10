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
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Entry point: loads the bundled recipe catalog, keeps live GE prices fresh, and drives the sidebar. On
 * start it adds a navigation button opening {@link ProcessingProfitPanel}, loads recipes off-thread,
 * refreshes prices on an interval, and rebuilds the Browse (all conversions) and On-hand (from current
 * stock) tabs. Browse is computed at max level for now; live levels and gating land in a later issue.
 */
@Slf4j
@PluginDescriptor(
	name = "Processing Profit",
	description = "Find profitable item processing: live GE profit for multi-step conversion chains, "
		+ "ranked by what to make from your items or the best right now",
	tags = {"profit", "process", "processing", "craft", "recipe", "chain", "ge", "grand exchange",
		"live price", "wiki", "moneymaking", "skilling", "margin"}
)
public class ProcessingProfitPlugin extends Plugin
{
	private static final int MAX_ROWS = 100;
	private static final int BROWSE_LEVEL = 99;

	@Inject
	private ProcessingProfitConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClientThread clientThread;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private GePriceLookup prices;

	private final RecipeRepository recipes = new RecipeRepository();
	private final ProfitCalculator calculator = new ProfitCalculator();
	private final SourcingEngine sourcing = new SourcingEngine(calculator);

	private ProcessingProfitPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> refreshFuture;
	private volatile boolean loaded;

	@Override
	protected void startUp()
	{
		panel = new ProcessingProfitPanel();
		panel.setModeListener(mode -> refreshOnHand());
		navButton = NavigationButton.builder()
				.tooltip("Processing Profit")
				.icon(navIcon())
				.priority(7)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);

		executor.execute(this::initialLoad);
		int seconds = Math.max(10, config.refreshSeconds());
		refreshFuture = executor.scheduleWithFixedDelay(this::onRefreshTick, seconds, seconds,
				TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown()
	{
		if (refreshFuture != null)
		{
			refreshFuture.cancel(true);
			refreshFuture = null;
		}

		clientToolbar.removeNavigation(navButton);
		loaded = false;
	}

	private void initialLoad()
	{
		recipes.load();
		prices.refresh();
		loaded = true;
		panel.setModifierText("none (auto-detect in a later update)");
		buildBrowse();
		refreshOnHand();
	}

	private void onRefreshTick()
	{
		if (!loaded)
			return;

		prices.refresh();
		buildBrowse();
		refreshOnHand();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV || id == InventoryID.BANK)
			refreshOnHand();
	}

	private void buildBrowse()
	{
		if (!loaded)
			return;

		PriceConfig cfg = priceConfig();
		List<RecipeRow> rows = new ArrayList<>();
		for (Recipe recipe : recipes.all())
		{
			Integer outId = recipe.primaryOutputId();
			if (outId == null)
				continue;

			ProfitResult result = calculator.evaluate(recipe, prices, cfg, BROWSE_LEVEL,
					Collections.emptyList());
			if (!passesFilters(result, outId))
				continue;

			rows.add(toRow(recipe, result, -1));
		}

		rows.sort((a, b) -> Long.compare(b.getProfitEach(), a.getProfitEach()));
		panel.setBrowseRows(cap(rows));
	}

	private void refreshOnHand()
	{
		if (!loaded)
			return;

		clientThread.invoke(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				panel.setOnHandRows(Collections.emptyList());
				return;
			}

			Map<Integer, Integer> held = readHeld();
			executor.execute(() -> buildOnHand(held));
		});
	}

	private void buildOnHand(Map<Integer, Integer> held)
	{
		PriceConfig cfg = priceConfig();
		List<RecipeRow> rows = new ArrayList<>();
		for (Recipe recipe : recipes.all())
		{
			Integer outId = recipe.primaryOutputId();
			if (outId == null)
				continue;

			SourcingResult result = sourcing.evaluate(recipe, prices, cfg, BROWSE_LEVEL,
					Collections.emptyList(), held, SourcingMode.ON_HAND,
					SourcingEngine.DEFAULT_HYBRID_TARGET);
			if (result.getMakeableNow() <= 0 || result.getProfitEach() < config.minProfit())
				continue;

			ProfitResult profit = calculator.evaluate(recipe, prices, cfg, BROWSE_LEVEL,
					Collections.emptyList());
			rows.add(toRow(recipe, profit, result.getMakeableNow()));
		}

		rows.sort((a, b) -> Long.compare(
				(long) b.getMakeableNow() * b.getProfitEach(),
				(long) a.getMakeableNow() * a.getProfitEach()));
		panel.setOnHandRows(cap(rows));
	}

	private Map<Integer, Integer> readHeld()
	{
		Map<Integer, Integer> held = new HashMap<>();
		addContainer(held, client.getItemContainer(InventoryID.INV));
		addContainer(held, client.getItemContainer(InventoryID.BANK));
		return held;
	}

	private void addContainer(Map<Integer, Integer> held, ItemContainer container)
	{
		if (container == null)
			return;

		for (Item item : container.getItems())
		{
			if (item.getId() < 0 || item.getQuantity() <= 0)
				continue;

			int id = itemManager.canonicalize(item.getId());
			held.merge(id, item.getQuantity(), Integer::sum);
		}
	}

	private boolean passesFilters(ProfitResult result, int outputId)
	{
		if (result.getProfitEach() < config.minProfit())
			return false;

		if (result.isThroughputKnown() && result.getGpPerHour() < config.minGpPerHour())
			return false;

		return prices.volume(outputId) >= config.minVolume();
	}

	private RecipeRow toRow(Recipe recipe, ProfitResult result, int makeable)
	{
		RecipeOutput primary = recipe.getOutputs().get(0);
		SkillReq skill = recipe.primarySkill();
		int levelReq = skill == null ? 1 : skill.getLevel();
		SuccessModel model = recipe.getSuccess();
		Double success = model == null || model.getType() == SuccessType.ALWAYS
				? null : result.getSuccessChance();
		long volume = prices.volume(primary.getItemId());
		return new RecipeRow(primary.getItemId(), primary.getName(), result.getProfitEach(),
				result.getGpPerHour(), result.isThroughputKnown(), success, levelReq, volume, makeable,
				result.isStalePrices());
	}

	private static List<RecipeRow> cap(List<RecipeRow> rows)
	{
		return rows.size() > MAX_ROWS ? new ArrayList<>(rows.subList(0, MAX_ROWS)) : rows;
	}

	private PriceConfig priceConfig()
	{
		double efficiency = Math.max(1, Math.min(100, config.efficiency())) / 100.0;
		return new PriceConfig(config.applyGeTax(), efficiency);
	}

	private static BufferedImage navIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setColor(new Color(220, 180, 60));
		g.setFont(new Font("SansSerif", Font.BOLD, 14));
		g.drawString("P", 4, 13);
		g.dispose();
		return icon;
	}

	@Provides
	ProcessingProfitConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ProcessingProfitConfig.class);
	}
}
