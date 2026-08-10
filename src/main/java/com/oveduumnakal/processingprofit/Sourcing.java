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

import lombok.Value;

/**
 * The cheapest way to obtain one unit of an item: the chosen {@code cost}, whether it is cheaper to
 * {@code viaBuy} (buy on the GE) or to craft it, the {@code craftRecipe} that gives the cheapest craft
 * path (the make alternative, {@code null} when nothing crafts it), and both the {@code buyCost} and
 * {@code craftCost} so the detail view can show the make-vs-buy comparison. Costs are
 * {@link Double#POSITIVE_INFINITY} when that option is unavailable.
 */
@Value
public class Sourcing
{
	int itemId;
	double cost;
	boolean viaBuy;
	Recipe craftRecipe;
	double buyCost;
	double craftCost;

	/**
	 * Whether the item can be obtained at all (a finite cost exists on some path).
	 *
	 * @return {@code true} when the chosen cost is finite
	 */
	public boolean obtainable()
	{
		return !Double.isInfinite(cost);
	}
}
