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
 * The full, click-through breakdown behind one recipe's headline profit: the consumed input lines and
 * their total cost, the non-consumed {@code tools}, the output lines with their pre-tax gross and the GE
 * {@code tax}, the success-weighted {@code expectedNet} and the four throughput dimensions. When the
 * recipe is {@code failCapable} it also carries the success breakdown ({@code baseChance} at the level
 * &rarr; {@code finalChance} after {@code modifierNotes}, the {@code yieldMult}, and the
 * {@code failureLabel}). {@code breakEven} lists the highest per-unit price each input can reach while
 * the recipe still breaks even. {@code chainTree} is the recursive make-or-buy breakdown over the
 * inputs (the scaled tree plus the Total materials and Leftovers roll-ups).
 * {@code description} is the item's examine text (may be {@code null}). A pure view-model built by
 * {@link DetailBuilder}.
 */
@Value
public class RecipeDetail
{
	String product;
	int itemId;
	List<CostLine> inputs;
	List<String> tools;
	long inputCost;
	List<CostLine> outputs;
	long grossOutput;
	long tax;
	long expectedNet;
	long profitEach;
	double roi;
	boolean throughputKnown;
	long gpPerHour;
	double xpPerHour;
	double profitPerXp;
	int level;
	boolean failCapable;
	Double baseChance;
	Double finalChance;
	double yieldMult;
	List<String> modifierNotes;
	String failureLabel;
	List<CostLine> breakEven;
	boolean stale;
	ChainTree chainTree;
	String description;
}
