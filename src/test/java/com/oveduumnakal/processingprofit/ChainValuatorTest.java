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
import java.util.function.ToIntFunction;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ChainValuator}: buy-only items, make-vs-buy selection, multi-step chains,
 * cycle safety, success-adjusted craft cost, and byproduct credit.
 */
public class ChainValuatorTest
{
	private final ToIntFunction<String> level99 = s -> 99;

	/** A map-backed {@link PriceLookup} test double. */
	private static final class MapPrices implements PriceLookup
	{
		private final Map<Integer, long[]> data = new HashMap<>();

		MapPrices put(int id, long buy, long sell)
		{
			data.put(id, new long[]{buy, sell});
			return this;
		}

		@Override
		public long buyPrice(int itemId)
		{
			long[] v = data.get(itemId);
			return v == null ? UNKNOWN : v[0];
		}

		@Override
		public long sellPrice(int itemId)
		{
			long[] v = data.get(itemId);
			return v == null ? UNKNOWN : v[1];
		}

		@Override
		public long volume(int itemId)
		{
			return 0L;
		}

		@Override
		public boolean isStale(int itemId)
		{
			return !data.containsKey(itemId);
		}
	}

	private static ItemStack in(int id, int qty)
	{
		return new ItemStack(id, "in-" + id, qty, true);
	}

	private static Recipe recipe(int outId, int outQty, SuccessModel success, ItemStack... inputs)
	{
		List<RecipeOutput> outs = Collections.singletonList(new RecipeOutput(outId, "out", outQty, null, null));
		return build(outs, success, inputs);
	}

	private static Recipe build(List<RecipeOutput> outs, SuccessModel success, ItemStack... inputs)
	{
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Smithing", 1, 1.0, false));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("r-" + outs.get(0).getItemId(), outs, Arrays.asList(inputs),
				Collections.emptyList(), skills, 5, null, false, null, reqs, success, null);
	}

	private ChainValuator valuator(RecipeRepository repo, PriceLookup prices)
	{
		return new ChainValuator(repo, prices, new PriceConfig(false, 1.0), level99, Collections.emptyList());
	}

	private static RecipeRepository repoOf(Recipe... recipes)
	{
		RecipeRepository repo = new RecipeRepository();
		repo.setAll(new ArrayList<>(Arrays.asList(recipes)));
		return repo;
	}

	@Test
	public void buysWhenNothingCraftsIt()
	{
		RecipeRepository repo = repoOf();
		MapPrices prices = new MapPrices().put(1, 100, 90);
		Sourcing s = valuator(repo, prices).cheapest(1);

		assertTrue(s.isViaBuy());
		assertEquals(100.0, s.getCost(), 1e-9);
		assertTrue(Double.isInfinite(s.getCraftCost()));
	}

	@Test
	public void craftsWhenCheaperThanBuying()
	{
		RecipeRepository repo = repoOf(recipe(2, 1, SuccessModel.always(), in(1, 1)));
		MapPrices prices = new MapPrices().put(1, 10, 8).put(2, 100, 90);
		Sourcing s = valuator(repo, prices).cheapest(2);

		assertFalse(s.isViaBuy());
		assertEquals(10.0, s.getCost(), 1e-9);
		assertEquals(100.0, s.getBuyCost(), 1e-9);
		assertNotNull(s.getCraftRecipe());
	}

	@Test
	public void buysWhenCheaperThanCrafting()
	{
		RecipeRepository repo = repoOf(recipe(2, 1, SuccessModel.always(), in(1, 1)));
		MapPrices prices = new MapPrices().put(1, 200, 190).put(2, 50, 45);
		Sourcing s = valuator(repo, prices).cheapest(2);

		assertTrue(s.isViaBuy());
		assertEquals(50.0, s.getCost(), 1e-9);
		assertEquals(200.0, s.getCraftCost(), 1e-9);
	}

	@Test
	public void resolvesMultiStepChains()
	{
		RecipeRepository repo = repoOf(
				recipe(2, 1, SuccessModel.always(), in(1, 1)),
				recipe(3, 1, SuccessModel.always(), in(2, 1)));
		MapPrices prices = new MapPrices().put(1, 5, 4).put(3, 100, 90);
		Sourcing s = valuator(repo, prices).cheapest(3);

		assertFalse(s.isViaBuy());
		assertEquals(5.0, s.getCost(), 1e-9);
	}

	@Test
	public void cycleIsGuardedAndReportsUnobtainable()
	{
		RecipeRepository repo = repoOf(
				recipe(1, 1, SuccessModel.always(), in(2, 1)),
				recipe(2, 1, SuccessModel.always(), in(1, 1)));
		MapPrices prices = new MapPrices();
		Sourcing s = valuator(repo, prices).cheapest(1);

		assertFalse(s.obtainable());
	}

	@Test
	public void successChanceRaisesCraftCost()
	{
		SuccessModel half = new SuccessModel(SuccessType.FIXED, null, null, null, null, null, 0.5);
		RecipeRepository repo = repoOf(recipe(2, 1, half, in(1, 1)));
		MapPrices prices = new MapPrices().put(1, 10, 8);
		Sourcing s = valuator(repo, prices).cheapest(2);

		assertEquals(20.0, s.getCost(), 1e-9);
	}

	@Test
	public void byproductCreditLowersCraftCost()
	{
		List<RecipeOutput> outs = Arrays.asList(
				new RecipeOutput(2, "primary", 1, null, null),
				new RecipeOutput(9, "byproduct", 1, null, null));
		RecipeRepository repo = repoOf(build(outs, SuccessModel.always(), in(1, 1)));
		MapPrices prices = new MapPrices().put(1, 100, 90).put(9, 40, 30);
		Sourcing s = valuator(repo, prices).cheapest(2);

		assertEquals(70.0, s.getCost(), 1e-9);
	}
}
