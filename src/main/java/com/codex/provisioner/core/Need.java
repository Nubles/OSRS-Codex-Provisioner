package com.codex.provisioner.core;

/** One supply a kit takes, and how much of it a trip is planned to use. */
public class Need
{
	private String supply;
	private int perTrip;

	public Need()
	{
	}

	public Need(String supply, int perTrip)
	{
		this.supply = supply;
		this.perTrip = perTrip;
	}

	public String getSupply()
	{
		return supply;
	}

	public int getPerTrip()
	{
		return perTrip;
	}
}
