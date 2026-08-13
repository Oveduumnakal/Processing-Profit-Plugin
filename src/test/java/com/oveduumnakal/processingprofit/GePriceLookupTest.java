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

import com.oveduumnakal.processingprofit.GePriceLookup.Resolved;
import com.oveduumnakal.processingprofit.WikiPriceClient.ItemAverages;
import com.oveduumnakal.processingprofit.WikiPriceClient.ItemPrices;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies {@link GePriceLookup} resolves prices with the correct instant-buy/instant-sell wiring
 * (guarding the inverted-spread bug class) and falls back to the 1-hour window instead of computing
 * garbage when a 5-minute average is null.
 */
public class GePriceLookupTest
{
	private static ItemAverages avg(Long high, Long low, long highVol, long lowVol)
	{
		return new ItemAverages(high, low, highVol, lowVol);
	}

	private static ItemPrices price(long high, long low)
	{
		return new ItemPrices(high, low, 0L, 0L);
	}

	private static Map<Integer, ItemPrices> noLatest()
	{
		return new HashMap<>();
	}

	@Test
	public void buyIsInstantBuyAndSellIsInstantSell()
	{
		Map<Integer, ItemAverages> five = new HashMap<>();
		five.put(10, avg(100L, 90L, 500L, 300L));

		Map<Integer, Resolved> resolved = GePriceLookup.resolve(five, new HashMap<>(), noLatest());
		Resolved r = resolved.get(10);

		assertEquals(100L, r.getBuy());
		assertEquals(90L, r.getSell());
		assertEquals(300L, r.getVolume());
		assertFalse(r.isStale());
	}

	@Test
	public void fallsBackToOneHourWhenFiveMinuteSideIsNullAndStaysFresh()
	{
		Map<Integer, ItemAverages> five = new HashMap<>();
		five.put(10, avg(null, 90L, 0L, 300L));
		Map<Integer, ItemAverages> hour = new HashMap<>();
		hour.put(10, avg(95L, 88L, 400L, 350L));

		Map<Integer, Resolved> resolved = GePriceLookup.resolve(five, hour, noLatest());
		Resolved r = resolved.get(10);

		assertEquals(95L, r.getBuy());
		assertEquals(90L, r.getSell());
		assertFalse(r.isStale());
	}

	@Test
	public void fallsBackToLatestWhenBothWindowsMissASide()
	{
		Map<Integer, ItemAverages> five = new HashMap<>();
		five.put(10, avg(2135L, null, 22L, 0L));
		Map<Integer, ItemPrices> latest = new HashMap<>();
		latest.put(10, price(2109L, 1828L));

		Map<Integer, Resolved> resolved = GePriceLookup.resolve(five, new HashMap<>(), latest);
		Resolved r = resolved.get(10);

		assertEquals(2135L, r.getBuy());
		assertEquals(1828L, r.getSell());
		assertTrue(r.isStale());
	}

	@Test
	public void unknownWhenAllThreeSourcesMissASide()
	{
		Map<Integer, ItemAverages> five = new HashMap<>();
		five.put(10, avg(null, null, 0L, 0L));

		Map<Integer, Resolved> resolved = GePriceLookup.resolve(five, new HashMap<>(), noLatest());
		Resolved r = resolved.get(10);

		assertEquals(PriceLookup.UNKNOWN, r.getBuy());
		assertEquals(PriceLookup.UNKNOWN, r.getSell());
		assertTrue(r.isStale());
	}

	@Test
	public void accessorsReadSnapshotAndDefaultForUnknownItems()
	{
		GePriceLookup lookup = new GePriceLookup(null);
		Map<Integer, Resolved> snapshot = new HashMap<>();
		snapshot.put(10, new Resolved(100L, 90L, 250L, false));
		lookup.setPrices(snapshot);

		assertEquals(100L, lookup.buyPrice(10));
		assertEquals(90L, lookup.sellPrice(10));
		assertEquals(250L, lookup.volume(10));
		assertFalse(lookup.isStale(10));

		assertEquals(PriceLookup.UNKNOWN, lookup.buyPrice(999));
		assertEquals(0L, lookup.volume(999));
		assertTrue(lookup.isStale(999));
	}
}
