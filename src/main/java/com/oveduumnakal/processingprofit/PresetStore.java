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

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * Pure (de)serialization and list-editing for {@link Preset}s. Presets are persisted as a single JSON
 * array under one hidden config key; this class turns that string into a list and back, and applies the
 * name-keyed upsert/remove/find edits the preset menu needs. No RuneLite dependencies, so it is fully
 * unit-testable. {@link #PRESET_KEYS} is the allowlist of config keys a preset may carry — capture snapshots
 * exactly these, and apply refuses to write anything outside the set.
 */
final class PresetStore
{
	/**
	 * The config keys a preset snapshots: the sourcing mode, valuation lens, gating mode, members filter,
	 * the three numeric thresholds, and the seven persisted panel toolbar keys (sort, compact, disabled
	 * skills, and the three warning show-flags). Nothing else — a preset is a "view", not the whole config.
	 */
	static final List<String> PRESET_KEYS = Collections.unmodifiableList(Arrays.asList(
			"sourcingMode",
			"valuationLens",
			"gatingMode",
			"showMembers",
			"minProfit",
			"minGpPerHour",
			"minVolume",
			"panelFilterSortIndex",
			"panelFilterSortReversed",
			"panelFilterCompact",
			"panelFilterDisabledSkills",
			"panelFilterShowLowVolume",
			"panelFilterShowStale",
			"panelFilterShowThrottled"));

	private static final Gson GSON = new Gson();
	private static final Type LIST_TYPE = new TypeToken<List<Preset>>()
	{
	}.getType();

	private PresetStore()
	{
	}

	/**
	 * Parses the persisted JSON array into a mutable list of presets. Malformed JSON, {@code null}, blank
	 * input, and entries missing a name or values map all yield an empty (or filtered) list rather than
	 * throwing, so a corrupt config value never breaks the panel.
	 *
	 * @param json the stored JSON, may be {@code null} or blank
	 * @return the parsed presets, never {@code null}
	 */
	static List<Preset> parse(String json)
	{
		if (json == null || json.trim().isEmpty())
			return new ArrayList<>();

		List<Preset> parsed;
		try
		{
			parsed = GSON.fromJson(json, LIST_TYPE);
		}
		catch (JsonParseException e)
		{
			return new ArrayList<>();
		}

		List<Preset> clean = new ArrayList<>();
		if (parsed == null)
			return clean;

		for (Preset preset : parsed)
		{
			if (preset != null && preset.getName() != null && preset.getValues() != null)
				clean.add(preset);
		}

		return clean;
	}

	/**
	 * Serializes the preset list to a JSON array for persistence.
	 *
	 * @param presets the presets to store
	 * @return the JSON representation
	 */
	static String format(List<Preset> presets)
	{
		return GSON.toJson(presets, LIST_TYPE);
	}

	/**
	 * Returns a copy of {@code presets} with {@code preset} inserted, replacing any existing entry of the
	 * same name in place (preserving order); a new name is appended. The input list is not modified.
	 *
	 * @param presets the current presets
	 * @param preset  the preset to add or replace
	 * @return the updated list
	 */
	static List<Preset> upsert(List<Preset> presets, Preset preset)
	{
		List<Preset> out = new ArrayList<>(presets.size() + 1);
		boolean replaced = false;
		for (Preset existing : presets)
		{
			if (existing.getName().equals(preset.getName()))
			{
				out.add(preset);
				replaced = true;
			}
			else
			{
				out.add(existing);
			}
		}

		if (!replaced)
			out.add(preset);

		return out;
	}

	/**
	 * Returns a copy of {@code presets} with any entry named {@code name} removed. The input list is not
	 * modified.
	 *
	 * @param presets the current presets
	 * @param name    the name to remove
	 * @return the updated list
	 */
	static List<Preset> remove(List<Preset> presets, String name)
	{
		List<Preset> out = new ArrayList<>(presets.size());
		for (Preset existing : presets)
		{
			if (!existing.getName().equals(name))
				out.add(existing);
		}

		return out;
	}

	/**
	 * Finds the preset with the given name.
	 *
	 * @param presets the presets to search
	 * @param name    the name to match
	 * @return the matching preset, or {@code null} if none
	 */
	static Preset find(List<Preset> presets, String name)
	{
		for (Preset preset : presets)
		{
			if (preset.getName().equals(name))
				return preset;
		}

		return null;
	}
}
