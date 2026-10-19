package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;

/**
 * The whole supply chain for a kit and a number of trips. Each supply short of the target is
 * made as one product, and its recipe is followed down to what has to be gathered. What is
 * already held at any stage, in the bank or carried, is taken off before going further down,
 * so a bank of clean herbs needs no grimy ones.
 *
 * <p>Steps come in the order they are done: for each supply in kit order, inputs before what is
 * made from them, so gathering always leads. A shared input, such as vials of water, appears once
 * with the total every chain needs.
 */
public final class Chain
{
	/** One supply of the kit against the target. */
	public static final class Line
	{
		private final Supply supply;
		private final long want;
		private final long have;
		private final int product;
		private final long toMake;
		private final List<Integer> options;
		private final double perTrip;

		Line(Supply supply, long want, long have, int product, long toMake, List<Integer> options, double perTrip)
		{
			this.perTrip = perTrip;
			this.supply = supply;
			this.want = want;
			this.have = have;
			this.product = product;
			this.toMake = toMake;
			this.options = options;
		}

		public Supply getSupply()
		{
			return supply;
		}

		/** Units the target number of trips takes: hitpoints, doses or items. */
		public long getWant()
		{
			return want;
		}

		public long getHave()
		{
			return have;
		}

		public long getShort()
		{
			return Math.max(0, want - have);
		}

		/** The item this supply is made as. */
		public int getProduct()
		{
			return product;
		}

		/** How many of the product are still to make. */
		public long getToMake()
		{
			return toMake;
		}

		/** Units one trip takes. */
		public double getPerTrip()
		{
			return perTrip;
		}

		/** Whole trips what is held already covers, or -1 when a trip takes none. */
		public long tripsHeld()
		{
			return perTrip <= 0 ? -1 : (long) Math.floor(have / perTrip + 1e-9);
		}

		/** The items this supply can be made as, best first; empty when there is nothing to choose. */
		public List<Integer> getOptions()
		{
			return options;
		}
	}

	private final Kit kit;
	private final int trips;
	private final List<Line> lines;
	private final List<Step> steps;

	private Chain(Kit kit, int trips, List<Line> lines, List<Step> steps)
	{
		this.kit = kit;
		this.trips = trips;
		this.lines = lines;
		this.steps = steps;
	}

	/**
	 * @param perTrip what a trip uses of each supply, from the kit or measured trips
	 * @param choices the product picked for a supply; supplies left out get the best one the levels reach
	 * @param held everything in the bank and carried
	 * @param levels the player's levels; empty when not known, which locks nothing
	 * @param baselines the most each item has had to go since the plan began, or empty
	 */
	public static Chain build(Kit kit, Map<String, Double> perTrip, int trips, Map<String, Integer> choices,
		Map<Integer, Long> held, Map<Skill, Integer> levels, Map<Integer, Long> baselines)
	{
		return build(kit, perTrip, trips, choices, Collections.emptySet(), held, levels, baselines);
	}

