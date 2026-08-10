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

import java.util.Set;

import lombok.Value;

/**
 * An item, area, or diary tier that changes an action's success or yield (jeweller's chisel, cooking
 * gauntlets, Hosidius range, cooking cape). Loaded from {@code success_modifiers.json} and toggled by
 * config or auto-detection. The {@code skill} gate and {@code items} gate decide which recipes it
 * applies to; an empty {@code items} set means "all recipes in the skill".
 */
@Value
public class SuccessModifier
{
	String id;
	ModifierEffect effect;
	double value;
	String skill;
	Set<Integer> items;

	/**
	 * Whether this modifier applies to a recipe, per its skill and item gates.
	 *
	 * @param r the recipe under evaluation
	 * @return {@code true} when the modifier is in scope for the recipe
	 */
	public boolean applies(Recipe r)
	{
		SkillReq primary = r.primarySkill();
		if (skill != null && (primary == null || !skill.equalsIgnoreCase(primary.getSkill())))
			return false;

		if (items == null || items.isEmpty())
			return true;

		for (RecipeOutput o : r.getOutputs())
			if (o.getItemId() != null && items.contains(o.getItemId()))
				return true;

		for (ItemStack i : r.getInputs())
			if (i.getItemId() != null && items.contains(i.getItemId()))
				return true;

		return false;
	}
}
