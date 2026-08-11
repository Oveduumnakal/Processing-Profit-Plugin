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
import java.util.function.ToIntFunction;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link Liquidity}: the binding per-window cap across inputs, unlimited passthrough,
 * quantity division, and the throttle / low-volume warning flags.
 */
public class LiquidityTest
{
	private static ItemStack in(int id, int qty)
	{
		return new ItemStack(id, "in-" + id, qty, true);
	}

	private static Recipe recipe(ItemStack... inputs)
	{
		List<RecipeOutput> outs = Collections.singletonList(new RecipeOutput(99, "out", 1, null, null));
		List<SkillReq> skills = Collections.singletonList(new SkillReq("Smithing", 1, 1.0, false));
		Requirements reqs = new Requirements(Collections.emptyList(), Collections.emptyList());
		return new Recipe("r", outs, Arrays.asList(inputs), Collections.emptyList(), skills, 5, null,
				false, null, reqs, SuccessModel.always(), null);
	}

	private static ToIntFunction<Integer> limits(Map<Integer, Integer> m)
	{
		return id -> m.getOrDefault(id, 0);
	}

	@Test
	public void unlimitedWhenNoInputHasALimit()
	{
		LiquidityResult r = Liquidity.assess(recipe(in(1, 1)), limits(new HashMap<>()), 5000);
		assertEquals(0, r.getUnitsPerWindow());
		assertFalse(r.isThrottled());
	}

	@Test
	public void bindingInputSetsCapAndThrottles()
	{
		Map<Integer, Integer> m = new HashMap<>();
		m.put(1, 13000);
		m.put(2, 500);
		LiquidityResult r = Liquidity.assess(recipe(in(1, 1), in(2, 1)), limits(m), 5000);
		assertEquals(500, r.getUnitsPerWindow());
		assertTrue(r.isThrottled());
	}

	@Test
	public void quantityDividesTheLimit()
	{
		Map<Integer, Integer> m = new HashMap<>();
		m.put(1, 12000);
		LiquidityResult r = Liquidity.assess(recipe(in(1, 6)), limits(m), 5000);
		assertEquals(2000, r.getUnitsPerWindow());
		assertFalse(r.isThrottled());
	}

	@Test
	public void highLimitIsNotThrottled()
	{
		Map<Integer, Integer> m = new HashMap<>();
		m.put(1, 13000);
		LiquidityResult r = Liquidity.assess(recipe(in(1, 1)), limits(m), 5000);
		assertEquals(13000, r.getUnitsPerWindow());
		assertFalse(r.isThrottled());
	}

	@Test
	public void lowProductVolumeIsFlagged()
	{
		LiquidityResult low = Liquidity.assess(recipe(in(1, 1)), limits(new HashMap<>()), 4);
		assertTrue(low.isLowVolume());
		assertTrue(low.hasWarning());

		LiquidityResult ok = Liquidity.assess(recipe(in(1, 1)), limits(new HashMap<>()), 500);
		assertFalse(ok.isLowVolume());
		assertFalse(ok.hasWarning());
	}
}
