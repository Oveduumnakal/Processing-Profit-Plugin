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

/**
 * An id-keyed source of item prices for the profit engine. Prices follow the OSRS Wiki convention: the
 * <em>buy</em> price is the instant-buy side (what you pay to buy now), the <em>sell</em> price is the
 * instant-sell side (what you get selling now) &mdash; getting this backwards inverts every spread.
 *
 * <p>Two implementations are envisaged: {@link GePriceLookup} (GE market, from {@code /5m}) and, later,
 * an Ironman valuation (outputs by alch/shop, inputs by shop/gather) behind the same interface.
 */
public interface PriceLookup
{
	/** Returned by the price accessors when an item has no usable price. */
	long UNKNOWN = -1L;

	/**
	 * The price to buy an item (instant-buy side).
	 *
	 * @param itemId the item id
	 * @return the buy price, or {@link #UNKNOWN} when unavailable
	 */
	long buyPrice(int itemId);

	/**
	 * The price received selling an item (instant-sell side).
	 *
	 * @param itemId the item id
	 * @return the sell price, or {@link #UNKNOWN} when unavailable
	 */
	long sellPrice(int itemId);

	/**
	 * The traded volume for an item (liquidity signal).
	 *
	 * @param itemId the item id
	 * @return the volume, or {@code 0} when unavailable
	 */
	long volume(int itemId);

	/**
	 * Whether an item's price is stale &mdash; missing from the freshest window and taken from a
	 * fallback, or absent entirely &mdash; so callers can flag it rather than trust it.
	 *
	 * @param itemId the item id
	 * @return {@code true} when the price is stale or unavailable
	 */
	boolean isStale(int itemId);
}
