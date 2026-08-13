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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A broad class of intermediate crafting materials the make-or-buy tree can be told to always buy rather
 * than break down further &mdash; the "force-buy by class" tree filter. Membership is decided by matching
 * an item's name against class-specific keywords ({@link #matches(String)}), since the recipe data has no
 * category field; the keyword sets are deliberately conservative so the classes read the way a player
 * expects ("bars", "planks", "gems").
 */
public enum MaterialClass
{
	/** Smithing bars (bronze, iron, steel, gold, mithril, adamant, rune, …). */
	BAR("Bars", Collections.singletonList("bar")),

	/** Sawmill planks (regular, oak, teak, mahogany). */
	PLANK("Planks", Collections.singletonList("plank")),

	/** Cut and uncut gems (sapphire, emerald, ruby, diamond, dragonstone, opal, jade, topaz). */
	GEM("Gems", Arrays.asList("sapphire", "emerald", "ruby", "diamond", "dragonstone", "opal", "jade",
			"topaz"));

	private final String label;
	private final List<String> keywords;

	MaterialClass(String label, List<String> keywords)
	{
		this.label = label;
		this.keywords = keywords;
	}

	/**
	 * The human-readable class name shown on the filter checkbox.
	 *
	 * @return the display label
	 */
	public String label()
	{
		return label;
	}

	/**
	 * Whether an item name belongs to this class. Case-insensitive substring match against the class
	 * keywords; a {@code null} name never matches.
	 *
	 * @param name the item name to test
	 * @return {@code true} if the name matches any of the class keywords
	 */
	public boolean matches(String name)
	{
		if (name == null)
			return false;

		String lower = name.toLowerCase();
		for (String keyword : keywords)
		{
			if (lower.contains(keyword))
				return true;
		}

		return false;
	}
}
