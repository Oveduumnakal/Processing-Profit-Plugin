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
 * Builds the make-or-buy tree for the detail view: the finished product is the single root node, and its
 * consumed inputs are walked as children while the shared
 * {@link ChainValuator} decides, per input, whether it is cheaper to buy or to make. Where crafting is
 * cheaper the chosen craft recipe's own inputs are recursed the same way, so the tree mirrors the
 * recursive valuation. Every quantity is scaled to the number of finished products by the same
 * success-and-yield-adjusted action count the {@link ShoppingListBuilder} uses, so a step that needs
 * three of an input, or a sub-recipe that yields a batch, shows real counts.
 *
 * <p>Alongside the visual tree it accumulates two roll-ups. <b>Total materials</b> is the aggregated
 * bill of raw materials consumed at the leaves (with held stock recorded so the view can show what is
 * already covered). <b>Leftovers</b> is the surplus produced beyond what the tree consumes: batch
 * overshoot (a step makes more than needed) and byproducts (secondary outputs). Leftover accounting is
 * done only for always-success steps, where the produced quantity is deterministic; fail-capable steps
 * are omitted from leftovers to avoid reporting a fractional expectation as a whole-item surplus.
 *
 * <p>An input the player already holds enough of is marked covered and not expanded &mdash; you own it,
 * so its sub-recipe is irrelevant. Held counts are per node and not deduplicated across branches that
 * consume the same item, a display simplification. Display recursion is cycle-safe (an item already on
 * the current path is not re-expanded) and depth-capped. Pure and network-free.
 */
public final class ChainTreeBuilder
{
	/** Deepest craft level the display expands before marking a node truncated. */
	public static final int MAX_DEPTH = 6;

	private static final int DEFAULT_LEVEL = 99;
	private static final List<String> NO_TOOLS = Collections.emptyList();

	private final ChainValuator valuator;
	private final Map<Integer, Integer> held;
	private final TreeOptions options;
	private final ToIntFunction<String> levelFn;
	private final List<SuccessModifier> active;
	private final Map<Integer, long[]> totals = new LinkedHashMap<>();
	private final Map<Integer, long[]> leftovers = new LinkedHashMap<>();
	private final Map<Integer, String> names = new LinkedHashMap<>();

	private ChainTreeBuilder(ChainValuator valuator, Map<Integer, Integer> held, TreeOptions options)
	{
		this.valuator = valuator;
		this.held = held;
		this.options = options;
		this.levelFn = valuator.levelFn();
		this.active = valuator.active();
	}

	/**
	 * Builds the scaled make-or-buy breakdown for producing {@code targetQty} of a recipe's product, with
	 * the default (unfiltered) tree options.
	 *
	 * @param rec       the recipe whose inputs form the tree roots
	 * @param valuator  the recursive make-or-buy valuator (fixes the price/level context)
	 * @param held      item id to units the player holds (inventory + bank); may be empty, never null
	 * @param targetQty the number of finished products to source for (at least 1)
	 * @return the tree roots plus the Total materials and Leftovers roll-ups
	 */
	public static ChainTree build(Recipe rec, ChainValuator valuator, Map<Integer, Integer> held,
			long targetQty)
	{
		return build(rec, valuator, held, targetQty, TreeOptions.defaults());
	}

	/**
	 * Builds the scaled make-or-buy breakdown, applying the pop-out tree's filter {@code options}:
	 * force-bought items (by id or class) are shown as bought leaves rather than expanded, and expansion
	 * stops at the options' {@code maxDepth}. Pass an empty {@code held} map to ignore holdings
	 * ("exclude already-owned" off).
	 *
	 * @param rec       the recipe whose inputs form the tree roots
	 * @param valuator  the recursive make-or-buy valuator (fixes the price/level context)
	 * @param held      item id to units the player holds (inventory + bank); may be empty, never null
	 * @param targetQty the number of finished products to source for (at least 1)
	 * @param options   the force-buy and depth overrides
	 * @return the tree roots plus the Total materials and Leftovers roll-ups
	 */
	public static ChainTree build(Recipe rec, ChainValuator valuator, Map<Integer, Integer> held,
			long targetQty, TreeOptions options)
	{
		ChainTreeBuilder b = new ChainTreeBuilder(valuator, held, options);
		return b.run(rec, Math.max(1L, targetQty));
	}

