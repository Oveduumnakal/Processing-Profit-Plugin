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

import java.util.function.IntFunction;

import com.oveduumnakal.processingprofit.WikiPriceClient.ItemMapping;

/**
 * A no-GE valuation {@link PriceLookup} for accounts that cannot use the Grand Exchange. Outputs are
 * valued by high alchemy net of the nature rune (falling back to the item's store value), and inputs by
 * their store value (gathered or unpriceable inputs come back {@link #UNKNOWN}, so the calculator treats
 * them as free/gathered). Alch and store values are static wiki metadata, so nothing here is ever stale.
 *
 * <p>It reads the nature rune price and per-item metadata from a backing {@link PriceLookup} plus a
 * mapping accessor (the plugin passes the live {@link GePriceLookup} and its {@code mapping} method),
 * which keeps this class pure and unit-testable: a test supplies a stub lookup and a map-backed mapping
 * function. GE tax, buy limits and volume gating are meaningless in this lens and the plugin disables
 * them when it is active.
 */
public class IronmanValuation implements PriceLookup
{
	/** Nature rune item id, consumed per high-alch cast. */
	static final int NATURE_RUNE_ID = 561;
	/** Nature rune cost assumed when its GE price is unavailable. */
	static final long NATURE_RUNE_FALLBACK = 180L;

	private final PriceLookup base;
	private final IntFunction<ItemMapping> mappingFn;

	/**
	 * @param base      the backing lookup, used only for the nature rune price and volume passthrough
	 * @param mappingFn resolves an item id to its wiki metadata (alch and store values), may return null
	 */
	public IronmanValuation(PriceLookup base, IntFunction<ItemMapping> mappingFn)
	{
		this.base = base;
		this.mappingFn = mappingFn;
	}

	@Override
	public long buyPrice(int itemId)
	{
		ItemMapping mapping = mappingFn.apply(itemId);
		if (mapping == null || mapping.getValue() <= 0)
			return UNKNOWN;

		return mapping.getValue();
	}

	@Override
	public long sellPrice(int itemId)
	{
		ItemMapping mapping = mappingFn.apply(itemId);
		if (mapping == null)
			return UNKNOWN;

		if (mapping.getHighAlch() > 0)
			return Math.max(0L, mapping.getHighAlch() - natureRuneCost());

		return mapping.getValue() > 0 ? mapping.getValue() : UNKNOWN;
	}

	@Override
	public long volume(int itemId)
	{
		return base.volume(itemId);
	}

	@Override
	public boolean isStale(int itemId)
	{
		return false;
	}

	private long natureRuneCost()
	{
		long ge = base.buyPrice(NATURE_RUNE_ID);
		return ge > 0 ? ge : NATURE_RUNE_FALLBACK;
	}
}
