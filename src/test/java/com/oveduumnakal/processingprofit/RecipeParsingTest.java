/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 */
package com.oveduumnakal.processingprofit;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Verifies real recipe JSON from the OSRS wiki recipe bucket parses into the recipe model. */
public class RecipeParsingTest
{
	private static final Gson GSON = new Gson();

	private static final String DIAMOND_BRACELET =
			"{\"ticks\":\"3\","
			+ "\"materials\":["
			+ "{\"quantity\":\"1\",\"name\":\"Diamond\",\"image\":\"Diamond.png\"},"
			+ "{\"quantity\":\"1\",\"name\":\"Gold bar\",\"image\":\"Gold bar.png\"}],"
			+ "\"facilities\":\"Furnace\",\"tools\":\"Bracelet mould\","
			+ "\"skills\":[{\"experience\":\"95\",\"level\":\"58\","
			+ "\"name\":\"Crafting\",\"boostable\":\"Yes\"}],"
			+ "\"members\":true,"
			+ "\"output\":{\"cost\":1895,\"quantity\":\"1\","
			+ "\"name\":\"Diamond bracelet\",\"image\":\"Diamond bracelet.png\"}}";

	@Test
	public void parsesOutputMaterialsAndSkill()
	{
		BucketRecipe recipe = RecipeParser.parse(new JsonPrimitive(DIAMOND_BRACELET))
				.orElseThrow(AssertionError::new);
		assertEquals("Diamond bracelet", recipe.getOutput().getName());
		assertEquals(1, recipe.getOutput().getQuantity());

		List<RecipeItem> materials = recipe.getMaterials();
		assertEquals(2, materials.size());
		assertEquals("Diamond", materials.get(0).getName());
		assertEquals("Gold bar", materials.get(1).getName());

		List<RecipeSkill> skills = recipe.getSkills();
		assertEquals(1, skills.size());

		RecipeSkill skill = skills.get(0);
		assertEquals("Crafting", skill.getName());
		assertEquals(58, skill.getLevel());
		assertEquals(Integer.valueOf(95), skill.getExperience());
		assertTrue(skill.isBoostable());
		assertTrue(recipe.isMembers());
	}

	@Test
	public void parsesFullBucketResponse()
	{
		String body = "{\"bucket\":[{\"production_json\":" + GSON.toJson(DIAMOND_BRACELET) + "}]}";
		JsonObject root = GSON.fromJson(body, JsonObject.class);
		List<BucketRecipe> recipes = BucketRecipeRepository.parseResponse(root);
		assertEquals(1, recipes.size());

		BucketRecipe first = recipes.get(0);
		assertEquals("Diamond bracelet", first.getOutput().getName());
	}

	@Test
	public void skipsRecipeWithNoOutput()
	{
		JsonPrimitive raw = new JsonPrimitive("{\"materials\":[],\"skills\":[],\"output\":\"\"}");
		assertFalse(RecipeParser.parse(raw).isPresent());
	}
}
