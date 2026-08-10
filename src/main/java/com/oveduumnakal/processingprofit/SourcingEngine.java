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
import java.util.List;
import java.util.Map;

/**
 * Computes a recipe's profitability under each {@link SourcingMode} over a snapshot of held items. The
 * per-action market profit is the same in every mode (v1 values everything at current market price); the
 * modes differ in how many actions are achievable and, for {@code HYBRID}, which inputs still need
 * buying. The held snapshot is a plain item id &rarr; count map so the logic stays pure and testable;
 * the plugin supplies it from the live bank/inventory.
 */
public class SourcingEngine
{
	/** Default target run for hybrid mode: a full inventory of actions. */
	public static final int DEFAULT_HYBRID_TARGET = 28;

	private final ProfitCalculator calculator;

	/**
	 * @param calculator the per-action profit math
	 */
	public SourcingEngine(ProfitCalculator calculator)
	{
		this.calculator = calculator;
	}

	/**
	 * Evaluates a recipe under one sourcing mode.
	 *
	 * @param rec          the recipe
	 * @param prices       the price source
	 * @param cfg          the tax/efficiency config
	 * @param level        the player's live level in the recipe's primary skill
	 * @param active       the success modifiers in effect
	 * @param held         item id &rarr; held count (bank + inventory), or {@code null} for none
	 * @param mode         the sourcing mode
	 * @param hybridTarget the target number of actions for {@code HYBRID}
	 * @return the sourcing result
	 */
	public SourcingResult evaluate(Recipe rec, PriceLookup prices, PriceConfig cfg, int level,
			List<SuccessModifier> active, Map<Integer, Integer> held, SourcingMode mode, int hybridTarget)
	{
		Map<Integer, Integer> stock = held == null ? Collections.emptyMap() : held;
		long profitEach = calculator.evaluate(rec, prices, cfg, level, active).getProfitEach();

		switch (mode)
		{
			case ON_HAND:
			{
				int makeable = makeableFromStock(rec, stock);
				return new SourcingResult(rec, mode, profitEach, makeable,
						profitEach * makeable, Collections.emptyMap());
			}
			case HYBRID:
			{
				int target = Math.max(0, hybridTarget);
				Map<Integer, Integer> shortfall = shortfallFor(rec, stock, target);
				return new SourcingResult(rec, mode, profitEach, target,
						profitEach * target, shortfall);
			}
			case BUY_TO_ORDER:
			default:
				return new SourcingResult(rec, mode, profitEach, 0, profitEach, Collections.emptyMap());
		}
	}

	/**
	 * How many actions the held stock can supply, bounded by the scarcest consumed input.
	 *
	 * @param rec   the recipe
	 * @param stock item id &rarr; held count
	 * @return the number of achievable actions, or {@code 0} when a consumed input is missing
	 */
	private static int makeableFromStock(Recipe rec, Map<Integer, Integer> stock)
	{
		int best = Integer.MAX_VALUE;
		boolean any = false;
		for (ItemStack in : rec.getInputs())
		{
			if (!in.isConsumed() || in.getItemId() == null)
				continue;

			any = true;
			int have = stock.getOrDefault(in.getItemId(), 0);
			int per = in.getQty();
			int canDo = per <= 0 ? 0 : have / per;
			best = Math.min(best, canDo);
		}

		return any ? best : 0;
	}

	/**
	 * The inputs (and quantities) that must still be bought to run the recipe {@code target} times after
	 * consuming held stock.
	 *
	 * @param rec    the recipe
	 * @param stock  item id &rarr; held count
	 * @param target the target number of actions
	 * @return item id &rarr; quantity to buy (only inputs with a shortfall)
	 */
	private static Map<Integer, Integer> shortfallFor(Recipe rec, Map<Integer, Integer> stock, int target)
	{
		Map<Integer, Integer> shortfall = new HashMap<>();
		for (ItemStack in : rec.getInputs())
		{
			if (!in.isConsumed() || in.getItemId() == null)
				continue;

			int need = target * in.getQty();
			int have = stock.getOrDefault(in.getItemId(), 0);
			int shortQty = need - have;
			if (shortQty > 0)
				shortfall.put(in.getItemId(), shortQty);
		}

		return shortfall;
	}
}
