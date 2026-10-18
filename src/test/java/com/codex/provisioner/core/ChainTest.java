package com.codex.provisioner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ChainTest
{
	private static Kit kit(Object... pairs)
	{
		Need[] needs = new Need[pairs.length / 2];
		for (int i = 0; i < needs.length; i++)
		{
			needs[i] = new Need((String) pairs[i * 2], (Integer) pairs[i * 2 + 1]);
		}
		return new Kit("test", "Test", Arrays.asList(needs), false);
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

	private static Step step(Chain c, int item)
	{
		for (Step s : c.getSteps())
		{
			if (s.getItem() == item)
			{
				return s;
			}
		}
		return null;
	}

	private static Chain build(Kit kit, int trips, Map<Integer, Long> held, Map<Skill, Integer> levels)
	{
		return Chain.build(kit, Collections.emptyMap(), trips, Collections.emptyMap(), held, levels, Collections.emptyMap());
	}

	@Test
	public void prayerChainFromNothing()
	{
		// 12 doses a trip for 10 trips is 120 doses, which is 40 three-dose potions.
		Chain c = build(kit("PRAYER", 12), 10, new HashMap<>(), levels(99));
		assertEquals(ItemID._3DOSEPRAYERRESTORE, c.getLines().get(0).getProduct());
		assertEquals(40, step(c, ItemID._3DOSEPRAYERRESTORE).getNet());
		assertEquals(40, step(c, ItemID.RANARRVIAL).getNet());
		assertEquals(40, step(c, ItemID.UNIDENTIFIED_RANARR).getNet());
		assertEquals(40, step(c, ItemID.VIAL_EMPTY).getNet());
		assertEquals(40, step(c, ItemID.SNAPE_GRASS).getNet());
		// Gathering leads.
		assertEquals(ItemID.UNIDENTIFIED_RANARR, c.getSteps().get(0).getItem());
		assertEquals(ItemID._3DOSEPRAYERRESTORE, c.getSteps().get(c.getSteps().size() - 1).getItem());
	}

	@Test
	public void heldStagesAreTakenOff()
	{
		Map<Integer, Long> held = new HashMap<>();
		held.put(ItemID._4DOSEPRAYERRESTORE, 10L); // 40 doses
		held.put(ItemID.RANARR_WEED, 15L);
		held.put(ItemID.VIAL_WATER, 100L);
		Chain c = build(kit("PRAYER", 12), 10, held, levels(99));
		// 80 doses short, 27 potions to make.
		assertEquals(80, c.getLines().get(0).getShort());
		assertEquals(27, step(c, ItemID._3DOSEPRAYERRESTORE).getNet());
		assertEquals(12, step(c, ItemID.UNIDENTIFIED_RANARR).getNet());
		assertEquals(Step.State.DONE, step(c, ItemID.VIAL_WATER).getState());
		assertNull(step(c, ItemID.VIAL_EMPTY));
		// Fifteen clean herbs and plenty of water: unfinished potions can be made now.
		assertEquals(15, step(c, ItemID.RANARRVIAL).getCanNow());
		assertEquals(ItemID.RANARRVIAL, c.current().getItem());
	}

	@Test
	public void decantingNeedsFourThreeDoseForThreeFourDose()
	{
		Chain c = build(kit("COMBAT", 4), 9, new HashMap<>(), levels(99));
		// 36 doses, 9 super combats, each one 4-dose super attack: 3 decants of 4 three-dose.
		assertEquals(9, step(c, ItemID._4DOSE2COMBAT).getNet());
		assertEquals(9, step(c, ItemID._4DOSE2ATTACK).getNet());
		assertEquals(12, step(c, ItemID._3DOSE2ATTACK).getNet());
		assertEquals(9, step(c, ItemID.TORSTOL).getNet());
	}

	@Test
	public void sharedVialsAreCountedOnce()
	{
		Chain c = build(kit("PRAYER", 3, "RANGING", 3), 10, new HashMap<>(), levels(99));
		assertEquals(20, step(c, ItemID.VIAL_WATER).getNet());
		long vialSteps = c.getSteps().stream().filter(s -> s.getItem() == ItemID.VIAL_WATER).count();
		assertEquals(1, vialSteps);
	}

	@Test
	public void foodPicksBestFishTheLevelsReach()
	{
		Map<Skill, Integer> lv = levels(70);
		lv.put(Skill.FISHING, 78);
		lv.put(Skill.COOKING, 81);
		Chain c = build(kit("FOOD", 200), 5, new HashMap<>(), lv);
		assertEquals(ItemID.SHARK, c.getLines().get(0).getProduct());
		assertEquals(50, step(c, ItemID.RAW_SHARK).getNet());

		lv.put(Skill.FISHING, 40);
		lv.put(Skill.COOKING, 40);
		c = build(kit("FOOD", 200), 5, new HashMap<>(), lv);
		assertEquals(ItemID.LOBSTER, c.getLines().get(0).getProduct());
	}

	@Test
	public void lockedStepsSayWhy()
	{
		Map<Skill, Integer> lv = levels(99);
		lv.put(Skill.HERBLORE, 60);
		Map<String, Integer> pick = new HashMap<>();
		pick.put("BREW", ItemID._3DOSEPOTIONOFSARADOMIN);
		Chain c = Chain.build(kit("BREW", 12), Collections.emptyMap(), 2, pick, new HashMap<>(), lv, Collections.emptyMap());
		Step brew = step(c, ItemID._3DOSEPOTIONOFSARADOMIN);
		assertEquals(Step.State.LOCKED, brew.getState());
		assertEquals(81, brew.getUnmet().getLevel());
	}

	@Test
	public void boltsEnchantInTens()
	{
		Chain c = build(kit("DRAGON_BOLTS", 150), 1, new HashMap<>(), levels(99));
		assertEquals(150, step(c, ItemID.DRAGON_BOLTS_UNENCHANTED_RUBY).getNet());
		assertEquals(15, step(c, ItemID.COSMICRUNE).getNet());
		assertEquals(75, step(c, ItemID.FIRERUNE).getNet());
	}

	@Test
	public void progressHasAFixedEnd()
	{
		Provisions p = new Provisions();
		p.observeBank(new HashMap<>(), 1);
		Kit k = kit("FOOD", 100);
		p.observeLevels(levels(99));
		p.choose("FOOD", ItemID.SHARK);
		Chain c = p.plan(k, Collections.emptyMap(), Collections.emptyMap());
		assertEquals(50, step(c, ItemID.RAW_SHARK).getBaseline());
		Map<Integer, Long> carried = new HashMap<>();
		carried.put(ItemID.RAW_SHARK, 20L);
		c = p.plan(k, Collections.emptyMap(), carried);
		Step raw = step(c, ItemID.RAW_SHARK);
		assertEquals(30, raw.getNet());
		assertEquals(50, raw.getBaseline());
		assertEquals(0.4, raw.progress(), 1e-9);
		// Cooking them leaves the catching step done, not gone.
		carried.clear();
		carried.put(ItemID.SHARK, 50L);
		c = p.plan(k, Collections.emptyMap(), carried);
		assertNotNull(step(c, ItemID.RAW_SHARK));
		assertEquals(Step.State.DONE, step(c, ItemID.RAW_SHARK).getState());
		assertTrue(c.provisioned());
		assertNull(c.current());
	}

	@Test
	public void everyPresetBuilds()
	{
		for (Kit k : Presets.all())
		{
			Chain c = build(k, 10, new HashMap<>(), new HashMap<>());
			assertTrue(k.getName(), !c.getSteps().isEmpty());
			for (Step s : c.getSteps())
			{
				assertNotNull(s.getRecipe().getHow());
			}
		}
	}
}
