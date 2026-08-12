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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the make-or-buy tree for the detail view by walking a recipe's consumed inputs and asking the
 * {@link ChainValuator} for the cheapest source of each. Where crafting is cheaper the chosen craft
 * recipe's own inputs are recursed the same way, so the tree mirrors the recursive valuation. Pure and
 * network-free. Display recursion is cycle-safe (an item already on the current path is not re-expanded)
 * and depth-capped, independent of the valuator's own memoised cycle guard.
 */
public final class ChainTreeBuilder
{
	/** Deepest craft level the display expands before marking a node truncated. */
	public static final int MAX_DEPTH = 6;

	private ChainTreeBuilder()
	{
	}

	/**
	 * Builds the top-level input nodes for a recipe.
	 *
	 * @param rec      the recipe whose inputs form the tree roots
	 * @param valuator the recursive make-or-buy valuator
	 * @return one node per consumed, priceable input (empty when the recipe has none)
	 */
	public static List<ChainNode> build(Recipe rec, ChainValuator valuator)
	{
		Set<Integer> path = new HashSet<>();
		Integer outId = rec.primaryOutputId();
		if (outId != null)
			path.add(outId);

		return childrenOf(rec, valuator, 1, path);
	}

	private static List<ChainNode> childrenOf(Recipe rec, ChainValuator valuator, int depth,
			Set<Integer> path)
	{
		List<ChainNode> nodes = new ArrayList<>();
		for (ItemStack in : rec.getInputs())
		{
			if (!in.isConsumed() || in.getItemId() == null)
				continue;

			nodes.add(node(in.getName(), in.getQty(), in.getItemId(), valuator, depth, path));
		}

		return nodes;
	}

	private static ChainNode node(String label, int qty, int itemId, ChainValuator valuator, int depth,
			Set<Integer> path)
	{
		Sourcing s = valuator.cheapest(itemId);
		long buy = cost(s.getBuyCost());
		long craft = cost(s.getCraftCost());
		long chosen = cost(s.getCost());

		List<ChainNode> children = new ArrayList<>();
		boolean truncated = false;
		Recipe craftRecipe = s.getCraftRecipe();
		if (!s.isViaBuy() && craftRecipe != null)
		{
			if (depth >= MAX_DEPTH || path.contains(itemId))
			{
				truncated = true;
			}
			else
			{
				path.add(itemId);
				children = childrenOf(craftRecipe, valuator, depth + 1, path);
				path.remove(itemId);
			}
		}

		return new ChainNode(label, qty, s.isViaBuy(), chosen, buy, craft, truncated, children);
	}

	private static long cost(double value)
	{
		return Double.isInfinite(value) ? ChainNode.UNAVAILABLE : Math.round(value);
	}
}
