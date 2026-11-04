package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Where a step that has to be done somewhere is done: the fishing spots, spawns and shops a
 * chain sends the player to. Steps done anywhere (cooking on any range, cleaning, mixing) have
 * none, and are worked where the player already is.
 *
 * <p>Fishing spot tiles come from RuneLite's world map data (FishingSpotLocation); other places
 * from RuneLite's map markers and clue data, and from Shortest Path's travel data, as shipped in
 * Codex Wayfinder. The Fishing Guild asks for 68 Fishing (OSRS Wiki).
 */
public final class Spots
{
	/** One place a step can be done. */
	public static final class Spot
	{
		public final String name;
		public final int x;
		public final int y;
		public final int plane;
		/** A level the place itself asks for, beyond the step's own, or null. */
		public final Skill skill;
		public final int level;

		Spot(String name, int x, int y, int plane, Skill skill, int level)
		{
			this.name = name;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.skill = skill;
			this.level = level;
		}

		/** Whether the player's levels let them use it; a skill not known counts as reached. */
		public boolean open(Map<Skill, Integer> levels)
		{
			return skill == null || levels.getOrDefault(skill, 99) >= level;
		}
	}

	private static final Map<Integer, List<Spot>> SPOTS = new HashMap<>();

	static final Spot CATHERBY = new Spot("Catherby", 2836, 3431, 0, null, 0);
	static final Spot FISHING_GUILD = new Spot("Fishing Guild", 2604, 3423, 0, Skill.FISHING, 68);
	static final Spot MUSA_POINT = new Spot("Musa Point, Karamja", 2925, 3179, 0, null, 0);

	static
	{
		put(ItemID.RAW_SHARK, CATHERBY, FISHING_GUILD);
		put(ItemID.RAW_SWORDFISH, CATHERBY, FISHING_GUILD, MUSA_POINT);
		put(ItemID.RAW_LOBSTER, CATHERBY, FISHING_GUILD, MUSA_POINT);
		put(ItemID.RAW_ANGLERFISH, new Spot("Port Piscarilius", 1831, 3773, 0, null, 0));
		put(ItemID.PISCARILIUS_SANDWORMS, new Spot("Port Piscarilius", 1800, 3790, 0, null, 0));
		put(ItemID.RAW_MONKFISH, new Spot("Piscatoris Fishing Colony", 2308, 3700, 0, null, 0));
		put(ItemID.TBWT_RAW_KARAMBWAN, new Spot("Fairy ring DKP", 2898, 3119, 0, null, 0));
		put(ItemID.TBWT_RAW_KARAMBWANJI, new Spot("South of Tai Bwo Wannai", 2806, 3014, 0, null, 0));
		put(ItemID.SNAPE_GRASS, new Spot("Waterbirth Island", 2551, 3755, 0, null, 0));
		put(ItemID.RED_SPIDERS_EGGS, new Spot("Edgeville Dungeon", 3096, 3469, 0, null, 0));
		put(ItemID.WINE_OF_ZAMORAK, new Spot("Goblin Village, by the Chaos Temple", 2956, 3505, 0, null, 0));
		put(ItemID.MORTMYREMUSHROOM, new Spot("Ver Sinhaza", 3649, 3230, 0, null, 0));
		put(ItemID.BLUE_DRAGON_SCALE, new Spot("Taverley Dungeon", 2883, 3397, 0, null, 0));
	}

	private Spots()
	{
	}

	private static void put(int item, Spot... spots)
	{
		SPOTS.put(item, Collections.unmodifiableList(new ArrayList<>(Arrays.asList(spots))));
	}

	/** Where an item's step is done; empty when anywhere will do. */
	public static List<Spot> of(int item)
	{
		List<Spot> s = SPOTS.get(item);
		return s == null ? Collections.emptyList() : s;
	}
}
