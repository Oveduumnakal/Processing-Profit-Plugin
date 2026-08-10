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

import java.util.List;

import com.google.gson.Gson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the normalized recipe model round-trips a bundled {@code recipes.json} row via Gson,
 * including a multi-output recipe and a BURN success model, and that the success math is correct.
 */
public class RecipeModelTest
{
	private static final Gson GSON = new Gson();

	private static final String SAMPLE =
			"[{"
			+ "\"recipeId\":\"smithing:sample-bar\","
			+ "\"outputs\":["
			+ "{\"itemId\":1,\"name\":\"Sample bar\",\"qty\":2,\"variant\":\"Furnace\",\"successNote\":null},"
			+ "{\"itemId\":2,\"name\":\"Byproduct\",\"qty\":1,\"variant\":null,\"successNote\":null}],"
			+ "\"inputs\":[{\"itemId\":3,\"name\":\"Sample ore\",\"qty\":1,\"consumed\":true}],"
			+ "\"tools\":[{\"itemId\":4,\"name\":\"Hammer\",\"qty\":1,\"consumed\":false}],"
			+ "\"skills\":[{\"skill\":\"Smithing\",\"level\":1,\"xp\":6.2,\"boostable\":false}],"
			+ "\"ticks\":5,\"ticksNote\":null,\"members\":false,\"facility\":\"Furnace\","
			+ "\"requirements\":{\"quests\":[],\"misc\":[]},"
			+ "\"success\":{\"type\":\"ALWAYS\"},\"onFailure\":null},"
			+ "{"
			+ "\"recipeId\":\"cooking:shark\","
			+ "\"outputs\":[{\"itemId\":385,\"name\":\"Shark\",\"qty\":1,\"variant\":null,\"successNote\":null}],"
			+ "\"inputs\":[{\"itemId\":383,\"name\":\"Raw shark\",\"qty\":1,\"consumed\":true}],"
			+ "\"tools\":[],"
			+ "\"skills\":[{\"skill\":\"Cooking\",\"level\":80,\"xp\":210.0,\"boostable\":false}],"
			+ "\"ticks\":4,\"ticksNote\":null,\"members\":true,\"facility\":\"Range\","
			+ "\"requirements\":{\"quests\":[],\"misc\":[]},"
			+ "\"success\":{\"type\":\"BURN\",\"stopLevel\":100,\"gauntletStopLevel\":94,\"reqLevel\":80},"
			+ "\"onFailure\":{\"itemId\":387,\"name\":\"Burnt shark\",\"qty\":1}}]";

	@Test
	public void roundTripsMultiOutputRecipe()
	{
		Recipe[] recipes = GSON.fromJson(SAMPLE, Recipe[].class);
		assertEquals(2, recipes.length);

		Recipe bar = recipes[0];
		List<RecipeOutput> outputs = bar.getOutputs();
		assertEquals(2, outputs.size());
		assertEquals(Integer.valueOf(1), outputs.get(0).getItemId());
		assertEquals("Byproduct", outputs.get(1).getName());
		assertEquals(Integer.valueOf(1), bar.primaryOutputId());

		ItemStack input = bar.getInputs().get(0);
		assertTrue(input.isConsumed());
		ItemStack tool = bar.getTools().get(0);
		assertFalse(tool.isConsumed());

		SkillReq skill = bar.primarySkill();
		assertEquals("Smithing", skill.getSkill());
		assertEquals(SuccessType.ALWAYS, bar.getSuccess().getType());
		assertEquals(1.0, bar.getSuccess().chance(1), 1e-9);
	}

	@Test
	public void roundTripsBurnSuccessModel()
	{
		Recipe[] recipes = GSON.fromJson(SAMPLE, Recipe[].class);
		Recipe shark = recipes[1];

		SuccessModel success = shark.getSuccess();
		assertEquals(SuccessType.BURN, success.getType());

		assertEquals(1.0 - 20.0 / 21.0, success.chance(80), 1e-9);
		assertEquals(1.0, success.chance(94, 94), 1e-9);

		FailureOutcome onFailure = shark.getOnFailure();
		assertNotNull(onFailure);
		assertEquals(Integer.valueOf(387), onFailure.getItemId());
	}

	@Test
	public void linearInterpMatchesWikiOpalCurve()
	{
		SuccessModel opal = new SuccessModel(SuccessType.LINEAR_INTERP, 128, 250, null, null, 1, null);
		assertEquals(129.0 / 256.0, opal.chance(1), 1e-9);
	}
}