	/**
	 * @param bought supplies the player buys finished on the Grand Exchange instead of making; each
	 *     is one buying step with nothing below it
	 */
	public static Chain build(Kit kit, Map<String, Double> perTrip, int trips, Map<String, Integer> choices,
		Set<String> bought, Map<Integer, Long> held, Map<Skill, Integer> levels, Map<Integer, Long> baselines)
	{
		Set<Integer> boughtItems = new HashSet<>();
		List<Line> lines = new ArrayList<>();
		Map<Integer, Long> demand = new HashMap<>();
		Map<Integer, String> home = new HashMap<>();
		List<Integer> order = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();

		for (Need need : kit.getNeeds())
		{
			Supply supply = Supplies.get(need.getSupply());
			if (supply == null)
			{
				continue;
			}
			double per = perTrip.getOrDefault(need.getSupply(), (double) need.getPerTrip());
			long want = (long) Math.ceil(per * trips - 1e-9);
			long have = supply.count(held);
			List<Integer> options = new ArrayList<>();
			int product;
			int units;
			if (supply.isItem())
			{
				product = supply.getIcon();
				units = 1;
			}
			else
			{
				for (int option : Cookbook.products(supply.getKey()))
				{
					if (supply.getUnits().containsKey(option))
					{
						options.add(option);
					}
				}
				product = choose(options, choices.get(supply.getKey()), levels, supply.getIcon());
				units = Math.max(1, supply.getUnits().getOrDefault(product, 1));
			}
			long toMake = (long) Math.ceil(Math.max(0, want - have) / (double) units);
			lines.add(new Line(supply, want, have, product, toMake, Collections.unmodifiableList(options), Math.max(0, per)));
			// What is held of the product itself is already counted in the supply, so add it back.
			if (toMake > 0)
			{
				demand.merge(product, toMake + held.getOrDefault(product, 0L), Long::sum);
			}
			if (bought.contains(supply.getKey()) && !supply.isItem())
			{
				boughtItems.add(product);
			}
			visit(product, supply.getKey(), order, seen, home, boughtItems);
		}

		// Parents before children, so every demand on an item is in before it is passed down.
		Map<Integer, Long> net = new HashMap<>();
		for (int i = order.size() - 1; i >= 0; i--)
		{
			int item = order.get(i);
			long d = demand.getOrDefault(item, 0L);
			long n = Math.max(0, d - held.getOrDefault(item, 0L));
			net.put(item, n);
			Recipe r = recipe(item, boughtItems);
			long crafts = (n + r.getMakes() - 1) / r.getMakes();
			if (crafts > 0)
			{
				for (Map.Entry<Integer, Integer> in : r.getInputs().entrySet())
				{
					demand.merge(in.getKey(), crafts * in.getValue(), Long::sum);
				}
			}
		}

		List<Step> steps = new ArrayList<>();
		for (int item : order)
		{
			long d = demand.getOrDefault(item, 0L);
			long base = baselines.getOrDefault(item, 0L);
			if (d <= 0 && base <= 0)
			{
				continue;
			}
			Recipe r = recipe(item, boughtItems);
			long n = net.getOrDefault(item, 0L);
			long have = held.getOrDefault(item, 0L);
			long canNow = r.isSource() ? n : Math.min(n, makeable(r, held));
			Req unmet = r.unmet(levels);
			Step.State state;
			if (n <= 0)
			{
				state = Step.State.DONE;
			}
			else if (unmet != null)
			{
				state = Step.State.LOCKED;
			}
			else if (canNow > 0)
			{
				state = Step.State.READY;
			}
			else
			{
				state = Step.State.WAITING;
			}
			steps.add(new Step(r, home.get(item), n, have, canNow, state, unmet, Math.max(base, n)));
		}
		return new Chain(kit, trips, Collections.unmodifiableList(lines), Collections.unmodifiableList(steps));
	}

	private static void visit(int item, String supply, List<Integer> order, Set<Integer> seen, Map<Integer, String> home,
		Set<Integer> bought)
	{
		if (!seen.add(item))
		{
			return;
		}
		home.put(item, supply);
		for (int input : recipe(item, bought).getInputs().keySet())
		{
			visit(input, supply, order, seen, home, bought);
		}
		order.add(item);
	}

	/** An item's recipe, or a plain buying step when its supply is bought finished. */
	private static Recipe recipe(int item, Set<Integer> bought)
	{
		return bought.contains(item) ? Recipe.buy(item) : Cookbook.recipe(item);
	}

	private static long makeable(Recipe r, Map<Integer, Long> held)
	{
		long goes = Long.MAX_VALUE;
		for (Map.Entry<Integer, Integer> in : r.getInputs().entrySet())
		{
			goes = Math.min(goes, held.getOrDefault(in.getKey(), 0L) / in.getValue());
		}
		return goes == Long.MAX_VALUE ? 0 : goes * r.getMakes();
	}

	/** The picked product when it is still an option, else the first one every step of which the levels reach. */
	static int choose(List<Integer> options, Integer picked, Map<Skill, Integer> levels, int fallback)
	{
		if (options.isEmpty())
		{
			return fallback;
		}
		if (picked != null && options.contains(picked))
		{
			return picked;
		}
		for (int option : options)
		{
			if (reachable(option, levels, new HashSet<>()))
			{
				return option;
			}
		}
		return options.get(0);
	}

