package com.codex.provisioner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** One sitting's worth of the chain, and the account read only once it has settled after a login. */
public class SittingTest
{
	private static Map<Skill, Integer> levels(int all)
	{
		Map<Skill, Integer> m = new EnumMap<>(Skill.class);
		for (Skill s : Skill.values())
		{
			m.put(s, all);
		}
		return m;
	}

	/** Prayer restores for 10 trips of 10 doses, with the grimy herbs and the snape grass in the bank. */
	private static Chain prayer(Map<Skill, Integer> levels, boolean herbs)
	{
		Kit kit = new Kit("t", "Test", Arrays.asList(new Need("PRAYER", 10)), false);
		Map<Integer, Long> held = new HashMap<>();
		if (herbs)
		{
			held.put(ItemID.UNIDENTIFIED_RANARR, 34L);
		}
		held.put(ItemID.SNAPE_GRASS, 34L);
		return Chain.build(kit, Collections.emptyMap(), 10, Collections.emptyMap(), held, levels, Collections.emptyMap());
	}

	/** A second an item to clean or fill, two to mix. */
	private static Map<Integer, Long> estimates(Chain chain)
	{
		Map<Integer, Long> out = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			Verb v = s.getRecipe().getVerb();
			long each = v == Verb.CLEAN || v == Verb.FILL ? 1_000 : v == Verb.MIX ? 2_000 : -1;
			out.put(s.getItem(), each < 0 ? -1 : s.getNet() * each);
		}
		return out;
	}

	private static long amount(Sitting plan, int item)
	{
		for (Sitting.Piece p : plan.getPieces())
		{
			if (p.getStep().getItem() == item)
			{
				return p.getAmount();
			}
		}
		return 0;
	}

	@Test
	public void aShortSittingGoesAsFarAsTheTimeAndTheInputsAllow()
	{
		Chain chain = prayer(levels(99), true);
		Sitting plan = Sitting.plan(chain, estimates(chain), Collections.emptySet(), 100_000);
		// Clean every herb (34s), buy the vials, fill them (34s), then 16 unfinished potions in the 32s left.
		assertEquals(34, amount(plan, ItemID.RANARR_WEED));
		assertEquals(34, amount(plan, ItemID.VIAL_EMPTY));
		assertEquals(34, amount(plan, ItemID.VIAL_WATER));
		assertEquals(16, amount(plan, ItemID.RANARRVIAL));
		assertEquals(0, amount(plan, ItemID._3DOSEPRAYERRESTORE));
		assertEquals(100_000, plan.getUsed());
		assertFalse(plan.isEverything());
		// The step in hand comes first.
		assertEquals(chain.current(), plan.getPieces().get(0).getStep());
		assertFalse(plan.getPieces().get(3).finishes());
	}

	@Test
	public void aLongSittingFinishesTheChain()
	{
		Chain chain = prayer(levels(99), true);
		Sitting plan = Sitting.plan(chain, estimates(chain), Collections.emptySet(), 3_600_000);
		assertTrue(plan.isEverything());
		assertTrue(plan.getLeft().isEmpty());
		assertEquals(34, amount(plan, ItemID._3DOSEPRAYERRESTORE));
		// 34 cleaned, 34 filled, 34 + 34 mixed at two seconds each.
		assertEquals(204_000, plan.getUsed());
	}

	@Test
	public void aStepCannotOutrunItsInputs()
	{
		Chain chain = prayer(levels(99), true);
		Map<Integer, Long> est = estimates(chain);
		// The vials cannot be filled in time: the potions can only use what the sitting makes of them.
		est.put(ItemID.VIAL_WATER, 34 * 60_000L);
		// Clean 34 (34s), buy, fill 5 (5m), then 5 unfinished and 5 finished potions (10s each).
		Sitting plan = Sitting.plan(chain, est, Collections.emptySet(), 34_000 + 5 * 60_000 + 20_000);
		assertEquals(5, amount(plan, ItemID.VIAL_WATER));
		assertEquals(5, amount(plan, ItemID.RANARRVIAL));
		assertEquals(5, amount(plan, ItemID._3DOSEPRAYERRESTORE));
	}

	@Test
	public void grownAndLockedStepsArePassedOverAndLeft()
	{
		Chain grown = prayer(levels(99), false);
		Sitting plan = Sitting.plan(grown, estimates(grown), Collections.singleton(ItemID.UNIDENTIFIED_RANARR), 3_600_000);
		// No herbs grown yet: only the vials can be got ready.
		assertEquals(0, amount(plan, ItemID.RANARR_WEED));
		assertEquals(34, amount(plan, ItemID.VIAL_WATER));
		assertTrue(plan.getLeft().stream().anyMatch(s -> s.getItem() == ItemID.UNIDENTIFIED_RANARR));

		Chain locked = prayer(levels(1), true);
		Sitting low = Sitting.plan(locked, estimates(locked), Collections.emptySet(), 3_600_000);
		List<Step> left = low.getLeft();
		assertTrue(left.stream().anyMatch(s -> s.getState() == Step.State.LOCKED));
		assertEquals(0, amount(low, ItemID._3DOSEPRAYERRESTORE));
	}

	@Test
	public void theAccountIsReadOnlyOnceTheLoginHasSettled()
	{
		Settle settle = new Settle();
		settle.restart();
		assertFalse(settle.isSettled());
		assertFalse(settle.tick());
		assertTrue(settle.tick());
		assertTrue(settle.isSettled());
		// Read once, not on every tick after.
		assertFalse(settle.tick());
		settle.restart();
		assertFalse(settle.isSettled());
	}

	@Test
	public void aSkillNotSentYetIsNoReading()
	{
		Provisions p = new Provisions();
		p.observeLevels(Collections.singletonMap(Skill.HERBLORE, 80));
		// During a hop the game can report a skill as 0; that must not lock every Herblore step.
		assertFalse(p.observeLevels(Collections.singletonMap(Skill.HERBLORE, 0)));
		assertEquals(Integer.valueOf(80), p.levels().get(Skill.HERBLORE));
	}
}
