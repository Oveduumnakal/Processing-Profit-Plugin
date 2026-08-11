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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link Gating}: logged-out passthrough, level locking with delta, multi-skill and
 * missing-skill handling, quest locking, and unrecognised/finished quest passthrough.
 */
public class GatingTest
{
	private static final Predicate<String> ALL_QUESTS_OK = q -> true;

	private static Recipe recipe(List<SkillReq> skills, List<String> quests)
	{
		List<RecipeOutput> outs = Collections.singletonList(new RecipeOutput(2, "out", 1, null, null));
		Requirements reqs = new Requirements(quests, Collections.emptyList());
		return new Recipe("r", outs, Collections.emptyList(), Collections.emptyList(), skills, 5, null,
				false, null, reqs, SuccessModel.always(), null);
	}

	private static SkillReq skill(String name, int level)
	{
		return new SkillReq(name, level, 1.0, false);
	}

	private static Map<String, Integer> levels(String name, int level)
	{
		Map<String, Integer> m = new HashMap<>();
		m.put(name, level);
		return m;
	}

	@Test
	public void loggedOutNeverGates()
	{
		Recipe r = recipe(Collections.singletonList(skill("Cooking", 80)), Collections.emptyList());
		GateResult g = Gating.evaluate(r, Collections.emptyMap(), ALL_QUESTS_OK, false);
		assertFalse(g.isLocked());
	}

	@Test
	public void locksBelowRequiredLevelWithDelta()
	{
		Recipe r = recipe(Collections.singletonList(skill("Cooking", 80)), Collections.emptyList());
		GateResult g = Gating.evaluate(r, levels("Cooking", 75), ALL_QUESTS_OK, true);
		assertTrue(g.isLocked());
		assertEquals("Cooking 80 (+5)", g.getReason());
	}

	@Test
	public void unlockedAtOrAboveLevel()
	{
		Recipe r = recipe(Collections.singletonList(skill("Cooking", 80)), Collections.emptyList());
		GateResult g = Gating.evaluate(r, levels("Cooking", 80), ALL_QUESTS_OK, true);
		assertFalse(g.isLocked());
	}

	@Test
	public void locksOnAnySecondarySkill()
	{
		Recipe r = recipe(Arrays.asList(skill("Construction", 18), skill("Cooking", 24)),
				Collections.emptyList());
		Map<String, Integer> m = levels("Construction", 40);
		m.put("Cooking", 10);
		GateResult g = Gating.evaluate(r, m, ALL_QUESTS_OK, true);
		assertTrue(g.isLocked());
		assertEquals("Cooking 24 (+14)", g.getReason());
	}

	@Test
	public void missingSkillInMapIsNotGated()
	{
		Recipe r = recipe(Collections.singletonList(skill("Cooking", 80)), Collections.emptyList());
		GateResult g = Gating.evaluate(r, levels("Smithing", 99), ALL_QUESTS_OK, true);
		assertFalse(g.isLocked());
	}

	@Test
	public void locksOnUnfinishedQuest()
	{
		Recipe r = recipe(Collections.singletonList(skill("Cooking", 1)),
				Collections.singletonList("Cook's Assistant"));
		Predicate<String> done = q -> false;
		GateResult g = Gating.evaluate(r, levels("Cooking", 99), done, true);
		assertTrue(g.isLocked());
		assertEquals("Quest: Cook's Assistant", g.getReason());
	}

	@Test
	public void satisfiedQuestDoesNotLock()
	{
		Recipe r = recipe(Collections.singletonList(skill("Cooking", 1)),
				Collections.singletonList("Cook's Assistant"));
		GateResult g = Gating.evaluate(r, levels("Cooking", 99), ALL_QUESTS_OK, true);
		assertFalse(g.isLocked());
	}
}
