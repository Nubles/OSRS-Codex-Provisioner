package com.codex.provisioner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** Making from what is already held, and pace that is not fooled by buying. */
public class HeldCostAndPaceTest
{
	private static final Map<Integer, Long> PRICES = new HashMap<>();

	static
	{
		PRICES.put(ItemID._3DOSEPRAYERRESTORE, 6_000L);
		PRICES.put(ItemID.UNIDENTIFIED_RANARR, 5_000L);
		PRICES.put(ItemID.RANARR_WEED, 5_400L);
		PRICES.put(ItemID.RANARRVIAL, 5_600L);
		PRICES.put(ItemID.VIAL_WATER, 4L);
		PRICES.put(ItemID.VIAL_EMPTY, 2L);
		PRICES.put(ItemID.SNAPE_GRASS, 300L);
	}

	private static long price(int id)
	{
		return PRICES.getOrDefault(id, 0L);
	}

	@Test
	public void heldIngredientsCostNoCoinsButCountAtTheirSaleValue()
	{
		Map<Integer, Long> pool = new HashMap<>();
		pool.put(ItemID.UNIDENTIFIED_RANARR, 10L);
		Costing.Quote q = Costing.quote(ItemID._3DOSEPRAYERRESTORE, 10, HeldCostAndPaceTest::price, pool);
		// Only the vials and snape grass are bought: 10 x (2 + 300).
		assertEquals(3_020, q.getMake());
		// The ten ranarr would sell for 5,000 less 2% tax each.
		assertEquals(49_000, q.getFromBank());
		assertEquals(52_020, q.getMakeTotal());
		assertEquals(60_000 - 52_020, q.saving());
		assertEquals(60_000 - 3_020, q.cashSaving());
		assertTrue(q.makeIsCheaper());
		// They are spoken for now.
		assertEquals(0L, (long) pool.get(ItemID.UNIDENTIFIED_RANARR));
	}

	@Test
	public void onePoolNeverCountsAHeldItemTwice()
	{
		Map<Integer, Long> pool = new HashMap<>();
		pool.put(ItemID.UNIDENTIFIED_RANARR, 6L);
		Costing.Quote first = Costing.quote(ItemID._3DOSEPRAYERRESTORE, 4, HeldCostAndPaceTest::price, pool);
		Costing.Quote second = Costing.quote(ItemID._3DOSEPRAYERRESTORE, 4, HeldCostAndPaceTest::price, pool);
		assertEquals(4 * 4_900, first.getFromBank());
		// Two ranarr were left for the second, and two had to be bought.
		assertEquals(2 * 4_900, second.getFromBank());
		assertEquals(4 * 302 + 2 * 5_000, second.getMake());
	}

	@Test
	public void aHeldStageAboveSkipsEverythingBelowIt()
	{
		Map<Integer, Long> pool = new HashMap<>();
		pool.put(ItemID.RANARRVIAL, 10L);
		pool.put(ItemID.UNIDENTIFIED_RANARR, 50L);
		Costing.Quote q = Costing.quote(ItemID._3DOSEPRAYERRESTORE, 10, HeldCostAndPaceTest::price, pool);
		assertEquals(10 * 300, q.getMake());
		assertEquals(10 * (5_600 - 112), q.getFromBank());
		// The grimy herbs were never needed.
		assertEquals(50L, (long) pool.get(ItemID.UNIDENTIFIED_RANARR));
	}

	@Test
	public void aHeldIngredientWithNoPriceStillLetsTheQuoteBeTold()
	{
		Map<Integer, Long> pool = new HashMap<>();
		pool.put(ItemID.SNAPE_GRASS, 10L);
		Prices noSnape = id -> id == ItemID.SNAPE_GRASS ? 0 : price(id);
		Costing.Quote q = Costing.quote(ItemID._3DOSEPRAYERRESTORE, 10, noSnape, pool);
		assertTrue(q.known());
		assertEquals(0, q.getFromBank());
		assertEquals(10 * 5_002, q.getMake());
	}

