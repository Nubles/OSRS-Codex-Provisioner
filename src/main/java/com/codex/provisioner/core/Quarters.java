package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What Codex Quartermaster keeps for the account, read as it saved it: the kits the player saved,
 * the kit they last chose, and what each kit has been measured using on real trips. The
 * Provisioner only reads it; without Quartermaster it works from the presets alone.
 */
public class Quarters
{
	/** Trips before Quartermaster trusts its measured amounts; the Provisioner does the same. */
	static final int TRUSTED = 2;

	private List<Kit> kits = new ArrayList<>();
	private String active;
	private Map<String, Usage> usage;

	static final class Usage
	{
		private int trips;
		private Map<String, Double> perTrip;
	}

	public static Quarters none()
	{
		return new Quarters();
	}

	/** The player's own kits, then the presets. */
	public List<Kit> allKits()
	{
		List<Kit> all = new ArrayList<>();
		if (kits != null)
		{
			for (Kit k : kits)
			{
				if (k != null && k.getId() != null && k.getName() != null)
				{
					all.add(k.repair());
				}
			}
		}
		all.addAll(Presets.all());
		return all;
	}

	public boolean hasSavedKits()
	{
		return kits != null && !kits.isEmpty();
	}

	/** The kit last chosen in Quartermaster, or null. */
	public String getActive()
	{
		return active;
	}

	/** What a kit has really used a trip, once measured over enough trips; empty otherwise. */
	public Map<String, Double> measured(String kitId)
	{
		Usage u = usage == null ? null : usage.get(kitId);
		if (u == null || u.trips < TRUSTED || u.perTrip == null)
		{
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(u.perTrip);
	}

	public int measuredTrips(String kitId)
	{
		Usage u = usage == null ? null : usage.get(kitId);
		return u == null ? 0 : u.trips;
	}
}
