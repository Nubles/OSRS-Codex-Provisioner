package com.codex.provisioner.core;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How long it takes this account to get somewhere, read from what Codex Wayfinder shares in the
 * RuneLite profile ({@code codexwayfinder.atlasV1}): every teleport the account owns with its
 * landing tile and seconds, and the player's own walking pace. Read-only; when Wayfinder is not
 * installed there is no atlas and callers fall back to tiles as the crow flies.
 *
 * <p>A trip is costed as the best of walking straight there from where the player stands, or
 * taking one teleport and walking on from where it lands. Walking only counts between tiles on the
 * same plane and the same side of the map (the surface, or the underground blocks above row
 * 4500), since there is no straight walk from a dungeon to the world above it.
 */
public final class Travel
{
	/** The config group and key Wayfinder saves the atlas under, in the RuneLite profile. */
	public static final String GROUP = "codexwayfinder";
	public static final String KEY = "atlasV1";
	/** The plugin message namespace Wayfinder listens on, and the messages it knows. */
	public static final String NAMESPACE = "codexwayfinder";
	public static final String ROUTE = "route";
	public static final String ROUTED = "routed";
	public static final String SHOW = "show";
	/** Wayfinder's plugin name, as its descriptor gives it. */
	public static final String PLUGIN_NAME = "Codex Wayfinder";

	/** A straight run: two tiles a game tick of 0.6 seconds, as Wayfinder uses when it has no pace. */
	static final double RUN_SECONDS_PER_TILE = 0.3;
	/** Rows at or above this are the underground and instanced blocks of the map. */
	static final int UNDERGROUND_ROW = 4500;

	/** One teleport as Wayfinder saves it. Field names are the contract's. */
	public static final class Teleport
	{
		public String name;
		public String source;
		public String kind;
		public int x;
		public int y;
		public int plane;
		public int seconds;
		public boolean carried;
		public boolean timed;
	}

	/** The saved atlas, as Wayfinder writes it. Field names are the contract's. */
	static final class Saved
	{
		int version;
		long savedAt;
		double walkSecondsPerTile;
		List<Teleport> teleports;
		Map<String, Boolean> networks;
		int timedTrips;
	}

	/** One way there: a teleport and the walk after it, or a walk alone. */
	public static final class Leg
	{
		public final int seconds;
		/** The teleport taken, or null for walking all the way. */
		public final String via;
		public final int walkTiles;
		/** The teleport is in the bank, not carried. */
		public final boolean banked;
		/** Timed from the player's own trips rather than estimated. */
		public final boolean timed;

		Leg(int seconds, String via, int walkTiles, boolean banked, boolean timed)
		{
			this.seconds = seconds;
			this.via = via;
			this.walkTiles = walkTiles;
			this.banked = banked;
			this.timed = timed;
		}

		/** "Camelot Teleport, then 25 tiles" or "Walk 120 tiles". */
		public String how()
		{
			if (via == null)
			{
				return "Walk " + walkTiles + (walkTiles == 1 ? " tile" : " tiles");
			}
			String from = banked ? " (from your bank)" : "";
			return walkTiles <= 2 ? via + from : via + from + ", then " + walkTiles + " tiles";
		}

		/** "~1m 40s · Camelot Teleport, then 25 tiles". */
		public String say()
		{
			return "~" + time(seconds) + " · " + how();
		}
	}

	private final double walk;
	private final List<Teleport> teleports;
	private final Map<String, Boolean> networks;
	private final long savedAt;

	Travel(double walk, List<Teleport> teleports, Map<String, Boolean> networks, long savedAt)
	{
		this.walk = walk > 0 && walk < 10 ? walk : RUN_SECONDS_PER_TILE;
		this.teleports = teleports;
		this.networks = networks;
		this.savedAt = savedAt;
	}

