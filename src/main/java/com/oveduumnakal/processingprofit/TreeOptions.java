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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import lombok.Value;

/**
 * The tree-shape overrides the pop-out recipe tree's filter checkboxes feed into {@link ChainTreeBuilder}:
 * which items are forced to buy rather than break down (by specific id or by {@link MaterialClass}), and
 * how deep the tree may expand. "Exclude already-owned" is not represented here &mdash; it is applied by
 * passing the real held map versus an empty one at build time. A pure value object.
 */
@Value
public class TreeOptions
{
	Set<Integer> forceBuyIds;
	Set<MaterialClass> forceBuyClasses;
	int maxDepth;

	/**
	 * The default options: nothing forced to buy, expanding to {@link ChainTreeBuilder#MAX_DEPTH}. Used by
	 * the detail-view tree, which has no filter controls.
	 *
	 * @return the unfiltered options
	 */
	public static TreeOptions defaults()
	{
		return new TreeOptions(Collections.emptySet(), Collections.emptySet(), ChainTreeBuilder.MAX_DEPTH);
	}

	/**
	 * Whether an item should be bought rather than expanded, either because its id is force-bought or its
	 * name matches a force-bought class.
	 *
	 * @param itemId the item id
	 * @param name   the item name
	 * @return {@code true} if the item is forced to buy
	 */
	public boolean forceBuy(int itemId, String name)
	{
		if (forceBuyIds.contains(itemId))
			return true;

		for (MaterialClass cls : forceBuyClasses)
		{
			if (cls.matches(name))
				return true;
		}

		return false;
	}

	/**
	 * A copy of these options with {@code itemId} added to or removed from the force-buy set, for the
	 * per-item buy toggle.
	 *
	 * @param itemId the item to toggle
	 * @param buy    {@code true} to force-buy it, {@code false} to allow making it
	 * @return the updated options
	 */
	public TreeOptions withForceBuy(int itemId, boolean buy)
	{
		Set<Integer> ids = new HashSet<>(forceBuyIds);
		if (buy)
			ids.add(itemId);
		else
			ids.remove(itemId);

		return new TreeOptions(ids, forceBuyClasses, maxDepth);
	}
}
