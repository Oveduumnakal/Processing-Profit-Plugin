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
import java.util.List;

/**
 * Turns a recipe into a fully-itemised {@link RecipeDetail} for the detail view. Pure and network-free:
 * it reuses the already-tested {@link ProfitCalculator} for the headline numbers and break-even prices,
 * and only adds the per-line pricing, the tool list and the success-curve breakdown around them.
 */
public final class DetailBuilder
{
	private DetailBuilder()
	{
	}

	/**
	 * Builds the breakdown for a recipe under the active prices, config, level and modifiers.
	 *
	 * @param rec    the recipe to detail
	 * @param prices the price source
	 * @param cfg    the tax/efficiency config
	 * @param level  the level to evaluate the success curve at
	 * @param active the success modifiers currently in effect
	 * @param calc   the profit calculator supplying headline and break-even numbers
	 * @return the itemised breakdown
	 */
	public static RecipeDetail build(Recipe rec, PriceLookup prices, PriceConfig cfg, int level,
			List<SuccessModifier> active, ProfitCalculator calc)
	{
		ProfitResult res = calc.evaluate(rec, prices, cfg, level, active);
		double yieldMult = Success.yieldMult(rec, active);

		List<CostLine> inputs = new ArrayList<>();
		List<String> tools = new ArrayList<>();
		List<CostLine> breakEven = new ArrayList<>();
		for (ItemStack in : rec.getInputs())
		{
			if (!in.isConsumed())
			{
				tools.add(in.getName());
				continue;
			}

			long unit = unitBuy(prices, in.getItemId());
			long total = unit == PriceLookup.UNKNOWN ? PriceLookup.UNKNOWN : unit * (long) in.getQty();
			inputs.add(new CostLine(in.getName(), in.getQty(), unit, total));

			if (in.getItemId() != null)
			{
				long be = calc.breakEvenInputPrice(rec, prices, cfg, level, in.getItemId());
				breakEven.add(new CostLine(in.getName(), in.getQty(), be, be));
			}
		}

		for (ItemStack tool : rec.getTools())
			tools.add(tool.getName());

		List<CostLine> outputs = new ArrayList<>();
		long grossOutput = 0L;
		long tax = 0L;
		for (RecipeOutput out : rec.getOutputs())
		{
			long sell = unitSell(prices, out.getItemId());
			long total = sell == PriceLookup.UNKNOWN
					? PriceLookup.UNKNOWN : Math.round(sell * (double) out.getQty() * yieldMult);
			outputs.add(new CostLine(out.getName(), out.getQty(), sell, total));
			if (total != PriceLookup.UNKNOWN)
			{
				grossOutput += total;
				tax += cfg.geTax(out.getItemId(), sell, out.getQty());
			}
		}

		SuccessModel model = rec.getSuccess();
		boolean failCapable = model != null && model.getType() != SuccessType.ALWAYS;
		Double baseChance = failCapable ? model.chance(level) : null;
		Double finalChance = failCapable ? Success.chance(rec, level, active) : null;

		List<String> modifierNotes = new ArrayList<>();
		for (SuccessModifier m : active)
			if (m.applies(rec))
				modifierNotes.add(modifierNote(m));

		FailureOutcome fail = rec.getOnFailure();
		String failureLabel = fail == null || fail.getName() == null
				? null : fail.getName() + " ×" + fail.getQty();

		return new RecipeDetail(primaryName(rec), primaryId(rec), inputs, tools, res.getInputCost(),
				outputs, grossOutput, tax, res.getProfitEach() + res.getInputCost(), res.getProfitEach(),
				res.getRoi(), res.isThroughputKnown(), res.getGpPerHour(), res.getXpPerHour(),
				res.getProfitPerXp(), level, failCapable, baseChance, finalChance, yieldMult,
				modifierNotes, failureLabel, breakEven, res.isStalePrices());
	}

	private static long unitBuy(PriceLookup prices, Integer id)
	{
		return id == null ? PriceLookup.UNKNOWN : prices.buyPrice(id);
	}

	private static long unitSell(PriceLookup prices, Integer id)
	{
		return id == null ? PriceLookup.UNKNOWN : prices.sellPrice(id);
	}

	private static String primaryName(Recipe rec)
	{
		List<RecipeOutput> outs = rec.getOutputs();
		return outs.isEmpty() ? "" : outs.get(0).getName();
	}

	private static int primaryId(Recipe rec)
	{
		Integer id = rec.primaryOutputId();
		return id == null ? -1 : id;
	}

	private static String modifierNote(SuccessModifier m)
	{
		switch (m.getEffect())
		{
			case ADD_CHANCE:
				return m.getId() + " +" + Math.round(m.getValue() * 100) + "%";
			case STOP_LEVEL:
				return m.getId() + " (gauntlet stop level)";
			case FORCE_SUCCESS:
				return m.getId() + " (guaranteed success)";
			case YIELD_MULT:
			default:
				return m.getId() + " ×" + m.getValue() + " yield";
		}
	}
}
