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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import javax.inject.Inject;

import com.google.inject.Provides;
import com.oveduumnakal.processingprofit.WikiPriceClient.ItemMapping;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * Entry point: loads the bundled recipe catalog, keeps live GE prices fresh, and drives the sidebar. On
 * start it adds a navigation button opening {@link ProcessingProfitPanel}, loads recipes off-thread,
 * refreshes prices on an interval, and rebuilds the Browse (all conversions) and On-hand (from current
 * stock) tabs. Profit and success math are read at the player's live skill levels (falling back to max
 * when logged out), and each row carries its skill/quest {@link GateResult} so the panel can hide or
 * grey recipes the player cannot yet do.
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
	private static final int BROWSE_LEVEL = 99;
	private static final String WATCHLIST_KEY = "watchlist";
	private static final int COOKING_GAUNTLETS_ID = 775;
	private static final Set<Integer> COOKING_CAPE_IDS = new HashSet<>(Arrays.asList(9801, 9802, 9948));

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

	@Inject
	private ConfigManager configManager;

	private final RecipeRepository recipes = new RecipeRepository();
	private final ProfitCalculator calculator = new ProfitCalculator();
	private final SourcingEngine sourcing = new SourcingEngine(calculator);
	private final List<ShoppingRequest> shoppingSelection = new ArrayList<>();
	private volatile Watchlist watchlist = new Watchlist();
	private volatile Set<String> pinnedSnapshot = Collections.emptySet();
	private volatile Map<String, Integer> liveLevels = Collections.emptyMap();
	private volatile Set<String> completedQuests = Collections.emptySet();
	private volatile Set<String> knownQuests = Collections.emptySet();
	private volatile List<SuccessModifier> activeModifiers = Collections.emptyList();
	private volatile boolean detectGauntlets;
	private volatile boolean detectCape;
	private volatile HosidiusRange detectHosidius = HosidiusRange.NONE;

	private ProcessingProfitPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> refreshFuture;
	private volatile boolean loaded;

	@Override
	protected void startUp()
	{
		panel = new ProcessingProfitPanel();
		panel.setModeListener(mode ->
		{
			refreshOnHand();
			rebuildShopping();
		});
		panel.setSelectionListener(this::showDetail);
		panel.setShoppingAddListener(this::addToShopping);
		panel.setShoppingClearListener(this::clearShopping);
		panel.setPinToggleListener(this::togglePin);
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
		recomputeModifiers();
		loadWatchlist();
		buildBrowse();
		refreshOnHand();
		buildWatchlist();
	}

	private void onRefreshTick()
	{
		if (!loaded)
			return;

		prices.refresh();
		buildBrowse();
		refreshOnHand();
		buildWatchlist();
		rebuildShopping();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.INV || id == InventoryID.BANK)
		{
			refreshOnHand();
			rebuildShopping();
		}
		else if (id == InventoryID.WORN)
		{
			snapshotDetection();
			recomputeModifiers();
			rebuildRecipeTabs();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			snapshotLevels();
			snapshotQuests();
			snapshotDetection();
			recomputeModifiers();
			rebuildRecipeTabs();
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			liveLevels = Collections.emptyMap();
			completedQuests = Collections.emptySet();
			knownQuests = Collections.emptySet();
			detectGauntlets = false;
			detectCape = false;
			detectHosidius = HosidiusRange.NONE;
			recomputeModifiers();
			rebuildRecipeTabs();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
			return;

		HosidiusRange tier = detectedHosidiusTier();
		if (tier != detectHosidius)
		{
			detectHosidius = tier;
			recomputeModifiers();
			rebuildRecipeTabs();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!ProcessingProfitConfig.GROUP.equals(event.getGroup())
				|| WATCHLIST_KEY.equals(event.getKey()))
			return;

		recomputeModifiers();
		rebuildRecipeTabs();
		rebuildShopping();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill == null)
			return;

		Integer prev = liveLevels.get(skill.getName());
		if (prev != null && prev == event.getLevel())
			return;

		snapshotLevels();
		rebuildRecipeTabs();
	}

	/**
	 * Snapshots the player's real skill levels into a volatile map keyed by skill name. Must run on the
	 * client thread (called from client-thread events).
	 */
	private void snapshotLevels()
	{
		Map<String, Integer> levels = new HashMap<>();
		for (Skill skill : Skill.values())
			levels.put(skill.getName(), client.getRealSkillLevel(skill));

		liveLevels = levels;
	}

	/**
	 * Snapshots which quests are finished (and the set of all recognised quest names, so unrecognised
	 * recipe requirement strings never hide a recipe). Must run on the client thread.
	 */
	private void snapshotQuests()
	{
		Set<String> done = new HashSet<>();
		Set<String> known = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			String name = quest.getName().toLowerCase();
			known.add(name);
			try
			{
				if (quest.getState(client) == QuestState.FINISHED)
					done.add(name);
			}
			catch (RuntimeException e)
			{
				// some quests can throw before their varbits load; treat as not-finished
			}
		}

		completedQuests = done;
		knownQuests = known;
	}

	/**
	 * Snapshots auto-detectable success modifiers from live equipment and diary varbits. Must run on the
	 * client thread.
	 */
	private void snapshotDetection()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		detectGauntlets = equippedIs(worn, EquipmentInventorySlot.GLOVES, COOKING_GAUNTLETS_ID);
		detectCape = equippedIn(worn, EquipmentInventorySlot.CAPE, COOKING_CAPE_IDS);
		detectHosidius = detectedHosidiusTier();
	}

	private HosidiusRange detectedHosidiusTier()
	{
		if (client.getVarbitValue(Varbits.DIARY_KOUREND_ELITE) == 1)
			return HosidiusRange.ELITE;

		if (client.getVarbitValue(Varbits.DIARY_KOUREND_EASY) == 1)
			return HosidiusRange.EASY;

		return HosidiusRange.NONE;
	}

	private static boolean equippedIs(ItemContainer worn, EquipmentInventorySlot slot, int itemId)
	{
		if (worn == null)
			return false;

		Item item = worn.getItem(slot.getSlotIdx());
		return item != null && item.getId() == itemId;
	}

	private static boolean equippedIn(ItemContainer worn, EquipmentInventorySlot slot, Set<Integer> ids)
	{
		if (worn == null)
			return false;

		Item item = worn.getItem(slot.getSlotIdx());
		return item != null && ids.contains(item.getId());
	}

	/**
	 * Resolves config toggles over live detection into the active modifier list and the status line.
	 */
	private void recomputeModifiers()
	{
		ModifierState state = Modifiers.resolveState(
				config.jewellersChisel(), false,
				config.cookingGauntlets(), detectGauntlets,
				config.cookingCape(), detectCape,
				config.hosidius(), detectHosidius);
		activeModifiers = Modifiers.active(recipes.modifiers(), state);
		panel.setModifierText(Modifiers.statusText(state));
	}

	private void rebuildRecipeTabs()
	{
		if (!loaded)
			return;

		executor.execute(() ->
		{
			buildBrowse();
			buildWatchlist();
		});
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

			ProfitResult result = calculator.evaluate(recipe, prices, cfg, primaryLevel(recipe),
					activeModifiers);
			if (!passesFilters(result, outId))
				continue;

			rows.add(toRow(recipe, result, -1));
		}

		panel.setBrowseRows(rows);
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

			int level = primaryLevel(recipe);
			SourcingResult result = sourcing.evaluate(recipe, prices, cfg, level,
					activeModifiers, held, SourcingMode.ON_HAND,
					SourcingEngine.DEFAULT_HYBRID_TARGET);
			if (result.getMakeableNow() <= 0 || result.getProfitEach() < config.minProfit())
				continue;

			ProfitResult profit = calculator.evaluate(recipe, prices, cfg, level,
					activeModifiers);
			rows.add(toRow(recipe, profit, result.getMakeableNow()));
		}

		panel.setOnHandRows(rows);
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

	private void showDetail(RecipeRow row)
	{
		Recipe recipe = row.getRecipe();
		if (!loaded || recipe == null)
			return;

		executor.execute(() ->
		{
			PriceConfig cfg = priceConfig();
			RecipeDetail detail = DetailBuilder.build(recipe, prices, cfg, primaryLevel(recipe),
					activeModifiers, calculator);
			panel.showDetail(detail);
		});
	}

	private void addToShopping(ShoppingRequest request)
	{
		if (request.getRecipe() == null || request.getTargetQty() <= 0)
			return;

		synchronized (shoppingSelection)
		{
			shoppingSelection.add(request);
		}

		rebuildShopping();
	}

	private void clearShopping()
	{
		synchronized (shoppingSelection)
		{
			shoppingSelection.clear();
		}

		rebuildShopping();
	}

	private void loadWatchlist()
	{
		String csv = configManager.getConfiguration(ProcessingProfitConfig.GROUP, WATCHLIST_KEY);
		watchlist = Watchlist.parse(csv);
		publishPinned();
	}

	private void togglePin(RecipeRow row)
	{
		Recipe recipe = row.getRecipe();
		if (recipe == null || recipe.getRecipeId() == null)
			return;

		watchlist.toggle(recipe.getRecipeId());
		configManager.setConfiguration(ProcessingProfitConfig.GROUP, WATCHLIST_KEY, watchlist.format());
		publishPinned();
		buildWatchlist();
	}

	private void publishPinned()
	{
		pinnedSnapshot = new LinkedHashSet<>(watchlist.ids());
		panel.setPinned(pinnedSnapshot);
	}

	private void buildWatchlist()
	{
		if (!loaded)
			return;

		Set<String> ids = pinnedSnapshot;
		executor.execute(() ->
		{
			PriceConfig cfg = priceConfig();
			List<RecipeRow> rows = new ArrayList<>();
			for (String id : ids)
			{
				Recipe recipe = recipes.byRecipeId(id);
				if (recipe == null || recipe.primaryOutputId() == null)
					continue;

				ProfitResult result = calculator.evaluate(recipe, prices, cfg, primaryLevel(recipe),
						activeModifiers);
				rows.add(toRow(recipe, result, -1));
			}

			panel.setWatchlistRows(rows);
		});
	}

	private void rebuildShopping()
	{
		if (!loaded)
			return;

		if (panel.selectedMode() != SourcingMode.HYBRID)
		{
			executor.execute(() -> buildShopping(Collections.emptyMap()));
			return;
		}

		clientThread.invoke(() ->
		{
			Map<Integer, Integer> held = client.getGameState() == GameState.LOGGED_IN
					? readHeld() : Collections.emptyMap();
			executor.execute(() -> buildShopping(held));
		});
	}

	private void buildShopping(Map<Integer, Integer> held)
	{
		List<ShoppingRequest> requests;
		List<String> summary = new ArrayList<>();
		synchronized (shoppingSelection)
		{
			requests = new ArrayList<>(shoppingSelection);
		}

		for (ShoppingRequest req : requests)
		{
			List<RecipeOutput> outputs = req.getRecipe().getOutputs();
			String name = outputs.get(0).getName();
			summary.add(req.getTargetQty() + "× " + name);
		}

		PriceConfig cfg = priceConfig();
		ChainValuator valuator = new ChainValuator(recipes, prices, cfg, this::levelFor,
				activeModifiers);
		ToIntFunction<Integer> buyLimit = this::buyLimitOf;
		ShoppingListBuilder builder = new ShoppingListBuilder(valuator, prices, this::levelFor,
				activeModifiers, buyLimit);
		ShoppingList list = builder.build(requests, held);
		panel.setShoppingList(list, summary);
	}

	private RecipeRow toRow(Recipe recipe, ProfitResult result, int makeable)
	{
		RecipeOutput primary = recipe.getOutputs().get(0);
		SkillReq skill = recipe.primarySkill();
		int levelReq = skill == null ? 1 : skill.getLevel();
		String skillName = skill == null ? "" : skill.getSkill();
		SuccessModel model = recipe.getSuccess();
		Double success = model == null || model.getType() == SuccessType.ALWAYS
				? null : result.getSuccessChance();
		long volume = prices.volume(primary.getItemId());
		GateResult gate = gateFor(recipe);
		LiquidityResult liq = Liquidity.assess(recipe, this::buyLimitOf, volume);
		return new RecipeRow(primary.getItemId(), primary.getName(), skillName, result.getProfitEach(),
				result.getRoi(), result.getGpPerHour(), result.getXpPerHour(), result.isThroughputKnown(),
				success, levelReq, volume, makeable, result.isStalePrices(), gate.isLocked(),
				gate.getReason(), liq.getUnitsPerWindow(), liq.isThrottled(), liq.isLowVolume(),
				recipe.isMembers(), recipe);
	}

	private int buyLimitOf(int itemId)
	{
		ItemMapping mapping = prices.mapping(itemId);
		return mapping == null ? 0 : mapping.getLimit();
	}

	private int levelFor(String skill)
	{
		Integer level = liveLevels.get(skill);
		return level != null ? level : BROWSE_LEVEL;
	}

	private int primaryLevel(Recipe recipe)
	{
		SkillReq skill = recipe.primarySkill();
		return skill == null ? BROWSE_LEVEL : levelFor(skill.getSkill());
	}

	private boolean questSatisfied(String quest)
	{
		if (quest == null || quest.isEmpty())
			return true;

		String key = quest.toLowerCase();
		return !knownQuests.contains(key) || completedQuests.contains(key);
	}

	private GateResult gateFor(Recipe recipe)
	{
		return Gating.evaluate(recipe, liveLevels, this::questSatisfied, !liveLevels.isEmpty());
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
