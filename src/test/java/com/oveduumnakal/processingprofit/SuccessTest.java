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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verifies the {@link Success} composer layers modifiers over the base {@link SuccessModel} curve in the
 * right order: gauntlets swap the stop level, Hosidius/jeweller's add chance, the cooking cape forces
 * success, and jeweller's double-gem multiplies yield.
 */
public class SuccessTest
{
	private static Recipe recipe(String skill, int level, int inputId, int outputId, SuccessModel success)
	{
		List<SkillReq> skills = Collections.singletonList(new SkillReq(skill, level, 1.0, false));
		List<ItemStack> inputs = Collections.singletonList(new ItemStack(inputId, "in", 1, true));
		List<RecipeOutput> outputs = Collections.singletonList(new RecipeOutput(outputId, "out", 1, null, null));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("test", outputs, inputs, Collections.emptyList(), skills, 5, null, false, null,
				reqs, success, null);
	}

	private static Set<Integer> ids(int... items)
	{
		Set<Integer> set = new HashSet<>();
		for (int i : items)
			set.add(i);

		return set;
	}

	@Test
	public void baseLinearInterpWithNoModifiers()
	{
		Recipe opal = recipe("Crafting", 1, 1625, 1609,
				new SuccessModel(SuccessType.LINEAR_INTERP, 128, 250, null, null, 1, null));
		assertEquals(129.0 / 256.0, Success.chance(opal, 1, Collections.emptyList()), 1e-9);
	}

	@Test
	public void jewellersChiselAddsChance()
	{
		Recipe opal = recipe("Crafting", 1, 1625, 1609,
				new SuccessModel(SuccessType.LINEAR_INTERP, 128, 250, null, null, 1, null));
		SuccessModifier chisel = new SuccessModifier("jewellers_chisel", ModifierEffect.ADD_CHANCE, 0.20,
				"Crafting", ids(1625));
		double expected = 129.0 / 256.0 + 0.20;
		assertEquals(expected, Success.chance(opal, 1, Collections.singletonList(chisel)), 1e-9);
	}

	@Test
	public void cookingGauntletsSwapTheStopLevel()
	{
		Recipe shark = recipe("Cooking", 94, 383, 385,
				new SuccessModel(SuccessType.BURN, null, null, 100, 94, 80, null));
		SuccessModifier gauntlets = new SuccessModifier("cooking_gauntlets", ModifierEffect.STOP_LEVEL,
				0.0, "Cooking", ids(383));

		assertEquals(1.0 - 6.0 / 21.0, Success.chance(shark, 94, Collections.emptyList()), 1e-9);
		assertEquals(1.0, Success.chance(shark, 94, Collections.singletonList(gauntlets)), 1e-9);
	}

	@Test
	public void cookingCapeForcesSuccess()
	{
		Recipe shark = recipe("Cooking", 40, 383, 385,
				new SuccessModel(SuccessType.BURN, null, null, 100, 94, 80, null));
		SuccessModifier cape = new SuccessModifier("cooking_cape", ModifierEffect.FORCE_SUCCESS, 0.0,
				"Cooking", Collections.emptySet());
		assertEquals(1.0, Success.chance(shark, 40, Collections.singletonList(cape)), 1e-9);
	}

	@Test
	public void noModelDefaultsToCertain()
	{
		Recipe bar = recipe("Smithing", 1, 3, 1, SuccessModel.always());
		assertEquals(1.0, Success.chance(bar, 1, Collections.emptyList()), 1e-9);
	}

	@Test
	public void jewellersDoubleMultipliesYield()
	{
		Recipe gem = recipe("Crafting", 50, 1625, 1609, SuccessModel.always());
		SuccessModifier doubleGem = new SuccessModifier("jewellers_double", ModifierEffect.YIELD_MULT,
				1.10, "Crafting", Collections.emptySet());
		SuccessModifier wrongSkill = new SuccessModifier("cape", ModifierEffect.YIELD_MULT, 2.0,
				"Cooking", Collections.emptySet());

		assertEquals(1.10, Success.yieldMult(gem, Collections.singletonList(doubleGem)), 1e-9);
		assertEquals(1.0, Success.yieldMult(gem, Collections.singletonList(wrongSkill)), 1e-9);
	}
}
