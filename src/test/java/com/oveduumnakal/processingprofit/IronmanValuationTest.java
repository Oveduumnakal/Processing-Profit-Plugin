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
import java.util.Map;

import com.oveduumnakal.processingprofit.WikiPriceClient.ItemMapping;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Verifies the {@link IronmanValuation} lens values outputs by high alch net of the nature rune (with a
 * fallback nature cost and a store-value fallback), values inputs by store value, and never reports a
 * price as stale.
 */
public class IronmanValuationTest
{
	private static ItemMapping mapping(int id, long value, long highAlch)
	{
		return new ItemMapping(id, "item" + id, 0, value, highAlch, 0L, null);
	}

	private static PriceLookup base(long naturePrice)
	{
		return new PriceLookup()
		{
			@Override
			public long buyPrice(int itemId)
			{
				return itemId == IronmanValuation.NATURE_RUNE_ID ? naturePrice : UNKNOWN;
			}

			@Override
			public long sellPrice(int itemId)
			{
				return UNKNOWN;
			}

			@Override
			public long volume(int itemId)
			{
				return 1234L;
			}

			@Override
			public boolean isStale(int itemId)
			{
				return true;
			}
		};
	}

	@Test
	public void sellPriceIsHighAlchNetOfNatureRune()
	{
		Map<Integer, ItemMapping> maps = new HashMap<>();
		maps.put(10, mapping(10, 400L, 1200L));
		IronmanValuation lens = new IronmanValuation(base(180L), maps::get);

		assertEquals(1020L, lens.sellPrice(10));
	}

	@Test
	public void natureRuneCostFallsBackWhenGePriceMissing()
	{
		Map<Integer, ItemMapping> maps = new HashMap<>();
		maps.put(10, mapping(10, 400L, 1200L));
		IronmanValuation lens = new IronmanValuation(base(PriceLookup.UNKNOWN), maps::get);

		assertEquals(1200L - IronmanValuation.NATURE_RUNE_FALLBACK, lens.sellPrice(10));
	}

	@Test
	public void sellPriceFallsBackToStoreValueWithoutAlch()
	{
		Map<Integer, ItemMapping> maps = new HashMap<>();
		maps.put(10, mapping(10, 55L, 0L));
		IronmanValuation lens = new IronmanValuation(base(180L), maps::get);

		assertEquals(55L, lens.sellPrice(10));
	}

	@Test
	public void buyPriceIsStoreValueAndUnpricedIsUnknown()
	{
		Map<Integer, ItemMapping> maps = new HashMap<>();
		maps.put(10, mapping(10, 400L, 1200L));
		maps.put(11, mapping(11, 0L, 0L));
		IronmanValuation lens = new IronmanValuation(base(180L), maps::get);

		assertEquals(400L, lens.buyPrice(10));
		assertEquals(PriceLookup.UNKNOWN, lens.buyPrice(11));
		assertEquals(PriceLookup.UNKNOWN, lens.buyPrice(99));
		assertEquals(PriceLookup.UNKNOWN, lens.sellPrice(99));
	}

	@Test
	public void ironmanPricesAreNeverStaleAndVolumePassesThrough()
	{
		IronmanValuation lens = new IronmanValuation(base(180L), id -> null);

		assertFalse(lens.isStale(10));
		assertEquals(1234L, lens.volume(10));
	}
}
