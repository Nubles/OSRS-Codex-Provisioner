package com.codex.provisioner.core;

import java.util.Map;
import net.runelite.api.Skill;

/**
 * A level a step asks for. A hard requirement stops the step until it is met; a soft one is only
 * a way in, such as the Farming level to grow a herb that monsters also drop.
 */
public final class Req
{
	private final Skill skill;
	private final int level;
	private final boolean soft;

	Req(Skill skill, int level, boolean soft)
	{
		this.skill = skill;
		this.level = level;
		this.soft = soft;
	}

	public Skill getSkill()
	{
		return skill;
	}

	public int getLevel()
	{
		return level;
	}

	public boolean isSoft()
	{
		return soft;
	}

	/** @return true when met, or when the player's levels are not known yet */
	public boolean met(Map<Skill, Integer> levels)
	{
		Integer have = levels.get(skill);
		return have == null || have >= level;
	}
}
