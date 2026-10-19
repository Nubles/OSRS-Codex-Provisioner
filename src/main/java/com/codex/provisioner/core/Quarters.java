package com.codex.provisioner.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What Codex Quartermaster keeps for the account: the kits the player saved, the kit they last
 * chose, and what each kit has been measured using on real trips. The Provisioner only reads it;
 * without Quartermaster it works from the presets alone.
 *
 * <p>Quartermaster publishes its kits in a documented shape under {@link #KITS_KEY}, which only
 * grows. Its own {@link #QUARTERS_KEY} entry may change between versions, so it is read only for
 * what the published one leaves out (how many trips were measured, and the potion storage), and
 * on its own only when Quartermaster is too old to publish.
 */
public class Quarters
{
	/** Quartermaster's config group, read and never written. */
	public static final String GROUP = "codexquartermaster";
	/** The published kits: {"version":1,"active":id,"kits":[{id,name,preset,needs:[{supply,perTrip,measured}]}]}. */
	public static final String KITS_KEY = "kitsV1";
	/** Quartermaster's own saved state. */
	public static final String QUARTERS_KEY = "quarters";

	/**
	 * The most of a supply one trip is taken to use. A bigger figure is damage, and read as it
	 * was it overflowed into a kit needing less than nothing.
	 */
	static final double MOST = 1_000_000;

	/** Trips before Quartermaster trusts its measured amounts; the Provisioner does the same. */
	static final int TRUSTED = 2;

	private List<Kit> kits = new ArrayList<>();
	private String active;
	private Map<String, Usage> usage;
	/** Potion storage doses as Quartermaster last read them, by 1-dose item. */
	private Map<Integer, Long> stored;

	static final class Usage
	{
		private int trips;
		private Map<String, Double> perTrip;
	}

	public static Quarters none()
	{
		return new Quarters();
	}

	/**
	 * Reads what Quartermaster saved, the published kits first.
	 *
	 * @param kitsV1 the {@link #KITS_KEY} entry, or null
	 * @param quarters the {@link #QUARTERS_KEY} entry, or null
	 */
	public static Quarters read(Gson gson, String kitsV1, String quarters)
	{
		Quarters old = null;
		try
		{
			old = quarters == null || quarters.trim().isEmpty() ? null : gson.fromJson(quarters, Quarters.class);
		}
		catch (RuntimeException e)
		{
			// Quartermaster's own entry may change shape between versions; read as none.
			old = null;
		}
		JsonObject book = object(gson, kitsV1);
		if (book == null || !(book.get("kits") instanceof JsonArray))
		{
			return old == null ? none() : old;
		}
		Quarters q = new Quarters();
		// Older Quartermasters published the preset they showed on a fresh install as if it were
		// chosen; their own entry names only a kit the player really picked, so it has the say.
		q.active = old != null ? old.active : string(book, "active");
		q.stored = old == null ? null : old.stored;
		q.usage = new HashMap<>();
		for (JsonElement e : book.getAsJsonArray("kits"))
		{
			if (!e.isJsonObject())
			{
				continue;
			}
			JsonObject kit = e.getAsJsonObject();
			String id = string(kit, "id");
			String name = string(kit, "name");
			if (id == null || name == null)
			{
				continue;
			}
			List<Need> needs = new ArrayList<>();
			Map<String, Double> measured = new HashMap<>();
			JsonElement lines = kit.get("needs");
			if (lines instanceof JsonArray)
			{
				for (JsonElement l : lines.getAsJsonArray())
				{
					if (!l.isJsonObject())
					{
						continue;
					}
					JsonObject line = l.getAsJsonObject();
					String supply = string(line, "supply");
					Double perTrip = number(line, "perTrip");
					if (supply == null || perTrip == null)
					{
						continue;
					}
					needs.add(new Need(supply, (int) Math.round(Math.max(0, Math.min(MOST, perTrip)))));
					Double used = number(line, "measured");
					if (used != null && used >= 0 && used <= MOST)
					{
						measured.put(supply, used);
					}
				}
			}
			boolean preset = kit.get("preset") != null && kit.get("preset").isJsonPrimitive() && kit.get("preset").getAsBoolean();
			if (!preset)
			{
				q.kits.add(new Kit(id, name, needs, false));
			}
			if (!measured.isEmpty())
			{
				// Published only once trusted; the trip count itself is Quartermaster's own detail.
				Usage u = new Usage();
				u.trips = Math.max(TRUSTED, old == null ? 0 : old.measuredTrips(id));
				u.perTrip = measured;
				q.usage.put(id, u);
			}
		}
		return q;
	}

	private static JsonObject object(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
		}
		catch (JsonParseException | IllegalStateException e)
		{
			return null;
		}
	}

	private static String string(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
	}

	private static Double number(JsonObject o, String key)
	{
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
		{
			return null;
		}
		double d = e.getAsDouble();
		return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
	}

	/** The player's own kits, then the presets. */
	public List<Kit> allKits()
	{
		List<Kit> all = new ArrayList<>();
		if (kits != null)
		{
			for (Kit k : kits)
			{
				if (k != null && k.getId() != null && k.getName() != null)
				{
					all.add(k.repair());
				}
			}
		}
		all.addAll(Presets.all());
		return all;
	}

	public boolean hasSavedKits()
	{
		return kits != null && !kits.isEmpty();
	}

	/** The kit last chosen in Quartermaster, or null. */
	public String getActive()
	{
		return active;
	}

	/** What a kit has really used a trip, once measured over enough trips; empty otherwise. */
	public Map<String, Double> measured(String kitId)
	{
		Usage u = usage == null ? null : usage.get(kitId);
		if (u == null || u.trips < TRUSTED || u.perTrip == null)
		{
			return Collections.emptyMap();
		}
		// Quartermaster's own entry is read as it stands, so a damaged figure is left out here.
		Map<String, Double> out = new HashMap<>();
		u.perTrip.forEach((supply, used) ->
		{
			if (supply != null && used != null && used >= 0 && used <= MOST)
			{
				out.put(supply, used);
			}
		});
		return Collections.unmodifiableMap(out);
	}

	/** The potion storage as Quartermaster last read it, or null without Quartermaster. */
	public Map<Integer, Long> getStored()
	{
		if (stored == null)
		{
			return null;
		}
		Map<Integer, Long> out = new HashMap<>();
		stored.forEach((id, doses) ->
		{
			if (id != null && doses != null && doses > 0)
			{
				out.put(id, Json.count(doses));
			}
		});
		return out;
	}

	public int measuredTrips(String kitId)
	{
		Usage u = usage == null ? null : usage.get(kitId);
		return u == null ? 0 : u.trips;
	}
}
