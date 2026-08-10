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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the pure {@link ProfitCalculator}: positive margin/ROI, a loss method, GP/hr n/a when
 * ticks are missing, GE tax cap and exemption, multi-output byproduct crediting, staleness, and the
 * break-even input price.
 */
public class ProfitCalculatorTest
{
	private final ProfitCalculator calc = new ProfitCalculator();

	/** A map-backed {@link PriceLookup} test double. */
	private static final class MapPrices implements PriceLookup
	{
		private final Map<Integer, long[]> data = new HashMap<>();

		MapPrices put(int id, long buy, long sell)
		{
			data.put(id, new long[]{buy, sell, 0L});
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

	private static RecipeOutput output(int id, int qty)
	{
		return new RecipeOutput(id, "item-" + id, qty, null, null);
	}

	private static ItemStack input(int id, int qty)
	{
		return new ItemStack(id, "item-" + id, qty, true);
	}

	private static Recipe recipe(List<RecipeOutput> outputs, List<ItemStack> inputs,
			Integer ticks, Double xp, SuccessModel success, FailureOutcome onFailure)
	{
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Smithing", 1, xp, false));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("test:recipe", outputs, inputs, Collections.emptyList(), skills,
				ticks, null, false, null, reqs, success, onFailure);
	}

	@Test
	public void positiveMarginRoiAndThroughput()
	{
		Recipe rec = recipe(Collections.singletonList(output(1, 1)),
				Collections.singletonList(input(3, 1)), 5, 6.2, SuccessModel.always(), null);
		MapPrices prices = new MapPrices().put(3, 100, 100).put(1, 300, 200);
		ProfitResult r = calc.evaluate(rec, prices, new PriceConfig(true, 1.0), 99);

		assertEquals(96L, r.getProfitEach());
		assertEquals(4L, r.getExpectedTax());
		assertEquals(0.96, r.getRoi(), 1e-9);
		assertTrue(r.isThroughputKnown());
		assertEquals(96L * 1200L, r.getGpPerHour());
		assertEquals(6.2 * 1200, r.getXpPerHour(), 1e-6);
		assertEquals(96.0 / 6.2, r.getProfitPerXp(), 1e-9);
		assertFalse(r.isStalePrices());
	}

	@Test
	public void lossMethodShowsNegativeProfit()
	{
		Recipe rec = recipe(Collections.singletonList(output(1, 1)),
				Collections.singletonList(input(3, 1)), 5, 1.0, SuccessModel.always(), null);
		MapPrices prices = new MapPrices().put(3, 300, 300).put(1, 300, 200);
		ProfitResult r = calc.evaluate(rec, prices, new PriceConfig(true, 1.0), 99);

		assertTrue(r.getProfitEach() < 0);
		assertTrue(r.getGpPerHour() < 0);
	}

	@Test
	public void gpPerHourIsNotKnownWhenTicksMissing()
	{
		Recipe rec = recipe(Collections.singletonList(output(1, 1)),
				Collections.singletonList(input(3, 1)), null, 6.2, SuccessModel.always(), null);
		MapPrices prices = new MapPrices().put(3, 100, 100).put(1, 300, 200);
		ProfitResult r = calc.evaluate(rec, prices, new PriceConfig(true, 1.0), 99);

		assertFalse(r.isThroughputKnown());
		assertEquals(0L, r.getGpPerHour());
		assertEquals(0.0, r.getXpPerHour(), 1e-9);
	}

	@Test
	public void geTaxIsCappedExemptedAndWaivable()
	{
		PriceConfig taxed = new PriceConfig(true, 1.0);

		assertEquals(5_000_000L, taxed.geTax(1, 1_000_000_000L, 1));
		assertEquals(0L, taxed.geTax(1, 40L, 3));
		assertEquals(0L, taxed.geTax(13190, 5_000_000L, 1));
		assertEquals(0L, new PriceConfig(false, 1.0).geTax(1, 1000L, 1));
	}

	@Test
	public void multiOutputCreditsByproducts()
	{
		Recipe single = recipe(Collections.singletonList(output(1, 1)),
				Collections.singletonList(input(3, 1)), 5, 1.0, SuccessModel.always(), null);
		Recipe multi = recipe(Arrays.asList(output(1, 1), output(2, 2)),
				Collections.singletonList(input(3, 1)), 5, 1.0, SuccessModel.always(), null);
		MapPrices prices = new MapPrices()
				.put(3, 100, 100)
				.put(1, 300, 200)
				.put(2, 60, 50);

		PriceConfig cfg = new PriceConfig(true, 1.0);
		ProfitResult one = calc.evaluate(single, prices, cfg, 99);
		ProfitResult two = calc.evaluate(multi, prices, cfg, 99);

		assertEquals(one.getProfitEach() + 98L, two.getProfitEach());
	}

	@Test
	public void unknownOutputPriceFlagsStaleWithoutGarbage()
	{
		Recipe rec = recipe(Collections.singletonList(output(1, 1)),
				Collections.singletonList(input(3, 1)), 5, 1.0, SuccessModel.always(), null);
		MapPrices prices = new MapPrices().put(3, 100, 100);
		ProfitResult r = calc.evaluate(rec, prices, new PriceConfig(true, 1.0), 99);

		assertTrue(r.isStalePrices());
		assertEquals(-100L, r.getProfitEach());
	}

	@Test
	public void breakEvenInputPriceSolvesForZeroProfit()
	{
		Recipe rec = recipe(Collections.singletonList(output(1, 1)),
				Collections.singletonList(input(3, 1)), 5, 1.0, SuccessModel.always(), null);
		MapPrices prices = new MapPrices().put(3, 100, 100).put(1, 300, 200);

		long be = calc.breakEvenInputPrice(rec, prices, new PriceConfig(false, 1.0), 99, 3);
		assertEquals(200L, be);
	}
}
