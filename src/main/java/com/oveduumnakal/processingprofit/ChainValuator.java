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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Recursive make-or-buy valuation over the recipe graph: the cheapest cost to obtain one unit of an
 * item is {@code min(buy price, cheapest craft cost)}, where crafting recursively make-or-buys each
 * input. Craft cost accounts for the success chance and yield (expected good output per action) and
 * credits secondary outputs (byproducts) at their post-tax sell value.
 *
 * <p>Guards against cycles with an on-stack set and caps recursion depth. Results are memoized per
 * valuator (one valuator captures a fixed price/level/modifier context; build a new one when that
 * changes). {@link #cheapest(int)} exposes the chosen path and the make-vs-buy alternative.
 */
public class ChainValuator
{
	private static final int MAX_DEPTH = 20;
	private static final int DEFAULT_LEVEL = 99;

	private final RecipeRepository recipes;
	private final PriceLookup prices;
	private final PriceConfig config;
	private final ToIntFunction<String> levelFn;
	private final List<SuccessModifier> active;
	private final Map<Integer, Sourcing> memo = new HashMap<>();

	/**
	 * @param recipes the recipe catalog
	 * @param prices  the price source
	 * @param config  the tax config (applied to byproduct credit)
	 * @param levelFn resolves a skill name to the player's live level
	 * @param active  the success modifiers currently in effect
	 */
	public ChainValuator(RecipeRepository recipes, PriceLookup prices, PriceConfig config,
			ToIntFunction<String> levelFn, List<SuccessModifier> active)
	{
		this.recipes = recipes;
		this.prices = prices;
		this.config = config;
		this.levelFn = levelFn;
		this.active = active;
	}

	/**
	 * The cheapest way to obtain one unit of an item, choosing per intermediate whether to make or buy.
	 *
	 * @param itemId the item to source
	 * @return the sourcing decision, including the make-vs-buy comparison
	 */
	public Sourcing cheapest(int itemId)
	{
		return acquire(itemId, 0, new HashSet<>());
	}

	private Sourcing acquire(int itemId, int depth, Set<Integer> onStack)
	{
		Sourcing cached = memo.get(itemId);
		if (cached != null)
			return cached;

		double buyCost = toCost(prices.buyPrice(itemId));
		if (depth > MAX_DEPTH || onStack.contains(itemId))
			return new Sourcing(itemId, buyCost, true, null, buyCost, Double.POSITIVE_INFINITY);

		onStack.add(itemId);
		double bestCraft = Double.POSITIVE_INFINITY;
		Recipe bestRecipe = null;
		for (Recipe r : recipes.forOutput(itemId))
		{
			double c = craftUnitCost(r, depth, onStack);
			if (c < bestCraft)
			{
				bestCraft = c;
				bestRecipe = r;
			}
		}

		onStack.remove(itemId);

		double cost = Math.min(buyCost, bestCraft);
		boolean viaBuy = buyCost <= bestCraft;
		Sourcing s = new Sourcing(itemId, cost, viaBuy, bestRecipe, buyCost, bestCraft);
		memo.put(itemId, s);
		return s;
	}

	/**
	 * The cost to obtain one unit of a recipe's primary output by performing that recipe: expected input
	 * cost (each input make-or-bought), less byproduct credit, divided by the expected good output per
	 * action. {@link Double#POSITIVE_INFINITY} when an input is unobtainable or the yield is zero.
	 *
	 * @param r       the recipe
	 * @param depth   the current recursion depth
	 * @param onStack the items currently being resolved (cycle guard)
	 * @return the per-unit craft cost
	 */
	private double craftUnitCost(Recipe r, int depth, Set<Integer> onStack)
	{
		List<RecipeOutput> outputs = r.getOutputs();
		if (outputs.isEmpty())
			return Double.POSITIVE_INFINITY;

		SkillReq skill = r.primarySkill();
		int level = skill == null ? DEFAULT_LEVEL : levelFn.applyAsInt(skill.getSkill());
		double p = Success.chance(r, level, active);
		double yield = Success.yieldMult(r, active);
		double expectedPrimary = p * outputs.get(0).getQty() * yield;
		if (expectedPrimary <= 0.0)
			return Double.POSITIVE_INFINITY;

		double inputs = 0.0;
		for (ItemStack in : r.getInputs())
		{
			if (!in.isConsumed())
				continue;

			if (in.getItemId() == null)
				return Double.POSITIVE_INFINITY;

			Sourcing sub = acquire(in.getItemId(), depth + 1, onStack);
			if (Double.isInfinite(sub.getCost()))
				return Double.POSITIVE_INFINITY;

			inputs += sub.getCost() * in.getQty();
		}

		return (inputs - byproductCredit(outputs, p)) / expectedPrimary;
	}

	/**
	 * The expected post-tax value recovered from a recipe's secondary outputs (byproducts).
	 *
	 * @param outputs the recipe outputs (index 0 is the primary output)
	 * @param p       the success chance
	 * @return the byproduct credit
	 */
	private double byproductCredit(List<RecipeOutput> outputs, double p)
	{
		double credit = 0.0;
		for (int i = 1; i < outputs.size(); i++)
		{
			RecipeOutput o = outputs.get(i);
			if (o.getItemId() == null)
				continue;

			long sell = prices.sellPrice(o.getItemId());
			if (sell == PriceLookup.UNKNOWN)
				continue;

			long tax = config.geTax(o.getItemId(), sell, o.getQty());
			credit += (sell * (long) o.getQty() - tax) * p;
		}

		return credit;
	}

	/**
	 * Converts a price to a cost, mapping {@link PriceLookup#UNKNOWN} to infinity.
	 *
	 * @param price the price
	 * @return the cost, or {@link Double#POSITIVE_INFINITY} when unknown
	 */
	private static double toCost(long price)
	{
		return price == PriceLookup.UNKNOWN || price < 0 ? Double.POSITIVE_INFINITY : price;
	}
}
