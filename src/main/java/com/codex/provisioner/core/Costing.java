package com.codex.provisioner.core;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Buy or make, for accounts that can trade: what a number of a supply's product costs bought
 * finished, against what it costs made from bought ingredients, and the experience making it
 * gives. Each ingredient is itself bought or made, whichever is cheaper, so a clean herb is
 * bought clean when cleaning saves nothing.
 *
 * <p>What the player already holds is not counted here: the question is how to fill the rest.
 */
public final class Costing
{
	/** What filling a supply one way or the other costs. */
	public static final class Quote
	{
		private final long buy;
		private final long make;
		private final Map<Skill, Double> xp;

		Quote(long buy, long make, Map<Skill, Double> xp)
		{
			this.buy = buy;
			this.make = make;
			this.xp = xp;
		}

		/** Coins to buy the product finished, or -1 when it has no price. */
		public long getBuy()
		{
			return buy;
		}

		/** Coins for the ingredients, or -1 when one of them can be neither bought nor made. */
		public long getMake()
		{
			return make;
		}

		/** Experience making it gives, by skill. */
		public Map<Skill, Double> getXp()
		{
			return xp;
		}

		public double totalXp()
		{
			double sum = 0;
			for (double v : xp.values())
			{
				sum += v;
			}
			return sum;
		}

		public boolean known()
		{
			return buy >= 0 && make >= 0;
		}

		/** Making is cheaper than buying. */
		public boolean makeIsCheaper()
		{
			return known() && make < buy;
		}

		/** Coins making saves over buying; negative when it costs more. */
		public long saving()
		{
			return known() ? buy - make : 0;
		}

		/**
		 * Coins each experience point costs when making costs more than buying, the way players
		 * judge a training method; 0 when making saves money or gives no experience.
		 */
		public double coinsPerXp()
		{
			double total = totalXp();
			return known() && make > buy && total > 0 ? (make - buy) / total : 0;
		}
	}

	private Costing()
	{
	}

	/** Prices and makes {@code count} of {@code product}, which is always made at the top. */
	public static Quote quote(int product, long count, Prices prices)
	{
		long price = prices.price(product);
		long buy = price > 0 ? price * count : -1;
		Map<Skill, Double> xp = new EnumMap<>(Skill.class);
		long make = count <= 0 ? 0 : made(Cookbook.recipe(product), count, prices, xp, 0);
		return new Quote(buy, make, xp);
	}

	/** The cheapest way to have {@code count} of an item: bought, or made when that is cheaper. */
	private static long cheapest(int item, long count, Prices prices, Map<Skill, Double> xp, int depth)
	{
		long price = prices.price(item);
		long buy = price > 0 ? price * count : -1;
		Recipe r = Cookbook.recipe(item);
		if (r.isSource() || depth > 8)
		{
			return buy;
		}
		Map<Skill, Double> madeXp = new EnumMap<>(Skill.class);
		long make = made(r, count, prices, madeXp, depth + 1);
		if (make >= 0 && (buy < 0 || make < buy))
		{
			madeXp.forEach((k, v) -> xp.merge(k, v, Double::sum));
			return make;
		}
		return buy;
	}

	private static long made(Recipe r, long count, Prices prices, Map<Skill, Double> xp, int depth)
	{
		long crafts = (count + r.getMakes() - 1) / r.getMakes();
		long total = 0;
		for (Map.Entry<Integer, Integer> in : r.getInputs().entrySet())
		{
			long c = cheapest(in.getKey(), crafts * in.getValue(), prices, xp, depth);
			if (c < 0)
			{
				return -1;
			}
			total += c;
		}
		if (r.getXpSkill() != null && r.getXp() > 0)
		{
			xp.merge(r.getXpSkill(), crafts * r.getXp(), Double::sum);
		}
		return total;
	}
}
