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

import lombok.Builder;
import lombok.Value;

/**
 * A display row for the recipe table: the product name and id, per-action profit, GP/hr (with a
 * {@code throughputKnown} flag so a recipe with no tick data shows "n/a"), success percentage
 * ({@code null} for always-100% recipes, shown as "&mdash;"), the level requirement, GE volume, the
 * number makeable now (used by the On-hand and Shopping tabs; {@code -1} when not applicable), a
 * staleness flag, whether the recipe is {@code locked} for the player (skill level or quest gated) with
 * a short {@code lockReason}, the binding input buy limit per 4h window ({@code buyLimitPerWindow},
 * {@code 0} = unlimited) with {@code throttled}/{@code lowVolume} liquidity warnings, whether the recipe
 * is {@code members}-only, and the source
 * {@link Recipe} so a click can rebuild the full detail breakdown. When several recipes make the same
 * product they are collapsed to one row (see the plugin's {@code dedupeByProduct}): {@code recipeCount}
 * is how many were folded in and {@code profitMin}/{@code profitMax} bound their per-action profit so the
 * card can show a range. A view model built by the plugin and rendered by {@link ProcessingProfitPanel}.
 */
@Value
@Builder(toBuilder = true)
public class RecipeRow
{
	int itemId;
	String product;
	String skill;
	long profitEach;
	double roi;
	long gpPerHour;
	double xpPerHour;
	boolean throughputKnown;
	Double successPercent;
	int levelReq;
	long volume;
	int makeableNow;
	boolean stale;
	boolean locked;
	String lockReason;
	int buyLimitPerWindow;
	boolean throttled;
	boolean lowVolume;
	boolean members;
	int recipeCount;
	long profitMin;
	long profitMax;
	Recipe recipe;
}
