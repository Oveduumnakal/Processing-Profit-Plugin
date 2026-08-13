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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link ChainTreeBuilder}: a buy-only input is a leaf that counts to the Total materials
 * roll-up, a cheaper-to-make input expands its craft recipe (carrying the crafting skill), a chain
 * deeper than the cap is truncated, a recipe cycle terminates, a held input is covered and not expanded,
 * a partially-held input is still sourced, and a batch step generates a leftover.
 */
public class ChainTreeBuilderTest
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

	private static Recipe recipe(int outId, int outQty, ItemStack... inputs)
	{
		List<RecipeOutput> outs = Collections.singletonList(new RecipeOutput(outId, "out-" + outId, outQty,
				null, null));
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Smithing", 1, 1.0, false));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("r-" + outId, outs, Arrays.asList(inputs), Collections.emptyList(), skills, 5,
				null, false, null, reqs, SuccessModel.always(), null);
	}

	private ChainValuator valuator(PriceLookup prices, Recipe... recipes)
	{
		RecipeRepository repo = new RecipeRepository();
		repo.setAll(new ArrayList<>(Arrays.asList(recipes)));
		return new ChainValuator(repo, prices, new PriceConfig(false, 1.0), level99,
				Collections.emptyList());
	}

	private static boolean anyTruncated(List<ChainNode> nodes)
	{
		for (ChainNode node : nodes)
			if (node.isTruncated() || anyTruncated(node.getChildren()))
				return true;

		return false;
	}

	private static MaterialLine line(List<MaterialLine> lines, int id)
	{
		for (MaterialLine l : lines)
			if (l.getItemId() == id)
				return l;

		return null;
	}

	@Test
	public void buyOnlyInputIsALeafAndCountsToTotals()
	{
		MapPrices prices = new MapPrices().put(1, 100L, 90L).put(2, 500L, 450L);
		Recipe product = recipe(2, 1, in(1, 3));
		ChainValuator valuator = valuator(prices, product);

		ChainTree tree = ChainTreeBuilder.build(product, valuator, Collections.emptyMap(), 1L);

		assertEquals(1, tree.getRoots().size());
		ChainNode root = tree.getRoots().get(0);
		assertEquals(2, root.getItemId());
		assertEquals(1, root.getChildren().size());
		ChainNode leaf = root.getChildren().get(0);
		assertTrue(leaf.isViaBuy());
		assertNull(leaf.getViaSkill());
		assertEquals(100L, leaf.getBuyUnit());
		assertEquals(3L, leaf.getQty());
		assertTrue(leaf.getChildren().isEmpty());

		MaterialLine total = line(tree.getTotals(), 1);
		assertEquals(3L, total.getQty());
		assertEquals(0L, total.getHeld());
		assertEquals(3L, total.toObtain());
	}

	@Test
	public void cheaperToMakeExpandsChildrenWithSkill()
	{
		MapPrices prices = new MapPrices()
				.put(0, 10L, 8L)
				.put(1, 1000L, 900L)
				.put(2, 5000L, 4500L);
		Recipe makeItem1 = recipe(1, 1, in(0, 1));
		Recipe product = recipe(2, 1, in(1, 1));
		ChainValuator valuator = valuator(prices, makeItem1, product);

		ChainTree tree = ChainTreeBuilder.build(product, valuator, Collections.emptyMap(), 1L);

		assertEquals(1, tree.getRoots().size());
		ChainNode root = tree.getRoots().get(0);
		assertEquals(1, root.getChildren().size());
		ChainNode made = root.getChildren().get(0);
		assertFalse(made.isViaBuy());
		assertEquals("Smithing", made.getViaSkill());
		assertEquals(1, made.getChildren().size());
		ChainNode leaf = made.getChildren().get(0);
		assertTrue(leaf.isViaBuy());
		assertEquals(0, leaf.getChildren().size());
	}

	@Test
	public void chainDeeperThanCapIsTruncated()
	{
		MapPrices prices = new MapPrices().put(0, 1L, 1L);
		List<Recipe> recipes = new ArrayList<>();
		for (int id = 1; id <= 10; id++)
		{
			prices.put(id, 100_000L, 90_000L);
			recipes.add(recipe(id, 1, in(id - 1, 1)));
		}

		ChainValuator valuator = valuator(prices, recipes.toArray(new Recipe[0]));
		ChainTree tree = ChainTreeBuilder.build(recipe(11, 1, in(10, 1)), valuator,
				Collections.emptyMap(), 1L);

		assertTrue(anyTruncated(tree.getRoots()));
	}

	@Test
	public void recipeCycleTerminates()
	{
		MapPrices prices = new MapPrices().put(3, 5L, 4L);
		Recipe makeOne = recipe(1, 1, in(2, 1), in(3, 1));
		Recipe makeTwo = recipe(2, 1, in(1, 1), in(3, 1));
		Recipe product = recipe(4, 1, in(1, 1));
		ChainValuator valuator = valuator(prices, makeOne, makeTwo, product);

		ChainTree tree = ChainTreeBuilder.build(product, valuator, Collections.emptyMap(), 1L);

		assertEquals(1, tree.getRoots().size());
	}

	@Test
	public void heldInputIsCoveredAndNotExpanded()
	{
		MapPrices prices = new MapPrices()
				.put(0, 10L, 8L)
				.put(1, 1000L, 900L)
				.put(2, 5000L, 4500L);
		Recipe makeItem1 = recipe(1, 1, in(0, 1));
		Recipe product = recipe(2, 1, in(1, 1));
		ChainValuator valuator = valuator(prices, makeItem1, product);
		Map<Integer, Integer> held = new HashMap<>();
		held.put(1, 1);

		ChainTree tree = ChainTreeBuilder.build(product, valuator, held, 1L);

		ChainNode root = tree.getRoots().get(0);
		ChainNode node = root.getChildren().get(0);
		assertTrue(node.isCovered());
		assertEquals(1L, node.getHeld());
		assertTrue(node.getChildren().isEmpty());

		MaterialLine total = line(tree.getTotals(), 1);
		assertTrue(total.isCovered());
		assertEquals(0L, total.toObtain());
	}

	@Test
	public void partialHeldIsRecordedButStillExpanded()
	{
		MapPrices prices = new MapPrices()
				.put(0, 10L, 8L)
				.put(1, 1000L, 900L)
				.put(2, 5000L, 4500L);
		Recipe makeItem1 = recipe(1, 1, in(0, 1));
		Recipe product = recipe(2, 1, in(1, 2));
		ChainValuator valuator = valuator(prices, makeItem1, product);
		Map<Integer, Integer> held = new HashMap<>();
		held.put(1, 1);

		ChainTree tree = ChainTreeBuilder.build(product, valuator, held, 1L);

		ChainNode root = tree.getRoots().get(0);
		ChainNode node = root.getChildren().get(0);
		assertFalse(node.isCovered());
		assertEquals(1L, node.getHeld());
		assertFalse(node.getChildren().isEmpty());
	}

	@Test
	public void batchOutputGeneratesLeftover()
	{
		MapPrices prices = new MapPrices()
				.put(0, 10L, 8L)
				.put(1, 1000L, 900L)
				.put(2, 5000L, 4500L);
		Recipe makeItem1 = recipe(1, 2, in(0, 1));
		Recipe product = recipe(2, 1, in(1, 1));
		ChainValuator valuator = valuator(prices, makeItem1, product);

		ChainTree tree = ChainTreeBuilder.build(product, valuator, Collections.emptyMap(), 1L);

		ChainNode root = tree.getRoots().get(0);
		ChainNode made = root.getChildren().get(0);
		assertFalse(made.isViaBuy());
		assertEquals(1L, made.getSurplus());

		MaterialLine left = line(tree.getLeftovers(), 1);
		assertEquals(1L, left.getQty());
	}
}
