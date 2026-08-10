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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies {@link RecipeRepository} loads and indexes recipes from a bundled fixture (and from the real
 * bundled resource) with no network access.
 */
public class RecipeRepositoryTest
{
	private static final String FIXTURE =
			"[{"
			+ "\"recipeId\":\"smithing:sample-bar\","
			+ "\"outputs\":[{\"itemId\":1,\"name\":\"Sample bar\",\"qty\":1},"
			+ "{\"itemId\":2,\"name\":\"Byproduct\",\"qty\":1}],"
			+ "\"inputs\":[{\"itemId\":3,\"name\":\"Sample ore\",\"qty\":1,\"consumed\":true}],"
			+ "\"tools\":[],\"skills\":[{\"skill\":\"Smithing\",\"level\":1,\"xp\":6.2}],"
			+ "\"members\":false,\"success\":{\"type\":\"ALWAYS\"}},"
			+ "{"
			+ "\"recipeId\":\"cooking:shark\","
			+ "\"outputs\":[{\"itemId\":385,\"name\":\"Shark\",\"qty\":1}],"
			+ "\"inputs\":[{\"itemId\":383,\"name\":\"Raw shark\",\"qty\":1,\"consumed\":true}],"
			+ "\"tools\":[],\"skills\":[{\"skill\":\"Cooking\",\"level\":80,\"xp\":210.0}],"
			+ "\"members\":true,\"success\":{\"type\":\"ALWAYS\"}}]";

	private static RecipeRepository loadFixture()
	{
		RecipeRepository repo = new RecipeRepository();
		InputStream in = new ByteArrayInputStream(FIXTURE.getBytes(StandardCharsets.UTF_8));
		repo.load(in);
		return repo;
	}

	@Test
	public void indexesByOutputAndSkill()
	{
		RecipeRepository repo = loadFixture();
		assertEquals(2, repo.all().size());

		List<Recipe> byBar = repo.forOutput(1);
		assertEquals(1, byBar.size());
		assertEquals("smithing:sample-bar", byBar.get(0).getRecipeId());

		assertEquals(1, repo.forSkill("Smithing").size());
		assertEquals(1, repo.forSkill("Cooking").size());
		assertTrue(repo.forSkill("Fishing").isEmpty());
		assertTrue(repo.forOutput(9999).isEmpty());
	}

	@Test
	public void emptyOnMissingResource()
	{
		RecipeRepository repo = new RecipeRepository();
		repo.load((InputStream) null);
		assertTrue(repo.all().isEmpty());
		assertTrue(repo.forSkill("Smithing").isEmpty());
	}

	@Test
	public void loadsBundledResource()
	{
		RecipeRepository repo = new RecipeRepository();
		repo.load();
		assertFalse(repo.all().isEmpty());
		assertFalse(repo.forSkill("Smithing").isEmpty());
	}
}
