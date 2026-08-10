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
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the bundled success data &mdash; scraped {@code success_params.json} (per-output success models),
 * curated {@code success_modifiers.json} (the modifier registry), and {@code failure_outputs.json}
 * (per-output failure items) &mdash; and merges the models and failure outcomes into recipes at load
 * time. Recipes with no scraped model keep their default {@link SuccessType#ALWAYS} (100%).
 */
@Slf4j
public class SuccessData
{
	private static final String PARAMS = "/success_params.json";
	private static final String MODIFIERS = "/success_modifiers.json";
	private static final String FAILURES = "/failure_outputs.json";
	private static final Gson GSON = new Gson();

	private final Map<Integer, SuccessModel> params;
	private final Map<Integer, FailureOutcome> failures;
	private final List<SuccessModifier> modifiers;

	private SuccessData(Map<Integer, SuccessModel> params, Map<Integer, FailureOutcome> failures,
			List<SuccessModifier> modifiers)
	{
		this.params = params;
		this.failures = failures;
		this.modifiers = modifiers;
	}

	/**
	 * Loads the success data from the bundled classpath resources.
	 *
	 * @return the loaded success data (empty maps/list on any failure)
	 */
	public static SuccessData bundled()
	{
		return from(resource(PARAMS), resource(FAILURES), resource(MODIFIERS));
	}

	/**
	 * Loads success data from arbitrary streams. Package-visible for testing against fixtures.
	 *
	 * @param paramsIn    the success-params JSON stream, or {@code null}
	 * @param failuresIn  the failure-outputs JSON stream, or {@code null}
	 * @param modifiersIn the success-modifiers JSON stream, or {@code null}
	 * @return the loaded success data
	 */
	static SuccessData from(InputStream paramsIn, InputStream failuresIn, InputStream modifiersIn)
	{
		Map<Integer, SuccessModel> params = readMap(paramsIn,
				new TypeToken<Map<String, SuccessModel>>()
				{
				}.getType());
		Map<Integer, FailureOutcome> failures = readMap(failuresIn,
				new TypeToken<Map<String, FailureOutcome>>()
				{
				}.getType());
		return new SuccessData(params, failures, readModifiers(modifiersIn));
	}

	private static InputStream resource(String name)
	{
		return SuccessData.class.getResourceAsStream(name);
	}

	/**
	 * Reads a JSON object of {@code "<itemId>": value} into an id-keyed map.
	 *
	 * @param in        the JSON stream, or {@code null}
	 * @param mapType   the {@code Map<String, T>} type token
	 * @param <T>       the value type
	 * @return the id-keyed map, empty on failure
	 */
	private static <T> Map<Integer, T> readMap(InputStream in, Type mapType)
	{
		Map<Integer, T> out = new HashMap<>();
		if (in == null)
			return out;

		try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			Map<String, T> raw = GSON.fromJson(r, mapType);
			if (raw == null)
				return out;

			for (Map.Entry<String, T> e : raw.entrySet())
			{
				try
				{
					out.put(Integer.parseInt(e.getKey()), e.getValue());
				}
				catch (NumberFormatException ignored)
				{
					// Skip a non-numeric key rather than failing the whole load.
				}
			}
		}
		catch (Exception e)
		{
			log.error("failed to load success data", e);
		}

		return out;
	}

	private static List<SuccessModifier> readModifiers(InputStream in)
	{
		if (in == null)
			return Collections.emptyList();

		try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			SuccessModifier[] arr = GSON.fromJson(r, SuccessModifier[].class);
			List<SuccessModifier> list = new ArrayList<>();
			if (arr != null)
				for (SuccessModifier m : arr)
					if (m != null && m.getEffect() != null)
						list.add(m);

			return list;
		}
		catch (Exception e)
		{
			log.error("failed to load success_modifiers.json", e);
			return Collections.emptyList();
		}
	}

	/**
	 * The full registry of success modifiers (which are active is decided at evaluation time).
	 *
	 * @return the loaded modifiers
	 */
	public List<SuccessModifier> modifiers()
	{
		return modifiers;
	}

	/**
	 * Returns a copy of a recipe with its success model and failure outcome merged from the scraped
	 * data, or the same recipe unchanged when there is nothing to merge.
	 *
	 * @param r the recipe to enrich
	 * @return the enriched recipe (or {@code r} unchanged)
	 */
	public Recipe enrich(Recipe r)
	{
		Integer outId = r.primaryOutputId();
		if (outId == null)
			return r;

		SuccessModel model = r.getSuccess();
		SuccessModel scraped = params.get(outId);
		if (scraped != null)
			model = fillReqLevel(scraped, r);

		FailureOutcome fail = r.getOnFailure();
		FailureOutcome scrapedFail = failures.get(outId);
		if (scrapedFail != null)
			fail = scrapedFail;

		if (model == r.getSuccess() && fail == r.getOnFailure())
			return r;

		return new Recipe(r.getRecipeId(), r.getOutputs(), r.getInputs(), r.getTools(), r.getSkills(),
				r.getTicks(), r.getTicksNote(), r.isMembers(), r.getFacility(), r.getRequirements(),
				model, fail);
	}

	/**
	 * Enriches every recipe in a list.
	 *
	 * @param recipes the recipes to enrich
	 * @return a new list of enriched recipes
	 */
	public List<Recipe> enrichAll(List<Recipe> recipes)
	{
		List<Recipe> out = new ArrayList<>(recipes.size());
		for (Recipe r : recipes)
			out.add(enrich(r));

		return out;
	}

	/**
	 * Fills a BURN model's {@code reqLevel} from the recipe's primary skill (the scraper leaves it null).
	 *
	 * @param m the scraped model
	 * @param r the recipe it belongs to
	 * @return the model with reqLevel filled for BURN, otherwise unchanged
	 */
	private static SuccessModel fillReqLevel(SuccessModel m, Recipe r)
	{
		if (m.getType() != SuccessType.BURN || m.getReqLevel() != null)
			return m;

		SkillReq skill = r.primarySkill();
		int req = skill == null ? 1 : skill.getLevel();
		return new SuccessModel(SuccessType.BURN, m.getLow(), m.getHigh(), m.getStopLevel(),
				m.getGauntletStopLevel(), req, m.getFixedRate());
	}
}