	private ChainTree run(Recipe rec, long targetQty)
	{
		List<ChainNode> roots = new ArrayList<>();
		double exp = expectedPrimary(rec);
		if (exp > 0.0)
		{
			long actions = actionsFor(targetQty, exp);
			recordProductLeftovers(rec, actions, targetQty);

			Set<Integer> path = new HashSet<>();
			Integer outId = rec.primaryOutputId();
			if (outId != null)
				path.add(outId);

			List<ChainNode> inputs = childrenOf(rec, actions, 1, path);
			roots.add(productNode(rec, targetQty, actions, inputs));
		}

		return new ChainTree(roots, materialLines(totals), materialLines(leftovers));
	}

	/**
	 * The tree's root node: the finished product itself, made via the recipe's primary skill, with the
	 * consumed inputs as its children.
	 *
	 * @param rec       the product recipe
	 * @param targetQty the number of finished products wanted
	 * @param actions   the actions run to make them
	 * @param children  the recipe's input nodes, already recursed
	 * @return the product root node
	 */
	private ChainNode productNode(Recipe rec, long targetQty, long actions, List<ChainNode> children)
	{
		Integer outId = rec.primaryOutputId();
		int id = outId == null ? -1 : outId;
		List<RecipeOutput> outputs = rec.getOutputs();
		String label = outputs.isEmpty() ? "?" : outputs.get(0).getName();
		String skill = rec.primarySkill() == null ? null : rec.primarySkill().getSkill();
		long surplus = primarySurplus(rec, actions, targetQty);
		long owned = id > 0 ? ownedOf(id) : 0L;
		return new ChainNode(id, label, targetQty, false, skill, PriceLookup.UNKNOWN, owned, false, surplus,
				false, children, toolNames(rec));
	}

	private List<ChainNode> childrenOf(Recipe rec, long actions, int depth, Set<Integer> path)
	{
		List<ChainNode> nodes = new ArrayList<>();
		for (ItemStack in : rec.getInputs())
		{
			if (!in.isConsumed() || in.getItemId() == null)
				continue;

			nodes.add(node(in.getItemId(), in.getName(), actions * in.getQty(), depth, path));
		}

		return nodes;
	}

	private ChainNode node(int itemId, String label, long qtyNeeded, int depth, Set<Integer> path)
	{
		long owned = ownedOf(itemId);
		if (owned >= qtyNeeded)
		{
			addTotal(itemId, label, qtyNeeded);
			return new ChainNode(itemId, label, qtyNeeded, false, null, PriceLookup.UNKNOWN, owned, true,
					0L, false, new ArrayList<>(), NO_TOOLS);
		}

		if (options.forceBuy(itemId, label))
		{
			addTotal(itemId, label, qtyNeeded);
			long unit = valuator.prices().buyPrice(itemId);
			return new ChainNode(itemId, label, qtyNeeded, true, null, unit, owned, false, 0L, false,
					new ArrayList<>(), NO_TOOLS);
		}

		Sourcing s = valuator.cheapest(itemId);
		boolean canMake = !s.isViaBuy() && s.getCraftRecipe() != null && s.obtainable();
		if (!canMake)
		{
			addTotal(itemId, label, qtyNeeded);
			long unit = valuator.prices().buyPrice(itemId);
			return new ChainNode(itemId, label, qtyNeeded, true, null, unit, owned, false, 0L, false,
					new ArrayList<>(), NO_TOOLS);
		}

		Recipe craft = s.getCraftRecipe();
		String skill = craft.primarySkill() == null ? null : craft.primarySkill().getSkill();
		if (depth >= options.getMaxDepth() || path.contains(itemId))
			return new ChainNode(itemId, label, qtyNeeded, false, skill, PriceLookup.UNKNOWN, owned, false,
					0L, true, new ArrayList<>(), toolNames(craft));

		long shortfall = qtyNeeded - owned;
		double exp = expectedPrimary(craft);
		if (exp <= 0.0)
		{
			addTotal(itemId, label, qtyNeeded);
			long unit = valuator.prices().buyPrice(itemId);
			return new ChainNode(itemId, label, qtyNeeded, true, null, unit, owned, false, 0L, false,
					new ArrayList<>(), NO_TOOLS);
		}

		long actions = actionsFor(shortfall, exp);
		long surplus = primarySurplus(craft, actions, shortfall);
		if (surplus > 0L)
			addLeftover(itemId, label, surplus);

		recordByproducts(craft, actions);

		path.add(itemId);
		List<ChainNode> children = childrenOf(craft, actions, depth + 1, path);
		path.remove(itemId);
		return new ChainNode(itemId, label, qtyNeeded, false, skill, PriceLookup.UNKNOWN, owned, false,
				surplus, false, children, toolNames(craft));
	}

