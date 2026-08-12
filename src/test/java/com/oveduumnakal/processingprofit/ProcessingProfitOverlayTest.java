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

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Verifies the bank overlay's total-achievable-profit sum weights each product's per-action profit by
 * how many are makeable now and ignores loss-making products.
 */
public class ProcessingProfitOverlayTest
{
	private static RecipeRow row(long profitEach, int makeable)
	{
		return RecipeRow.builder()
				.product("p")
				.profitEach(profitEach)
				.makeableNow(makeable)
				.build();
	}

	@Test
	public void totalWeightsProfitByMakeableAndSkipsLosses()
	{
		long total = ProcessingProfitOverlay.totalAchievable(Arrays.asList(
				row(100L, 3),
				row(50L, 10),
				row(-200L, 5),
				row(1000L, 0)));

		assertEquals(100L * 3 + 50L * 10, total);
	}

	@Test
	public void emptyTotalIsZero()
	{
		assertEquals(0L, ProcessingProfitOverlay.totalAchievable(Collections.emptyList()));
	}
}
