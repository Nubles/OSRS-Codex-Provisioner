package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Experience;
import net.runelite.api.Skill;

/**
 * What working through the rest of a chain trains: the experience every open step gives, skill by
 * skill, and the level each skill will stand at once it is all done. Locked steps count too, since
 * the chain cannot be finished without them.
 */
public final class Training
{
	/** One skill's share of the chain. */
	public static final class Gain
	{
		private final Skill skill;
		private final double xp;
		private final long before;

		Gain(Skill skill, double xp, long before)
		{
			this.skill = skill;
			this.xp = xp;
			this.before = before;
		}

		public Skill getSkill()
		{
			return skill;
		}

		/** Experience the chain's open steps give in this skill. */
		public double getXp()
		{
			return xp;
		}

		/** The skill's experience now. */
		public long getBefore()
		{
			return before;
		}

		public int levelBefore()
		{
			return level(before);
		}

		public int levelAfter()
		{
			return level(before + Math.round(xp));
		}

		/** How far through the level after the chain the skill will be, 0 to 1; 1 at 99. */
		public double progressAfter()
		{
			int level = levelAfter();
			if (level >= Experience.MAX_REAL_LEVEL)
			{
				return 1;
			}
			long at = before + Math.round(xp);
			long low = Experience.getXpForLevel(level);
			long high = Experience.getXpForLevel(level + 1);
			return high <= low ? 1 : Math.max(0, Math.min(1, (at - low) / (double) (high - low)));
		}

		private static int level(long xp)
		{
			return Experience.getLevelForXp((int) Math.max(0, Math.min(Experience.MAX_SKILL_XP, xp)));
		}
	}

	private Training()
	{
	}

	/**
	 * @param xp each skill's experience now; a skill left out is taken from its level, or as new
	 * @param levels the levels as last seen, for skills whose experience is not known
	 * @return each skill trained, the most experience first
	 */
	public static List<Gain> of(Chain chain, Map<Skill, Long> xp, Map<Skill, Integer> levels)
	{
		Map<Skill, Double> gained = new EnumMap<>(Skill.class);
		for (Step s : chain.getSteps())
		{
			Recipe r = s.getRecipe();
			if (s.getState() == Step.State.DONE || r.getXpSkill() == null || r.getXp() <= 0 || r.getVerb() == Verb.BUY)
			{
				continue;
			}
			long crafts = (s.getNet() + r.getMakes() - 1) / r.getMakes();
			gained.merge(r.getXpSkill(), crafts * r.getXp(), Double::sum);
		}
		List<Gain> out = new ArrayList<>();
		for (Map.Entry<Skill, Double> e : gained.entrySet())
		{
			// Experience read before the game sent it can lag the level; the level's own floor is the least it can be.
			Integer level = levels.get(e.getKey());
			long floor = Experience.getXpForLevel(Math.max(1, Math.min(Experience.MAX_REAL_LEVEL, level == null ? 1 : level)));
			Long now = xp.get(e.getKey());
			out.add(new Gain(e.getKey(), e.getValue(), now == null ? floor : Math.max(floor, now)));
		}
		out.sort((a, b) -> Double.compare(b.xp, a.xp));
		return out;
	}
}
