package com.codex.provisioner.ui;

import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Costing;
import com.codex.provisioner.core.HerbRuns;
import com.codex.provisioner.core.Kit;
import com.codex.provisioner.core.Loads;
import com.codex.provisioner.core.Supply;
import com.codex.provisioner.core.Training;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
		/** Whole trips what is held covers now, or -1 when the kit uses nothing up. */
		long ready = -1;

		public KitSummary(Kit kit, int steps, int shortSupplies, int measured)
		{
			this.kit = kit;
			this.steps = steps;
			this.shortSupplies = shortSupplies;
			this.measured = measured;
		}

		public KitSummary ready(long trips)
		{
			this.ready = trips;
			return this;
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
	/** An iron account, which makes everything and is shown no prices. */
	boolean iron = true;
	Set<String> bought = Collections.emptySet();
	/** Buy or make for each supply still to fill, for accounts that can trade. */
	Map<String, Costing.Quote> quotes = Collections.emptyMap();
	/** Each open step's time left in milliseconds, -1 when it cannot be told. */
	Map<Integer, Long> estimates = Collections.emptyMap();
	/** Whether a step's estimate comes from the player's own pace rather than a typical one. */
	Map<Integer, Boolean> measuredPace = Collections.emptyMap();
	/** How the step in hand splits into inventories, or null. */
	Loads loads;
	/** What the rest of the chain trains, the most experience first. */
	List<Training.Gain> training = Collections.emptyList();
	/** Herb runs for each grimy herb still to grow, by the grimy herb. */
	Map<Integer, HerbRuns.Plan> herbRuns = Collections.emptyMap();
	/** The bank as last seen is the one Codex Bank Steward shared. */
	boolean bankShared;

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

	public View trade(boolean iron, Set<String> bought, Map<String, Costing.Quote> quotes)
	{
		this.iron = iron;
		this.bought = bought == null ? Collections.emptySet() : new HashSet<>(bought);
		this.quotes = quotes == null ? Collections.emptyMap() : quotes;
		return this;
	}

	public View pace(Map<Integer, Long> estimates, Map<Integer, Boolean> measured)
	{
		this.estimates = estimates == null ? Collections.emptyMap() : estimates;
		this.measuredPace = measured == null ? Collections.emptyMap() : measured;
		return this;
	}

	public View work(Loads loads, List<Training.Gain> training)
	{
		this.loads = loads;
		this.training = training == null ? Collections.emptyList() : training;
		return this;
	}

	public View herbs(Map<Integer, HerbRuns.Plan> plans)
	{
		this.herbRuns = plans == null ? Collections.emptyMap() : plans;
		return this;
	}

	public View shared(boolean bankShared)
	{
		this.bankShared = bankShared;
		return this;
	}

	/** The herb runs a step takes, or null when it is not grown in herb patches. */
	HerbRuns.Plan herbRuns(int itemId)
	{
		return herbRuns.get(itemId);
	}

	/** Herb runs, said the way a player would: "1 herb run", "3 herb runs". */
	static String runs(long n)
	{
		return n + (n == 1 ? " herb run" : " herb runs");
	}

	/** A step's time left, or -1 when it cannot be told. */
	long estimate(int itemId)
	{
		return estimates.getOrDefault(itemId, -1L);
	}

	boolean ownPace(int itemId)
	{
		return measuredPace.getOrDefault(itemId, false);
	}

	String name(int itemId)
	{
		String n = names.get(itemId);
		return n == null ? "Item " + itemId : n;
	}

	/**
	 * A name short enough for a row: "Ranarr potion (unf)" reads as "Ranarr (unf)", since the
	 * vial is plain from the step around it.
	 */
	String shortName(int itemId)
	{
		String n = name(itemId).replace(" potion (unf)", " (unf)");
		// "Grimy ranarr weed" is plain as "Grimy ranarr"; dwarf weed is the herb's whole name.
		return n.startsWith("Grimy ") && n.endsWith(" weed") && !n.endsWith("dwarf weed") ? n.substring(0, n.length() - 5) : n;
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
