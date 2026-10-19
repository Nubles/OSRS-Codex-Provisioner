package com.codex.provisioner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

public class TreeTest
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

	private static Chain chain(Map<Integer, Long> held, Object... pairs)
	{
		Need[] needs = new Need[pairs.length / 2];
		for (int i = 0; i < needs.length; i++)
		{
			needs[i] = new Need((String) pairs[i * 2], (Integer) pairs[i * 2 + 1]);
		}
		Kit kit = new Kit("test", "Test", Arrays.asList(needs), false);
		Map<String, Integer> choices = new HashMap<>();
		choices.put("PRAYER", ItemID._3DOSEPRAYERRESTORE);
		return Chain.build(kit, Collections.emptyMap(), 1, choices, held, levels(99), Collections.emptyMap());
	}

	private static int index(List<Tree.Node> nodes, int item)
	{
		for (int i = 0; i < nodes.size(); i++)
		{
			if (nodes.get(i).getItem() == item)
			{
				return i;
			}
		}
		return -1;
	}

	@Test
	public void growsFromTheProductDownToWhatIsGathered()
	{
		Chain c = chain(new HashMap<>(), "PRAYER", 12);
		List<Tree.Node> prayer = Tree.of(c).values().iterator().next();
		Tree.Node root = prayer.get(0);
		assertEquals(ItemID._3DOSEPRAYERRESTORE, root.getItem());
		assertEquals(0, root.getDepth());
		int unf = index(prayer, ItemID.RANARRVIAL);
		int grimy = index(prayer, ItemID.UNIDENTIFIED_RANARR);
		int snape = index(prayer, ItemID.SNAPE_GRASS);
		assertEquals(1, prayer.get(unf).getDepth());
		// Grimy ranarr is cleaned into the herb that goes into the unfinished potion: two below it.
		assertEquals(3, prayer.get(grimy).getDepth());
		assertTrue(grimy > unf);
		// Snape grass is the potion's last input, so its branch ends there, and the unfinished
		// potion's branch runs on past everything beneath it until then.
		assertEquals(1, prayer.get(snape).getDepth());
		assertTrue(prayer.get(snape).isLast());
		assertFalse(prayer.get(unf).isLast());
		assertTrue(prayer.get(grimy).rail(0));
		assertFalse(prayer.get(snape).rail(0));
	}

	@Test
	public void aStockedPartIsALeafAndASharedPartOpensOnce()
	{
		// Clean ranarr in hand: nothing beneath it is needed.
		Map<Integer, Long> held = new HashMap<>();
		held.put(ItemID.RANARR_WEED, 50L);
		Chain c = chain(held, "PRAYER", 12, "RANGING", 4);
		Map<Chain.Line, List<Tree.Node>> trees = Tree.of(c);
		List<List<Tree.Node>> all = new java.util.ArrayList<>(trees.values());
		List<Tree.Node> prayer = all.get(0);
		int herb = index(prayer, ItemID.RANARR_WEED);
		assertTrue(prayer.get(herb).isDone());
		assertEquals(-1, index(prayer, ItemID.UNIDENTIFIED_RANARR));

		// Vials of water go into both potions: opened under the first, only named under the second.
		List<Tree.Node> ranging = all.get(1);
		int vialFirst = index(prayer, ItemID.VIAL_WATER);
		int vialAgain = index(ranging, ItemID.VIAL_WATER);
		assertFalse(prayer.get(vialFirst).isRepeat());
		assertTrue(ranging.get(vialAgain).isRepeat());
		assertTrue(vialAgain == ranging.size() - 1 || ranging.get(vialAgain + 1).getDepth() <= ranging.get(vialAgain).getDepth());
	}

	@Test
	public void aSupplyAlreadyStockedIsOneLine()
	{
		Map<Integer, Long> held = new HashMap<>();
		held.put(ItemID._4DOSEPRAYERRESTORE, 10L);
		Chain c = chain(held, "PRAYER", 12);
		List<Tree.Node> prayer = Tree.of(c).values().iterator().next();
		assertEquals(1, prayer.size());
		assertTrue(prayer.get(0).isDone());
		assertNull(prayer.get(0).getStep());
	}
}
