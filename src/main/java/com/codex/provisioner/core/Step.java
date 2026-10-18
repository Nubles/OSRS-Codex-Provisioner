package com.codex.provisioner.core;

import java.util.Map;

/** One thing to do in the chain: gather, catch or make some number of one item. */
public final class Step
{
	public enum State
	{
		/** Enough is held, now or for everything above it in the chain. */
		DONE,
		/** Can be worked on now: a source, or something with inputs to hand. */
		READY,
		/** Waits for an earlier step to supply its inputs. */
		WAITING,
		/** The player's levels do not reach it yet. */
		LOCKED
	}

	private final Recipe recipe;
	private final String supply;
	private final long net;
	private final long held;
	private final long canNow;
	private final State state;
	private final Req unmet;
	private final long baseline;

	Step(Recipe recipe, String supply, long net, long held, long canNow, State state, Req unmet, long baseline)
	{
		this.recipe = recipe;
		this.supply = supply;
		this.net = net;
		this.held = held;
		this.canNow = canNow;
		this.state = state;
		this.unmet = unmet;
		this.baseline = baseline;
	}

	public int getItem()
	{
		return recipe.getOutput();
	}

	public Recipe getRecipe()
	{
		return recipe;
	}

	/** The kit supply whose chain this step was first reached from. */
	public String getSupply()
	{
		return supply;
	}

	/** Items still to get. */
	public long getNet()
	{
		return net;
	}

	public long getHeld()
	{
		return held;
	}

	/** How many could be made from what is held right now, at most what is still to get. */
	public long getCanNow()
	{
		return canNow;
	}

	public State getState()
	{
		return state;
	}

	/** The requirement holding a locked step back, or null. */
	public Req getUnmet()
	{
		return unmet;
	}

	/** The most this step has ever had to go, so progress has a fixed end. */
	public long getBaseline()
	{
		return baseline;
	}

	public long getDone()
	{
		return Math.max(0, baseline - net);
	}

	public double progress()
	{
		if (state == State.DONE || baseline <= 0)
		{
			return 1;
		}
		return Math.max(0, Math.min(1, (baseline - net) / (double) baseline));
	}

	/** How many of each tool is held, for "bring" lines; tools are never used up. */
	public boolean hasTool(int tool, Map<Integer, Long> holdings)
	{
		return holdings.getOrDefault(tool, 0L) > 0;
	}
}
