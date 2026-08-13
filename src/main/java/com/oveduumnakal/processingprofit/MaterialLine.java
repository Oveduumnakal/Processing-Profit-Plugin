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
 * One aggregated item row in the make-or-buy tree's roll-up sections: the {@code itemId} and
 * {@code name}, the total {@code qty} involved across the whole tree, and how many the player already
 * {@code held}. In the <b>Total materials</b> section {@code qty} is the amount consumed at the leaves
 * and {@code held} is how many of those you own ({@code qty - held} is what you must buy or gather; when
 * {@code held >= qty} the material is fully covered). In the <b>Leftovers</b> section {@code qty} is the
 * surplus generated and {@code held} is {@code 0}. A pure view-model built by {@link ChainTreeBuilder}.
 */
@Value
public class MaterialLine
{
	int itemId;
	String name;
	long qty;
	long held;

	/**
	 * Whether the player already owns enough of this material to cover the whole quantity.
	 *
	 * @return {@code true} when {@code held} covers {@code qty}
	 */
	public boolean isCovered()
	{
		return held >= qty;
	}

	/**
	 * The quantity that must still be bought or gathered after applying held stock.
	 *
	 * @return {@code qty - held}, never negative
	 */
	public long toObtain()
	{
		return Math.max(0L, qty - held);
	}
}
