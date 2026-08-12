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

import java.util.List;

import lombok.Value;

/**
 * One node in the make-or-buy tree shown in the detail view: an input {@code label} and per-craft
 * {@code qty}, whether the cheapest source is to {@code viaBuy} it or craft it, and the three costs so
 * the view can highlight the chosen option and show the alternative &mdash; the {@code chosenCost}, the
 * {@code buyCost} and the {@code craftCost} (each {@code -1} when that option is unavailable). When the
 * cheapest source is to craft it, {@code children} holds that recipe's inputs recursed the same way;
 * {@code truncated} is {@code true} when a craft path existed but display recursion stopped at the depth
 * cap or a cycle. A pure view-model built by {@link ChainTreeBuilder}.
 */
@Value
public class ChainNode
{
	/** Cost sentinel for an unavailable buy/craft/chosen option. */
	public static final long UNAVAILABLE = -1L;

	String label;
	int qty;
	boolean viaBuy;
	long chosenCost;
	long buyCost;
	long craftCost;
	boolean truncated;
	List<ChainNode> children;
}
