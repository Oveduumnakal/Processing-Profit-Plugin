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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link PresetStore}: the JSON round-trip (including value preservation), the name-keyed
 * upsert (replace in place vs append) and remove edits, lookup, and defensive parsing of malformed input.
 */
public class PresetStoreTest
{
	private static Preset preset(String name, String key, String value)
	{
		Map<String, String> values = new LinkedHashMap<>();
		values.put(key, value);
		return new Preset(name, values);
	}

	@Test
	public void roundTripsPresetsAndValues()
	{
		List<Preset> in = new ArrayList<>();
		in.add(preset("F2P Smithing", "sourcingMode", "BUY_TO_ORDER"));
		in.add(preset("Cheap Herblore XP", "valuationLens", "GE_MARKET"));

		List<Preset> out = PresetStore.parse(PresetStore.format(in));

		assertEquals(2, out.size());
		assertEquals("F2P Smithing", out.get(0).getName());
		Map<String, String> first = out.get(0).getValues();
		Map<String, String> second = out.get(1).getValues();
		assertEquals("BUY_TO_ORDER", first.get("sourcingMode"));
		assertEquals("GE_MARKET", second.get("valuationLens"));
	}

	@Test
	public void parseReturnsEmptyForBlankOrGarbage()
	{
		assertTrue(PresetStore.parse(null).isEmpty());
		assertTrue(PresetStore.parse("   ").isEmpty());
		assertTrue(PresetStore.parse("not json {[").isEmpty());
	}

	@Test
	public void parseDropsEntriesMissingNameOrValues()
	{
		String json = "[{\"values\":{\"a\":\"b\"}},{\"name\":\"ok\",\"values\":{\"a\":\"b\"}},{\"name\":\"noVals\"}]";

		List<Preset> out = PresetStore.parse(json);

		assertEquals(1, out.size());
		assertEquals("ok", out.get(0).getName());
	}

	@Test
	public void upsertReplacesInPlaceThenAppends()
	{
		List<Preset> list = new ArrayList<>();
		list.add(preset("A", "minVolume", "1"));
		list.add(preset("B", "minVolume", "2"));

		List<Preset> replaced = PresetStore.upsert(list, preset("A", "minVolume", "99"));
		assertEquals(2, replaced.size());
		assertEquals("A", replaced.get(0).getName());
		Map<String, String> aValues = replaced.get(0).getValues();
		assertEquals("99", aValues.get("minVolume"));

		List<Preset> appended = PresetStore.upsert(replaced, preset("C", "minVolume", "3"));
		assertEquals(3, appended.size());
		assertEquals("C", appended.get(2).getName());
	}

	@Test
	public void removeDropsByNameAndLeavesInputUntouched()
	{
		List<Preset> list = new ArrayList<>();
		list.add(preset("A", "minVolume", "1"));
		list.add(preset("B", "minVolume", "2"));

		List<Preset> out = PresetStore.remove(list, "A");

		assertEquals(1, out.size());
		assertEquals("B", out.get(0).getName());
		assertEquals(2, list.size());
	}

	@Test
	public void findMatchesByNameElseNull()
	{
		List<Preset> list = new ArrayList<>();
		list.add(preset("A", "minVolume", "1"));

		assertEquals("A", PresetStore.find(list, "A").getName());
		assertNull(PresetStore.find(list, "missing"));
	}
}
