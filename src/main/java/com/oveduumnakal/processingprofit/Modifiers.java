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
 * Pure resolution of which success modifiers are active. Config toggles ({@link ModifierToggle} /
 * {@link HosidiusRange}) are combined with live detection into a {@link ModifierState}, which then
 * filters the loaded modifier registry (keyed by the ids in {@code success_modifiers.json}) into the
 * active list fed to {@link Success}, and renders the panel's status line. Kept free of RuneLite state so
 * the precedence rules (auto vs forced, elite supersedes easy) are unit-testable.
 */
public final class Modifiers
{
	private static final String JEWELLERS_CHISEL = "jewellers_chisel";
	private static final String JEWELLERS_DOUBLE = "jewellers_double";
	private static final String COOKING_GAUNTLETS = "cooking_gauntlets";
	private static final String COOKING_CAPE = "cooking_cape";
	private static final String HOSIDIUS_EASY = "hosidius_easy";
	private static final String HOSIDIUS_ELITE = "hosidius_elite";

	private Modifiers()
	{
	}

	/**
	 * Resolves each toggle over its detected value into the concrete active state.
	 *
	 * @param jewellers       the jeweller's chisel toggle
	 * @param jewellersDet    whether a jeweller's chisel was detected
	 * @param gauntlets       the cooking gauntlets toggle
	 * @param gauntletsDet    whether cooking gauntlets were detected equipped
	 * @param cape            the cooking cape toggle
	 * @param capeDet         whether a cooking cape was detected equipped
	 * @param hosidius        the Hosidius range setting
	 * @param hosidiusDet     the detected Hosidius tier ({@code NONE}/{@code EASY}/{@code ELITE})
	 * @return the resolved modifier state
	 */
	public static ModifierState resolveState(ModifierToggle jewellers, boolean jewellersDet,
			ModifierToggle gauntlets, boolean gauntletsDet, ModifierToggle cape, boolean capeDet,
			HosidiusRange hosidius, HosidiusRange hosidiusDet)
	{
		HosidiusRange tier = hosidius == HosidiusRange.AUTO ? hosidiusDet : hosidius;
		return new ModifierState(
				resolve(jewellers, jewellersDet),
				resolve(gauntlets, gauntletsDet),
				resolve(cape, capeDet),
				tier == HosidiusRange.EASY,
				tier == HosidiusRange.ELITE);
	}

	/**
	 * Resolves a single toggle against its detected value.
	 *
	 * @param toggle   the toggle
	 * @param detected the live-detected value
	 * @return {@code true} when the modifier is active
	 */
	public static boolean resolve(ModifierToggle toggle, boolean detected)
	{
		switch (toggle)
		{
			case ON:
				return true;
			case OFF:
				return false;
			case AUTO:
			default:
				return detected;
		}
	}

	/**
	 * Selects the modifiers from the registry that the state turns on.
	 *
	 * @param registry the loaded modifier registry
	 * @param state    the resolved active state
	 * @return the active modifiers, in registry order
	 */
	public static List<SuccessModifier> active(List<SuccessModifier> registry, ModifierState state)
	{
		List<SuccessModifier> out = new ArrayList<>();
		if (registry == null)
			return out;

		for (SuccessModifier m : registry)
		{
			if (isActive(m.getId(), state))
				out.add(m);
		}

		return out;
	}

	private static boolean isActive(String id, ModifierState state)
	{
		if (id == null)
			return false;

		switch (id)
		{
			case JEWELLERS_CHISEL:
			case JEWELLERS_DOUBLE:
				return state.isJewellersChisel();
			case COOKING_GAUNTLETS:
				return state.isCookingGauntlets();
			case COOKING_CAPE:
				return state.isCookingCape();
			case HOSIDIUS_EASY:
				return state.isHosidiusEasy();
			case HOSIDIUS_ELITE:
				return state.isHosidiusElite();
			default:
				return false;
		}
	}

	/**
	 * The human-readable active-modifier summary for the panel status line.
	 *
	 * @param state the resolved active state
	 * @return a comma-separated list of active modifiers, or "none"
	 */
	public static String statusText(ModifierState state)
	{
		List<String> parts = new ArrayList<>();
		if (state.isJewellersChisel())
			parts.add("Jeweller's chisel");

		if (state.isCookingGauntlets())
			parts.add("Cooking gauntlets");

		if (state.isCookingCape())
			parts.add("Cooking cape");

		if (state.isHosidiusElite())
			parts.add("Hosidius elite");
		else if (state.isHosidiusEasy())
			parts.add("Hosidius easy");

		return parts.isEmpty() ? "none" : String.join(", ", parts);
	}
}
