package com.codex.provisioner.core;

import java.util.Locale;
import java.util.function.IntFunction;

/**
 * The word at the bank when the kit is running low: how many whole trips what is held covers,
 * which supply runs out first, and how many more of what it is made as would see the warning
 * mark through. Given as the bank opens, and not again for the same shortfall until it grows
 * or half an hour has passed, so a player banking between loads is not told at every visit.
 */
public final class Restock
{
	/** The same warning comes again no sooner than this. */
	static final long QUIET_MS = 30 * 60_000L;

	/** The kit last warned of, and the trips then held. */
	private String lastKey;
	private long lastReady;
	private long lastAt;

	/**
	 * What to say as the bank opens, or null for nothing. A player working through the chain at
	 * the bank sees the count fall with each load, so the same kit is warned of again
	 * only once fewer trips are held than last time, or after half an hour, whichever supply is
	 * first to run out.
	 *
	 * @param chosen the kit the player picked on the Kits tab, or null: a kit only shown by
	 *     default, never picked, is not one the player is stocking for, so it is never warned of
	 * @param below warn when fewer whole trips than this are held; 0 never warns
	 * @param names an item's name by its id
	 */
	public String onBank(Chain chain, String chosen, int below, IntFunction<String> names, long now)
	{
		String text = chain == null || chosen == null || !chosen.equals(chain.getKit().getId()) ? null : warning(chain, below, names);
		if (text == null)
		{
			// Stocked again: a later shortfall is news, even within the half hour.
			lastKey = null;
			return null;
		}
		// Keyed by the kit alone: with two supplies near level, the one first to run out swaps
		// from visit to visit while the trips held stay the same, which is no news.
		String key = chain.getKit().getId();
		long ready = chain.limiting().tripsHeld();
		if (key.equals(lastKey) && ready >= lastReady && now - lastAt < QUIET_MS)
		{
			return null;
		}
		lastKey = key;
		lastReady = ready;
		lastAt = now;
		return text;
	}

	/** Forgets what was said, for another account. */
	public void reset()
	{
		lastKey = null;
		lastReady = 0;
		lastAt = 0;
	}

	/**
	 * "Ready for 2 trips of Vorkath. First to run out: food; 18 more shark cover 3 trips.", or null when
	 * the kit is stocked for at least {@code below} trips, uses nothing up, or the check is off.
	 */
	public static String warning(Chain chain, int below, IntFunction<String> names)
	{
		if (chain == null || below <= 0)
		{
			return null;
		}
		// Never more than the trips the plan is for: stocking for one trip asks for no more.
		below = Math.min(below, chain.getTrips());
		Chain.Line first = chain.limiting();
		if (first == null || first.tripsHeld() >= below)
		{
			return null;
		}
		long ready = first.tripsHeld();
		String kit = chain.getKit().getName();
		// Worded as the Chain tab words it under the step in hand.
		String head = ready == 0 ? "Not stocked for a single trip of " + kit + "." : "Ready for " + plural(ready, "trip") + " of " + kit + ".";
		Supply supply = first.getSupply();
		String what = supply.getName() != null ? supply.getName() : names.apply(supply.getIcon());
		Integer each = supply.getUnits().get(first.getProduct());
		long more = more(first, below, each == null ? 0 : each);
		String product = names.apply(first.getProduct());
		String lead = head + " First to run out: " + (what == null ? "one supply" : what.toLowerCase(Locale.ENGLISH));
		if (more <= 0 || product == null)
		{
			return lead + ".";
		}
		return lead + "; " + String.format(Locale.ENGLISH, "%,d", more) + " more " + product.toLowerCase(Locale.ENGLISH) + " cover " + plural(below, "trip") + ".";
	}

	/** How many more of the product it takes for a supply to cover so many trips; 0 when it cannot be told. */
	static long more(Chain.Line line, int trips, int unitsEach)
	{
		if (unitsEach <= 0 || line.getPerTrip() <= 0)
		{
			return 0;
		}
		double missing = line.getPerTrip() * trips - line.getHave();
		return missing <= 0 ? 0 : (long) Math.ceil(missing / unitsEach - 1e-9);
	}

	private static String plural(long n, String one)
	{
		return n + " " + (n == 1 ? one : one + "s");
	}
}
