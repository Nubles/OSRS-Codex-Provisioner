package com.codex.provisioner.core;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** A sitting as a trip: the spot each step is done at, picked by travel time, and the travel inside the sitting. */
public class ItineraryTest
{
	/** Sharks, not anglerfish: 80 Fishing. */
	private static Map<Skill, Integer> levels(int all)
	{
		Map<Skill, Integer> m = new EnumMap<>(Skill.class);
		for (Skill s : Skill.values())
		{
			m.put(s, all);
		}
		m.put(Skill.FISHING, 80);
		return m;
	}

	private static Chain sharks(Map<Skill, Integer> levels)
	{
		Kit kit = new Kit("t", "Test", Arrays.asList(new Need("FOOD", 10)), false);
		return Chain.build(kit, Collections.emptyMap(), 10, Collections.emptyMap(), new HashMap<>(), levels, Collections.emptyMap());
	}

	/** Two seconds a catch, one a cook. */
	private static Map<Integer, Long> estimates(Chain chain)
	{
		Map<Integer, Long> out = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			Verb v = s.getRecipe().getVerb();
			out.put(s.getItem(), v == Verb.CATCH ? s.getNet() * 2_000 : v == Verb.COOK ? s.getNet() * 1_000 : -1);
		}
		return out;
	}

	static final Travel ATLAS = Travel.read(new Gson(), "{\"version\":1,\"walkSecondsPerTile\":0.3,\"teleports\":["
		+ "{\"name\":\"Camelot Teleport\",\"x\":2757,\"y\":3477,\"plane\":0,\"seconds\":5,\"carried\":true},"
		+ "{\"name\":\"Skills necklace: Fishing Guild\",\"x\":2611,\"y\":3390,\"plane\":0,\"seconds\":5,\"carried\":true}]}");

	@Test
	public void theSpotsAreFromRuneLitesMapAndTheGuildAsksSixtyEight()
	{
		assertEquals(2836, Spots.CATHERBY.x);
		assertEquals(3431, Spots.CATHERBY.y);
		assertEquals(Skill.FISHING, Spots.FISHING_GUILD.skill);
		assertEquals(68, Spots.FISHING_GUILD.level);
		assertTrue(Spots.of(ItemID.SHARK).isEmpty());
		assertEquals(2, Spots.of(ItemID.RAW_SHARK).size());
	}

	@Test
	public void eachStepGoesToTheQuickestSpotItsLevelsOpen()
	{
		Chain chain = sharks(levels(99));
		int[] lumbridge = {3222, 3218, 0};
		Itinerary it = Itinerary.plan(chain, estimates(chain), Collections.emptySet(), 3_600_000L, levels(99), ATLAS, lumbridge);
		Itinerary.Stop first = it.getStops().get(0);
		assertEquals(ItemID.RAW_SHARK, first.piece.getStep().getItem());
		// The skills necklace lands 33 tiles from the guild's spots, nearer than Camelot is to Catherby.
		assertEquals("Fishing Guild", first.spot.name);
		assertEquals("Skills necklace: Fishing Guild", first.leg.via);
		assertEquals(5 + Math.round(33 * 0.3), first.leg.seconds);
		// Cooking is done anywhere: no trip.
		Itinerary.Stop cook = it.getStops().get(1);
		assertEquals(ItemID.SHARK, cook.piece.getStep().getItem());
		assertNull(cook.spot);
		assertNull(cook.leg);
		assertEquals(first.leg.seconds * 1000L, it.getTravelMs());

		// Below 68 Fishing the guild is shut, so Catherby by Camelot it is.
		Map<Skill, Integer> low = levels(99);
		low.put(Skill.FISHING, 67);
		Itinerary shut = Itinerary.plan(chain, estimates(chain), Collections.emptySet(), 3_600_000L, low, ATLAS, lumbridge);
		assertEquals("Catherby", shut.getStops().get(0).spot.name);
		assertEquals("Camelot Teleport", shut.getStops().get(0).leg.via);
	}

	@Test
	public void travelComesOutOfTheSittingSoItStillFits()
	{
		Chain chain = sharks(levels(99));
		Map<Integer, Long> est = estimates(chain);
		long budget = 25_000L;
		Itinerary it = Itinerary.plan(chain, est, Collections.emptySet(), budget, levels(99), ATLAS, new int[]{3222, 3218, 0});
		assertTrue(it.getTravelMs() > 0);
		assertTrue(it.getTotalMs() <= budget);
		Itinerary bare = Itinerary.plan(chain, est, Collections.emptySet(), budget, levels(99), null, null);
		assertEquals(0, bare.getTravelMs());
		assertTrue(bare.getSitting().getUsed() + " vs " + it.getSitting().getUsed() + " travel " + it.getTravelMs(), bare.getSitting().getUsed() > it.getSitting().getUsed());
		assertNotNull(bare.getStops().get(0).spot);
	}

	@Test
	public void standingAtTheSpotNeedsNoTrip()
	{
		Chain chain = sharks(levels(99));
		Itinerary it = Itinerary.plan(chain, estimates(chain), Collections.emptySet(), 3_600_000L, levels(99), ATLAS, new int[]{2606, 3420, 0});
		assertEquals("Fishing Guild", it.getStops().get(0).spot.name);
		assertNull(it.getStops().get(0).leg);
		assertEquals(0, it.getTravelMs());
	}
}
