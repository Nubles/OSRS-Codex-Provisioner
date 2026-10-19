package com.codex.provisioner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class HerbRunsAndRunwayTest
{
	private static Map<Integer, Long> items(long... pairs)
	{
		Map<Integer, Long> m = new HashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2)
		{
			m.put((int) pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void herbsBecomeRunsAndSeeds()
	{
		// 100 ranarr at 8 a patch is 13 seeds, two runs on seven patches.
		HerbRuns.Plan p = HerbRuns.plan(ItemID.UNIDENTIFIED_RANARR, 100, 7, 8, items(ItemID.RANARR_SEED, 5));
		assertEquals(ItemID.RANARR_SEED, p.getSeed());
		assertEquals(13, p.getSeeds());
		assertEquals(2, p.getRuns());
		assertEquals(8, p.getSeedsShort());
		assertEquals(2 * HerbRuns.GROW_MS, p.getSoonestMs());
		assertEquals(7, p.getPatchesUsed());
		// 28 ranarr need only 4 seeds: one run plants 4 of the 7 patches, not all of them.
		HerbRuns.Plan few = HerbRuns.plan(ItemID.UNIDENTIFIED_RANARR, 28, 7, 8, items());
		assertEquals(1, few.getRuns());
		assertEquals(4, few.getPatchesUsed());
		// Exactly a patch's worth is one seed, not two.
		assertEquals(1, HerbRuns.plan(ItemID.UNIDENTIFIED_TORSTOL, 8, 7, 8, items()).getSeeds());
		assertEquals(0, HerbRuns.plan(ItemID.UNIDENTIFIED_TORSTOL, 8, 7, 8, items(ItemID.TORSTOL_SEED, 3)).getSeedsShort());
	}

	@Test
	public void onlyHerbPatchHerbsWithPatchesArePlanned()
	{
		assertNull(HerbRuns.plan(ItemID.SNAPE_GRASS, 50, 7, 8, items()));
		assertNull(HerbRuns.plan(ItemID.UNIDENTIFIED_RANARR, 50, 0, 8, items()));
		assertNull(HerbRuns.plan(ItemID.UNIDENTIFIED_RANARR, 0, 7, 8, items()));
		assertEquals(0, HerbRuns.seed(ItemID.RANARR_WEED));
	}

	@Test
	public void everyHerbTheCookbookGrowsHasASeed()
	{
		for (int grimy : new int[]{ItemID.UNIDENTIFIED_RANARR, ItemID.UNIDENTIFIED_TOADFLAX, ItemID.UNIDENTIFIED_IRIT,
			ItemID.UNIDENTIFIED_AVANTOE, ItemID.UNIDENTIFIED_KWUARM, ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.UNIDENTIFIED_CADANTINE,
			ItemID.UNIDENTIFIED_LANTADYME, ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.UNIDENTIFIED_TORSTOL})
		{
			Recipe r = Cookbook.recipe(grimy);
			assertEquals(Verb.GATHER, r.getVerb());
			assertEquals(true, HerbRuns.seed(grimy) > 0);
		}
	}

	@Test
	public void readyTripsAreTheSupplyThatRunsOutFirst()
	{
		Kit kit = new Kit("k", "K", Arrays.asList(new Need("FOOD", 200), new Need("PRAYER", 12), new Need("item:" + ItemID.SPADE, 0)), false);
		// 60 sharks (20 hp each) is 6 trips of food; 4 four-dose prayer potions is 1 trip.
		Map<Integer, Long> held = items(ItemID.SHARK, 60, ItemID._4DOSEPRAYERRESTORE, 4, ItemID.SPADE, 1);
		Chain c = Chain.build(kit, Collections.emptyMap(), 10, Collections.emptyMap(), held, Collections.emptyMap(), Collections.emptyMap());
		assertEquals(1, c.readyTrips());
		assertEquals("PRAYER", c.limiting().getSupply().getKey());
		assertEquals(6, c.getLines().get(0).tripsHeld());
		assertEquals(-1, c.getLines().get(2).tripsHeld());

		// Measured use counts: 9 doses a trip makes 16 doses last one trip still, 18 two.
		held.put(ItemID._4DOSEPRAYERRESTORE, 5L);
		Map<String, Double> measured = new HashMap<>();
		measured.put("PRAYER", 9.0);
		assertEquals(2, Chain.build(kit, measured, 10, Collections.emptyMap(), held, Collections.emptyMap(), Collections.emptyMap()).readyTrips());

		// Nothing used up: no runway at all.
		Kit tools = new Kit("t", "T", Collections.singletonList(new Need("item:" + ItemID.SPADE, 0)), false);
		Chain none = Chain.build(tools, Collections.emptyMap(), 10, Collections.emptyMap(), held, Collections.emptyMap(), Collections.emptyMap());
		assertEquals(-1, none.readyTrips());
		assertNull(none.limiting());
	}

	@Test
	public void aTieGoesToTheFirstSupplyInTheKit()
	{
		Kit kit = new Kit("k", "K", Arrays.asList(new Need("FOOD", 200), new Need("PRAYER", 16)), false);
		Chain c = Chain.build(kit, Collections.emptyMap(), 10, Collections.emptyMap(),
			items(ItemID.SHARK, 10, ItemID._4DOSEPRAYERRESTORE, 4), Collections.emptyMap(), Collections.emptyMap());
		assertEquals(1, c.readyTrips());
		assertSame(c.getLines().get(0), c.limiting());
	}
}
