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
import java.util.HashSet;
import java.util.Set;

/**
 * The pricing knobs the {@link ProfitCalculator} needs, kept as a plain value object (no RuneLite config
 * proxy) so the calculator stays pure and unit-testable: whether the GE tax applies, and the efficiency
 * fraction applied to theoretical action throughput. Provides the GE-tax and actions-per-hour math.
 */
public final class PriceConfig
{
	/** One game tick is 0.6s, so 6000 ticks pass per hour. */
	private static final double TICKS_PER_HOUR = 6000.0;
	/** GE tax is 2% of the sale price (i.e. price / 50). */
	private static final long TAX_DIVISOR = 50L;
	/** GE tax is capped per item. */
	private static final long TAX_CAP = 5_000_000L;
	/** Sales at or below this per-item price are tax-exempt. */
	private static final long TAX_EXEMPT_BELOW = 50L;
	/** Items that are never taxed (e.g. Old school bond). */
	private static final Set<Integer> TAX_EXEMPT_ITEMS;

	static
	{
		Set<Integer> exempt = new HashSet<>();
		exempt.add(13190);
		exempt.add(13191);
		exempt.add(13192);
		TAX_EXEMPT_ITEMS = Collections.unmodifiableSet(exempt);
	}

	private final boolean applyGeTax;
	private final double efficiency;

	/**
	 * @param applyGeTax whether the 2% GE sell tax is applied
	 * @param efficiency the fraction (0..1) of theoretical action throughput actually achieved
	 */
	public PriceConfig(boolean applyGeTax, double efficiency)
	{
		this.applyGeTax = applyGeTax;
		this.efficiency = efficiency;
	}

	/**
	 * The GE tax on selling a quantity of an item: 2% of the per-item price, capped per item, waived for
	 * exempt items, low-value sales, and when the tax is toggled off.
	 *
	 * @param itemId        the item being sold
	 * @param unitSellPrice the per-item sell price
	 * @param qty           the quantity sold
	 * @return the total tax, never negative
	 */
	public long geTax(int itemId, long unitSellPrice, int qty)
	{
		if (!applyGeTax || qty <= 0 || unitSellPrice < TAX_EXEMPT_BELOW || TAX_EXEMPT_ITEMS.contains(itemId))
			return 0L;

		long perItem = Math.min(unitSellPrice / TAX_DIVISOR, TAX_CAP);
		return perItem * qty;
	}

	/**
	 * The theoretical actions per hour for a recipe's tick cost, scaled by the efficiency fraction.
	 *
	 * @param ticks the action's game-tick cost, or {@code null} when unknown
	 * @return actions per hour, or {@link Double#NaN} when the tick cost is unknown or non-positive
	 */
	public double actionsPerHour(Integer ticks)
	{
		if (ticks == null || ticks <= 0)
			return Double.NaN;

		return (TICKS_PER_HOUR / (double) ticks) * efficiency;
	}
}
