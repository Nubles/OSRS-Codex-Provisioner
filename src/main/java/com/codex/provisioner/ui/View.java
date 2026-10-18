package com.codex.provisioner.ui;

import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Kit;
import com.codex.provisioner.core.Supply;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/** Everything the panel shows, worked out on the client thread and handed over whole. */
public final class View
{
	public enum State
	{
		LOGGED_OUT,
		WAITING_FOR_BANK,
		READY
	}

	/** A kit in the Kits tab with what its chain would take. */
	public static final class KitSummary
	{
		final Kit kit;
		final int steps;
		final int shortSupplies;
		final int measured;

		public KitSummary(Kit kit, int steps, int shortSupplies, int measured)
		{
			this.kit = kit;
			this.steps = steps;
			this.shortSupplies = shortSupplies;
			this.measured = measured;
		}
	}

	final State state;
	final Chain chain;
	final List<KitSummary> kits;
	final Map<Integer, String> names;
	final Map<Integer, Long> held;
	final Map<Skill, Integer> levels;
	final boolean quartermaster;
	final int measured;
	final long bankSeen;
	final boolean bankOpen;
	final long now;

	public View(State state, Chain chain, List<KitSummary> kits, Map<Integer, String> names, Map<Integer, Long> held,
		Map<Skill, Integer> levels, boolean quartermaster, int measured, long bankSeen, boolean bankOpen, long now)
	{
		this.state = state;
		this.chain = chain;
		this.kits = kits;
		this.names = names;
		this.held = held;
		this.levels = levels;
		this.quartermaster = quartermaster;
		this.measured = measured;
		this.bankSeen = bankSeen;
		this.bankOpen = bankOpen;
		this.now = now;
	}

	public static View loggedOut()
	{
		return new View(State.LOGGED_OUT, null, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
			Collections.emptyMap(), false, 0, 0, false, 0);
	}

	String name(int itemId)
	{
		String n = names.get(itemId);
		return n == null ? "Item " + itemId : n;
	}

	String name(Supply supply)
	{
		return supply.getName() != null ? supply.getName() : name(supply.getIcon());
	}

	/** " hp", " doses" or "" for supplies counted in items. */
	static String unit(Supply supply)
	{
		return supply.getUnit() == null ? "" : " " + supply.getUnit();
	}

	long held(int itemId)
	{
		return held.getOrDefault(itemId, 0L);
	}

	Integer level(Skill skill)
	{
		return levels.get(skill);
	}
}
