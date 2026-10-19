package com.codex.provisioner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class LoadsAndTrainingTest
{
	private static final Set<Integer> STACKS = new HashSet<>(Arrays.asList(ItemID.SNAKEBOSS_SCALE, ItemID.AMYLASE,
		ItemID.TBWT_RAW_KARAMBWANJI, ItemID.DRAGON_BOLTS_UNENCHANTED_RUBY, ItemID.DRAGON_BOLTS_ENCHANTED_RUBY,
		ItemID.COSMICRUNE, ItemID.FIRERUNE, ItemID.BLOODRUNE));
	private static final IntPredicate STACKABLE = STACKS::contains;

	private static Kit kit(String supply, int perTrip)
	{
		return new Kit("k", "K", Collections.singletonList(new Need(supply, perTrip)), false);
	}

	private static Map<Skill, Integer> levels(int all)
	{
		Map<Skill, Integer> m = new EnumMap<>(Skill.class);
		for (Skill s : Skill.values())
		{
			m.put(s, all);
		}
		return m;
	}

	private static Chain chain(String supply, int perTrip, Map<String, Integer> choices)
	{
		return Chain.build(kit(supply, perTrip), Collections.emptyMap(), 10, choices, new HashMap<>(), levels(99), Collections.emptyMap());
	}

	private static Step step(Chain c, int item)
	{
		for (Step s : c.getSteps())
		{
			if (s.getItem() == item)
			{
				return s;
			}
		}
		throw new AssertionError("no step for " + item);
	}

	@Test
	public void bankMarksShrinkAsTheInventoryFills()
	{
		Chain c = chain("PRAYER", 12, Collections.emptyMap());
		Loads mix = Loads.of(step(c, ItemID._3DOSEPRAYERRESTORE), STACKABLE);
		Map<Integer, Long> carried = new HashMap<>();
		carried.put(ItemID.RANARRVIAL, 14L);
		carried.put(ItemID.SNAPE_GRASS, 5L);
		carried.put(ItemID.PESTLE_AND_MORTAR, 1L);
		Map<Integer, Long> take = Loads.toTake(mix, Arrays.asList(ItemID.PESTLE_AND_MORTAR, ItemID.CHISEL), carried);
		// The unfinished potions are all out already, and the pestle is in the pack: no marks for them.
		assertFalse(take.containsKey(ItemID.RANARRVIAL));
		assertFalse(take.containsKey(ItemID.PESTLE_AND_MORTAR));
		assertEquals(9L, (long) take.get(ItemID.SNAPE_GRASS));
		assertEquals(1L, (long) take.get(ItemID.CHISEL));
		// With nothing to split into trips, only the tools are marked.
		assertEquals(Collections.singletonMap(ItemID.CHISEL, 1L), Loads.toTake(null, Arrays.asList(ItemID.CHISEL), null));
	}

	@Test
	public void twoInputMixesFitFourteenOfEach()
	{
		Chain c = chain("PRAYER", 12, Collections.emptyMap());
		Loads mix = Loads.of(step(c, ItemID._3DOSEPRAYERRESTORE), STACKABLE);
		assertEquals(14, mix.getPerLoad());
		assertEquals(14L, (long) mix.getWithdraw().get(ItemID.RANARRVIAL));
		assertEquals(14L, (long) mix.getWithdraw().get(ItemID.SNAPE_GRASS));
		// Forty potions are three trips.
		assertEquals(3, mix.getLoads());
		Loads clean = Loads.of(step(c, ItemID.RANARR_WEED), STACKABLE);
		assertEquals(28, clean.getPerLoad());
		assertEquals(2, clean.getLoads());
	}

	@Test
	public void aStackTakesOneSlotAndAToolAnother()
	{
		Chain venom = chain("ANTIVENOM", 40, Collections.emptyMap());
		Loads scales = Loads.of(step(venom, ItemID.ANTIVENOM4), STACKABLE);
		// The scales stack in one slot: 27 antidotes, with 20 scales each.
		assertEquals(27, scales.getPerLoad());
		assertEquals(27L * 20, (long) scales.getWithdraw().get(ItemID.SNAKEBOSS_SCALE));
		Chain food = chain("FOOD", 200, Collections.singletonMap("FOOD", ItemID.TBWT_COOKED_KARAMBWAN));
		Loads catching = Loads.of(step(food, ItemID.TBWT_RAW_KARAMBWAN), STACKABLE);
		// The vessel and the stack of karambwanji leave 26 slots for the catch.
		assertEquals(26, catching.getPerLoad());
	}

	@Test
	public void decantingIsSetByWhatGoesIn()
	{
		// 400 doses is 100 super combats, each needing a four-dose of each stat potion: 34 decants each.
		Chain c = chain("COMBAT", 40, Collections.emptyMap());
		Step decant = null;
		for (Step s : c.getSteps())
		{
			if (s.getRecipe().getVerb() == Verb.DECANT)
			{
				decant = s;
			}
		}
		assertNotNull(decant);
		Loads l = Loads.of(decant, STACKABLE);
		// Four three-dose potions make three four-dose: seven goes fit, twenty-one made.
		assertEquals(21, l.getPerLoad());
		assertEquals(28L, (long) l.getWithdraw().values().iterator().next());
		assertEquals(5, l.getLoads());
	}

	@Test
	public void allStacksIsOneTrip()
	{
		Chain c = chain("DRAGON_BOLTS", 100, Collections.singletonMap("DRAGON_BOLTS", ItemID.DRAGON_BOLTS_ENCHANTED_RUBY));
		Loads l = Loads.of(step(c, ItemID.DRAGON_BOLTS_ENCHANTED_RUBY), STACKABLE);
		assertEquals(1, l.getLoads());
		assertEquals(1000, l.getPerLoad());
	}

	@Test
	public void farmRunsBuyingAndDoneStepsHaveNoTrips()
	{
		Chain c = chain("PRAYER", 12, Collections.emptyMap());
		assertNull(Loads.of(step(c, ItemID.UNIDENTIFIED_RANARR), STACKABLE));
		Chain bought = Chain.build(kit("PRAYER", 12), Collections.emptyMap(), 10, Collections.emptyMap(),
			Collections.singleton("PRAYER"), new HashMap<>(), levels(99), Collections.emptyMap());
		assertNull(Loads.of(bought.getSteps().get(0), STACKABLE));
	}

	@Test
	public void trainingAddsUpTheOpenStepsAndTheLevelsTheyReach()
	{
		Chain c = chain("PRAYER", 12, Collections.emptyMap());
		Map<Skill, Long> xp = new EnumMap<>(Skill.class);
		xp.put(Skill.HERBLORE, (long) Experience.getXpForLevel(38));
		List<Training.Gain> gains = Training.of(c, xp, levels(38));
		assertEquals(1, gains.size());
		Training.Gain herb = gains.get(0);
		assertEquals(Skill.HERBLORE, herb.getSkill());
		// Forty ranarr cleaned (7.5 each) and forty potions mixed (87.5 each).
		assertEquals(40 * 7.5 + 40 * 87.5, herb.getXp(), 0.01);
		assertEquals(38, herb.levelBefore());
		assertEquals(Experience.getLevelForXp(Experience.getXpForLevel(38) + 3_800), herb.levelAfter());
		assertTrue(herb.levelAfter() > herb.levelBefore());
	}

	@Test
	public void trainingFallsBackOnTheLevelWithoutExperience()
	{
		Chain c = chain("PRAYER", 12, Collections.emptyMap());
		Training.Gain herb = Training.of(c, Collections.emptyMap(), levels(90)).get(0);
		assertEquals(Experience.getXpForLevel(90), herb.getBefore());
		assertEquals(90, herb.levelBefore());
		// Experience read as nothing before the game sent it does not undo a known level.
		Training.Gain early = Training.of(c, Collections.singletonMap(Skill.HERBLORE, 0L), levels(90)).get(0);
		assertEquals(90, early.levelBefore());
	}

	@Test
	public void experienceReadAsNothingIsNotTakenIn()
	{
		Provisions p = new Provisions();
		assertTrue(p.observeXp(Collections.singletonMap(Skill.HERBLORE, 1_000_000L)));
		assertFalse(p.observeXp(Collections.singletonMap(Skill.HERBLORE, 0L)));
		assertEquals(1_000_000L, (long) p.xp().get(Skill.HERBLORE));
	}
}
