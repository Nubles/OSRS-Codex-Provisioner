package com.codex.provisioner.core;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Pins what Codex Wayfinder shares, as its own code writes it (com.codex.wayfinder.core.Shared,
 * saved by Gson under codexwayfinder.atlasV1) and the plugin messages it answers
 * (CodexWayfinderPlugin.onPluginMessage: "route" and "show" with x, y, plane, name, id; "routed"
 * back with id, name, found, seconds, steps). If Wayfinder changes a field name, this fails.
 */
public class WayfinderContractTest
{
	/** Shared serialised by Gson: field order and names are Wayfinder's. */
	static final String ATLAS = "{\"version\":1,\"savedAt\":1762300000000,\"walkSecondsPerTile\":0.345,"
		+ "\"teleports\":["
		+ "{\"name\":\"Camelot Teleport\",\"source\":\"Spell\",\"kind\":\"SPELL\",\"x\":2757,\"y\":3477,\"plane\":0,\"seconds\":5,\"carried\":true,\"timed\":false},"
		+ "{\"name\":\"Ardougne cloak: Monastery\",\"source\":\"Ardougne cloak 2\",\"kind\":\"ITEM\",\"x\":2606,\"y\":3222,\"plane\":0,\"seconds\":4,\"carried\":false,\"timed\":true}"
		+ "],\"networks\":{\"FAIRY_RING\":true,\"SPIRIT_TREE\":false},\"timedTrips\":12}";

	@Test
	public void namesAreWayfinders()
	{
		assertEquals("codexwayfinder", Travel.GROUP);
		assertEquals("atlasV1", Travel.KEY);
		assertEquals("codexwayfinder", Travel.NAMESPACE);
		assertEquals("route", Travel.ROUTE);
		assertEquals("routed", Travel.ROUTED);
		assertEquals("show", Travel.SHOW);
		assertEquals("Codex Wayfinder", Travel.PLUGIN_NAME);
	}

	@Test
	public void readsTheSharedAtlas()
	{
		Travel t = Travel.read(new Gson(), ATLAS);
		assertNotNull(t);
		assertEquals(2, t.teleports().size());
		assertEquals(0.345, t.walkSecondsPerTile(), 1e-9);
		assertTrue(t.open("FAIRY_RING"));
		assertFalse(t.open("SPIRIT_TREE"));
		assertFalse(t.open("GLIDER"));
		Travel.Teleport c = t.teleports().get(0);
		assertEquals("Camelot Teleport", c.name);
		assertEquals(2757, c.x);
		assertEquals(3477, c.y);
		assertEquals(5, c.seconds);
		assertTrue(c.carried);
		assertTrue(t.teleports().get(1).timed);
		assertEquals(1762300000000L, t.savedAt());
	}

	@Test
	public void failsSafe()
	{
		Gson g = new Gson();
		assertNull(Travel.read(g, null));
		assertNull(Travel.read(g, ""));
		assertNull(Travel.read(g, "{not json"));
		assertNull(Travel.read(g, "[1,2]"));
		assertNull(Travel.read(g, "{\"version\":0}"));
		Travel bare = Travel.read(g, "{\"version\":1}");
		assertNotNull(bare);
		assertEquals(0, bare.teleports().size());
		assertEquals(Travel.RUN_SECONDS_PER_TILE, bare.walkSecondsPerTile(), 1e-9);
		Travel odd = Travel.read(g, "{\"version\":1,\"teleports\":[null,{\"name\":\"X\",\"x\":1,\"y\":1,\"plane\":9,\"seconds\":3}]}");
		assertEquals(0, odd.teleports().size());
	}

	@Test
	public void costsTheQuickestWay()
	{
		Travel t = Travel.read(new Gson(), ATLAS);
		// Standing in Catherby, the Camelot landing is 20 tiles from the target: 5s + 20 x 0.345.
		int[] here = {2809, 3440, 0};
		Travel.Leg leg = t.to(here, 2757, 3497, 0, -1);
		assertEquals("Camelot Teleport", leg.via);
		assertEquals(20, leg.walkTiles);
		assertEquals(5 + Math.round(20 * 0.345), leg.seconds);
		// A target next door is a walk.
		Travel.Leg walk = t.to(here, 2812, 3440, 0, -1);
		assertNull(walk.via);
		assertEquals(3, walk.walkTiles);
		// The banked cloak only counts when banked teleports are allowed, with the fetch on top.
		Travel.Leg mon = t.to(null, 2606, 3222, 0, -1);
		assertEquals("Camelot Teleport", mon.via);
		Travel.Leg banked = t.to(null, 2606, 3222, 0, 60);
		assertEquals("Ardougne cloak: Monastery", banked.via);
		assertTrue(banked.banked);
		assertEquals(4 + 60, banked.seconds);
		// No straight walk into a dungeon from the surface, nor to another plane.
		assertNull(t.to(null, 2884, 9798, 0, -1));
		assertNull(t.to(here, 2809, 3440, 1, -1));
	}

	@Test
	public void requestCarriesWhatWayfinderReads()
	{
		Map<String, Object> m = Travel.request(3200, 3201, 1, "Somewhere", "app:7");
		assertEquals(Arrays.asList("x", "y", "plane", "name", "id"), Arrays.asList(m.keySet().toArray()));
		assertEquals(3200, m.get("x"));
		assertEquals(1, m.get("plane"));
	}

	@Test
	public void readsTheRoutedReply()
	{
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("id", "app:7");
		out.put("name", "Somewhere");
		out.put("found", true);
		out.put("seconds", 84);
		out.put("steps", Arrays.asList("Camelot Teleport", "Walk to the bank"));
		Travel.Routed r = Travel.routed(out);
		assertEquals("app:7", r.id);
		assertTrue(r.found);
		assertEquals(84, r.seconds);
		assertEquals(2, r.steps.size());
		Map<String, Object> other = new LinkedHashMap<>();
		other.put("id", 12);
		assertNull(Travel.routed(other));
		assertNull(Travel.routed(null));
	}

	@Test
	public void timesRead()
	{
		assertEquals("45s", Travel.time(45));
		assertEquals("1m 40s", Travel.time(100));
		assertEquals("2m", Travel.time(120));
		assertEquals("12m", Travel.time(720));
		assertEquals("1h 5m", Travel.time(3900));
	}
}
