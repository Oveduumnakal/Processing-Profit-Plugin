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
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.oveduumnakal.processingprofit.WikiPriceClient.ItemAverages;
import com.oveduumnakal.processingprofit.WikiPriceClient.ItemMapping;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * GE-market {@link PriceLookup} backed by the wiki {@code /5m} averages (buy = {@code avgHighPrice},
 * sell = {@code avgLowPrice}) with a {@code /1h} fallback when a 5-minute window has no trades on one
 * side. Volume is the per-item {@code min(highPriceVolume, lowPriceVolume)} from the same payload.
 * {@code /mapping} metadata (buy limit, alch, id&harr;name) is fetched alongside for later use.
 *
 * <p>{@link #refresh()} does all network work and is meant to run off the client thread on an interval;
 * accessors read a {@code volatile} snapshot and never block. The price-resolution logic is factored
 * into {@link #resolve(Map, Map)} so it can be unit-tested without a network.
 */
@Slf4j
@Singleton
public class GePriceLookup implements PriceLookup
{
	/**
	 * A resolved per-item price row: the buy and sell prices, traded volume, and whether the row had to
	 * fall back to the 1-hour window (or is otherwise stale).
	 */
	@Value
	static class Resolved
	{
		long buy;
		long sell;
		long volume;
		boolean stale;
	}

	private final WikiPriceClient client;

	private volatile Map<Integer, Resolved> prices = Collections.emptyMap();
	private volatile Map<Integer, ItemMapping> mapping = Collections.emptyMap();

	@Inject
	public GePriceLookup(WikiPriceClient client)
	{
		this.client = client;
	}

	/**
	 * Fetches {@code /5m}, {@code /1h}, and {@code /mapping} and swaps in the freshly resolved snapshot.
	 * A failed fetch leaves the previous snapshot in place.
	 */
	public void refresh()
	{
		Map<Integer, ItemAverages> five = client.fetch5m();
		Map<Integer, ItemAverages> hour = client.fetch1h();
		Map<Integer, ItemMapping> map = client.fetchMapping();
		if (!map.isEmpty())
			this.mapping = map;

		if (!five.isEmpty() || !hour.isEmpty())
			this.prices = resolve(five, hour);
	}

	/**
	 * Merges 5-minute and 1-hour averages into resolved price rows. For each side, the 5-minute average
	 * is used when present; otherwise the 1-hour average is used and the row is flagged stale; a side
	 * missing from both is {@link #UNKNOWN} and stale.
	 *
	 * @param five the 5-minute averages by item id
	 * @param hour the 1-hour averages by item id (fallback)
	 * @return resolved price rows by item id
	 */
	static Map<Integer, Resolved> resolve(Map<Integer, ItemAverages> five, Map<Integer, ItemAverages> hour)
	{
		Set<Integer> ids = new HashSet<>(five.keySet());
		ids.addAll(hour.keySet());

		Map<Integer, Resolved> out = new HashMap<>(ids.size());
		for (Integer id : ids)
		{
			ItemAverages f = five.get(id);
			ItemAverages h = hour.get(id);
			Long fHigh = f == null ? null : f.getAvgHigh();
			Long fLow = f == null ? null : f.getAvgLow();
			Long hHigh = h == null ? null : h.getAvgHigh();
			Long hLow = h == null ? null : h.getAvgLow();

			boolean stale = false;
			long buy;
			if (fHigh != null)
			{
				buy = fHigh;
			}
			else if (hHigh != null)
			{
				buy = hHigh;
				stale = true;
			}
			else
			{
				buy = UNKNOWN;
				stale = true;
			}

			long sell;
			if (fLow != null)
			{
				sell = fLow;
			}
			else if (hLow != null)
			{
				sell = hLow;
				stale = true;
			}
			else
			{
				sell = UNKNOWN;
				stale = true;
			}

			long volume;
			if (f != null && (f.getHighVolume() > 0 || f.getLowVolume() > 0))
				volume = Math.min(f.getHighVolume(), f.getLowVolume());
			else if (h != null)
				volume = Math.min(h.getHighVolume(), h.getLowVolume());
			else
				volume = 0;

			out.put(id, new Resolved(buy, sell, volume, stale));
		}

		return out;
	}

	@Override
	public long buyPrice(int itemId)
	{
		Resolved r = prices.get(itemId);
		return r == null ? UNKNOWN : r.getBuy();
	}

	@Override
	public long sellPrice(int itemId)
	{
		Resolved r = prices.get(itemId);
		return r == null ? UNKNOWN : r.getSell();
	}

	@Override
	public long volume(int itemId)
	{
		Resolved r = prices.get(itemId);
		return r == null ? 0L : r.getVolume();
	}

	@Override
	public boolean isStale(int itemId)
	{
		Resolved r = prices.get(itemId);
		return r == null || r.isStale();
	}

	/**
	 * The GE metadata for an item (buy limit, alch, name), or {@code null} when unknown.
	 *
	 * @param itemId the item id
	 * @return the mapping entry, or {@code null}
	 */
	public ItemMapping mapping(int itemId)
	{
		return mapping.get(itemId);
	}

	/**
	 * Replaces the resolved snapshot directly. Package-visible for tests.
	 *
	 * @param snapshot the resolved rows to install
	 */
	void setPrices(Map<Integer, Resolved> snapshot)
	{
		this.prices = snapshot;
	}
}
