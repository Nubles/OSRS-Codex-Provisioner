package com.codex.provisioner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(CodexProvisionerConfig.GROUP)
public interface CodexProvisionerConfig extends Config
{
	String GROUP = "codexprovisioner";

	@ConfigItem(
		keyName = "plaque",
		name = "Step plaque",
		description = "Show a small plaque in the game view with the step in hand and how many are left",
		position = 0
	)
	default boolean plaque()
	{
		return true;
	}

	@ConfigItem(
		keyName = "announce",
		name = "Announce finished steps",
		description = "When a step is done, say so in the chat box along with what comes next",
		position = 1
	)
	default boolean announce()
	{
		return true;
	}

	@ConfigItem(
		keyName = "measured",
		name = "Plan from measured trips",
		description = "When Codex Quartermaster has measured a kit over a couple of trips, plan with what you really used instead of the kit's own amounts",
		position = 2
	)
	default boolean measured()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bankMarks",
		name = "Mark what to take out",
		description = "With the bank open, outline what the step in hand needs for one inventory, with how many of each, and its tools",
		position = 3
	)
	default boolean bankMarks()
	{
		return true;
	}
}
