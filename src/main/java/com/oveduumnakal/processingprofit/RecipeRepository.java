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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Fetches and holds the processing recipe set from the OSRS Wiki recipe bucket
 * (<a href="https://oldschool.runescape.wiki">oldschool.runescape.wiki</a>).
 *
 * <p>Queries the bucket for each recipe's {@code production_json}, parses the rows through
 * {@link RecipeParser}, and keeps the result in memory. Fetches are defensive: a failed request
 * leaves the previously held recipes untouched and returns an empty list. Disk caching and paged
 * fetches are layered on later; a single call currently returns up to {@link #DEFAULT_LIMIT} rows.
 */
@Slf4j
public class RecipeRepository
{
	private static final HttpUrl BUCKET_URL = HttpUrl.parse("https://oldschool.runescape.wiki/api.php");
	private static final String USER_AGENT = "RuneLite Processing Profit Plugin";
	private static final int DEFAULT_LIMIT = 5000;

	private final OkHttpClient httpClient;
	private final Gson gson;

	@Getter
	private volatile List<Recipe> recipes = Collections.emptyList();

	@Inject
	public RecipeRepository(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		this.gson = gson;
	}

	/**
	 * Fetches the recipe set and, if the fetch returned anything, replaces the held recipes with it.
	 *
	 * @return the number of recipes fetched, or {@code 0} on failure
	 */
	public int refresh()
	{
		List<Recipe> fetched = fetch(DEFAULT_LIMIT);
		if (!fetched.isEmpty())
			this.recipes = fetched;

		return fetched.size();
	}

	/**
	 * Fetches and parses up to {@code limit} recipes from the bucket API.
	 *
	 * @param limit the maximum number of rows to request
	 * @return the parsed recipes, or an empty list on any failure
	 */
	public List<Recipe> fetch(int limit)
	{
		HttpUrl url = BUCKET_URL.newBuilder()
				.addQueryParameter("action", "bucket")
				.addQueryParameter("format", "json")
				.addQueryParameter("query",
						"bucket('recipe').select('production_json').limit(" + limit + ").run()")
				.build();

		Request request = new Request.Builder()
				.url(url)
				.header("User-Agent", USER_AGENT)
				.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				log.warn("Recipe bucket fetch failed: {}", response.code());
				return Collections.emptyList();
			}

			JsonObject root = gson.fromJson(response.body().charStream(), JsonObject.class);
			return parseResponse(root);
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Error fetching recipe bucket", e);
			return Collections.emptyList();
		}
	}

	/**
	 * Parses a bucket API response body into recipes, skipping rows that carry no usable output.
	 *
	 * @param root the parsed response object, expected to hold a {@code bucket} array
	 * @return the parsed recipes, or an empty list when the response has no rows
	 */
	static List<Recipe> parseResponse(JsonObject root)
	{
		if (root == null || !root.has("bucket") || !root.get("bucket").isJsonArray())
			return Collections.emptyList();

		JsonArray rows = root.getAsJsonArray("bucket");
		List<Recipe> parsed = new ArrayList<>(rows.size());
		for (JsonElement row : rows)
		{
			if (!row.isJsonObject())
				continue;

			JsonElement production = row.getAsJsonObject().get("production_json");
			RecipeParser.parse(production).ifPresent(parsed::add);
		}

		return parsed;
	}
}