	/** The atlas from Wayfinder's saved JSON, or null when it is absent or does not read. */
	public static Travel read(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			Saved s = gson.fromJson(json, Saved.class);
			if (s == null || s.version < 1)
			{
				return null;
			}
			List<Teleport> list = new ArrayList<>();
			if (s.teleports != null)
			{
				for (Teleport t : s.teleports)
				{
					if (t != null && t.name != null && t.seconds >= 0 && t.seconds < 3600 && t.plane >= 0 && t.plane <= 3
						&& t.x > 0 && t.y > 0)
					{
						list.add(t);
					}
				}
			}
			Map<String, Boolean> nets = new LinkedHashMap<>();
			if (s.networks != null)
			{
				s.networks.forEach((k, v) ->
				{
					if (k != null && v != null)
					{
						nets.put(k, v);
					}
				});
			}
			return new Travel(s.walkSecondsPerTile, Collections.unmodifiableList(list), Collections.unmodifiableMap(nets), s.savedAt);
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	public double walkSecondsPerTile()
	{
		return walk;
	}

	public List<Teleport> teleports()
	{
		return teleports;
	}

	public long savedAt()
	{
		return savedAt;
	}

	/** Whether a network such as FAIRY_RING or SPIRIT_TREE is open to this account. */
	public boolean open(String network)
	{
		return Boolean.TRUE.equals(networks.get(network));
	}

	/** Tiles to walk between two points, or -1 when there is no straight walk between them. */
	static int tiles(int x1, int y1, int p1, int x2, int y2, int p2)
	{
		if (p1 != p2 || (y1 >= UNDERGROUND_ROW) != (y2 >= UNDERGROUND_ROW))
		{
			return -1;
		}
		return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
	}

	/**
	 * The quickest way to a tile.
	 *
	 * @param here the player's x, y and plane, or null when not known
	 * @param fetchSeconds what fetching a teleport from the bank first is taken to cost, or a
	 *     negative number to use carried teleports only (an ultimate ironman has no bank)
	 * @return the best leg, or null when there is no known way
	 */
	public Leg to(int[] here, int x, int y, int plane, int fetchSeconds)
	{
		Leg best = null;
		if (here != null && here.length >= 3)
		{
			int t = tiles(here[0], here[1], here[2], x, y, plane);
			if (t >= 0)
			{
				best = new Leg((int) Math.round(t * walk), null, t, false, false);
			}
		}
		for (Teleport tp : teleports)
		{
			if (!tp.carried && fetchSeconds < 0)
			{
				continue;
			}
			int t = tiles(tp.x, tp.y, tp.plane, x, y, plane);
			if (t < 0)
			{
				continue;
			}
			int s = tp.seconds + (int) Math.round(t * walk) + (tp.carried ? 0 : fetchSeconds);
			if (best == null || s < best.seconds)
			{
				best = new Leg(s, tp.name, t, !tp.carried, tp.timed);
			}
		}
		return best;
	}

	/** "45s", "1m 40s", "12m", "1h 5m". */
	public static String time(int seconds)
	{
		int s = Math.max(0, seconds);
		if (s < 60)
		{
			return s + "s";
		}
		if (s < 600)
		{
			return s / 60 + "m" + (s % 60 == 0 ? "" : " " + s % 60 + "s");
		}
		if (s < 3600)
		{
			return Math.round(s / 60.0) + "m";
		}
		int m = (int) Math.round(s / 60.0);
		return m / 60 + "h" + (m % 60 == 0 ? "" : " " + m % 60 + "m");
	}

	/** What Wayfinder said back to a route request. */
	public static final class Routed
	{
		public String id;
		public boolean found;
		public int seconds;
		public List<String> steps = Collections.emptyList();
	}

	/** The fields of a route request, as Wayfinder reads them: x, y, plane, name and id. */
	public static Map<String, Object> request(int x, int y, int plane, String name, String id)
	{
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("x", x);
		m.put("y", y);
		m.put("plane", plane);
		m.put("name", name);
		m.put("id", id);
		return m;
	}

	/** Reads a "routed" reply, or null when it is not one this app asked for. */
	public static Routed routed(Map<String, Object> data)
	{
		if (data == null || !(data.get("id") instanceof String))
		{
			return null;
		}
		Routed r = new Routed();
		r.id = (String) data.get("id");
		r.found = Boolean.TRUE.equals(data.get("found"));
		Object s = data.get("seconds");
		r.seconds = s instanceof Number ? Math.max(0, ((Number) s).intValue()) : 0;
		Object steps = data.get("steps");
		if (steps instanceof List)
		{
			List<String> out = new ArrayList<>();
			for (Object o : (List<?>) steps)
			{
				if (o instanceof String)
				{
					out.add((String) o);
				}
			}
			r.steps = Collections.unmodifiableList(out);
		}
		return r;
	}
}
