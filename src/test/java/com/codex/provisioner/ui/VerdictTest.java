package com.codex.provisioner.ui;

import static org.junit.Assert.assertEquals;
import com.codex.provisioner.core.Costing;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** What the Stores tab says when making would use ingredients already held. */
public class VerdictTest
{
	private static Costing.Quote quote(long potion, long ranarrHeld)
	{
		Map<Integer, Long> prices = new HashMap<>();
		prices.put(ItemID._3DOSEPRAYERRESTORE, potion);
		prices.put(ItemID.UNIDENTIFIED_RANARR, 5_000L);
		prices.put(ItemID.RANARR_WEED, 5_400L);
		prices.put(ItemID.RANARRVIAL, 5_600L);
		prices.put(ItemID.VIAL_WATER, 4L);
		prices.put(ItemID.VIAL_EMPTY, 2L);
		prices.put(ItemID.SNAPE_GRASS, 300L);
		Map<Integer, Long> pool = new HashMap<>();
		pool.put(ItemID.UNIDENTIFIED_RANARR, ranarrHeld);
		return Costing.quote(ItemID._3DOSEPRAYERRESTORE, 10, id -> prices.getOrDefault(id, 0L), pool);
	}

	@Test
	public void makingStillWinsCountingTheHerbs()
	{
		assertEquals("Making saves 7,980 even counting what your ingredients would sell for, and gives 950 Herblore xp.",
			SupplyCard.verdict(quote(6_000, 10), false));
	}

	@Test
	public void fewerCoinsButSellingAndBuyingIsBetter()
	{
		// 4,000 a potion: making spends 3,020 coins against 40,000, but the herbs alone would sell for 49,000.
		assertEquals("Making spends 37k fewer coins, but selling what you hold and buying them finished comes out 12k ahead.",
			SupplyCard.verdict(quote(4_000, 10), false));
	}

	@Test
	public void buyingWinsEvenWithTheHerbsFree()
	{
		assertEquals("Buying is cheaper by 2,020, even counting what you hold as free.", SupplyCard.verdict(quote(100, 10), false));
	}
}
