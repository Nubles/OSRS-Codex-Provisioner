package com.codex.provisioner.core;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Skill;

/**
 * Buy or make, for accounts that can trade: what a number of a supply's product costs bought
 * finished, against what it costs made, and the experience making it gives. Each ingredient is
 * itself bought or made, whichever is cheaper, so a clean herb is bought clean when cleaning
 * saves nothing.
 *
 * <p>Ingredients the player already holds are taken first, and are not priced as if bought:
 * they cost no coins, but using them gives up what they would sell for. So a quote has two
 * prices for making: the coins still to spend, and the sale value of what comes out of the bank.
 * Buy or make is judged on both together, since buying finished leaves those ingredients to sell.
 */
public final class Costing
{
	/** The Grand Exchange's tax on a sale: two per cent, at most five million coins an item. */
	static final int TAX_PERCENT = 2;
	static final long TAX_CAP = 5_000_000L;

	/** What filling a supply one way or the other costs. */
	public static final class Quote
	{
		private final long buy;
		private final long make;
		private final long fromBank;
		private final Map<Skill, Double> xp;

		Quote(long buy, long make, long fromBank, Map<Skill, Double> xp)
		{
			this.buy = buy;
			this.make = make;
			this.fromBank = fromBank;
			this.xp = xp;
		}

		/** Coins to buy the product finished, or -1 when it has no price. */
		public long getBuy()
		{
			return buy;
		}

		/**
		 * Coins still to spend on ingredients to make it, after what is held is used, or -1 when one of
		 * them can be neither bought nor made.
		 */
		public long getMake()
		{
			return make;
		}

		/** What the held ingredients making uses would sell for, after the exchange's tax. */
		public long getFromBank()
		{
			return fromBank;
		}

		/** Making's whole cost: the coins to spend and the held ingredients given up; -1 when unknown. */
		public long getMakeTotal()
		{
			return make < 0 ? -1 : make + fromBank;
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

		/** Making is cheaper than buying, counting what the held ingredients would sell for. */
		public boolean makeIsCheaper()
		{
			return known() && getMakeTotal() < buy;
		}

		/** Coins making saves over buying, held ingredients counted at their sale value; negative when it costs more. */
		public long saving()
		{
			return known() ? buy - getMakeTotal() : 0;
		}

		/** Coins out of pocket making saves over buying, held ingredients counted as free. */
		public long cashSaving()
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
			return known() && getMakeTotal() > buy && total > 0 ? (getMakeTotal() - buy) / total : 0;
		}
	}

	/** What a branch of the recipe tree took: held items' sale value, the items taken and the experience. */
	private static final class Tally
	{
		long fromBank;
		final Map<Integer, Long> pool;
		final Map<Skill, Double> xp = new EnumMap<>(Skill.class);

		Tally(Map<Integer, Long> pool)
		{
			this.pool = pool;
		}
	}

	private Costing()
	{
	}

	/** Prices and makes {@code count} of {@code product} from bought ingredients alone. */
	public static Quote quote(int product, long count, Prices prices)
	{
		return quote(product, count, prices, new HashMap<>());
	}

	/**
	 * Prices and makes {@code count} of {@code product}, which is always made at the top.
	 *
	 * @param pool what is held and not yet spoken for; the ingredients this quote uses are taken out
	 *     of it, so quoting each supply in turn from one pool never counts a held item twice
	 */
	public static Quote quote(int product, long count, Prices prices, Map<Integer, Long> pool)
	{
		long price = prices.price(product);
		long buy = price > 0 ? price * count : -1;
		// Worked on a copy, so a quote that cannot be made takes nothing from the pool.
		Tally tally = new Tally(pool == null ? new HashMap<>() : new HashMap<>(pool));
		long make = count <= 0 ? 0 : made(Cookbook.recipe(product), count, prices, tally, 0);
		if (make >= 0 && pool != null)
		{
			pool.clear();
			pool.putAll(tally.pool);
		}
		return new Quote(buy, make, make < 0 ? 0 : tally.fromBank, tally.xp);
	}

	/** What one of an item would fetch sold on the exchange, after its tax. */
	static long saleValue(long price)
	{
		if (price <= 0)
		{
			return 0;
		}
		return price - Math.min(TAX_CAP, price * TAX_PERCENT / 100);
	}

	/**
	 * The cheapest way to have {@code count} of an item: what is held first, then bought, or made
	 * when that is cheaper. Returns the coins to spend, or -1 when it cannot be had.
	 */
	private static long cheapest(int item, long count, Prices prices, Tally tally, int depth)
	{
		long price = prices.price(item);
		long held = tally.pool.getOrDefault(item, 0L);
		long take = Math.min(count, Math.max(0, held));
		if (take > 0)
		{
			tally.pool.put(item, held - take);
			tally.fromBank += take * saleValue(price);
			count -= take;
		}
		if (count <= 0)
		{
			return 0;
		}
		long buy = price > 0 ? price * count : -1;
		Recipe r = Cookbook.recipe(item);
		if (r.isSource() || depth > 8)
		{
			return buy;
		}
		// Try making it on a copy of the pool, and keep that only if it wins.
		Tally trial = new Tally(new HashMap<>(tally.pool));
		long make = made(r, count, prices, trial, depth + 1);
		if (make >= 0 && (buy < 0 || make + trial.fromBank < buy))
		{
			tally.pool.clear();
			tally.pool.putAll(trial.pool);
			tally.fromBank += trial.fromBank;
			trial.xp.forEach((k, v) -> tally.xp.merge(k, v, Double::sum));
			return make;
		}
		return buy;
	}

	private static long made(Recipe r, long count, Prices prices, Tally tally, int depth)
	{
		long crafts = (count + r.getMakes() - 1) / r.getMakes();
		long total = 0;
		for (Map.Entry<Integer, Integer> in : r.getInputs().entrySet())
		{
			long c = cheapest(in.getKey(), crafts * in.getValue(), prices, tally, depth);
			if (c < 0)
			{
				return -1;
			}
			total += c;
		}
		if (r.getXpSkill() != null && r.getXp() > 0)
		{
			tally.xp.merge(r.getXpSkill(), crafts * r.getXp(), Double::sum);
		}
		return total;
	}
}
