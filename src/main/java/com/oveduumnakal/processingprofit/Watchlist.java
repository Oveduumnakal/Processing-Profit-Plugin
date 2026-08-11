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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The set of pinned recipe ids, in the order they were pinned. Persisted as a comma-separated string via
 * the RuneLite config (recipe ids never contain commas). Kept as a small mutable value so the plugin can
 * {@link #toggle(String)} a pin and re-{@link #format()} it for storage; rendering reads {@link #ids()}.
 */
public class Watchlist
{
	private final Set<String> ids;

	/**
	 * An empty watchlist.
	 */
	public Watchlist()
	{
		this.ids = new LinkedHashSet<>();
	}

	private Watchlist(Set<String> ids)
	{
		this.ids = ids;
	}

	/**
	 * Pins the id if absent, unpins it if present.
	 *
	 * @param recipeId the recipe id to toggle
	 * @return {@code true} if the id is now pinned, {@code false} if it was unpinned
	 */
	public boolean toggle(String recipeId)
	{
		if (recipeId == null || recipeId.isEmpty())
			return false;

		if (ids.remove(recipeId))
			return false;

		ids.add(recipeId);
		return true;
	}

	/**
	 * Whether a recipe id is pinned.
	 *
	 * @param recipeId the recipe id
	 * @return {@code true} when pinned
	 */
	public boolean contains(String recipeId)
	{
		return ids.contains(recipeId);
	}

	/**
	 * Whether nothing is pinned.
	 *
	 * @return {@code true} when the watchlist is empty
	 */
	public boolean isEmpty()
	{
		return ids.isEmpty();
	}

	/**
	 * The pinned recipe ids in pin order.
	 *
	 * @return an unmodifiable view of the pinned ids
	 */
	public Set<String> ids()
	{
		return Collections.unmodifiableSet(ids);
	}

	/**
	 * Serializes the watchlist for config storage.
	 *
	 * @return the pinned ids joined by commas (empty string when none)
	 */
	public String format()
	{
		return String.join(",", ids);
	}

	/**
	 * Parses a stored watchlist string, ignoring blank entries and de-duplicating while preserving order.
	 *
	 * @param csv the comma-separated ids, or {@code null}
	 * @return the parsed watchlist
	 */
	public static Watchlist parse(String csv)
	{
		Set<String> ids = new LinkedHashSet<>();
		if (csv != null)
		{
			for (String part : csv.split(","))
			{
				String id = part.trim();
				if (!id.isEmpty())
					ids.add(id);
			}
		}

		return new Watchlist(ids);
	}
}
