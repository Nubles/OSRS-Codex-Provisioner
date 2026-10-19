package com.codex.provisioner.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import net.runelite.api.Experience;
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
	/** The bank as last seen came from Codex Bank Steward rather than the Provisioner's own look. */
	private boolean bankShared;
	private String kit;
	private int trips = DEFAULT_TRIPS;
	private Map<String, Integer> choices = new HashMap<>();
	private Map<Integer, Long> baselines = new HashMap<>();
	private Map<String, Integer> levels = new HashMap<>();
	/** Experience in each skill as last seen, for what the chain trains. */
	private Map<String, Long> xp = new HashMap<>();
	/** Potion doses in the bank's potion storage, by each potion's 1-dose item; null until first read. */
	private Map<Integer, Long> stored;
	/** Supplies bought finished on the Grand Exchange rather than made. */
	private Set<String> bought = new HashSet<>();
	/** An iron account, remembered so the panel stays right while logged out. */
	private boolean iron;
	private Pace pace = new Pace();
	private transient boolean baselinesChanged;

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
		bankShared = false;
	}

	/**
	 * Takes the bank another Codex plugin saw, when it saw it later than this one did. A minute's
	 * grace keeps the two from trading the same look back and forth when both saw it.
	 *
	 * @return true when it was taken
	 */
	public boolean adoptShared(SharedBank shared)
	{
		if (shared == null || shared.getSavedAt() <= bankSeen + 60_000)
		{
			return false;
		}
		bank = new HashMap<>(shared.getItems());
		bankSeen = shared.getSavedAt();
		bankShared = true;
		return true;
	}

	/** Whether the bank as last seen is the one Codex Bank Steward shared. */
	public boolean isBankShared()
	{
		return bankShared;
	}

	/** The bank as last seen, its potion storage, and what is carried, together. */
	public Map<Integer, Long> holdings(Map<Integer, Long> carried)
	{
		return holdings(carried, null);
	}

	/**
	 * @param fallback potion storage to count until the Provisioner has read it itself, such as
	 *     what Codex Quartermaster last saw; null for none
	 */
	public Map<Integer, Long> holdings(Map<Integer, Long> carried, Map<Integer, Long> fallback)
	{
		Map<Integer, Long> all = new HashMap<>(bank);
		carried.forEach((id, q) -> all.merge(id, q, Long::sum));
		Map<Integer, Long> doses = stored != null ? stored : fallback;
		if (doses != null)
		{
			// Filed under the 1-dose potion, one each, so they count as doses toward their supply.
			doses.forEach((id, q) -> all.merge(id, q, Long::sum));
		}
		return all;
	}

	/** @return true when the doses in potion storage changed */
	public boolean observeStorage(Map<Integer, Long> doses)
	{
		if (doses.equals(stored))
		{
			return false;
		}
		stored = new HashMap<>(doses);
		return true;
	}

	public Map<Integer, Long> getStored()
	{
		return stored;
	}

	public boolean isIron()
	{
		return iron;
	}

	public void setIron(boolean iron)
	{
		this.iron = iron;
	}

	/** Supplies bought finished; an iron account buys none, whatever was picked before. */
	public Set<String> getBought()
	{
		return iron ? Collections.emptySet() : bought;
	}

	/** Buys a supply finished instead of making it, or goes back to making it. */
	public void setBought(String supply, boolean buy)
	{
		if (buy ? bought.add(supply) : bought.remove(supply))
		{
			baselines.clear();
		}
	}

	public Pace getPace()
	{
		return pace;
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
			if (e.getValue() == null || e.getValue() <= 0)
			{
				// Not a real reading: a skill not sent yet, as during a world hop, would lock every step.
				continue;
			}
			Integer old = levels.put(e.getKey().name(), e.getValue());
			changed |= old == null || !old.equals(e.getValue());
		}
		return changed;
	}

	public Map<Skill, Long> xp()
	{
		Map<Skill, Long> out = new HashMap<>();
		for (Map.Entry<String, Long> e : xp.entrySet())
		{
			try
			{
				out.put(Skill.valueOf(e.getKey()), e.getValue());
			}
			catch (IllegalArgumentException ignored)
			{
				// A skill from a newer client.
			}
		}
		return out;
	}

	/**
	 * Takes in experience as the game sends it. A reading of nothing for a skill already known is
	 * a skill the game has not sent yet, not a real one, and is passed over.
	 *
	 * @return true when anything changed
	 */
	public boolean observeXp(Map<Skill, Long> seen)
	{
		boolean changed = false;
		for (Map.Entry<Skill, Long> e : seen.entrySet())
		{
			if (e.getValue() <= 0 && xp.containsKey(e.getKey().name()))
			{
				continue;
			}
			Long old = xp.put(e.getKey().name(), e.getValue());
			changed |= old == null || !old.equals(e.getValue());
		}
		return changed;
	}

	/** Works out the chain and remembers the most each step has had to go. */
	public Chain plan(Kit kit, Map<String, Double> perTrip, Map<Integer, Long> carried)
	{
		return plan(kit, perTrip, carried, null);
	}

	/** @param fallback potion storage to count until it has been read here, or null */
	public Chain plan(Kit kit, Map<String, Double> perTrip, Map<Integer, Long> carried, Map<Integer, Long> fallback)
	{
		Chain chain = Chain.build(kit, perTrip, trips, choices, getBought(), holdings(carried, fallback), levels(), baselines);
		Map<Integer, Long> next = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			if (s.getBaseline() > 0)
			{
				next.put(s.getItem(), s.getBaseline());
			}
		}
		baselinesChanged = !next.equals(baselines);
		baselines = next;
		return chain;
	}

	/** Item counts with nothing below one or past a stack. */
	private static Map<Integer, Long> counts(Map<Integer, Long> in)
	{
		Map<Integer, Long> out = new HashMap<>();
		in.forEach((id, q) ->
		{
			if (id != null && id > 0 && q != null && q > 0)
			{
				out.put(id, Json.count(q));
			}
		});
		return out;
	}

	/** Whether the last plan moved a progress bar's end, which is worth saving. */
	public boolean baselinesChanged()
	{
		return baselinesChanged;
	}

	/** A saved provisions entry as read back: what could be kept, and whether anything was left out. */
	public static final class Loaded
	{
		/** What was read; fresh provisions when nothing could be. Never null. */
		public final Provisions provisions;
		/** True when the save was not read whole, so it is worth setting aside as it was. */
		public final boolean damaged;

		Loaded(Provisions provisions, boolean damaged)
		{
			this.provisions = provisions;
			this.damaged = damaged;
		}
	}

	/**
	 * Reads the saved provisions. A save that reads whole is repaired and kept; one that does not
	 * (a figure of the wrong kind or too big for its field, one bad entry in the bank, a save cut
	 * short) keeps every field and every entry that still reads, so one damaged figure never
	 * costs the bank, the choices or the levels.
	 *
	 * @param now the time now; a bank seen later than this is taken as seen now
	 */
	public static Loaded read(Gson gson, String saved, long now)
	{
		if (saved == null || saved.trim().isEmpty())
		{
			return new Loaded(new Provisions(), false);
		}
		try
		{
			Provisions whole = gson.fromJson(saved, Provisions.class);
			if (whole != null)
			{
				return new Loaded(whole.repair(now), false);
			}
		}
		catch (RuntimeException e)
		{
			// Read it field by field below.
		}
		Provisions p = new Provisions();
		JsonReader in = new JsonReader(new StringReader(saved));
		in.setLenient(true);
		try
		{
			if (in.peek() == JsonToken.BEGIN_OBJECT)
			{
				in.beginObject();
				while (in.hasNext())
				{
					String name = in.nextName();
					p.field(gson, name, gson.fromJson(in, JsonElement.class));
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			// A save cut short keeps every field before the cut.
		}
		return new Loaded(p.repair(now), true);
	}

	/** Sets one field from a damaged save, keeping each entry of it that reads. */
	private void field(Gson gson, String name, JsonElement value)
	{
		try
		{
			switch (name)
			{
				case "bank":
					bank = items(value);
					break;
				case "baselines":
					baselines = items(value);
					break;
				case "stored":
					stored = items(value);
					break;
				case "bankSeen":
					Long seen = Json.whole(value);
					bankSeen = seen == null ? 0 : seen;
					break;
				case "trips":
					Long t = Json.whole(value);
					trips = t == null ? DEFAULT_TRIPS : (int) Math.max(1, Math.min(1000, t));
					break;
				case "kit":
					kit = value.isJsonPrimitive() ? value.getAsString() : null;
					break;
				case "bankShared":
					bankShared = value.isJsonPrimitive() && value.getAsBoolean();
					break;
				case "iron":
					iron = value.isJsonPrimitive() && value.getAsBoolean();
					break;
				case "choices":
				case "levels":
					Map<String, Integer> small = new HashMap<>();
					named(value).forEach((k, v) -> small.put(k, (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v))));
					if ("choices".equals(name))
					{
						choices = small;
					}
					else
					{
						levels = small;
					}
					break;
				case "xp":
					xp = named(value);
					break;
				case "bought":
					bought = new HashSet<>();
					if (value.isJsonArray())
					{
						value.getAsJsonArray().forEach(e -> bought.add(e.isJsonPrimitive() ? e.getAsString() : null));
					}
					break;
				case "pace":
					pace = gson.fromJson(value, Pace.class);
					break;
				default:
					// A field a later version keeps, unknown here.
			}
		}
		catch (RuntimeException e)
		{
			// This field is lost; the rest are not.
		}
	}

	/** Item counts by id, every entry that reads. */
	private static Map<Integer, Long> items(JsonElement value)
	{
		Map<Integer, Long> out = new HashMap<>();
		named(value).forEach((k, v) ->
		{
			try
			{
				out.put(Integer.parseInt(k), v);
			}
			catch (NumberFormatException ignored)
			{
				// Not an item id.
			}
		});
		return out;
	}

	/** Whole numbers by name, every entry that reads. */
	private static Map<String, Long> named(JsonElement value)
	{
		Map<String, Long> out = new HashMap<>();
		if (value != null && value.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> e : value.getAsJsonObject().entrySet())
			{
				Long n = Json.whole(e.getValue());
				if (n != null)
				{
					out.put(e.getKey(), n);
				}
			}
		}
		return out;
	}

	/** Restores what saved data can leave out. */
	public Provisions repair()
	{
		return repair(Long.MAX_VALUE);
	}

	/**
	 * Restores what saved data can leave out, and sets right what damage can put in: counts below
	 * zero or past a stack, levels and experience past the game's own, a bank seen after now.
	 */
	Provisions repair(long now)
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
		if (xp == null)
		{
			xp = new HashMap<>();
		}
		if (bought == null)
		{
			bought = new HashSet<>();
		}
		pace = pace == null ? new Pace() : pace.repair();
		if (trips <= 0)
		{
			trips = DEFAULT_TRIPS;
		}
		trips = Math.min(1000, trips);
		bank = counts(bank);
		baselines = counts(baselines);
		stored = stored == null ? null : counts(stored);
		choices.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
		levels.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() <= 0);
		levels.replaceAll((k, v) -> Math.min(Experience.MAX_VIRT_LEVEL, v));
		xp.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null || e.getValue() < 0);
		xp.replaceAll((k, v) -> Math.min(Experience.MAX_SKILL_XP, v));
		bought.removeIf(b -> b == null || b.isEmpty());
		kit = kit == null || kit.isEmpty() ? null : kit;
		bankSeen = Math.max(0, Math.min(now, bankSeen));
		return this;
	}
}
