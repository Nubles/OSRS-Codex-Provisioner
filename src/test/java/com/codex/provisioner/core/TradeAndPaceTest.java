package com.codex.provisioner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class TradeAndPaceTest
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
		PRICES.put(ItemID.SHARK, 900L);
		PRICES.put(ItemID.RAW_SHARK, 1_000L);
	}

	private static long price(int id)
	{
		return PRICES.getOrDefault(id, 0L);
	}

	private static Kit kit(String supply, int perTrip)
	{
		return new Kit("k", "K", Collections.singletonList(new Need(supply, perTrip)), false);
	}

	@Test
	public void makingPrayerPotionsFromGrimyRanarr()
	{
		Costing.Quote q = Costing.quote(ItemID._3DOSEPRAYERRESTORE, 10, TradeAndPaceTest::price);
		assertEquals(60_000, q.getBuy());
		// Grimy ranarr (5,000) is cheaper than clean (5,400), so it is bought grimy and cleaned; the
		// vial is filled from an empty one; plus snape grass: 10 x (5,000 + 2 + 300).
		assertEquals(53_020, q.getMake());
		assertTrue(q.makeIsCheaper());
		assertEquals(6_980, q.saving());
		assertEquals(875 + 75, q.getXp().get(Skill.HERBLORE), 0.01);
		assertEquals(0, q.coinsPerXp(), 0);
	}

	@Test
	public void makingThatCostsMoreIsPricedPerXp()
	{
		// Raw sharks dearer than cooked: cooking costs 100 a fish for 210 xp.
		Costing.Quote q = Costing.quote(ItemID.SHARK, 100, TradeAndPaceTest::price);
		assertFalse(q.makeIsCheaper());
		assertEquals(-10_000, q.saving());
		assertEquals(10_000 / 21_000.0, q.coinsPerXp(), 1e-9);
	}

	@Test
	public void unpricedIngredientMakesTheQuoteUnknown()
	{
		Costing.Quote q = Costing.quote(ItemID._3DOSE2RESTORE, 5, TradeAndPaceTest::price);
		assertEquals(-1, q.getBuy());
		assertEquals(-1, q.getMake());
		assertFalse(q.known());
	}

	@Test
	public void boughtSupplyIsOneStep()
	{
		Chain c = Chain.build(kit("PRAYER", 12), Collections.emptyMap(), 10, Collections.emptyMap(),
			Collections.singleton("PRAYER"), new HashMap<>(), Collections.emptyMap(), Collections.emptyMap());
		assertEquals(1, c.getSteps().size());
		Step s = c.getSteps().get(0);
		assertEquals(ItemID._3DOSEPRAYERRESTORE, s.getItem());
		assertEquals(Verb.BUY, s.getRecipe().getVerb());
		assertEquals(40, s.getNet());
		assertEquals(Step.State.READY, s.getState());
	}

	@Test
	public void ironAccountsBuyNothing()
	{
		Provisions p = new Provisions();
		p.setBought("PRAYER", true);
		assertEquals(Collections.singleton("PRAYER"), p.getBought());
		p.setIron(true);
		assertTrue(p.getBought().isEmpty());
	}

	@Test
	public void potionStorageCountsTowardTheSupply()
	{
		Provisions p = new Provisions();
		p.observeBank(new HashMap<>(), 1);
		Map<Integer, Long> fromQuartermaster = Collections.singletonMap(ItemID._1DOSEPRAYERRESTORE, 60L);
		// 120 doses wanted; Quartermaster's reading of 60 stored doses halves the work.
		Chain c = p.plan(kit("PRAYER", 12), Collections.emptyMap(), new HashMap<>(), fromQuartermaster);
		assertEquals(60, c.getLines().get(0).getShort());
		// Once read here, the Provisioner's own reading wins.
		assertTrue(p.observeStorage(Collections.singletonMap(ItemID._1DOSEPRAYERRESTORE, 120L)));
		assertFalse(p.observeStorage(Collections.singletonMap(ItemID._1DOSEPRAYERRESTORE, 120L)));
		c = p.plan(kit("PRAYER", 12), Collections.emptyMap(), new HashMap<>(), fromQuartermaster);
		assertTrue(c.provisioned());
	}

	@Test
	public void paceLearnsFromSteadyWorkOnly()
	{
		Pace pace = new Pace();
		Map<Integer, Integer> makes = Collections.emptyMap();
		long t = 0;
		long net = 100;
		pace.observe("p", Collections.singletonMap(1, net), makes, t);
		// A shark every 24 seconds for 25 sharks: 150 an hour.
		for (int i = 0; i < 25; i++)
		{
			t += 24_000;
			net--;
			pace.observe("p", Collections.singletonMap(1, net), makes, t);
		}
		assertEquals(150, pace.measured(1), 0.01);
		// A long break and a stack bought at once teach nothing.
		t += 10 * 60_000;
		pace.observe("p", Collections.singletonMap(1, net - 1), makes, t);
		t += 1_000;
		pace.observe("p", Collections.singletonMap(1, net - 500), makes, t);
		assertEquals(150, pace.measured(1), 0.01);
		// A change of plan starts the counts afresh rather than reading the drop as work.
		assertFalse(pace.observe("q", Collections.singletonMap(1, 3L), makes, t + 1_000));
	}

	@Test
	public void paceTrustsOnlyEnough()
	{
		Pace pace = new Pace();
		pace.observe("p", Collections.singletonMap(1, 10L), Collections.emptyMap(), 0);
		pace.observe("p", Collections.singletonMap(1, 5L), Collections.emptyMap(), 30_000);
		assertEquals(0, pace.measured(1), 0);
	}

	@Test
	public void estimateFallsBackToTypicalRate()
	{
		Chain c = Chain.build(kit("FOOD", 200), Collections.emptyMap(), 10, Collections.singletonMap("FOOD", ItemID.SHARK),
			new HashMap<>(), Collections.emptyMap(), Collections.emptyMap());
		Step cook = null;
		for (Step s : c.getSteps())
		{
			if (s.getItem() == ItemID.SHARK)
			{
				cook = s;
			}
		}
		// 2,000 hp is 100 sharks; at 1,300 an hour that is about 4.6 minutes.
		assertEquals(100, cook.getNet());
		assertEquals(Math.round(100 * 3_600_000.0 / 1300), new Pace().estimate(cook));
		Chain herbs = Chain.build(kit("PRAYER", 12), Collections.emptyMap(), 10, Collections.emptyMap(),
			new HashMap<>(), Collections.emptyMap(), Collections.emptyMap());
		for (Step s : herbs.getSteps())
		{
			if (s.getItem() == ItemID.UNIDENTIFIED_RANARR)
			{
				assertEquals("farm runs have no rate", -1, new Pace().estimate(s));
			}
		}
	}
}
