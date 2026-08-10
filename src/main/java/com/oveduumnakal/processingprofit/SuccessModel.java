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

import lombok.Value;

/**
 * A recipe's per-level success probability. Parameters are scraped from the wiki: {@code low}/{@code high}
 * markers (out of 255) for {@link SuccessType#LINEAR_INTERP}; {@code stopLevel}/{@code gauntletStopLevel}
 * for {@link SuccessType#BURN} (with {@code reqLevel} filled from the recipe at merge time);
 * {@code fixedRate} for {@link SuccessType#FIXED}. A {@link SuccessType#ALWAYS} model is always 100%.
 */
@Value
public class SuccessModel
{
	SuccessType type;
	Integer low;
	Integer high;
	Integer stopLevel;
	Integer gauntletStopLevel;
	Integer reqLevel;
	Double fixedRate;

	/**
	 * The success probability at a skill level, using the model's own stop level.
	 *
	 * @param level the player's live level in the recipe's primary skill
	 * @return the probability in {@code [0, 1]}
	 */
	public double chance(int level)
	{
		return chance(level, null);
	}

	/**
	 * The success probability at a skill level, letting a modifier swap in an effective stop level.
	 *
	 * @param level   the player's live level in the recipe's primary skill
	 * @param effStop an overriding BURN stop level (e.g. cooking gauntlets), or {@code null} for none
	 * @return the probability in {@code [0, 1]}
	 */
	public double chance(int level, Integer effStop)
	{
		if (type == null)
			return 1.0;

		switch (type)
		{
			case FIXED:
				return fixedRate == null ? 1.0 : clamp(fixedRate);
			case LINEAR_INTERP:
			{
				double c = (low * (99 - level) + high * (level - 1)) / 98.0;
				return clamp((Math.floor(c) + 1) / 256.0);
			}
			case BURN:
			{
				int stop = effStop != null ? effStop : stopLevel;
				int req = reqLevel != null ? reqLevel : 1;
				if (level >= stop)
					return 1.0;

				double burn = (double) (stop - level) / (stop - req + 1);
				return clamp(1.0 - burn);
			}
			case ALWAYS:
			default:
				return 1.0;
		}
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

	/**
	 * An always-100% model.
	 *
	 * @return a {@link SuccessType#ALWAYS} model
	 */
	public static SuccessModel always()
	{
		return new SuccessModel(SuccessType.ALWAYS, null, null, null, null, null, null);
	}
}
