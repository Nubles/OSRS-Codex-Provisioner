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

	@ConfigItem(
		keyName = "herbPatches",
		name = "Herb patches",
		description = "How many herb patches you run, to turn herbs still to grow into herb runs and seeds. 0 leaves herbs untimed",
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
		description = "How many herbs one patch gives you on average. About 8 with ultracompost and magic secateurs at a high Farming level; fewer without them",
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
		description = "When you open the bank holding supplies for fewer trips of your kit than this, a chat line says so, which supply runs out first and how many more would cover it. 0 turns it off",
		position = 6
	)
	@Range(max = 50)
	default int lowStock()
	{
		return 3;
	}
}
