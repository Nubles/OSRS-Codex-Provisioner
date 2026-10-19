package com.codex.provisioner.core;

/** Where the Provisioner learns what an item costs on the Grand Exchange today. */
public interface Prices
{
	/** @return the item's price in coins, or 0 or less when it has none */
	long price(int itemId);
}
