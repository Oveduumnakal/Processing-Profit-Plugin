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
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Verifies {@link SuccessData} loads the bundled success resources and merges the scraped success model
 * and failure outcome into recipes by primary output id.
 */
public class SuccessDataTest
{
	private static Recipe cookingRecipe(int outputId, int level)
	{
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Cooking", level, 210.0, false));
		List<ItemStack> inputs = Collections.singletonList(new ItemStack(383, "Raw shark", 1, true));
		List<RecipeOutput> outputs =
				Collections.singletonList(new RecipeOutput(outputId, "out", 1, null, null));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("test", outputs, inputs, Collections.emptyList(), skills, 4, null, true, null,
				reqs, SuccessModel.always(), null);
	}

	@Test
	public void loadsCuratedModifierRegistry()
	{
		SuccessData data = SuccessData.bundled();
		assertEquals(6, data.modifiers().size());
	}

	@Test
	public void mergesLinearInterpModelForOpal()
	{
		SuccessData data = SuccessData.bundled();
		List<RecipeOutput> outputs =
				Collections.singletonList(new RecipeOutput(1609, "Opal", 1, null, null));
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Crafting", 1, 15.0, false));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		Recipe opal = new Recipe("test", outputs, Collections.emptyList(), Collections.emptyList(),
				skills, 2, null, false, null, reqs, SuccessModel.always(), null);

		Recipe merged = data.enrich(opal);
		assertEquals(SuccessType.LINEAR_INTERP, merged.getSuccess().getType());
		assertEquals(Integer.valueOf(128), merged.getSuccess().getLow());
	}

	@Test
	public void mergesBurnModelAndFailureForShark()
	{
		SuccessData data = SuccessData.bundled();
		Recipe merged = data.enrich(cookingRecipe(385, 80));

		SuccessModel success = merged.getSuccess();
		assertEquals(SuccessType.BURN, success.getType());
		assertEquals(Integer.valueOf(94), success.getGauntletStopLevel());
		assertEquals(Integer.valueOf(80), success.getReqLevel());

		assertNotNull(merged.getOnFailure());
		assertEquals(Integer.valueOf(387), merged.getOnFailure().getItemId());
	}

	@Test
	public void leavesUnknownOutputsUnchanged()
	{
		SuccessData data = SuccessData.bundled();
		Recipe unknown = cookingRecipe(999999, 80);
		Recipe merged = data.enrich(unknown);

		assertSame(unknown, merged);
		assertTrue(merged.getSuccess().getType() == SuccessType.ALWAYS);
	}
}
