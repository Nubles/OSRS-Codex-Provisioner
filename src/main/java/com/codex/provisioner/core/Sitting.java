package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one sitting of a given length gets through, at the player's own pace where it is known:
 * the step in hand first, then the chain in its order, each step as far as the time and its
 * inputs allow. A step that waits on an earlier one counts only what the sitting has made of its
 * inputs by then. A shop run is taken as quickly done, since the steps after it need what it buys;
 * other steps that cannot be timed, herbs grown on runs and steps above the player's levels are
 * passed over and named.
 */
public final class Sitting
{
	/** One step's share of the sitting. */
	public static final class Piece
	{
		private final Step step;
		private final long amount;
		private final long ms;

		Piece(Step step, long amount, long ms)
		{
			this.step = step;
			this.amount = amount;
			this.ms = ms;
		}

		public Step getStep()
		{
			return step;
		}

		/** Items of the step the sitting gets. */
		public long getAmount()
		{
			return amount;
		}

		/** Time they take. */
		public long getMs()
		{
			return ms;
		}

		/** Whether the sitting finishes the step. */
		public boolean finishes()
		{
			return amount >= step.getNet();
		}
	}

	private final long budget;
	private final List<Piece> pieces;
	private final long used;
	/** Open steps the sitting does not reach or cannot plan, in chain order. */
	private final List<Step> left;
	private final boolean everything;
	/** Times the chain is walked while time is left. */
	private static final int PASSES = 4;

	private Sitting(long budget, List<Piece> pieces, long used, List<Step> left, boolean everything)
	{
		this.budget = budget;
		this.pieces = pieces;
		this.used = used;
		this.left = left;
		this.everything = everything;
	}

	/**
	 * @param estimates milliseconds each open step's remaining items take, by item; -1 or missing when it cannot be timed
	 * @param grown items grown on herb runs rather than worked in a sitting
	 * @param budget the sitting's length in milliseconds
	 */
	public static Sitting plan(Chain chain, Map<Integer, Long> estimates, Set<Integer> grown, long budget)
	{
		List<Step> order = new ArrayList<>();
		Step now = chain.current();
		if (now != null && now.getState() != Step.State.DONE)
		{
			order.add(now);
		}
		for (Step s : chain.getSteps())
		{
			if (s != now && s.getState() != Step.State.DONE)
			{
				order.add(s);
			}
		}
		// Inputs still to be got: a step can use only what the sitting makes of them. Inputs whose
		// step is done are held in full, so they hold nothing back.
		Set<Integer> inChain = new java.util.HashSet<>();
		for (Step s : chain.getSteps())
		{
			if (s.getState() != Step.State.DONE)
			{
				inChain.add(s.getItem());
			}
		}

		// Each step's share so far, in the order first reached. The chain is walked again while time is
		// left, so a load caught after its cooking step was passed over is still cooked in the sitting.
		Map<Step, long[]> share = new java.util.LinkedHashMap<>();
		Map<Integer, Long> made = new HashMap<>();
		long remaining = Math.max(0, budget);
		for (int pass = 0; pass < PASSES && remaining > 0; pass++)
		{
			boolean progress = false;
			for (Step s : order)
			{
				long[] got = share.computeIfAbsent(s, k -> new long[2]);
				long still = s.getNet() - got[0];
				if (still <= 0 || s.getState() == Step.State.LOCKED)
				{
					continue;
				}
				if (s.getRecipe().getVerb() == Verb.BUY)
				{
					// A shop run cannot be timed, but it is quickly done and the steps after it need it.
					got[0] += still;
					made.merge(s.getItem(), still, Long::sum);
					progress = true;
					continue;
				}
				long est = estimates.getOrDefault(s.getItem(), -1L);
				if (grown.contains(s.getItem()) || est < 0)
				{
					continue;
				}
				long most = still;
				long fromHeld = 0;
				if (!s.getRecipe().isSource())
				{
					// What the held inputs make, and as much more as the sitting has made of what goes in.
					fromHeld = Math.max(0, s.getCanNow() - got[0]);
					long extra = Long.MAX_VALUE;
					for (Map.Entry<Integer, Integer> in : s.getRecipe().getInputs().entrySet())
					{
						if (inChain.contains(in.getKey()) && in.getValue() > 0)
						{
							extra = Math.min(extra, made.getOrDefault(in.getKey(), 0L) / in.getValue() * Math.max(1, s.getRecipe().getMakes()));
						}
					}
					most = Math.min(most, fromHeld + (extra == Long.MAX_VALUE ? 0 : extra));
				}
				double perItem = est / (double) s.getNet();
				long fits = perItem <= 0 ? most : (long) Math.floor(remaining / perItem);
				long amount = Math.min(most, fits);
				if (amount <= 0)
				{
					continue;
				}
				long ms = Math.round(amount * perItem);
				remaining -= ms;
				got[0] += amount;
				got[1] += ms;
				made.merge(s.getItem(), amount, Long::sum);
				// What came from the sitting's own work is used up, so two steps never share one load.
				long fromMade = amount - Math.min(amount, fromHeld);
				if (fromMade > 0)
				{
					int makes = Math.max(1, s.getRecipe().getMakes());
					for (Map.Entry<Integer, Integer> in : s.getRecipe().getInputs().entrySet())
					{
						if (inChain.contains(in.getKey()))
						{
							made.merge(in.getKey(), -((fromMade + makes - 1) / makes) * in.getValue(), Long::sum);
						}
					}
				}
				progress = true;
			}
			if (!progress)
			{
				break;
			}
		}

		List<Piece> pieces = new ArrayList<>();
		List<Step> left = new ArrayList<>();
		for (Step s : order)
		{
			long[] got = share.get(s);
			if (got != null && got[0] > 0)
			{
				pieces.add(new Piece(s, Math.min(s.getNet(), got[0]), got[1]));
			}
			if (got == null || got[0] < s.getNet())
			{
				left.add(s);
			}
		}
		return new Sitting(budget, Collections.unmodifiableList(pieces), budget - remaining,
			Collections.unmodifiableList(left), left.isEmpty() && !pieces.isEmpty());
	}

	/** Why a step is left after the sitting. */
	public enum Reason
	{
		/** Above the player's levels. */
		LOCKED,
		/** Grown on herb runs, dropped or otherwise not timed. */
		UNTIMED,
		/** Its inputs come from a step the sitting cannot do. */
		WAITING,
		/** The sitting ran out of time. */
		TIME
	}

	/** Why a step the sitting leaves is left; only for steps in {@link #getLeft()}. */
	public Reason reason(Step s, Map<Integer, Long> estimates, Set<Integer> grown)
	{
		if (s.getState() == Step.State.LOCKED)
		{
			return Reason.LOCKED;
		}
		if (grown.contains(s.getItem()) || estimates.getOrDefault(s.getItem(), -1L) < 0)
		{
			return Reason.UNTIMED;
		}
		// Time enough left for one more item, and still left: it waits on something the sitting cannot get.
		double perItem = s.getNet() <= 0 ? 0 : estimates.get(s.getItem()) / (double) s.getNet();
		return budget - used >= Math.max(1, perItem) ? Reason.WAITING : Reason.TIME;
	}

	public long getBudget()
	{
		return budget;
	}

	public List<Piece> getPieces()
	{
		return pieces;
	}

	/** Time the pieces take together, at most the budget. */
	public long getUsed()
	{
		return used;
	}

	/** Open steps left after the sitting, or that it could not plan. */
	public List<Step> getLeft()
	{
		return left;
	}

	/** Whether the sitting finishes every open step. */
	public boolean isEverything()
	{
		return everything;
	}
}
