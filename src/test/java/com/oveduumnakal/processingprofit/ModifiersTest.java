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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link Modifiers}: toggle precedence (auto/on/off), Hosidius tier selection with elite
 * superseding easy, registry filtering by modifier id, and the status-line text.
 */
public class ModifiersTest
{
	private static SuccessModifier mod(String id)
	{
		return new SuccessModifier(id, ModifierEffect.ADD_CHANCE, 0.1, "Cooking", Collections.emptySet());
	}

	private static final List<SuccessModifier> REGISTRY = Arrays.asList(
			mod("jewellers_chisel"), mod("jewellers_double"), mod("cooking_gauntlets"),
			mod("cooking_cape"), mod("hosidius_easy"), mod("hosidius_elite"));

	private static List<String> activeIds(ModifierState state)
	{
		List<String> ids = new ArrayList<>();
		for (SuccessModifier m : Modifiers.active(REGISTRY, state))
			ids.add(m.getId());

		return ids;
	}

	@Test
	public void autoFollowsDetectionOnOffForce()
	{
		assertTrue(Modifiers.resolve(ModifierToggle.AUTO, true));
		assertFalse(Modifiers.resolve(ModifierToggle.AUTO, false));
		assertTrue(Modifiers.resolve(ModifierToggle.ON, false));
		assertFalse(Modifiers.resolve(ModifierToggle.OFF, true));
	}

	@Test
	public void jewellersChiselEnablesBothEntries()
	{
		ModifierState state = Modifiers.resolveState(ModifierToggle.ON, false, ModifierToggle.OFF, true,
				ModifierToggle.OFF, true, HosidiusRange.NONE, HosidiusRange.NONE);
		assertEquals(Arrays.asList("jewellers_chisel", "jewellers_double"), activeIds(state));
	}

	@Test
	public void eliteSupersedesEasy()
	{
		ModifierState auto = Modifiers.resolveState(ModifierToggle.OFF, false, ModifierToggle.OFF, false,
				ModifierToggle.OFF, false, HosidiusRange.AUTO, HosidiusRange.ELITE);
		assertTrue(auto.isHosidiusElite());
		assertFalse(auto.isHosidiusEasy());
		assertEquals(Collections.singletonList("hosidius_elite"), activeIds(auto));
	}

	@Test
	public void forcedHosidiusOverridesDetection()
	{
		ModifierState state = Modifiers.resolveState(ModifierToggle.OFF, false, ModifierToggle.OFF, false,
				ModifierToggle.OFF, false, HosidiusRange.EASY, HosidiusRange.ELITE);
		assertTrue(state.isHosidiusEasy());
		assertFalse(state.isHosidiusElite());
	}

	@Test
	public void statusTextListsActiveModifiers()
	{
		ModifierState state = Modifiers.resolveState(ModifierToggle.OFF, false, ModifierToggle.ON, false,
				ModifierToggle.OFF, false, HosidiusRange.ELITE, HosidiusRange.NONE);
		assertEquals("Cooking gauntlets, Hosidius elite", Modifiers.statusText(state));
	}

	@Test
	public void statusTextNoneWhenNothingActive()
	{
		ModifierState state = Modifiers.resolveState(ModifierToggle.OFF, true, ModifierToggle.OFF, true,
				ModifierToggle.OFF, true, HosidiusRange.NONE, HosidiusRange.ELITE);
		assertEquals("none", Modifiers.statusText(state));
		assertTrue(activeIds(state).isEmpty());
	}
}
