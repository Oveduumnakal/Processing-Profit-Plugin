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

import java.util.List;

/**
 * Composes a recipe's base {@link SuccessModel} curve with the player's active {@link SuccessModifier}s
 * in the correct order: a {@code STOP_LEVEL} modifier (cooking gauntlets) swaps in the recipe's
 * gauntlet stop level, then {@code ADD_CHANCE} modifiers (Hosidius, jeweller's chisel) add percentage
 * points, then {@code FORCE_SUCCESS} (cooking cape) overrides to certainty, all clamped to {@code [0, 1]}.
 * {@code YIELD_MULT} (jeweller's double-gem) scales the good-output quantity separately.
 */
public final class Success
{
	private Success()
	{
	}

	/**
	 * The composed success probability for a recipe at a level under the active modifiers.
	 *
	 * @param r      the recipe
	 * @param level  the player's live level in the recipe's primary skill
	 * @param active the modifiers currently in effect
	 * @return the success probability in {@code [0, 1]}
	 */
	public static double chance(Recipe r, int level, List<SuccessModifier> active)
	{
		boolean force = false;
		boolean gauntlets = false;
		double add = 0.0;
		for (SuccessModifier m : active)
		{
			if (!m.applies(r))
				continue;

			switch (m.getEffect())
			{
				case STOP_LEVEL:
					gauntlets = true;
					break;
				case ADD_CHANCE:
					add += m.getValue();
					break;
				case FORCE_SUCCESS:
					force = true;
					break;
				case YIELD_MULT:
				default:
					break;
			}
		}

		if (force)
			return 1.0;

		SuccessModel model = r.getSuccess();
		if (model == null)
			return clamp(1.0 + add);

		Integer effStop = gauntlets ? model.getGauntletStopLevel() : null;
		return clamp(model.chance(level, effStop) + add);
	}

	/**
	 * The good-output yield multiplier for a recipe under the active modifiers (e.g. jeweller's
	 * double-gem &asymp; &times;1.10).
	 *
	 * @param r      the recipe
	 * @param active the modifiers currently in effect
	 * @return the yield multiplier (1.0 when none apply)
	 */
	public static double yieldMult(Recipe r, List<SuccessModifier> active)
	{
		double y = 1.0;
		for (SuccessModifier m : active)
			if (m.getEffect() == ModifierEffect.YIELD_MULT && m.applies(r))
				y *= m.getValue();

		return y;
	}

	/**
	 * Clamps a probability into {@code [0, 1]}.
	 *
	 * @param p the raw probability
	 * @return {@code p} bounded to {@code [0, 1]}
	 */
	private static double clamp(double p)
	{
		return Math.min(1.0, Math.max(0.0, p));
	}
}
