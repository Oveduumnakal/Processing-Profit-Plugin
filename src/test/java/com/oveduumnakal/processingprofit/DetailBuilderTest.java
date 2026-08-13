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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the pure {@link DetailBuilder}: per-line input/output pricing, the input cost and
 * break-even prices reused from {@link ProfitCalculator}, and the success breakdown (base curve, final
 * chance, and failure label) for a fail-capable recipe.
 */
public class DetailBuilderTest
{
	private final ProfitCalculator calc = new ProfitCalculator();
	private final ToIntFunction<String> level99 = s -> 99;

	private ChainValuator valuator(Recipe rec, PriceLookup prices)
	{
		RecipeRepository repo = new RecipeRepository();
		repo.setAll(new ArrayList<>(Collections.singletonList(rec)));
		return new ChainValuator(repo, prices, new PriceConfig(false, 1.0), level99,
				Collections.emptyList());
	}

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

	private static Recipe recipe(SuccessModel success, FailureOutcome onFailure)
	{
		List<RecipeOutput> outputs = Collections.singletonList(new RecipeOutput(1, "Widget", 1, null, null));
		List<ItemStack> inputs = Collections.singletonList(new ItemStack(3, "Bar", 1, true));
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Smithing", 1, 6.2, false));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("test:recipe", outputs, inputs, Collections.emptyList(), skills,
				5, null, false, null, reqs, success, onFailure);
	}

	@Test
	public void itemisesCostAndBreakEvenForAlwaysSuccess()
	{
		Recipe rec = recipe(SuccessModel.always(), null);
		MapPrices prices = new MapPrices().put(3, 100, 100).put(1, 300, 200);

		RecipeDetail d = DetailBuilder.build(rec, prices, new PriceConfig(false, 1.0), 99,
				Collections.emptyList(), calc, valuator(rec, prices), Collections.emptyMap(),
				"A useful widget.");

		assertEquals("Widget", d.getProduct());
		assertEquals(1, d.getInputs().size());
		CostLine in = d.getInputs().get(0);
		assertEquals(100L, in.getUnitPrice());
		assertEquals(100L, in.getTotal());
		assertEquals(100L, d.getInputCost());
		assertEquals(200L, d.getGrossOutput());
		assertEquals(0L, d.getTax());
		assertEquals(100L, d.getProfitEach());
		assertFalse(d.isFailCapable());
		assertNull(d.getBaseChance());
		assertEquals(1, d.getBreakEven().size());
		CostLine be = d.getBreakEven().get(0);
		assertEquals(200L, be.getUnitPrice());
	}

	@Test
	public void showsSuccessBreakdownAndFailureForFailCapable()
	{
		SuccessModel burn = new SuccessModel(SuccessType.BURN, null, null, 40, null, 1, null);
		Recipe rec = recipe(burn, new FailureOutcome(99, "Burnt widget", 1));
		MapPrices prices = new MapPrices().put(3, 100, 100).put(1, 300, 200);

		RecipeDetail d = DetailBuilder.build(rec, prices, new PriceConfig(false, 1.0), 30,
				Collections.emptyList(), calc, valuator(rec, prices), Collections.emptyMap(), null);

		assertTrue(d.isFailCapable());
		assertEquals(burn.chance(30), d.getBaseChance(), 1e-9);
		assertEquals(burn.chance(30), d.getFinalChance(), 1e-9);
		assertEquals("Burnt widget ×1", d.getFailureLabel());
		assertEquals(30, d.getLevel());
	}
}
