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

import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/** Entry point: wires the profit engine, price client, tooltip, and sidebar together. */
@Slf4j
@PluginDescriptor(
	name = "Processing Profit",
	description = "Find profitable item processing: live GE profit for multi-step conversion chains, "
		+ "with an alt-hover tooltip and a sidebar of what to make from your items or the best right now",
	tags = {"profit", "process", "processing", "craft", "recipe", "chain", "ge", "grand exchange",
		"live price", "wiki", "moneymaking", "skilling", "margin"}
)
public class ProcessingProfitPlugin extends Plugin
{
	@Inject
	private ProcessingProfitConfig config;

	@Override
	protected void startUp()
	{
		log.debug("Processing Profit started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("Processing Profit stopped");
	}

	@Provides
	ProcessingProfitConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ProcessingProfitConfig.class);
	}
}
