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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link HeldMakeable}: held-only counts, crafting an intermediate from held sub-materials,
 * mixing held and craftable, being bounded by the scarcest input, and terminating on a recipe cycle.
 */
public class HeldMakeableTest
{
	private static ItemStack in(int id, int qty)
	{
		return new ItemStack(id, "in-" + id, qty, true);
	}

	private static Recipe recipe(int outId, int outQty, ItemStack... inputs)
	{
		List<RecipeOutput> outs = Collections.singletonList(new RecipeOutput(outId, "out-" + outId, outQty,
				null, null));
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Crafting", 1, 1.0, false));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("r-" + outId, outs, Arrays.asList(inputs), Collections.emptyList(), skills, 5,
				null, false, null, reqs, SuccessModel.always(), null);
	}

	private static RecipeRepository repoOf(Recipe... recipes)
	{
		RecipeRepository repo = new RecipeRepository();
		repo.setAll(new ArrayList<>(Arrays.asList(recipes)));
		return repo;
	}

	private static Map<Integer, Integer> stock(int... pairs)
	{
		Map<Integer, Integer> map = new HashMap<>();
		for (int i = 0; i < pairs.length; i += 2)
			map.put(pairs[i], pairs[i + 1]);

		return map;
	}

	@Test
	public void countsHeldFinishedInputs()
	{
		RecipeRepository repo = repoOf(recipe(2, 1, in(1, 1)));
		HeldMakeable hm = new HeldMakeable(stock(1, 5), repo::forOutput);

		assertEquals(5, hm.makeable(recipe(2, 1, in(1, 1))));
	}

	@Test
	public void craftsIntermediateFromHeldSubMaterials()
	{
		Recipe makeIntermediate = recipe(1, 1, in(0, 1));
		Recipe product = recipe(2, 1, in(1, 1));
		RecipeRepository repo = repoOf(makeIntermediate, product);
		HeldMakeable hm = new HeldMakeable(stock(0, 3), repo::forOutput);

		assertEquals(3, hm.makeable(product));
	}

	@Test
	public void addsHeldIntermediateToCraftableOnes()
	{
		Recipe makeIntermediate = recipe(1, 1, in(0, 1));
		Recipe product = recipe(2, 1, in(1, 1));
		RecipeRepository repo = repoOf(makeIntermediate, product);
		HeldMakeable hm = new HeldMakeable(stock(1, 2, 0, 3), repo::forOutput);

		assertEquals(5, hm.makeable(product));
	}

	@Test
	public void boundedByScarcestInput()
	{
		Recipe makeIntermediate = recipe(1, 1, in(0, 1));
		Recipe product = recipe(2, 1, in(1, 1), in(3, 1));
		RecipeRepository repo = repoOf(makeIntermediate, product);
		HeldMakeable hm = new HeldMakeable(stock(0, 3, 3, 1), repo::forOutput);

		assertEquals(1, hm.makeable(product));
	}

	@Test
	public void heldOnlyWhenNoRecipeLookup()
	{
		HeldMakeable hm = new HeldMakeable(stock(0, 9), null);

		assertEquals(0, hm.makeable(recipe(2, 1, in(1, 1))));
	}

	@Test
	public void recipeCycleTerminates()
	{
		Recipe makeOne = recipe(1, 1, in(2, 1));
		Recipe makeTwo = recipe(2, 1, in(1, 1));
		RecipeRepository repo = repoOf(makeOne, makeTwo);
		HeldMakeable hm = new HeldMakeable(stock(5, 4), repo::forOutput);

		assertEquals(0, hm.makeable(recipe(9, 1, in(1, 1))));
	}
}
