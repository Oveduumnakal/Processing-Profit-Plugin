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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
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
import net.runelite.client.ui.overlay.OverlayManager;

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

	@Inject
	private OverlayManager overlayManager;

	private final RecipeRepository recipes = new RecipeRepository();
	private final Map<Integer, Map<Integer, Integer>> containerCache = new ConcurrentHashMap<>();
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
	private ProcessingProfitOverlay overlay;
	private NavigationButton navButton;
	private ScheduledFuture<?> refreshFuture;
	private IronmanValuation ironmanLens;
	private volatile List<RecipeRow> onHandSnapshot = Collections.emptyList();
	private volatile boolean bankOpen;
	private volatile boolean loaded;

	@Override
	protected void startUp()
	{
		ironmanLens = new IronmanValuation(prices, prices::mapping);
		panel = new ProcessingProfitPanel();
		panel.setModeListener(mode -> rebuildShopping());
		panel.setSelectionListener(this::showDetail);
		panel.setShoppingAddListener(this::addToShopping);
		panel.setShoppingClearListener(this::clearShopping);
		panel.setPinToggleListener(this::togglePin);
		panel.setValuationListener(this::onValuationSelected);
		panel.setValuationLens(config.valuationLens());
		navButton = NavigationButton.builder()
				.tooltip("Processing Profit")
				.icon(navIcon())
				.priority(7)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);

		overlay = new ProcessingProfitOverlay(config, () -> onHandSnapshot, () -> bankOpen);
		overlayManager.add(overlay);

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

		if (overlay != null)
		{
			overlayManager.remove(overlay);
			overlay = null;
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
			if (id == InventoryID.BANK)
				captureContainer(id, event.getItemContainer());

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
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
			bankOpen = true;
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
			bankOpen = false;
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
			containerCache.clear();
			bankOpen = false;
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

		panel.setValuationLens(config.valuationLens());
		recomputeModifiers();
		rebuildRecipeTabs();
		rebuildShopping();
	}

	/**
	 * Persists the sidebar's chosen valuation lens to config; the resulting {@code onConfigChanged}
	 * rebuilds every tab through the new lens.
	 *
	 * @param lens the newly selected lens
	 */
	private void onValuationSelected(ValuationLens lens)
	{
		configManager.setConfiguration(ProcessingProfitConfig.GROUP, "valuationLens", lens);
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

			ProfitResult result = calculator.evaluate(recipe, activeLens(), cfg, primaryLevel(recipe),
					activeModifiers);
			if (!passesFilters(result, outId))
				continue;

			rows.add(toRow(recipe, result, -1));
		}

		panel.setBrowseRows(dedupeByProduct(rows));
	}

	/**
	 * Collapses rows that share a primary output item to a single best-profit row, so the same product
	 * made by several recipes shows once instead of duplicating (e.g. "Mystic fire staff").
	 *
	 * @param rows the rows to collapse
	 * @return one row per output item, keeping the highest profit per action
	 */
	private static List<RecipeRow> dedupeByProduct(List<RecipeRow> rows)
	{
		Map<Integer, RecipeRow> best = new LinkedHashMap<>();
		Map<Integer, Integer> counts = new HashMap<>();
		Map<Integer, Long> mins = new HashMap<>();
		Map<Integer, Long> maxes = new HashMap<>();
		for (RecipeRow row : rows)
		{
			int id = row.getItemId();
			counts.merge(id, 1, Integer::sum);
			mins.merge(id, row.getProfitEach(), Math::min);
			maxes.merge(id, row.getProfitEach(), Math::max);
			RecipeRow existing = best.get(id);
			if (existing == null || row.getProfitEach() > existing.getProfitEach())
				best.put(id, row);
		}

		List<RecipeRow> out = new ArrayList<>(best.size());
		for (RecipeRow winner : best.values())
		{
			int id = winner.getItemId();
			out.add(winner.toBuilder()
					.recipeCount(counts.get(id))
					.profitMin(mins.get(id))
					.profitMax(maxes.get(id))
					.build());
		}

		return out;
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
			SourcingResult result = sourcing.evaluate(recipe, activeLens(), cfg, level,
					activeModifiers, held, SourcingMode.ON_HAND,
					SourcingEngine.DEFAULT_HYBRID_TARGET);
			if (result.getMakeableNow() <= 0 || result.getProfitEach() < config.minProfit())
				continue;

			ProfitResult profit = calculator.evaluate(recipe, activeLens(), cfg, level,
					activeModifiers);
			rows.add(toRow(recipe, profit, result.getMakeableNow()));
		}

		List<RecipeRow> deduped = dedupeByProduct(rows);
		onHandSnapshot = deduped;
		panel.setOnHandRows(deduped);
	}

	/**
	 * Snapshots the bank's canonicalised counts into {@link #containerCache}. Called on the client thread
	 * from {@code onItemContainerChanged} so the last-seen bank contents survive the bank interface
	 * unloading &mdash; reading the live bank container after it closes yields an empty set, which would
	 * otherwise blank the On-hand tab on the next refresh tick. The inventory is always read live (it
	 * stays valid the whole session), so only the bank needs caching.
	 *
	 * @param id        the container id (bank)
	 * @param container the changed container, may be {@code null}
	 */
	private void captureContainer(int id, ItemContainer container)
	{
		Map<Integer, Integer> counts = new HashMap<>();
		addContainer(counts, container);
		containerCache.put(id, counts);
	}

	private Map<Integer, Integer> readHeld()
	{
		Map<Integer, Integer> held = new HashMap<>();
		addContainer(held, client.getItemContainer(InventoryID.INV));
		Map<Integer, Integer> bank = containerCache.get(InventoryID.BANK);
		if (bank != null)
			for (Map.Entry<Integer, Integer> entry : bank.entrySet())
				held.merge(entry.getKey(), entry.getValue(), Integer::sum);

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

		return ironman() || prices.volume(outputId) >= config.minVolume();
	}

	private void showDetail(RecipeRow row)
	{
		Recipe recipe = row.getRecipe();
		if (!loaded || recipe == null)
			return;

		executor.execute(() ->
		{
			PriceConfig cfg = priceConfig();
			PriceLookup lens = activeLens();
			ChainValuator valuator = new ChainValuator(recipes, lens, cfg, this::levelFor,
					activeModifiers);
			RecipeDetail detail = DetailBuilder.build(recipe, lens, cfg, primaryLevel(recipe),
					activeModifiers, calculator, valuator);
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

				ProfitResult result = calculator.evaluate(recipe, activeLens(), cfg, primaryLevel(recipe),
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
		PriceLookup lens = activeLens();
		ChainValuator valuator = new ChainValuator(recipes, lens, cfg, this::levelFor,
				activeModifiers);
		ToIntFunction<Integer> buyLimit = this::buyLimitOf;
		ShoppingListBuilder builder = new ShoppingListBuilder(valuator, lens, this::levelFor,
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
		long volume = activeLens().volume(primary.getItemId());
		GateResult gate = gateFor(recipe);
		LiquidityResult liq = ironman()
				? new LiquidityResult(0, false, false)
				: Liquidity.assess(recipe, this::buyLimitOf, volume);
		return new RecipeRow(primary.getItemId(), primary.getName(), skillName, result.getProfitEach(),
				result.getRoi(), result.getGpPerHour(), result.getXpPerHour(), result.isThroughputKnown(),
				success, levelReq, volume, makeable, result.isStalePrices(), gate.isLocked(),
				gate.getReason(), liq.getUnitsPerWindow(), liq.isThrottled(), liq.isLowVolume(),
				recipe.isMembers(), 1, result.getProfitEach(), result.getProfitEach(), recipe);
	}

	private int buyLimitOf(int itemId)
	{
		if (ironman())
			return 0;

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
		return new PriceConfig(config.applyGeTax() && !ironman(), efficiency);
	}

	private boolean ironman()
	{
		return config.valuationLens() == ValuationLens.IRONMAN;
	}

	/**
	 * The active valuation lens: the live GE-market lookup, or the no-GE {@link IronmanValuation} (alch/
	 * shop) when the Ironman lens is selected. The concrete {@link GePriceLookup} is still the data source
	 * for refreshes and item metadata; only the buy/sell/volume valuation is routed through the lens.
	 *
	 * @return the price lookup the engine should value with
	 */
	private PriceLookup activeLens()
	{
		return ironman() ? ironmanLens : prices;
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
