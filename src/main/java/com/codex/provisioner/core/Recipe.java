package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * How one item comes to be: what goes in, how many come out, the levels it asks for, any tools
 * that are not used up, and a line on where or how to do it. An item with nothing going in is
 * gathered, caught or bought, which is where a chain starts.
 */
public final class Recipe
{
	private final int output;
	private final Verb verb;
	private int makes = 1;
	private final Map<Integer, Integer> inputs = new LinkedHashMap<>();
	private final List<Req> reqs = new ArrayList<>();
	private final List<Integer> tools = new ArrayList<>();
	private String how;

	Recipe(Verb verb, int output)
	{
		this.verb = verb;
		this.output = output;
	}

	/** A supply with nothing to make, such as a teleport tablet a kit carries as it is. */
	static Recipe stock(int itemId)
	{
		return new Recipe(Verb.STOCK, itemId).how("Bring these as they are; there is nothing to make.");
	}

	Recipe makes(int count)
	{
		makes = count;
		return this;
	}

	Recipe from(int itemId, int qty)
	{
		inputs.put(itemId, qty);
		return this;
	}

	Recipe needs(Skill skill, int level)
	{
		reqs.add(new Req(skill, level, false));
		return this;
	}

	Recipe grows(Skill skill, int level)
	{
		reqs.add(new Req(skill, level, true));
		return this;
	}

	Recipe tool(int itemId)
	{
		tools.add(itemId);
		return this;
	}

	Recipe how(String text)
	{
		how = text;
		return this;
	}

	public int getOutput()
	{
		return output;
	}

	public Verb getVerb()
	{
		return verb;
	}

	/** How many items one go makes, such as three 4-dose potions from four 3-dose ones. */
	public int getMakes()
	{
		return makes;
	}

	public Map<Integer, Integer> getInputs()
	{
		return Collections.unmodifiableMap(inputs);
	}

	public List<Req> getReqs()
	{
		return Collections.unmodifiableList(reqs);
	}

	public List<Integer> getTools()
	{
		return Collections.unmodifiableList(tools);
	}

	public String getHow()
	{
		return how;
	}

	public boolean isSource()
	{
		return inputs.isEmpty();
	}

	/** The first hard requirement the player's levels do not meet, or null. */
	public Req unmet(Map<Skill, Integer> levels)
	{
		for (Req r : reqs)
		{
			if (!r.isSoft() && !r.met(levels))
			{
				return r;
			}
		}
		return null;
	}
}