	/**
	 * Records the leftover output the finished product itself generates: batch overshoot on the target
	 * quantity plus any byproducts of the top recipe.
	 *
	 * @param rec       the product recipe
	 * @param actions   the actions run to make the target quantity
	 * @param targetQty the finished products wanted
	 */
	private void recordProductLeftovers(Recipe rec, long actions, long targetQty)
	{
		List<RecipeOutput> outputs = rec.getOutputs();
		if (outputs.isEmpty())
			return;

		RecipeOutput primary = outputs.get(0);
		long surplus = primarySurplus(rec, actions, targetQty);
		if (surplus > 0L && primary.getItemId() != null)
			addLeftover(primary.getItemId(), primary.getName(), surplus);

		recordByproducts(rec, actions);
	}

	/**
	 * The surplus units of a recipe's primary output beyond what is wanted, for always-success recipes
	 * (deterministic yield); {@code 0} for fail-capable recipes or when there is no overshoot.
	 *
	 * @param r      the recipe
	 * @param actions the actions run
	 * @param wanted the units wanted from those actions
	 * @return the whole-item surplus, never negative
	 */
	private long primarySurplus(Recipe r, long actions, long wanted)
	{
		if (!alwaysSuccess(r) || r.getOutputs().isEmpty())
			return 0L;

		RecipeOutput primary = r.getOutputs().get(0);
		long produced = actions * primary.getQty();
		return Math.max(0L, produced - wanted);
	}

	/**
	 * Records a recipe's secondary outputs (byproducts) as leftovers, for always-success recipes only.
	 *
	 * @param r       the recipe
	 * @param actions the actions run
	 */
	private void recordByproducts(Recipe r, long actions)
	{
		if (!alwaysSuccess(r))
			return;

		List<RecipeOutput> outputs = r.getOutputs();
		for (int i = 1; i < outputs.size(); i++)
		{
			RecipeOutput o = outputs.get(i);
			if (o.getItemId() != null)
				addLeftover(o.getItemId(), o.getName(), actions * (long) o.getQty());
		}
	}

	private void addTotal(int itemId, String name, long qty)
	{
		accumulate(totals, itemId, name, qty);
	}

	private void addLeftover(int itemId, String name, long qty)
	{
		accumulate(leftovers, itemId, name, qty);
	}

	private void accumulate(Map<Integer, long[]> into, int itemId, String name, long qty)
	{
		names.putIfAbsent(itemId, name);
		into.computeIfAbsent(itemId, k -> new long[]{0L})[0] += qty;
	}

	private List<MaterialLine> materialLines(Map<Integer, long[]> source)
	{
		boolean isTotals = source == totals;
		List<MaterialLine> lines = new ArrayList<>();
		for (Map.Entry<Integer, long[]> entry : source.entrySet())
		{
			int id = entry.getKey();
			long qty = entry.getValue()[0];
			long owned = isTotals ? Math.min(ownedOf(id), qty) : 0L;
			lines.add(new MaterialLine(id, names.get(id), qty, owned));
		}

		return lines;
	}

	private long ownedOf(int itemId)
	{
		return Math.max(0, held.getOrDefault(itemId, 0));
	}

	private static long actionsFor(long wanted, double expectedPrimary)
	{
		return (long) Math.ceil(wanted / expectedPrimary);
	}

	/**
	 * The expected number of good primary-output units per action, folding in the success chance and
	 * yield multiplier at the player's level &mdash; the same measure {@link ShoppingListBuilder} uses,
	 * so material quantities match.
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

	private static boolean alwaysSuccess(Recipe r)
	{
		SuccessModel model = r.getSuccess();
		return model == null || model.getType() == SuccessType.ALWAYS;
	}

	/**
	 * The names of a recipe's non-consumed tools/equipment (hammer, needle, chisel, …), shown on a crafted
	 * node so the tree says what each step is made with.
	 *
	 * @param r the recipe
	 * @return the tool names, or an empty list when the recipe needs no tools
	 */
	private static List<String> toolNames(Recipe r)
	{
		List<ItemStack> tools = r.getTools();
		if (tools.isEmpty())
			return NO_TOOLS;

		List<String> names = new ArrayList<>(tools.size());
		for (ItemStack tool : tools)
			names.add(tool.getName());

		return names;
	}
}
