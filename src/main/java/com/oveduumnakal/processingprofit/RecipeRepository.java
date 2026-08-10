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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads and indexes the bundled processing recipe catalog from the {@code /recipes.json} resource
 * shipped in the jar. No network access: recipes are extracted at build time (see {@code tools/}) and
 * only read here. Indexes recipes by primary output item id and by primary skill for fast lookup.
 *
 * <p>Loading is defensive: any failure logs and leaves the repository empty rather than throwing.
 */
@Slf4j
public class RecipeRepository
{
	private static final String RESOURCE = "/recipes.json";
	private static final Gson GSON = new Gson();

	private final Map<Integer, List<Recipe>> byOutput = new HashMap<>();
	private final Map<String, List<Recipe>> bySkill = new HashMap<>();
	private List<Recipe> all = Collections.emptyList();

	/**
	 * Loads the bundled {@code recipes.json} from the classpath and rebuilds the indexes.
	 */
	public void load()
	{
		load(getClass().getResourceAsStream(RESOURCE));
	}

	/**
	 * Loads recipes from an arbitrary stream and rebuilds the indexes. Package-visible for testing
	 * against a fixture. A {@code null} or malformed stream leaves the repository empty.
	 *
	 * <p>A future enhancement may merge a user override file from the RuneLite profile directory here.
	 *
	 * @param in the JSON stream to read, or {@code null}
	 */
	void load(InputStream in)
	{
		if (in == null)
		{
			log.error("recipes.json resource not found on the classpath");
			all = Collections.emptyList();
			index();
			return;
		}

		try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Recipe[] loaded = GSON.fromJson(r, Recipe[].class);
			all = loaded == null ? Collections.emptyList() : Arrays.asList(loaded);
		}
		catch (Exception e)
		{
			log.error("failed to load recipes.json", e);
			all = Collections.emptyList();
		}

		index();
	}

	/**
	 * Rebuilds the by-output and by-skill indexes from the current recipe list.
	 */
	private void index()
	{
		byOutput.clear();
		bySkill.clear();
		for (Recipe rec : all)
		{
			Integer outputId = rec.primaryOutputId();
			if (outputId != null)
				byOutput.computeIfAbsent(outputId, k -> new ArrayList<>()).add(rec);

			SkillReq skill = rec.primarySkill();
			if (skill != null && skill.getSkill() != null)
				bySkill.computeIfAbsent(skill.getSkill(), k -> new ArrayList<>()).add(rec);
		}
	}

	/**
	 * All loaded recipes.
	 *
	 * @return an immutable view of every loaded recipe
	 */
	public List<Recipe> all()
	{
		return all;
	}

	/**
	 * Recipes whose primary output is a given item.
	 *
	 * @param itemId the output item id
	 * @return the matching recipes, or an empty list
	 */
	public List<Recipe> forOutput(int itemId)
	{
		return byOutput.getOrDefault(itemId, Collections.emptyList());
	}

	/**
	 * Recipes whose primary skill matches a given name.
	 *
	 * @param skill the skill name (e.g. "Smithing")
	 * @return the matching recipes, or an empty list
	 */
	public List<Recipe> forSkill(String skill)
	{
		return bySkill.getOrDefault(skill, Collections.emptyList());
	}
}
