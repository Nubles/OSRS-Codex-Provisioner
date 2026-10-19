package com.codex.provisioner.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * How fast the player really works each step, learned by watching steps shrink. Each time a
 * step's count goes down a little, the items and the time since the last change are added up;
 * a long pause starts afresh, and a big jump, such as a bank first seen or a stack bought, is
 * not counting at all. Until enough is seen, a recipe's typical rate stands in.
 *
 * <p>Items that arrive by trade are not work either, however few: a handful bought on the Grand
 * Exchange or from a shop shrinks a step just as making them does. Nothing is learned while a
 * trading window is open, an item the exchange has just bought for the player is marked so the
 * next drop in its step is passed over, and steps that are themselves buying are never timed.
 */
public class Pace
{
	/** A gap longer than this is a break, not part of the work. */
	static final long GAP_MS = 120_000;
	/** More than this many at once was not made by hand in one go. */
	static final long MAX_STEP = 30;
	/** Measured once this many items and this much time have been seen. */
	static final long TRUST_ITEMS = 20;
	static final long TRUST_MS = 120_000;
	/** How long an exchange purchase waits to be collected before its mark is forgotten. */
	static final long TRADE_MS = 30 * 60_000L;

	/** Items and milliseconds seen for each item, as two numbers. */
	private Map<Integer, long[]> seen = new HashMap<>();

	// What each step had to go and when it last changed; not saved.
	private transient Map<Integer, Long> lastNet = new HashMap<>();
	private transient Map<Integer, Long> lastAt = new HashMap<>();
	private transient String plan;
	/** Items the exchange bought for the player, and when, until their arrival is seen. */
	private transient Map<Integer, Long> traded = new HashMap<>();

	/** The exchange bought some of an item for the player; its next drop is a purchase, not work. */
	public void bought(int item, long now)
	{
		if (traded == null)
		{
			traded = new HashMap<>();
		}
		traded.put(item, now);
	}

	/**
	 * Notes the counts of a chain's steps now.
	 *
	 * @param plan names the kit, trips and choices; when it changes, counts start afresh
	 * @param makes how many one go makes, so a cast enchanting ten bolts is one go
	 * @return true when anything was learned
	 */
	public boolean observe(String plan, Map<Integer, Long> nets, Map<Integer, Integer> makes, long now)
	{
		return observe(plan, nets, makes, Collections.emptySet(), false, now);
	}

	/**
	 * @param untimed steps whose drops are never work, such as buying steps
	 * @param trading a shop, trade or exchange window is open, so whatever changes now was traded
	 */
	public boolean observe(String plan, Map<Integer, Long> nets, Map<Integer, Integer> makes, Set<Integer> untimed,
		boolean trading, long now)
	{
		if (lastNet == null)
		{
			lastNet = new HashMap<>();
			lastAt = new HashMap<>();
		}
		if (traded == null)
		{
			traded = new HashMap<>();
		}
		traded.values().removeIf(at -> now - at > TRADE_MS);
		if (!plan.equals(this.plan))
		{
			this.plan = plan;
			lastNet.clear();
			lastAt.clear();
		}
		boolean learned = false;
		for (Map.Entry<Integer, Long> e : nets.entrySet())
		{
			int item = e.getKey();
			long net = e.getValue();
			Long before = lastNet.put(item, net);
			if (before == null || net == before)
			{
				if (before == null)
				{
					lastAt.put(item, now);
				}
				continue;
			}
			long at = lastAt.getOrDefault(item, now);
			lastAt.put(item, now);
			long made = before - net;
			if ((made > 0 && traded.remove(item) != null) || trading || untimed.contains(item))
			{
				continue;
			}
			int per = Math.max(1, makes.getOrDefault(item, 1));
			if (made <= 0 || made > MAX_STEP * per || now - at > GAP_MS || now <= at)
			{
				continue;
			}
			long[] s = seen.computeIfAbsent(item, k -> new long[2]);
			s[0] += made;
			s[1] += now - at;
			learned = true;
		}
		return learned;
	}

	/** Items an hour the player has really managed, or 0 when not enough is seen yet. */
	public double measured(int item)
	{
		long[] s = seen.get(item);
		if (s == null || s[0] < TRUST_ITEMS || s[1] < TRUST_MS)
		{
			return 0;
		}
		return s[0] * 3_600_000.0 / s[1];
	}

	/**
	 * How long a step's remaining items take, at the player's own pace once known, else the
	 * recipe's typical rate.
	 *
	 * @return milliseconds, or -1 when there is nothing to go on
	 */
	public long estimate(Step step)
	{
		double rate = measured(step.getItem());
		if (rate <= 0)
		{
			rate = step.getRecipe().getRate();
		}
		if (rate <= 0)
		{
			return -1;
		}
		return Math.round(step.getNet() * 3_600_000.0 / rate);
	}

	Pace repair()
	{
		if (seen == null)
		{
			seen = new HashMap<>();
		}
		seen.values().removeIf(v -> v == null || v.length != 2 || v[0] < 0 || v[1] < 0);
		seen.keySet().removeIf(k -> k == null);
		return this;
	}
}
