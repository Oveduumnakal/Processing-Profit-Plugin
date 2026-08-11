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
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ShoppingListBuilder}: input aggregation and action rounding, recursive
 * make-or-buy expansion to raw leaves, shared inputs across requests, buy-limit window splitting, and
 * hybrid held-stock subtraction (partial shortfall and fully covered).
 */
public class ShoppingListBuilderTest
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
		List<RecipeOutput> outs = Collections.singletonList(new RecipeOutput(outId, "out-" + outId, outQty,
				null, null));
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Smithing", 1, 1.0, false));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("r-" + outId, outs, Arrays.asList(inputs), Collections.emptyList(), skills, 5,
				null, false, null, reqs, success, null);
	}

	private static RecipeRepository repoOf(Recipe... recipes)
	{
		RecipeRepository repo = new RecipeRepository();
		repo.setAll(new ArrayList<>(Arrays.asList(recipes)));
		return repo;
	}

	private ShoppingListBuilder builder(RecipeRepository repo, PriceLookup prices,
			Map<Integer, Integer> limits)
	{
		ChainValuator valuator = new ChainValuator(repo, prices, new PriceConfig(false, 1.0), level99,
				Collections.emptyList());
		ToIntFunction<Integer> buyLimit = id -> limits.getOrDefault(id, 0);
		return new ShoppingListBuilder(valuator, prices, level99, Collections.emptyList(), buyLimit);
	}

	@Test
	public void aggregatesInputsAndCosts()
	{
		RecipeRepository repo = repoOf(recipe(2, 1, SuccessModel.always(), in(1, 2)));
		MapPrices prices = new MapPrices().put(1, 10, 8).put(2, 100, 90);
		ShoppingListBuilder b = builder(repo, prices, Collections.emptyMap());

		List<ShoppingRequest> reqs = Collections.singletonList(new ShoppingRequest(recipe(2, 1,
				SuccessModel.always(), in(1, 2)), 3));
		ShoppingList list = b.build(reqs, Collections.emptyMap());

		assertEquals(1, list.getLines().size());
		ShoppingLine line = list.getLines().get(0);
		assertEquals(1, line.getItemId());
		assertEquals(6L, line.getToBuy());
		assertEquals(60L, line.getTotalCost());
		assertEquals(60L, list.getTotalCost());
		assertTrue(list.isFullyPriced());
	}

	@Test
	public void expandsRecursiveMakeOrBuyToRawLeaf()
	{
		RecipeRepository repo = repoOf(
				recipe(2, 1, SuccessModel.always(), in(1, 1)),
				recipe(3, 1, SuccessModel.always(), in(2, 1)));
		MapPrices prices = new MapPrices().put(1, 5, 4).put(2, 100, 90);
		ShoppingListBuilder b = builder(repo, prices, Collections.emptyMap());

		List<ShoppingRequest> reqs = Collections.singletonList(new ShoppingRequest(recipe(3, 1,
				SuccessModel.always(), in(2, 1)), 1));
		ShoppingList list = b.build(reqs, Collections.emptyMap());

		assertEquals(1, list.getLines().size());
		ShoppingLine line = list.getLines().get(0);
		assertEquals(1, line.getItemId());
		assertEquals(1L, line.getToBuy());
		assertEquals(5L, line.getTotalCost());
	}

	@Test
	public void sharedInputsAcrossRequestsAreSummed()
	{
		RecipeRepository repo = repoOf(
				recipe(2, 1, SuccessModel.always(), in(1, 1)),
				recipe(3, 1, SuccessModel.always(), in(1, 1)));
		MapPrices prices = new MapPrices().put(1, 10, 8);
		ShoppingListBuilder b = builder(repo, prices, Collections.emptyMap());

		List<ShoppingRequest> reqs = Arrays.asList(
				new ShoppingRequest(recipe(2, 1, SuccessModel.always(), in(1, 1)), 2),
				new ShoppingRequest(recipe(3, 1, SuccessModel.always(), in(1, 1)), 3));
		ShoppingList list = b.build(reqs, Collections.emptyMap());

		assertEquals(1, list.getLines().size());
		ShoppingLine line = list.getLines().get(0);
		assertEquals(5L, line.getToBuy());
	}

	@Test
	public void successChanceRaisesActionCount()
	{
		SuccessModel half = new SuccessModel(SuccessType.FIXED, null, null, null, null, null, 0.5);
		RecipeRepository repo = repoOf(recipe(2, 1, half, in(1, 1)));
		MapPrices prices = new MapPrices().put(1, 10, 8);
		ShoppingListBuilder b = builder(repo, prices, Collections.emptyMap());

		List<ShoppingRequest> reqs = Collections.singletonList(new ShoppingRequest(recipe(2, 1, half,
				in(1, 1)), 1));
		ShoppingList list = b.build(reqs, Collections.emptyMap());

		ShoppingLine line = list.getLines().get(0);
		assertEquals(2L, line.getToBuy());
	}

	@Test
	public void buyLimitSplitsIntoWindows()
	{
		RecipeRepository repo = repoOf(recipe(2, 1, SuccessModel.always(), in(1, 1)));
		MapPrices prices = new MapPrices().put(1, 10, 8);
		Map<Integer, Integer> limits = new HashMap<>();
		limits.put(1, 10);
		ShoppingListBuilder b = builder(repo, prices, limits);

		List<ShoppingRequest> reqs = Collections.singletonList(new ShoppingRequest(recipe(2, 1,
				SuccessModel.always(), in(1, 1)), 30));
		ShoppingList list = b.build(reqs, Collections.emptyMap());

		ShoppingLine line = list.getLines().get(0);
		assertEquals(10, line.getBuyLimit());
		assertEquals(3, line.getWindows());
		assertEquals(3, list.getMaxWindows());
	}

	@Test
	public void hybridSubtractsHeldShortfall()
	{
		RecipeRepository repo = repoOf(recipe(2, 1, SuccessModel.always(), in(1, 1)));
		MapPrices prices = new MapPrices().put(1, 10, 8);
		ShoppingListBuilder b = builder(repo, prices, Collections.emptyMap());
		Recipe product = recipe(2, 1, SuccessModel.always(), in(1, 1));

		Map<Integer, Integer> held = new HashMap<>();
		held.put(1, 2);
		ShoppingList partial = b.build(Collections.singletonList(new ShoppingRequest(product, 5)), held);
		ShoppingLine line = partial.getLines().get(0);
		assertEquals(2L, line.getHeld());
		assertEquals(3L, line.getToBuy());

		Map<Integer, Integer> covered = new HashMap<>();
		covered.put(1, 5);
		ShoppingList none = b.build(Collections.singletonList(new ShoppingRequest(product, 5)), covered);
		assertTrue(none.isEmpty());
	}

	@Test
	public void unpricedLeafMarksListNotFullyPriced()
	{
		RecipeRepository repo = repoOf(recipe(2, 1, SuccessModel.always(), in(1, 1)));
		MapPrices prices = new MapPrices();
		ShoppingListBuilder b = builder(repo, prices, Collections.emptyMap());

		List<ShoppingRequest> reqs = Collections.singletonList(new ShoppingRequest(recipe(2, 1,
				SuccessModel.always(), in(1, 1)), 4));
		ShoppingList list = b.build(reqs, Collections.emptyMap());

		ShoppingLine line = list.getLines().get(0);
		assertFalse(line.isPriced());
		assertEquals(0L, line.getTotalCost());
		assertFalse(list.isFullyPriced());
	}
}
