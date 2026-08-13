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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Counts how many times a recipe can be performed from held stock alone, treating each input as
 * obtainable either directly (held) or by first crafting it from held sub-materials. So a recipe lists
 * as makeable on the On-hand tab when the player holds the ingredients to build its intermediates, not
 * only the finished intermediates &mdash; e.g. Ring of recoil counts when the player holds a sapphire, a
 * gold bar and the runes rather than an assembled sapphire ring.
 *
 * <p>Pure and network-free. The producible count per item is memoised across a whole On-hand pass (the
 * held stock is fixed), so common sub-materials are only walked once. Recursion is cycle-safe (an item
 * already on the current path is not re-expanded) and depth-capped. The count is an upper bound: it
 * treats held stock as reusable across branches, so a held sub-material feeding two branches of one
 * recipe can be over-counted &mdash; acceptable for a "makeable now" hint.
 */
public class HeldMakeable
{
	/** Deepest sub-craft level considered before an item counts as held-only. */
	static final int MAX_DEPTH = 8;

	private final Map<Integer, Integer> stock;
	private final IntFunction<List<Recipe>> byOutput;
	private final Map<Integer, Long> memo = new HashMap<>();

	/**
	 * @param stock    item id &rarr; held count (bank + inventory), or {@code null} for none
	 * @param byOutput resolves an item id to the recipes that produce it, or {@code null} to disable
	 *                 recursive crafting (held-only)
	 */
	public HeldMakeable(Map<Integer, Integer> stock, IntFunction<List<Recipe>> byOutput)
	{
		this.stock = stock == null ? Collections.emptyMap() : stock;
		this.byOutput = byOutput;
	}

	/**
	 * How many times the recipe can be performed from held stock, each consumed input sourced directly or
	 * recursively crafted.
	 *
	 * @param rec the recipe
	 * @return the number of achievable actions, or {@code 0} when a consumed input cannot be sourced
	 */
	public int makeable(Recipe rec)
	{
		long best = Long.MAX_VALUE;
		boolean any = false;
		for (ItemStack in : rec.getInputs())
		{
			if (!in.isConsumed() || in.getItemId() == null)
				continue;

			any = true;
			int per = in.getQty();
			if (per <= 0)
				return 0;

			long canDo = producible(in.getItemId(), new HashSet<>(), 0) / per;
			best = Math.min(best, canDo);
		}

		if (!any)
			return 0;

		return (int) Math.min(best, Integer.MAX_VALUE);
	}

	private long producible(int itemId, Set<Integer> onStack, int depth)
	{
		Long cached = memo.get(itemId);
		if (cached != null)
			return cached;

		long held = stock.getOrDefault(itemId, 0);
		if (depth >= MAX_DEPTH || onStack.contains(itemId))
			return held;

		onStack.add(itemId);
		long craftable = 0;
		if (byOutput != null)
			for (Recipe rec : byOutput.apply(itemId))
				craftable = Math.max(craftable, runsFor(rec, itemId, onStack, depth));

		onStack.remove(itemId);

		long total = held + craftable;
		memo.put(itemId, total);
		return total;
	}

	private long runsFor(Recipe rec, int outputId, Set<Integer> onStack, int depth)
	{
		long best = Long.MAX_VALUE;
		boolean any = false;
		for (ItemStack in : rec.getInputs())
		{
			if (!in.isConsumed() || in.getItemId() == null)
				continue;

			any = true;
			int per = in.getQty();
			if (per <= 0)
				return 0;

			long p = producible(in.getItemId(), onStack, depth + 1);
			best = Math.min(best, p / per);
		}

		if (!any)
			return 0;

		return best * outputQty(rec, outputId);
	}

	private static long outputQty(Recipe rec, int outputId)
	{
		for (RecipeOutput out : rec.getOutputs())
			if (out.getItemId() != null && out.getItemId() == outputId)
				return Math.max(1, out.getQty());

		return 1;
	}
}
