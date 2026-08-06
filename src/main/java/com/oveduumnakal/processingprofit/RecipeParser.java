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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Parses a recipe bucket's {@code production_json} value into a {@link Recipe}.
 *
 * <p>The wiki returns {@code production_json} as an escaped JSON string, so this unwraps a string
 * element before reading it. Numbers arrive as strings ({@code "58"}), and experience may be
 * non-numeric ({@code "Varies"}); both are parsed defensively. A row with no real {@code output}
 * object (quest or bonus rows carry {@code output: ""}) yields an empty result.
 */
public final class RecipeParser
{
	private static final Gson GSON = new Gson();

	private RecipeParser()
	{
	}

	/**
	 * Parses one recipe's {@code production_json} element.
	 *
	 * @param raw the {@code production_json} value, either a JSON object or an escaped JSON string
	 * @return the parsed recipe, or empty when the row has no usable output
	 */
	public static Optional<Recipe> parse(JsonElement raw)
	{
		JsonObject obj = unwrap(raw);
		if (obj == null)
			return Optional.empty();

		JsonElement outputEl = obj.get("output");
		if (outputEl == null || !outputEl.isJsonObject())
			return Optional.empty();

		RecipeItem output = parseItem(outputEl.getAsJsonObject());
		if (output == null)
			return Optional.empty();

		List<RecipeItem> materials = parseItems(obj.getAsJsonArray("materials"));
		List<RecipeSkill> skills = parseSkills(obj.getAsJsonArray("skills"));
		String tools = readString(obj, "tools");
		String facilities = readString(obj, "facilities");
		int ticks = parseInt(readString(obj, "ticks"), 0);
		boolean members = readBoolean(obj, "members");
		return Optional.of(new Recipe(output, materials, skills, tools, facilities, ticks, members));
	}

	/**
	 * Resolves the raw element to a JSON object, parsing an escaped JSON string if needed.
	 *
	 * @param raw the raw {@code production_json} element
	 * @return the object form, or {@code null} when it is absent or not an object
	 */
	private static JsonObject unwrap(JsonElement raw)
	{
		if (raw == null || raw.isJsonNull())
			return null;

		if (raw.isJsonObject())
			return raw.getAsJsonObject();

		if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isString())
			return parseObject(raw.getAsString());

		return null;
	}

	/**
	 * Parses a JSON string into an object, returning {@code null} when it is malformed.
	 *
	 * @param json the JSON text
	 * @return the parsed object, or {@code null}
	 */
	private static JsonObject parseObject(String json)
	{
		try
		{
			return GSON.fromJson(json, JsonObject.class);
		}
		catch (JsonSyntaxException e)
		{
			return null;
		}
	}

	/**
	 * Parses a single item object with a {@code name} and string {@code quantity}.
	 *
	 * @param obj the item object
	 * @return the item, or {@code null} when it has no name
	 */
	private static RecipeItem parseItem(JsonObject obj)
	{
		String name = readString(obj, "name");
		if (name == null || name.isEmpty())
			return null;

		int quantity = parseInt(readString(obj, "quantity"), 1);
		return new RecipeItem(name, quantity);
	}

	/**
	 * Parses an array of item objects, skipping any without a name.
	 *
	 * @param arr the array, or {@code null}
	 * @return the parsed items, never {@code null}
	 */
	private static List<RecipeItem> parseItems(JsonArray arr)
	{
		List<RecipeItem> items = new ArrayList<>();
		if (arr == null)
			return items;

		for (JsonElement el : arr)
		{
			if (!el.isJsonObject())
				continue;

			RecipeItem item = parseItem(el.getAsJsonObject());
			if (item != null)
				items.add(item);
		}

		return items;
	}

	/**
	 * Parses an array of skill objects, skipping any without a name.
	 *
	 * @param arr the array, or {@code null}
	 * @return the parsed skills, never {@code null}
	 */
	private static List<RecipeSkill> parseSkills(JsonArray arr)
	{
		List<RecipeSkill> skills = new ArrayList<>();
		if (arr == null)
			return skills;

		for (JsonElement el : arr)
		{
			if (!el.isJsonObject())
				continue;

			JsonObject obj = el.getAsJsonObject();
			String name = readString(obj, "name");
			if (name == null || name.isEmpty())
				continue;

			int level = parseInt(readString(obj, "level"), 0);
			Integer experience = parseNullableInt(readString(obj, "experience"));
			boolean boostable = "Yes".equalsIgnoreCase(readString(obj, "boostable"));
			skills.add(new RecipeSkill(name, level, experience, boostable));
		}

		return skills;
	}

	/**
	 * Reads a string field, treating an absent, null, or non-primitive value as {@code null}.
	 *
	 * @param obj the object to read from
	 * @param key the field name
	 * @return the field's string value, or {@code null}
	 */
	private static String readString(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
			return null;

		JsonElement el = obj.get(key);
		return el.isJsonPrimitive() ? el.getAsString() : null;
	}

	/**
	 * Reads a boolean field, treating anything but a boolean primitive as {@code false}.
	 *
	 * @param obj the object to read from
	 * @param key the field name
	 * @return the field's boolean value, or {@code false}
	 */
	private static boolean readBoolean(JsonObject obj, String key)
	{
		if (!obj.has(key) || obj.get(key).isJsonNull())
			return false;

		JsonElement el = obj.get(key);
		if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean())
			return false;

		return el.getAsBoolean();
	}

	/**
	 * Parses an integer from a string, returning a fallback when it is null or non-numeric.
	 *
	 * @param value    the string to parse
	 * @param fallback the value returned when parsing fails
	 * @return the parsed integer, or {@code fallback}
	 */
	private static int parseInt(String value, int fallback)
	{
		if (value == null)
			return fallback;

		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e)
		{
			return fallback;
		}
	}

	/**
	 * Parses an integer from a string, returning {@code null} when it is null or non-numeric.
	 *
	 * @param value the string to parse
	 * @return the parsed integer, or {@code null}
	 */
	private static Integer parseNullableInt(String value)
	{
		if (value == null)
			return null;

		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}
}
