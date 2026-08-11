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

import lombok.Value;

/**
 * One raw material to buy for a shopping list: the item {@code id} and {@code name}, the total
 * {@code needed} across every request, how much is already {@code held} (subtracted in hybrid mode),
 * the resulting {@code toBuy} shortfall, the {@code unitPrice} (buy price, {@link PriceLookup#UNKNOWN}
 * when unpriced), the {@code totalCost} for the shortfall, the 4-hour GE {@code buyLimit} ({@code 0}
 * when unlimited/unknown), and the number of buy {@code windows} the limit forces.
 */
@Value
public class ShoppingLine
{
	int itemId;
	String name;
	long needed;
	long held;
	long toBuy;
	long unitPrice;
	long totalCost;
	int buyLimit;
	int windows;

	/**
	 * Whether a buy price is known for this line.
	 *
	 * @return {@code true} when {@code unitPrice} is not {@link PriceLookup#UNKNOWN}
	 */
	public boolean isPriced()
	{
		return unitPrice != PriceLookup.UNKNOWN;
	}
}
