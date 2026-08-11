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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Turns a set of {@link ShoppingRequest}s (product + target quantity) into an aggregated raw material
 * shopping list. Each request's recipe is expanded recursively: for every consumed input, the shared
 * {@link ChainValuator} decides whether it is cheaper to buy or to make; buy leaves are accumulated into
 * a bill of materials, make leaves recurse into their cheapest recipe. Quantities scale by the
 * success-and-yield-adjusted actions needed (rounded up &mdash; you cannot run a fractional action).
 *
 * <p>Requests that share an input have it summed so it is bought once. In hybrid mode the caller passes a
 * held-stock map, which is subtracted from each raw material so only the shortfall is listed. Per line
 * the builder attaches the buy price, total cost, 4-hour GE buy limit, and the number of parallel buy
 * windows the limit forces. Untradeable/gathered inputs (no resolved id) are silently dropped &mdash;
 * they cannot be bought on the GE.
 *
 * <p>Held stock is subtracted at the raw-material (leaf) level only; a held intermediate that the
 * valuator chooses to make is not credited. Pure and deterministic given a fixed price/level context.
 */
public class ShoppingListBuilder
{
	private static final int MAX_DEPTH = 20;
	private static final int DEFAULT_LEVEL = 99;

	private final ChainValuator valuator;
	private final PriceLookup prices;
	private final ToIntFunction<String> levelFn;
	private final List<SuccessModifier> active;
	private final ToIntFunction<Integer> buyLimit;

	/**
	 * @param valuator the shared make-or-buy valuator (fixes the price/level context)
	 * @param prices   the price source (buy prices for the leaf materials)
	 * @param levelFn  resolves a skill name to the player's live level (for action counts)
	 * @param active   the success modifiers currently in effect
	 * @param buyLimit resolves an item id to its 4-hour GE buy limit ({@code 0} when unlimited/unknown)
	 */
	public ShoppingListBuilder(ChainValuator valuator, PriceLookup prices, ToIntFunction<String> levelFn,
			List<SuccessModifier> active, ToIntFunction<Integer> buyLimit)
	{
		this.valuator = valuator;
		this.prices = prices;
		this.levelFn = levelFn;
		this.active = active;
		this.buyLimit = buyLimit;
	}

	/**
	 * Builds the aggregated shopping list for a set of requests.
	 *
	 * @param requests the products and target quantities to source materials for
	 * @param held     item id &rarr; held count subtracted from the shortfall (empty for buy-to-order)
	 * @return the aggregated shopping list
	 */
	public ShoppingList build(List<ShoppingRequest> requests, Map<Integer, Integer> held)
	{
		Map<Integer, Integer> stock = held == null ? Collections.emptyMap() : held;
		Bom bom = new Bom();
		for (ShoppingRequest req : requests)
		{
			if (req.getTargetQty() <= 0 || req.getRecipe() == null)
				continue;

			produce(req.getRecipe(), req.getTargetQty(), bom, new HashSet<>(), 0);
		}

		List<ShoppingLine> lines = new ArrayList<>();
		long totalCost = 0L;
		int maxWindows = 0;
		boolean fullyPriced = true;
		for (Map.Entry<Integer, Long> entry : bom.quantities.entrySet())
		{
			int id = entry.getKey();
			long needed = entry.getValue();
			long have = stock.getOrDefault(id, 0);
			long toBuy = Math.max(0L, needed - have);
			if (toBuy <= 0L)
				continue;

			long unit = prices.buyPrice(id);
			boolean priced = unit != PriceLookup.UNKNOWN;
			long cost = priced ? unit * toBuy : 0L;
			int limit = Math.max(0, buyLimit.applyAsInt(id));
			int windows = limit > 0 ? (int) ((toBuy + limit - 1) / limit) : 1;
			lines.add(new ShoppingLine(id, bom.names.get(id), needed, have, toBuy, unit, cost, limit,
					windows));
			totalCost += cost;
			maxWindows = Math.max(maxWindows, windows);
			fullyPriced = fullyPriced && priced;
		}

		return new ShoppingList(lines, totalCost, maxWindows, fullyPriced);
	}

	/**
	 * Accumulates the material requirements of running a recipe enough times to yield {@code unitsWanted}
	 * of its primary output.
	 *
	 * @param r           the recipe to run
	 * @param unitsWanted the number of primary-output units wanted
	 * @param bom         the bill of materials being accumulated
	 * @param onStack     the item ids currently being resolved (cycle guard)
	 * @param depth       the current recursion depth
	 */
	private void produce(Recipe r, long unitsWanted, Bom bom, Set<Integer> onStack, int depth)
	{
		double expectedPrimary = expectedPrimary(r);
		if (expectedPrimary <= 0.0)
			return;

		long actions = (long) Math.ceil(unitsWanted / expectedPrimary);
		for (ItemStack in : r.getInputs())
		{
			if (!in.isConsumed())
				continue;

			long qtyNeeded = actions * in.getQty();
			if (qtyNeeded > 0L)
				acquire(in, qtyNeeded, bom, onStack, depth);
		}
	}

	/**
	 * Sources a quantity of one input, adding it to the bill as a buy leaf or recursing into its cheapest
	 * recipe when making it is cheaper. Untradeable/gathered inputs (no resolved id) are dropped.
	 *
	 * @param stack     the input (id, name, per-action quantity)
	 * @param qtyNeeded the total quantity of this input needed
	 * @param bom       the bill of materials being accumulated
	 * @param onStack   the item ids currently being resolved (cycle guard)
	 * @param depth     the current recursion depth
	 */
	private void acquire(ItemStack stack, long qtyNeeded, Bom bom, Set<Integer> onStack, int depth)
	{
		Integer id = stack.getItemId();
		if (id == null)
			return;

		Sourcing s = valuator.cheapest(id);
		boolean canMake = !s.isViaBuy() && s.getCraftRecipe() != null && s.obtainable()
				&& !onStack.contains(id) && depth < MAX_DEPTH;
		if (!canMake)
		{
			bom.add(id, stack.getName(), qtyNeeded);
			return;
		}

		onStack.add(id);
		produce(s.getCraftRecipe(), qtyNeeded, bom, onStack, depth + 1);
		onStack.remove(id);
	}

	/**
	 * The expected number of good primary-output units per action for a recipe, folding in the success
	 * chance and yield multiplier at the player's level.
	 *
	 * @param r the recipe
	 * @return the expected good output per action, or {@code 0} when it has no outputs
	 */
	private double expectedPrimary(Recipe r)
	{
		List<RecipeOutput> outputs = r.getOutputs();
		if (outputs.isEmpty())
			return 0.0;

		SkillReq skill = r.primarySkill();
		int level = skill == null ? DEFAULT_LEVEL : levelFn.applyAsInt(skill.getSkill());
		double p = Success.chance(r, level, active);
		double yield = Success.yieldMult(r, active);
		return p * outputs.get(0).getQty() * yield;
	}

	/**
	 * A bill of materials: raw item id &rarr; accumulated quantity, plus each item's display name, kept in
	 * discovery order so the rendered list reads roughly top-down.
	 */
	private static final class Bom
	{
		private final Map<Integer, Long> quantities = new LinkedHashMap<>();
		private final Map<Integer, String> names = new LinkedHashMap<>();

		private void add(int id, String name, long qty)
		{
			quantities.merge(id, qty, Long::sum);
			names.putIfAbsent(id, name);
		}
	}
}
