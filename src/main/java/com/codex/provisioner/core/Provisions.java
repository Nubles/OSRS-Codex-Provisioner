package com.codex.provisioner.core;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Everything the Provisioner keeps for one account: the bank as last seen, the kit and number of
 * trips being provisioned for, the product picked for each supply, the player's levels as last
 * seen, and how much each step had to go at its most, so progress bars have a fixed end.
 */
public class Provisions
{
	public static final int DEFAULT_TRIPS = 10;

	private Map<Integer, Long> bank = new HashMap<>();
	private long bankSeen;
	private String kit;
	private int trips = DEFAULT_TRIPS;
	private Map<String, Integer> choices = new HashMap<>();
	private Map<Integer, Long> baselines = new HashMap<>();
	private Map<String, Integer> levels = new HashMap<>();

	public Map<Integer, Long> getBank()
	{
		return bank;
	}

	public long getBankSeen()
	{
		return bankSeen;
	}

	public boolean hasBank()
	{
		return bankSeen > 0;
	}

	public void observeBank(Map<Integer, Long> items, long now)
	{
		bank = new HashMap<>(items);
		bankSeen = now;
	}

	/** The bank as last seen and what is carried, together. */
	public Map<Integer, Long> holdings(Map<Integer, Long> carried)
	{
		Map<Integer, Long> all = new HashMap<>(bank);
		carried.forEach((id, q) -> all.merge(id, q, Long::sum));
		return all;
	}

	public String getKit()
	{
		return kit;
	}

	/** Picks the kit to provision for; a new kit starts its progress afresh. */
	public void setKit(String id)
	{
		if (id != null && !id.equals(kit))
		{
			kit = id;
			baselines.clear();
		}
	}

	public int getTrips()
	{
		return trips;
	}

	public void setTrips(int value)
	{
		value = Math.max(1, Math.min(1000, value));
		if (value != trips)
		{
			trips = value;
			baselines.clear();
		}
	}

	public Map<String, Integer> getChoices()
	{
		return choices;
	}

	public void choose(String supply, int product)
	{
		Integer old = choices.put(supply, product);
		if (old == null || old != product)
		{
			baselines.clear();
		}
	}

	/** Starts every progress bar again from what is still to do. */
	public void restart()
	{
		baselines.clear();
	}

	public Map<Skill, Integer> levels()
	{
		Map<Skill, Integer> out = new HashMap<>();
		for (Map.Entry<String, Integer> e : levels.entrySet())
		{
			try
			{
				out.put(Skill.valueOf(e.getKey()), e.getValue());
			}
			catch (IllegalArgumentException ignored)
			{
				// A skill from a newer client; not one any recipe asks for.
			}
		}
		return out;
	}

	/** @return true when a level changed */
	public boolean observeLevels(Map<Skill, Integer> seen)
	{
		boolean changed = false;
		for (Map.Entry<Skill, Integer> e : seen.entrySet())
		{
			Integer old = levels.put(e.getKey().name(), e.getValue());
			changed |= old == null || !old.equals(e.getValue());
		}
		return changed;
	}

	/** Works out the chain and remembers the most each step has had to go. */
	public Chain plan(Kit kit, Map<String, Double> perTrip, Map<Integer, Long> carried)
	{
		Chain chain = Chain.build(kit, perTrip, trips, choices, holdings(carried), levels(), baselines);
		Map<Integer, Long> next = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			if (s.getBaseline() > 0)
			{
				next.put(s.getItem(), s.getBaseline());
			}
		}
		baselines = next;
		return chain;
	}

	/** Restores what saved data can leave out. */
	public Provisions repair()
	{
		if (bank == null)
		{
			bank = new HashMap<>();
		}
		if (choices == null)
		{
			choices = new HashMap<>();
		}
		if (baselines == null)
		{
			baselines = new HashMap<>();
		}
		if (levels == null)
		{
			levels = new HashMap<>();
		}
		if (trips <= 0)
		{
			trips = DEFAULT_TRIPS;
		}
		return this;
	}
}
