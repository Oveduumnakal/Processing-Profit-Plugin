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
import static org.junit.Assert.assertTrue;

/**
 * Verifies the three sourcing modes yield different makeable/total numbers over a fixed inventory, and
 * that hybrid mode produces the correct shortfall shopping list.
 */
public class SourcingEngineTest
{
	private final SourcingEngine engine = new SourcingEngine(new ProfitCalculator());

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

	private static Recipe recipe()
	{
		List<RecipeOutput> outs =
				Collections.singletonList(new RecipeOutput(100, "product", 1, null, null));
		List<ItemStack> inputs = Arrays.asList(
				new ItemStack(1, "matA", 2, true),
				new ItemStack(2, "matB", 1, true));
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Smithing", 1, 1.0, false));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("test", outs, inputs, Collections.emptyList(), skills, 5, null, false, null,
				reqs, SuccessModel.always(), null);
	}

	private static Map<Integer, Integer> held()
	{
		Map<Integer, Integer> h = new HashMap<>();
		h.put(1, 10);
		h.put(2, 3);
		return h;
	}

	private SourcingResult run(SourcingMode mode)
	{
		MapPrices prices = new MapPrices()
				.put(1, 5, 4)
				.put(2, 10, 8)
				.put(100, 200, 100);
		PriceConfig cfg = new PriceConfig(false, 1.0);
		return engine.evaluate(recipe(), prices, cfg, 99, Collections.emptyList(), held(), mode, 5);
	}

	@Test
	public void perActionProfitIsMarketValuedInEveryMode()
	{
		assertEquals(80L, run(SourcingMode.ON_HAND).getProfitEach());
		assertEquals(80L, run(SourcingMode.BUY_TO_ORDER).getProfitEach());
		assertEquals(80L, run(SourcingMode.HYBRID).getProfitEach());
	}

	@Test
	public void onHandIsBoundedByScarcestInput()
	{
		SourcingResult r = run(SourcingMode.ON_HAND);
		assertEquals(3, r.getMakeableNow());
		assertEquals(240L, r.getTotalProfit());
		assertTrue(r.getShortfall().isEmpty());
	}

	@Test
	public void buyToOrderIsNotStockBound()
	{
		SourcingResult r = run(SourcingMode.BUY_TO_ORDER);
		assertEquals(0, r.getMakeableNow());
		assertEquals(80L, r.getTotalProfit());
	}

	@Test
	public void hybridBuysOnlyTheShortfall()
	{
		SourcingResult r = run(SourcingMode.HYBRID);
		assertEquals(5, r.getMakeableNow());
		assertEquals(400L, r.getTotalProfit());

		Map<Integer, Integer> shortfall = r.getShortfall();
		assertEquals(1, shortfall.size());
		assertEquals(Integer.valueOf(2), shortfall.get(2));
	}
}