	/** Whether the levels reach every hard requirement in an item's chain. */
	public static boolean reachable(int item, Map<Skill, Integer> levels, Set<Integer> seen)
	{
		if (!seen.add(item))
		{
			return true;
		}
		Recipe r = Cookbook.recipe(item);
		if (r.unmet(levels) != null)
		{
			return false;
		}
		for (int input : r.getInputs().keySet())
		{
			if (!reachable(input, levels, seen))
			{
				return false;
			}
		}
		return true;
	}

	/** The highest level an item's chain asks for in each skill, hard requirements only. */
	public static Map<Skill, Integer> requirements(int item)
	{
		Map<Skill, Integer> out = new LinkedHashMap<>();
		collect(item, out, new HashSet<>());
		return out;
	}

	private static void collect(int item, Map<Skill, Integer> out, Set<Integer> seen)
	{
		if (!seen.add(item))
		{
			return;
		}
		Recipe r = Cookbook.recipe(item);
		for (Req req : r.getReqs())
		{
			if (!req.isSoft())
			{
				out.merge(req.getSkill(), req.getLevel(), Math::max);
			}
		}
		for (int input : r.getInputs().keySet())
		{
			collect(input, out, seen);
		}
	}

	public Kit getKit()
	{
		return kit;
	}

	public int getTrips()
	{
		return trips;
	}

	public List<Line> getLines()
	{
		return lines;
	}

	public List<Step> getSteps()
	{
		return steps;
	}

	/**
	 * The step to work on now. Something that can be made from what is held comes first, so a
	 * load of raw fish is cooked before the next is caught; then the first thing to gather; then
	 * the first step not done. Null when everything is provisioned.
	 */
	public Step current()
	{
		Step source = null;
		Step open = null;
		for (Step s : steps)
		{
			if (s.getState() == Step.State.READY)
			{
				if (!s.getRecipe().isSource())
				{
					return s;
				}
				if (source == null)
				{
					source = s;
				}
			}
			if (open == null && s.getState() != Step.State.DONE)
			{
				open = s;
			}
		}
		return source != null ? source : open;
	}

	/** 1-based position of a step among all of them. */
	public int number(Step step)
	{
		return steps.indexOf(step) + 1;
	}

	public int doneCount()
	{
		int n = 0;
		for (Step s : steps)
		{
			if (s.getState() == Step.State.DONE)
			{
				n++;
			}
		}
		return n;
	}

	public boolean provisioned()
	{
		for (Line l : lines)
		{
			if (l.getShort() > 0)
			{
				return false;
			}
		}
		return true;
	}

	/** Overall progress: each step counts the same, partly done steps by their share. */
	public double progress()
	{
		if (steps.isEmpty())
		{
			return provisioned() ? 1 : 0;
		}
		double sum = 0;
		for (Step s : steps)
		{
			sum += s.progress();
		}
		return sum / steps.size();
	}

	/**
	 * The supply that runs out first: the one whose holdings cover the fewest whole trips, the
	 * first in the kit on a tie. Null when no supply is used up on a trip.
	 */
	public Line limiting()
	{
		Line first = null;
		for (Line l : lines)
		{
			if (l.tripsHeld() >= 0 && (first == null || l.tripsHeld() < first.tripsHeld()))
			{
				first = l;
			}
		}
		return first;
	}

	/** Whole trips the kit could go on now with what is held, or -1 when no supply is used up. */
	public long readyTrips()
	{
		Line first = limiting();
		return first == null ? -1 : first.tripsHeld();
	}

	/** The things to gather or buy, with how many, for the gathering list. */
	public List<Step> sources()
	{
		List<Step> out = new ArrayList<>();
		for (Step s : steps)
		{
			if (s.getRecipe().isSource() && s.getState() != Step.State.DONE)
			{
				out.add(s);
			}
		}
		return out;
	}
}