	@Test
	public void aQuoteThatCannotBeMadeTakesNothing()
	{
		Map<Integer, Long> pool = new HashMap<>();
		pool.put(ItemID.UNIDENTIFIED_RANARR, 10L);
		// Without snape grass at any price, nothing can be made, and the ranarr stay in the pool.
		Prices noSnape = id -> id == ItemID.SNAPE_GRASS ? 0 : price(id);
		Costing.Quote q = Costing.quote(ItemID._3DOSEPRAYERRESTORE, 10, noSnape, pool);
		assertFalse(q.known());
		assertEquals(10L, (long) pool.get(ItemID.UNIDENTIFIED_RANARR));
	}

	@Test
	public void saleValueTakesTheTaxUpToItsCap()
	{
		assertEquals(49, Costing.saleValue(49));
		assertEquals(4_900, Costing.saleValue(5_000));
		assertEquals(1_000_000_000L - 5_000_000L, Costing.saleValue(1_000_000_000L));
		assertEquals(0, Costing.saleValue(0));
	}

	/** A pace fed one item's count each 24 seconds, as cooking a shark at a time. */
	private static long work(Pace pace, int item, long net, long t, int count)
	{
		for (int i = 0; i < count; i++)
		{
			t += 24_000;
			net--;
			pace.observe("p", Collections.singletonMap(item, net), Collections.emptyMap(), Collections.emptySet(), false, t);
		}
		return t;
	}

	@Test
	public void aSmallExchangeBuyIsNotWork()
	{
		Pace pace = new Pace();
		pace.observe("p", Collections.singletonMap(1, 100L), Collections.emptyMap(), 0);
		long t = work(pace, 1, 100, 0, 25);
		assertEquals(150, pace.measured(1), 0.01);
		// Five bought on the exchange and collected later: a drop of five a few seconds on, no work.
		pace.bought(1, t + 1_000);
		assertFalse(pace.observe("p", Collections.singletonMap(1, 70L), Collections.emptyMap(), Collections.emptySet(), false, t + 4_000));
		assertEquals(150, pace.measured(1), 0.01);
		// The mark is spent on that one drop; the next real one counts.
		assertTrue(pace.observe("p", Collections.singletonMap(1, 69L), Collections.emptyMap(), Collections.emptySet(), false, t + 28_000));
	}

	@Test
	public void nothingIsLearnedWhileTrading()
	{
		Pace pace = new Pace();
		pace.observe("p", Collections.singletonMap(1, 100L), Collections.emptyMap(), 0);
		long t = work(pace, 1, 100, 0, 25);
		// Three bought from a shop, a few seconds after the last fish.
		assertFalse(pace.observe("p", Collections.singletonMap(1, 72L), Collections.emptyMap(), Collections.emptySet(), true, t + 6_000));
		assertEquals(150, pace.measured(1), 0.01);
	}

	@Test
	public void buyingStepsAreNeverTimed()
	{
		Pace pace = new Pace();
		pace.observe("p", Collections.singletonMap(1, 100L), Collections.emptyMap(), 0);
		long t = 0;
		for (int i = 0; i < 40; i++)
		{
			t += 10_000;
			assertFalse(pace.observe("p", Collections.singletonMap(1, 99L - i), Collections.emptyMap(), Collections.singleton(1), false, t));
		}
		assertEquals(0, pace.measured(1), 0);
	}

	@Test
	public void anUncollectedPurchaseIsForgottenInTime()
	{
		for (long markAge : new long[]{60_000, Pace.TRADE_MS + 60_000})
		{
			Pace pace = new Pace();
			long t = 10 * Pace.TRADE_MS;
			pace.observe("p", Collections.singletonMap(1, 100L), Collections.emptyMap(), t);
			pace.bought(1, t + 1_000 - markAge);
			// A rise (a deposit of more needed, say) moves the clock on without spending the mark.
			pace.observe("p", Collections.singletonMap(1, 101L), Collections.emptyMap(), Collections.emptySet(), false, t + 1_000);
			boolean learned = pace.observe("p", Collections.singletonMap(1, 100L), Collections.emptyMap(), Collections.emptySet(), false, t + 21_000);
			// A fresh mark passes the drop over; one left uncollected past its time does not.
			assertEquals(markAge > Pace.TRADE_MS, learned);
		}
	}
}
