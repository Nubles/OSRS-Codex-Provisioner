package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.List;

/**
 * What a trip to one piece of content takes. Presets ship with the plugin and start from typical
 * numbers; kits the player saves are taken from what they actually carried.
 */
public class Kit
{
	private String id;
	private String name;
	private List<Need> needs = new ArrayList<>();
	private transient boolean preset;

	public Kit()
	{
	}

	public Kit(String id, String name, List<Need> needs, boolean preset)
	{
		this.id = id;
		this.name = name;
		this.needs = new ArrayList<>(needs);
		this.preset = preset;
	}

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public List<Need> getNeeds()
	{
		return needs;
	}

	public boolean isPreset()
	{
		return preset;
	}

	public Need need(String supply)
	{
		for (Need n : needs)
		{
			if (n.getSupply().equals(supply))
			{
				return n;
			}
		}
		return null;
	}

	/** Drops a supply from a saved kit, for things that were carried but are not supplies. */
	public boolean remove(String supply)
	{
		return needs.removeIf(n -> n.getSupply().equals(supply));
	}

	/** Restores what saved data can leave out. */
	Kit repair()
	{
		if (needs == null)
		{
			needs = new ArrayList<>();
		}
		needs.removeIf(n -> n == null || n.getSupply() == null || Supplies.get(n.getSupply()) == null || n.getPerTrip() < 0);
		return this;
	}
}
