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
 * One node in the make-or-buy tree shown in the detail view: an input item ({@code itemId} +
 * {@code label}) and the {@code qty} the tree needs of it, scaled to the number of finished products.
 * The node is rendered one of three ways:
 *
 * <ul>
 *   <li><b>Covered</b> ({@code covered} is {@code true}) &mdash; the player already holds enough
 *       ({@code held >= qty}); shown struck through and not expanded, since nothing need be sourced.</li>
 *   <li><b>Made</b> ({@code viaSkill} is non-null) &mdash; cheaper to craft; {@code viaSkill} is the
 *       skill it is made with and {@code children} holds its recipe's inputs recursed the same way.
 *       {@code surplus} is how many extra units this step produces beyond what the tree consumes (a
 *       batch overshoot, also rolled into the tree's leftovers).</li>
 *   <li><b>Bought</b> ({@code viaBuy} is {@code true}, {@code viaSkill} null) &mdash; a leaf bought or
 *       gathered; {@code buyUnit} is the per-unit buy price ({@link PriceLookup#UNKNOWN} when unpriced
 *       or gathered).</li>
 * </ul>
 *
 * <p>{@code held} is how many units the player owns (partial holdings show against a still-sourced
 * node). {@code truncated} is {@code true} when a craft path existed but display recursion stopped at
 * the depth cap or a cycle. {@code tools} names the non-consumed tools/equipment a crafted step needs
 * (hammer, needle, chisel, …); it is empty for bought or covered leaves. A pure view-model built by
 * {@link ChainTreeBuilder}.
 */
@Value
public class ChainNode
{
	int itemId;
	String label;
	long qty;
	boolean viaBuy;
	String viaSkill;
	long buyUnit;
	long held;
	boolean covered;
	long surplus;
	boolean truncated;
	List<ChainNode> children;
	List<String> tools;
}
