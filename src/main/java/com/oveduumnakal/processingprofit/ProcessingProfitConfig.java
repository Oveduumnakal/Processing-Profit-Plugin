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

	/** How the sidebar presents processing opportunities. */
	@ConfigSection(
		name = "Display",
		description = "What the sidebar shows and how it is filtered.",
		position = 1
	)
	String displaySection = "display";

	/** Success-boosting gear/diaries: auto-detected by default, overridable per modifier. */
	@ConfigSection(
		name = "Success modifiers",
		description = "Gear and diary bonuses that change success/yield. Auto-detected unless overridden.",
		position = 2
	)
	String modifierSection = "modifiers";

	/**
	 * The default sourcing assumption profits are computed under. The sidebar's selector overrides this
	 * for the session.
	 *
	 * @return the default sourcing mode
	 */
	@ConfigItem(
		keyName = "sourcingMode",
		name = "Sourcing mode",
		description = "On-hand (from your stock), Buy to order (all bought), or Hybrid (held + shortfall).",
		section = calculationSection,
		position = 0
	)
	default SourcingMode sourcingMode()
	{
		return SourcingMode.HYBRID;
	}

	/**
	 * The valuation lens: GE market, or the Ironman (no-GE) lens that values by alch/shop with GE tax,
	 * buy limits and volume gating disabled.
	 *
	 * @return the valuation lens
	 */
	@ConfigItem(
		keyName = "valuationLens",
		name = "Valuation",
		description = "GE market prices, or the Ironman (no-GE) alch/shop lens.",
		section = calculationSection,
		position = 1
	)
	default ValuationLens valuationLens()
	{
		return ValuationLens.GE_MARKET;
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
		position = 2
	)
	default boolean applyGeTax()
	{
		return true;
	}

	/**
	 * The efficiency percentage applied to theoretical action throughput for GP/hr and XP/hr, covering
	 * banking and travel overhead.
	 *
	 * @return the efficiency percentage (1&ndash;100)
	 */
	@ConfigItem(
		keyName = "efficiency",
		name = "Efficiency %",
		description = "Fraction of theoretical actions/hr actually achieved (banking, travel overhead).",
		section = calculationSection,
		position = 3
	)
	default int efficiency()
	{
		return 90;
	}

	/**
	 * How often live prices are refreshed from the wiki, in seconds.
	 *
	 * @return the refresh interval in seconds
	 */
	@ConfigItem(
		keyName = "refreshSeconds",
		name = "Price refresh (s)",
		description = "How often live prices are refreshed from the OSRS Wiki (5-minute averages).",
		section = calculationSection,
		position = 4
	)
	default int refreshSeconds()
	{
		return 60;
	}

	/**
	 * Minimum profit per action, in gp, a recipe must clear to be shown.
	 *
	 * @return the minimum profit threshold in gp
	 */
	@ConfigItem(
		keyName = "minProfit",
		name = "Min profit (gp)",
		description = "Hide recipes whose profit per action is below this many gp.",
		section = displaySection,
		position = 0
	)
	default int minProfit()
	{
		return 0;
	}

	/**
	 * Minimum GP/hr a recipe must clear to be shown (recipes with no tick data are not filtered out).
	 *
	 * @return the minimum GP/hr threshold
	 */
	@ConfigItem(
		keyName = "minGpPerHour",
		name = "Min GP/hr",
		description = "Hide recipes whose GP/hr is below this. Recipes with no tick data are kept.",
		section = displaySection,
		position = 1
	)
	default int minGpPerHour()
	{
		return 0;
	}

	/**
	 * Minimum GE trading volume a product must have to be shown, filtering illiquid traps.
	 *
	 * @return the minimum volume threshold
	 */
	@ConfigItem(
		keyName = "minVolume",
		name = "Min volume",
		description = "Hide products whose GE trading volume is below this.",
		section = displaySection,
		position = 2
	)
	default int minVolume()
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
		position = 3
	)
	default boolean showTooltip()
	{
		return true;
	}

	/**
	 * Whether to draw the bank overlay listing the top makeable products and total achievable profit
	 * while the bank is open.
	 *
	 * @return true to draw the bank overlay
	 */
	@ConfigItem(
		keyName = "showBankOverlay",
		name = "Bank overlay",
		description = "While banking, show the top makeable products and total achievable profit.",
		section = displaySection,
		position = 4
	)
	default boolean showBankOverlay()
	{
		return true;
	}

	/**
	 * How recipes the player cannot yet do (skill or quest gated) are shown: hidden, greyed with the
	 * lock reason, or listed normally.
	 *
	 * @return the gating display mode
	 */
	@ConfigItem(
		keyName = "gatingMode",
		name = "Locked recipes",
		description = "Hide, grey out (with reason), or show recipes your level or quests don't allow yet.",
		section = displaySection,
		position = 5
	)
	default GatingMode gatingMode()
	{
		return GatingMode.HIDE;
	}

	/**
	 * Whether members-only recipes are listed. When off, only free-to-play recipes are shown.
	 *
	 * @return true to list members recipes
	 */
	@ConfigItem(
		keyName = "showMembers",
		name = "Show members recipes",
		description = "List members-only recipes. Turn off to show only free-to-play recipes.",
		section = displaySection,
		position = 6
	)
	default boolean showMembers()
	{
		return true;
	}

	/**
	 * Whether the jeweller's chisel gem-cutting bonus applies. Not auto-detectable, so {@code AUTO}
	 * behaves as off until forced on.
	 *
	 * @return the jeweller's chisel toggle
	 */
	@ConfigItem(
		keyName = "modJewellersChisel",
		name = "Jeweller's chisel",
		description = "Boosts semi-precious gem cut chance and doubling. Auto-detect can't see it — set On to apply.",
		section = modifierSection,
		position = 0
	)
	default ModifierToggle jewellersChisel()
	{
		return ModifierToggle.AUTO;
	}

	/**
	 * Whether the cooking gauntlets not-burn bonus applies (auto-detects equipped gauntlets).
	 *
	 * @return the cooking gauntlets toggle
	 */
	@ConfigItem(
		keyName = "modCookingGauntlets",
		name = "Cooking gauntlets",
		description = "Lowers the burn-stop level on lobster/swordfish/monkfish/shark/anglerfish.",
		section = modifierSection,
		position = 1
	)
	default ModifierToggle cookingGauntlets()
	{
		return ModifierToggle.AUTO;
	}

	/**
	 * Whether the cooking cape never-burn bonus applies (auto-detects an equipped cooking/max cape).
	 *
	 * @return the cooking cape toggle
	 */
	@ConfigItem(
		keyName = "modCookingCape",
		name = "Cooking cape",
		description = "Never burn any food (auto-detects an equipped cooking or max cape).",
		section = modifierSection,
		position = 2
	)
	default ModifierToggle cookingCape()
	{
		return ModifierToggle.AUTO;
	}

	/**
	 * Which Hosidius cooking-range not-burn tier applies (auto-detects the Kourend &amp; Kebos diary).
	 *
	 * @return the Hosidius range setting
	 */
	@ConfigItem(
		keyName = "modHosidius",
		name = "Hosidius range",
		description = "Kourend & Kebos diary not-burn bonus: auto-detect, or force a tier.",
		section = modifierSection,
		position = 3
	)
	default HosidiusRange hosidius()
	{
		return HosidiusRange.AUTO;
	}
}
