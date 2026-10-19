package com.codex.provisioner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The word at the bank when the kit runs low. */
public class RestockTest
{
	private static final IntFunction<String> NAMES = id -> id == ItemID._3DOSEPRAYERRESTORE ? "Prayer potion(3)" : "Item " + id;

	/** Ten doses of prayer a trip of Vorkath, with so many four-dose potions held. */
	private static Chain vorkath(long potions)
	{
		Kit kit = new Kit("v", "Vorkath", Arrays.asList(new Need("PRAYER", 10)), false);
		Map<Integer, Long> held = new HashMap<>();
		held.put(ItemID._4DOSEPRAYERRESTORE, potions);
		return Chain.build(kit, Collections.emptyMap(), 10, Collections.emptyMap(), held, Collections.emptyMap(), Collections.emptyMap());
	}

	@Test
	public void saysHowManyTripsAndWhatCoversTheRest()
	{
		// 24 doses: two trips, and 6 doses short of three, two of the three-dose potions mixing makes.
		assertEquals("Ready for 2 trips of Vorkath. First to run out: prayer and restores; 2 more prayer potion(3) cover 3 trips.",
			Restock.warning(vorkath(6), 3, NAMES));
		assertEquals("Not stocked for a single trip of Vorkath. First to run out: prayer and restores; 10 more prayer potion(3) cover 3 trips.",
			Restock.warning(vorkath(0), 3, NAMES));
		// Stocked for the mark, or the warning off: nothing.
		assertNull(Restock.warning(vorkath(8), 3, NAMES));
		assertNull(Restock.warning(vorkath(0), 0, NAMES));
		// A mark above the plan's own trips asks no more than the plan: ten trips here, so 12 reads as 10.
		assertEquals("Ready for 2 trips of Vorkath. First to run out: prayer and restores; 26 more prayer potion(3) cover 10 trips.",
			Restock.warning(vorkath(6), 12, NAMES));
	}

	@Test
	public void saysNothingOfAKitNeverPicked()
	{
		// The first kit is only shown until one is picked; a player who never set one up is not nagged.
		assertNull(new Restock().onBank(vorkath(0), null, 3, NAMES, 1_000));
		assertNull(new Restock().onBank(vorkath(0), "zulrah", 3, NAMES, 1_000));
		assertNotNull(new Restock().onBank(vorkath(0), "v", 3, NAMES, 1_000));
	}

	@Test
	public void isNotRepeatedAtEveryBankVisitWhileTheStockGrows()
	{
		Restock r = new Restock();
		long t = 1_000_000;
		assertNotNull(r.onBank(vorkath(3), "v", 3, NAMES, t));
		// More made since: the same shortfall, smaller, is not told again.
		assertNull(r.onBank(vorkath(5), "v", 3, NAMES, t + 60_000));
		// Fewer trips than last told: news.
		assertNotNull(r.onBank(vorkath(0), "v", 3, NAMES, t + 120_000));
		assertNull(r.onBank(vorkath(0), "v", 3, NAMES, t + 180_000));
		// Half an hour on, a reminder.
		assertNotNull(r.onBank(vorkath(0), "v", 3, NAMES, t + 180_000 + Restock.QUIET_MS));
		// Stocked, then short again: told at once.
		assertNull(r.onBank(vorkath(8), "v", 3, NAMES, t + 200_000 + Restock.QUIET_MS));
		assertNotNull(r.onBank(vorkath(1), "v", 3, NAMES, t + 210_000 + Restock.QUIET_MS));
	}
}
