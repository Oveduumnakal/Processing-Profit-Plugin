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

import java.util.function.ToIntFunction;

/**
 * Pure buy-limit and volume liquidity checks for a recipe. The binding constraint is the scarcest
 * consumed input: {@code min(buyLimit / qty)} across inputs that have a finite 4-hour GE buy limit gives
 * how many actions can be sourced per window. A recipe is flagged {@code throttled} when that cap is
 * small (a great margin on something you can only buy a few hundred of per 4h is a trap), and
 * {@code lowVolume} when the product's traded volume is too thin to offload.
 *
 * <p>The thresholds are heuristics for a warning badge, not hard filters (the min-volume config still
 * hides genuinely dead items); the raw per-window cap and volume are always shown so the user can judge.
 */
public final class Liquidity
{
	/** Below this many sourceable actions per 4h window, a recipe is flagged buy-limit throttled. */
	static final int THROTTLE_UNITS_PER_WINDOW = 2000;
	/** At or below this traded volume (from the {@code /5m} payload), a product is flagged low-volume. */
	static final long LOW_VOLUME = 10L;

	private Liquidity()
	{
	}

	/**
	 * Assesses a recipe's buy-limit throttling and product liquidity.
	 *
	 * @param rec           the recipe
	 * @param buyLimit      resolves an item id to its 4-hour GE buy limit ({@code 0} = unlimited/unknown)
	 * @param productVolume the primary product's traded volume
	 * @return the liquidity assessment
	 */
	public static LiquidityResult assess(Recipe rec, ToIntFunction<Integer> buyLimit, long productVolume)
	{
		int cap = unitsPerWindow(rec, buyLimit);
		boolean throttled = cap > 0 && cap < THROTTLE_UNITS_PER_WINDOW;
		boolean lowVolume = productVolume >= 0 && productVolume <= LOW_VOLUME;
		return new LiquidityResult(cap, throttled, lowVolume);
	}

	/**
	 * How many actions the binding input buy limit allows per 4-hour window: the minimum of
	 * {@code floor(limit / qty)} over consumed inputs that carry a finite limit.
	 *
	 * @param rec      the recipe
	 * @param buyLimit resolves an item id to its 4-hour GE buy limit ({@code 0} = unlimited/unknown)
	 * @return the per-window action cap, or {@code 0} when no input has a finite limit (unlimited)
	 */
	public static int unitsPerWindow(Recipe rec, ToIntFunction<Integer> buyLimit)
	{
		int best = 0;
		boolean any = false;
		if (rec.getInputs() != null)
		{
			for (ItemStack in : rec.getInputs())
			{
				if (!in.isConsumed() || in.getItemId() == null)
					continue;

				int limit = buyLimit.applyAsInt(in.getItemId());
				if (limit <= 0)
					continue;

				int per = in.getQty() <= 0 ? limit : limit / in.getQty();
				if (!any || per < best)
				{
					best = per;
					any = true;
				}
			}
		}

		return any ? best : 0;
	}
}
