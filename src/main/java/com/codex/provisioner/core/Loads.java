package com.codex.provisioner.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * How a step splits into bank trips: what to take out of the bank for each inventory, and how
 * many inventories the step has left. A mix of an unfinished potion and its secondary fits
 * fourteen of each; cleaning herbs fits twenty-eight; a tool takes a slot, and a stack, such as
 * Zulrah's scales or the bait for karambwans, takes one however big it is.
 *
 * <p>An inventory has to hold the inputs before the work and what is made after it, so whichever
 * of the two takes more room sets how many goes fit.
 */
public final class Loads
{
	public static final int SLOTS = 28;

	private final long perLoad;
	private final long loads;
	private final Map<Integer, Long> withdraw;

	private Loads(long perLoad, long loads, Map<Integer, Long> withdraw)
	{
		this.perLoad = perLoad;
		this.loads = loads;
		this.withdraw = withdraw;
	}

	/**
	 * @param stackable whether an item stacks in one slot
	 * @return the split, or null for a step with no inventories to fill: done, farmed, bought, or
	 *     a stock to bring as it is
	 */
	public static Loads of(Step step, IntPredicate stackable)
	{
		Recipe r = step.getRecipe();
		Verb verb = r.getVerb();
		if (step.getState() == Step.State.DONE || step.getNet() <= 0 || verb == Verb.BUY || verb == Verb.STOCK
			|| (r.isSource() && verb != Verb.CATCH))
		{
			return null;
		}
		long crafts = (step.getNet() + r.getMakes() - 1) / r.getMakes();
		int fixed = r.getTools().size();
		int in = 0;
		for (Map.Entry<Integer, Integer> e : r.getInputs().entrySet())
		{
			if (stackable.test(e.getKey()))
			{
				fixed++;
			}
			else
			{
				in += e.getValue();
			}
		}
		boolean stackedOutput = stackable.test(r.getOutput());
		int out = stackedOutput ? 0 : r.getMakes();
		if (stackedOutput)
		{
			fixed++;
		}
		int room = SLOTS - fixed;
		int each = Math.max(in, out);
		if (room <= 0)
		{
			return null;
		}
		long perLoad = each == 0 ? crafts : Math.min(crafts, room / each);
		if (perLoad <= 0)
		{
			return null;
		}
		long loads = (crafts + perLoad - 1) / perLoad;
		Map<Integer, Long> withdraw = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> e : r.getInputs().entrySet())
		{
			withdraw.put(e.getKey(), perLoad * e.getValue());
		}
		return new Loads(perLoad * r.getMakes(), loads, Collections.unmodifiableMap(withdraw));
	}

	/** Items one full inventory makes or catches. */
	public long getPerLoad()
	{
		return perLoad;
	}

	/** Inventories still to go for the whole step, the one in hand included. */
	public long getLoads()
	{
		return loads;
	}

	/** What to take out of the bank for one full inventory, by item, in recipe order; tools aside. */
	public Map<Integer, Long> getWithdraw()
	{
		return withdraw;
	}
}
