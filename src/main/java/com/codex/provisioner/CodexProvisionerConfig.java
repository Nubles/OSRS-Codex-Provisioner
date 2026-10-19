package com.codex.provisioner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(CodexProvisionerConfig.GROUP)
public interface CodexProvisionerConfig extends Config
{
	String GROUP = "codexprovisioner";

	@ConfigItem(
		keyName = "plaque",
		name = "Step plaque",
		description = "Show a small plaque in the game view with the step in hand and how many are left to make",
		position = 0
	)
	default boolean plaque()
	{
		return true;
	}

	@ConfigItem(
		keyName = "announce",
		name = "Steps in chat",
		description = "When a step is done, add a line to the chat box saying so and what comes next",
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
		name = "Mark items in the bank",
		description = "With the bank open, outline what the step in hand needs for one inventory, with how many of each, and its tools",
		position = 3
	)
	default boolean bankMarks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "herbPatches",
		name = "Herb patches",
		description = "How many herb patches you run, so herbs still to grow are told as herb runs and seeds; 0 leaves them out",
		position = 4
	)
	@Range(max = 15)
	default int herbPatches()
	{
		return 7;
	}

	@ConfigItem(
		keyName = "herbsPerPatch",
		name = "Herbs per patch",
		description = "How many herbs one patch gives you on average: about 8 with ultracompost and magic secateurs at a high Farming level, fewer without",
		position = 5
	)
	@Range(min = 3, max = 15)
	default int herbsPerPatch()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "lowStock",
		name = "Low stock warning",
		description = "When you open the bank with supplies for fewer trips of your kit than this, add a line to the chat box naming the supply that runs out first and how many more would cover it; 0 turns it off",
		position = 6
	)
	@Range(max = 50)
	default int lowStock()
	{
		return 3;
	}
}
