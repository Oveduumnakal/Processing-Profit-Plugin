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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

/** Settings for Processing Profit: how ingredients are valued and what the tooltip and sidebar show. */
@ConfigGroup(ProcessingProfitConfig.GROUP)
public interface ProcessingProfitConfig extends Config
{
	/** Config group key, shared with the plugin so both agree on where settings are stored. */
	String GROUP = "processingprofit";

	/** How profit is priced: ingredient basis, GE tax, and the minimum profit worth surfacing. */
	@ConfigSection(
		name = "Profit calculation",
		description = "How ingredient cost and product value are priced.",
		position = 0
	)
	String calculationSection = "calculation";

	/** How the tooltip and sidebar present processing opportunities. */
	@ConfigSection(
		name = "Display",
		description = "What the tooltip and sidebar show.",
		position = 1
	)
	String displaySection = "display";

	/**
	 * Whether unlisted ingredients are assumed bought at their instant-buy price. When off, an unlisted
	 * ingredient is treated as gathered (cost 0). Per-item overrides come later.
	 *
	 * @return true to value unlisted ingredients at buy price
	 */
	@ConfigItem(
		keyName = "assumeIngredientsBought",
		name = "Assume ingredients bought",
		description = "Value unlisted ingredients at their buy price. Off treats them as gathered (free).",
		section = calculationSection,
		position = 0
	)
	default boolean assumeIngredientsBought()
	{
		return true;
	}

	/**
	 * Whether the 2% Grand Exchange sales tax is subtracted from the value of every sold product.
	 *
	 * @return true to subtract GE sales tax from product value
	 */
	@ConfigItem(
		keyName = "applyGeTax",
		name = "Apply 2% GE tax",
		description = "Subtract the Grand Exchange sales tax from product value.",
		section = calculationSection,
		position = 1
	)
	default boolean applyGeTax()
	{
		return true;
	}

	/**
	 * Minimum profit, in gp, a process must clear before it is shown in the sidebar or tooltip.
	 *
	 * @return the minimum profit threshold in gp
	 */
	@ConfigItem(
		keyName = "minProfit",
		name = "Minimum profit (gp)",
		description = "Hide processes whose profit is below this many gp.",
		section = calculationSection,
		position = 2
	)
	default int minProfit()
	{
		return 0;
	}

	/**
	 * Whether the alt-hover tooltip showing the best processing chain is drawn over inventory and bank
	 * items.
	 *
	 * @return true to draw the hover tooltip
	 */
	@ConfigItem(
		keyName = "showTooltip",
		name = "Show hover tooltip",
		description = "Show the best-chain profit tooltip when hovering an item with alt held.",
		section = displaySection,
		position = 0
	)
	default boolean showTooltip()
	{
		return true;
	}
}
