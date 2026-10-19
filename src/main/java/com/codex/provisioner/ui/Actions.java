package com.codex.provisioner.ui;

/** What the panel asks the plugin to do. */
public interface Actions
{
	void kit(String kitId);

	void trips(int trips);

	void choose(String supply, int product);

	/** Buy a supply finished on the Grand Exchange, or go back to making it. */
	void buy(String supply, boolean buy);

	void restart();
}
