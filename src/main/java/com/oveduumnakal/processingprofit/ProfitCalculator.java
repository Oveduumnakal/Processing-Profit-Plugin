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

/**
 * Pure, network-free profit math: evaluates a recipe into a {@link ProfitResult} across all four
 * dimensions (raw margin/ROI, GP/hr, XP/hr, profit-per-XP) under a {@link PriceLookup} and
 * {@link PriceConfig}. Values every declared output (byproducts credited), applies the GE sell tax,
 * weights the success and failure branches by the level-scaled success chance, and propagates a
 * staleness flag. Kept isolated so the inverted-spread class of bug is caught by unit tests.
 */
public class ProfitCalculator
{
	/**
	 * Evaluates a recipe at a skill level using the model's own success curve (no active modifiers).
	 *
	 * @param rec         the recipe to evaluate
	 * @param prices      the price source
	 * @param cfg         the tax/efficiency config
	 * @param playerLevel the player's live level in the recipe's primary skill
	 * @return the evaluated profitability
	 */
	public ProfitResult evaluate(Recipe rec, PriceLookup prices, PriceConfig cfg, int playerLevel)
	{
		boolean stale = false;

		long inputCost = 0L;
		for (ItemStack in : rec.getInputs())
		{
			if (!in.isConsumed())
				continue;

			Integer id = in.getItemId();
			long buy = id == null ? PriceLookup.UNKNOWN : prices.buyPrice(id);
			if (id == null || buy == PriceLookup.UNKNOWN)
			{
				stale = true;
				continue;
			}

			inputCost += buy * (long) in.getQty();
			if (prices.isStale(id))
				stale = true;
		}

		long goodGross = 0L;
		long goodTax = 0L;
		for (RecipeOutput out : rec.getOutputs())
		{
			Integer id = out.getItemId();
			long sell = id == null ? PriceLookup.UNKNOWN : prices.sellPrice(id);
			if (id == null || sell == PriceLookup.UNKNOWN)
			{
				stale = true;
				continue;
			}

			goodGross += sell * (long) out.getQty();
			goodTax += cfg.geTax(id, sell, out.getQty());
			if (prices.isStale(id))
				stale = true;
		}

		double p = rec.getSuccess() == null ? 1.0 : rec.getSuccess().chance(playerLevel);
		long failNet = failureNet(rec, prices, cfg);

		long goodNet = goodGross - goodTax;
		long expectedOutputPreTax = Math.round(p * goodGross);
		long expectedTax = Math.round(p * goodTax);
		long expectedNet = Math.round(p * goodNet + (1.0 - p) * failNet);
		long profitEach = expectedNet - inputCost;

		double roi = inputCost == 0L ? 0.0 : (double) profitEach / inputCost;
		double perHour = cfg.actionsPerHour(rec.getTicks());
		boolean throughputKnown = !Double.isNaN(perHour);
		long gpPerHour = throughputKnown ? Math.round(profitEach * perHour) : 0L;

		double xp = recipeXp(rec);
		double xpPerHour = throughputKnown ? xp * perHour : 0.0;
		double profitPerXp = xp == 0.0 ? 0.0 : profitEach / xp;

		return new ProfitResult(rec, inputCost, expectedOutputPreTax, expectedTax, profitEach,
				roi, gpPerHour, xpPerHour, profitPerXp, p, throughputKnown, stale);
	}

	/**
	 * The net value of the failure branch (materials are consumed either way), or {@code 0} when a
	 * failed attempt yields nothing or the failure item has no price.
	 *
	 * @param rec    the recipe
	 * @param prices the price source
	 * @param cfg    the tax config
	 * @return the post-tax failure value
	 */
	private static long failureNet(Recipe rec, PriceLookup prices, PriceConfig cfg)
	{
		FailureOutcome fail = rec.getOnFailure();
		if (fail == null || fail.getItemId() == null)
			return 0L;

		long sell = prices.sellPrice(fail.getItemId());
		if (sell == PriceLookup.UNKNOWN)
			return 0L;

		long gross = sell * (long) fail.getQty();
		return gross - cfg.geTax(fail.getItemId(), sell, fail.getQty());
	}

	/**
	 * The experience granted by a recipe's primary skill, or {@code 0} when unknown.
	 *
	 * @param rec the recipe
	 * @return the primary-skill xp per action
	 */
	private static double recipeXp(Recipe rec)
	{
		SkillReq skill = rec.primarySkill();
		if (skill == null || skill.getXp() == null)
			return 0.0;

		return skill.getXp();
	}

	/**
	 * The highest per-unit price payable for one input while the recipe still breaks even, holding the
	 * other inputs at their current prices. Useful for setting GE buy offers.
	 *
	 * @param rec         the recipe
	 * @param prices      the price source
	 * @param cfg         the tax/efficiency config
	 * @param playerLevel the player's live level in the recipe's primary skill
	 * @param inputItemId the input to solve for
	 * @return the break-even unit price, or {@link PriceLookup#UNKNOWN} when the input is not consumed
	 */
	public long breakEvenInputPrice(Recipe rec, PriceLookup prices, PriceConfig cfg,
			int playerLevel, int inputItemId)
	{
		ProfitResult result = evaluate(rec, prices, cfg, playerLevel);
		long expectedNet = result.getProfitEach() + result.getInputCost();

		long otherCost = 0L;
		int targetQty = 0;
		for (ItemStack in : rec.getInputs())
		{
			if (!in.isConsumed() || in.getItemId() == null)
				continue;

			if (in.getItemId() == inputItemId)
			{
				targetQty += in.getQty();
			}
			else
			{
				long buy = prices.buyPrice(in.getItemId());
				if (buy != PriceLookup.UNKNOWN)
					otherCost += buy * (long) in.getQty();
			}
		}

		if (targetQty <= 0)
			return PriceLookup.UNKNOWN;

		return (expectedNet - otherCost) / targetQty;
	}
}
