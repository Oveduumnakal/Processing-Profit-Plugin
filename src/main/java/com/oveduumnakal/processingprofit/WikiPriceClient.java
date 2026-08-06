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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Thin client for the OSRS Wiki real-time prices API
 * (<a href="https://prices.runescape.wiki">prices.runescape.wiki</a>).
 *
 * <p>Ported from the Stockpile plugin, trimmed to what the profit engine needs: live instant-buy/
 * instant-sell prices and the item metadata mapping (item id, name, buy limit, GE value, alch values).
 * The {@code name} field the profit engine keys recipes on is added here. Every call is defensive:
 * network, HTTP, and malformed-JSON failures are logged and return an empty result rather than throwing,
 * and individual bad entries are skipped.
 */
@Slf4j
public class WikiPriceClient
{
	private static final HttpUrl LATEST_URL = HttpUrl.parse(
			"https://prices.runescape.wiki/api/v1/osrs/latest");
	private static final HttpUrl MAPPING_URL = HttpUrl.parse(
			"https://prices.runescape.wiki/api/v1/osrs/mapping");

	private static final String USER_AGENT = "RuneLite Processing Profit Plugin";

	/**
	 * The latest instant-buy ({@code high}) and instant-sell ({@code low}) prices for one item, each
	 * with the epoch-second timestamp of the trade that set it ({@code highTime}, {@code lowTime}). The
	 * two sides are independent, so one can be much staler than the other.
	 */
	@Value
	public static class ItemPrices
	{
		long high;
		long low;
		long highTime;
		long lowTime;

		/**
		 * @return the midpoint of high and low, or whichever side is present if only one is
		 */
		public long avg()
		{
			if (high > 0 && low > 0)
				return (high + low) / 2;

			return Math.max(high, low);
		}
	}

	/**
	 * Static GE metadata for an item: the {@code id}, {@code name} the wiki recipes key on, buy
	 * {@code limit}, store {@code value}, high/low alch values, and the in-game {@code examine} text
	 * ({@code null} when absent).
	 */
	@Value
	public static class ItemMapping
	{
		int id;
		String name;
		int limit;
		long value;
		long highAlch;
		long lowAlch;
		String examine;
	}

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Inject
	public WikiPriceClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Fetches the latest high/low prices for every item from the {@code /latest} endpoint.
	 *
	 * @return a map of item id to prices, or an empty map on any failure
	 */
	public Map<Integer, ItemPrices> fetchLatest()
	{
		Request request = new Request.Builder()
				.url(LATEST_URL)
				.header("User-Agent", USER_AGENT)
				.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				log.warn("Wiki price fetch failed: {}", response.code());
				return Collections.emptyMap();
			}

			JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
			JsonObject data = root.getAsJsonObject("data");
			if (data == null)
				return Collections.emptyMap();

			Map<Integer, ItemPrices> result = new HashMap<>(data.size());
			for (Map.Entry<String, JsonElement> entry : data.entrySet())
			{
				try
				{
					int id = Integer.parseInt(entry.getKey());
					JsonObject obj = entry.getValue().getAsJsonObject();
					long high = readLong(obj, "high");
					long low = readLong(obj, "low");
					long highTime = readLong(obj, "highTime");
					long lowTime = readLong(obj, "lowTime");
					result.put(id, new ItemPrices(high, low, highTime, lowTime));
				}
				catch (NumberFormatException | IllegalStateException e)
				{
					// Skip a malformed entry rather than failing the whole fetch.
				}
			}

			return result;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Error fetching wiki prices", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * Fetches per-item GE metadata from the {@code /mapping} endpoint, keyed by item id. Each
	 * {@link ItemMapping} carries the item {@code name}, letting the profit engine resolve recipe names.
	 *
	 * @return a map of item id to {@link ItemMapping}, or an empty map on any failure
	 */
	public Map<Integer, ItemMapping> fetchMapping()
	{
		Request request = new Request.Builder()
				.url(MAPPING_URL)
				.header("User-Agent", USER_AGENT)
				.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				log.warn("Wiki mapping fetch failed: {}", response.code());
				return Collections.emptyMap();
			}

			JsonArray data = gson.fromJson(response.body().charStream(), JsonArray.class);
			if (data == null)
				return Collections.emptyMap();

			Map<Integer, ItemMapping> result = new HashMap<>(data.size());
			for (JsonElement el : data)
			{
				try
				{
					JsonObject obj = el.getAsJsonObject();
					if (!obj.has("id") || obj.get("id").isJsonNull())
						continue;

					int id = obj.get("id").getAsInt();
					String name = readString(obj, "name");
					int limit = (int) readLong(obj, "limit");
					long value = readLong(obj, "value");
					long highAlch = readLong(obj, "highalch");
					long lowAlch = readLong(obj, "lowalch");
					String examine = readString(obj, "examine");
					result.put(id, new ItemMapping(id, name, limit, value, highAlch, lowAlch, examine));
				}
				catch (IllegalStateException | NumberFormatException e)
				{
					// Skip a malformed entry rather than failing the whole fetch.
				}
			}

			return result;
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Error fetching wiki mapping", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * Reads a long field, treating an absent or JSON-null value as {@code 0}.
	 *
	 * @param obj the object to read from
	 * @param key the field name
	 * @return the field's value, or {@code 0} when absent or null
	 */
	private static long readLong(JsonObject obj, String key)
	{
		if (!obj.has(key) || obj.get(key).isJsonNull())
			return 0L;

		return obj.get(key).getAsLong();
	}

	/**
	 * Reads a string field, treating an absent or JSON-null value as {@code null}.
	 *
	 * @param obj the object to read from
	 * @param key the field name
	 * @return the field's value, or {@code null} when absent or null
	 */
	private static String readString(JsonObject obj, String key)
	{
		if (!obj.has(key) || obj.get(key).isJsonNull())
			return null;

		return obj.get(key).getAsString();
	}
}
