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
import java.util.Map;
import java.util.function.Predicate;

/**
 * Pure skill-level and quest gating for a recipe against a snapshot of live player state. A recipe is
 * locked when any of its required skills is above the player's level, or when a recognised required quest
 * is unfinished. The decision is separated from live RuneLite state so it can be unit-tested: the plugin
 * supplies a skill&rarr;level map, a {@code questSatisfied} predicate (true when a quest is finished
 * <em>or</em> unrecognised, so unknown quest strings never hide a recipe), and whether levels are known
 * at all ({@code false} when logged out, in which case nothing is gated).
 */
public final class Gating
{
	private Gating()
	{
	}

	/**
	 * Evaluates whether a recipe is gated out for the current player.
	 *
	 * @param rec            the recipe
	 * @param levels         skill name &rarr; the player's real level (missing skills are not gated)
	 * @param questSatisfied returns {@code true} when a quest name is finished or unrecognised
	 * @param levelsKnown    whether live levels are available ({@code false} when logged out)
	 * @return the gate result: locked plus a short reason, or {@link GateResult#OPEN}
	 */
	public static GateResult evaluate(Recipe rec, Map<String, Integer> levels,
			Predicate<String> questSatisfied, boolean levelsKnown)
	{
		if (!levelsKnown)
			return GateResult.OPEN;

		if (rec.getSkills() != null)
		{
			for (SkillReq req : rec.getSkills())
			{
				if (req == null || req.getSkill() == null)
					continue;

				Integer have = levels.get(req.getSkill());
				if (have != null && have < req.getLevel())
				{
					int delta = req.getLevel() - have;
					return new GateResult(true, req.getSkill() + " " + req.getLevel() + " (+" + delta + ")");
				}
			}
		}

		Requirements reqs = rec.getRequirements();
		if (reqs != null && reqs.getQuests() != null)
		{
			for (String quest : reqs.getQuests())
			{
				if (quest == null || quest.isEmpty())
					continue;

				if (!questSatisfied.test(quest))
					return new GateResult(true, "Quest: " + quest);
			}
		}

		return GateResult.OPEN;
	}

	/**
	 * The highest required level across a recipe's skills (the level at which its success math should be
	 * read when the player is above it), or {@code 1} when none is declared.
	 *
	 * @param rec the recipe
	 * @return the maximum required skill level
	 */
	public static int maxRequiredLevel(Recipe rec)
	{
		int max = 1;
		List<SkillReq> skills = rec.getSkills();
		if (skills != null)
		{
			for (SkillReq req : skills)
			{
				if (req != null && req.getLevel() > max)
					max = req.getLevel();
			}
		}

		return max;
	}
}
