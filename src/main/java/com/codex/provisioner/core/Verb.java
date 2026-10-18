package com.codex.provisioner.core;

/** What the player does at a step, in the words the panel and the chat use. */
public enum Verb
{
	CATCH("Catch", "caught"),
	GATHER("Gather", "gathered"),
	BUY("Buy", "bought"),
	STOCK("Stock", "stocked"),
	FILL("Fill", "filled"),
	CLEAN("Clean", "cleaned"),
	CRUSH("Crush", "crushed"),
	MIX("Mix", "mixed"),
	DECANT("Decant", "decanted"),
	COOK("Cook", "cooked"),
	ENCHANT("Enchant", "enchanted");

	private final String label;
	private final String past;

	Verb(String label, String past)
	{
		this.label = label;
		this.past = past;
	}

	public String label()
	{
		return label;
	}

	public String past()
	{
		return past;
	}
}
