package com.codex.provisioner.core;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * Grimy herbs to grow, turned into herb runs: how many runs the patches take, how many seeds
 * that plants, and how long the herbs take to grow between runs. Yields depend on compost,
 * secateurs, diaries and luck, so the herbs one patch gives is the player's to set; the plan is
 * an estimate, never a promise.
 */
public final class HerbRuns
{
	/** Herbs take four 20-minute stages to grow. */
	public static final long GROW_MS = 80 * 60_000L;

	private static final Map<Integer, Integer> SEEDS = new HashMap<>();

	static
	{
		SEEDS.put(ItemID.UNIDENTIFIED_RANARR, ItemID.RANARR_SEED);
		SEEDS.put(ItemID.UNIDENTIFIED_TOADFLAX, ItemID.TOADFLAX_SEED);
		SEEDS.put(ItemID.UNIDENTIFIED_IRIT, ItemID.IRIT_SEED);
		SEEDS.put(ItemID.UNIDENTIFIED_AVANTOE, ItemID.AVANTOE_SEED);
		SEEDS.put(ItemID.UNIDENTIFIED_KWUARM, ItemID.KWUARM_SEED);
		SEEDS.put(ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.SNAPDRAGON_SEED);
		SEEDS.put(ItemID.UNIDENTIFIED_CADANTINE, ItemID.CADANTINE_SEED);
		SEEDS.put(ItemID.UNIDENTIFIED_LANTADYME, ItemID.LANTADYME_SEED);
		SEEDS.put(ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.DWARF_WEED_SEED);
		SEEDS.put(ItemID.UNIDENTIFIED_TORSTOL, ItemID.TORSTOL_SEED);
	}

	private HerbRuns()
	{
	}

	/** The seed a grimy herb grows from, or 0 when it is not a herb grown in herb patches. */
	public static int seed(int grimy)
	{
		return SEEDS.getOrDefault(grimy, 0);
	}

	/** The herb runs for one grimy herb. */
	public static final class Plan
	{
		private final int seed;
		private final long runs;
		private final long seeds;
		private final long seedsHeld;
		private final int patches;

		Plan(int seed, long runs, long seeds, long seedsHeld, int patches)
		{
			this.seed = seed;
			this.runs = runs;
			this.seeds = seeds;
			this.seedsHeld = seedsHeld;
			this.patches = patches;
		}

		public int getSeed()
		{
			return seed;
		}

		/** Herb runs, every patch planted with this herb. */
		public long getRuns()
		{
			return runs;
		}

		/** Seeds those runs plant; the last run may need fewer than every patch. */
		public long getSeeds()
		{
			return seeds;
		}

		public long getSeedsHeld()
		{
			return seedsHeld;
		}

		public long getSeedsShort()
		{
			return Math.max(0, seeds - seedsHeld);
		}

		public int getPatches()
		{
			return patches;
		}

		/** Patches the fullest run plants: every patch, or fewer when the herbs need fewer seeds. */
		public int getPatchesUsed()
		{
			return (int) Math.min(patches, seeds);
		}

		/** The soonest the last run can be picked, from planting the first: one growth between runs. */
		public long getSoonestMs()
		{
			return runs * GROW_MS;
		}
	}

	/**
	 * @param herbs grimy herbs still to gather
	 * @param patches herb patches the player runs; 0 or less plans nothing
	 * @param perPatch herbs one patch gives on average
	 * @param held what is held, for the seeds
	 * @return the plan, or null when the item is not a herb, nothing is left, or no patches are run
	 */
	public static Plan plan(int grimy, long herbs, int patches, double perPatch, Map<Integer, Long> held)
	{
		int seed = seed(grimy);
		if (seed == 0 || herbs <= 0 || patches <= 0 || perPatch <= 0)
		{
			return null;
		}
		long seeds = (long) Math.ceil(herbs / perPatch - 1e-9);
		long runs = (seeds + patches - 1) / patches;
		return new Plan(seed, runs, seeds, held.getOrDefault(seed, 0L), patches);
	}
}
