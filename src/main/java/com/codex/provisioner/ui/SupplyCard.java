package com.codex.provisioner.ui;

import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Costing;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** One supply of the kit: how much of the target is held, and the items it can be made as to pick from. */
class SupplyCard extends Card
{
	SupplyCard(Chain.Line line, View view, ItemIcons icons, Actions actions, int width)
	{
		super(new BorderLayout(0, 6));
		boolean stocked = line.getShort() <= 0;
		accent(stocked ? Theme.GREEN : Theme.GOLD);
		add(new Gauge(line, view), BorderLayout.NORTH);
		if (line.getOptions().size() > 1)
		{
			JPanel chips = new JPanel(new GridLayout(0, 4, 4, 4));
			chips.setOpaque(false);
			String key = line.getSupply().getKey();
			for (int option : line.getOptions())
			{
				JPanel cell = new JPanel(new BorderLayout());
				cell.setOpaque(false);
				cell.add(new OptionChip(option, option == line.getProduct(), view, icons, () -> actions.choose(key, option)), BorderLayout.WEST);
				chips.add(cell);
			}
			add(chips, BorderLayout.CENTER);
		}
		String key = line.getSupply().getKey();
		boolean bought = view.bought.contains(key);
		String made = stocked
			? "Stocked for " + view.chain.getTrips() + " trips."
			: (bought ? "Buy " : "Make ") + Format.amount(line.getToMake()) + " " + view.name(line.getProduct()) + ".";
		Costing.Quote quote = stocked ? null : view.quotes.get(key);
		if (quote == null)
		{
			add(Ui.wrapped(made, Theme.small(), stocked ? Theme.GREEN : Theme.TEXT_MUTED, width), BorderLayout.SOUTH);
			return;
		}
		StackPanel south = new StackPanel(4, 0);
		south.add(Ui.wrapped(made, Theme.small(), Theme.TEXT_MUTED, width));
		south.add(new Ledger(quote, bought));
		if (quote.getMake() >= 0 && quote.getFromBank() > 0)
		{
			// Making's coins are only what is still to buy; what comes out of the bank is said apart.
			south.add(Ui.wrapped("Make is coins still to spend. It also uses " + Format.coins(quote.getFromBank())
				+ " of what you hold, at what it would sell for.", Theme.small(), Theme.TEAL, width));
		}
		south.add(Ui.wrapped(verdict(quote, bought), Theme.small(), Theme.TEXT_FAINT, width));
		// Something the exchange does not sell cannot be bought instead.
		if (bought || quote.getBuy() >= 0)
		{
			JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			buttons.setOpaque(false);
			buttons.add(new CodexButton(bought ? "Make them instead" : "Buy them instead", false, () -> actions.buy(key, !bought)));
			south.add(buttons);
		}
		add(south, BorderLayout.SOUTH);
	}

	/** What the numbers mean for this supply, in a line. */
	static String verdict(Costing.Quote q, boolean bought)
	{
		String xp = xpText(q);
		if (!q.known())
		{
			return q.getBuy() < 0 ? "No exchange price for it, so making is the way." : "Some ingredients have no exchange price.";
		}
		if (bought)
		{
			return q.makeIsCheaper()
				? "Making would save " + Format.coins(q.saving()) + (xp.isEmpty() ? "." : " and give " + xp + ".")
				: "Buying is cheaper by " + Format.coins(-q.saving()) + ".";
		}
		if (q.getFromBank() > 0)
		{
			return withHeld(q, xp);
		}
		if (q.makeIsCheaper())
		{
			return "Making saves " + Format.coins(q.saving()) + (xp.isEmpty() ? "." : " and gives " + xp + ".");
		}
		if (q.coinsPerXp() > 0)
		{
			return "Making costs " + Format.coins(-q.saving()) + " more, " + String.format(java.util.Locale.ENGLISH, "%.1f", q.coinsPerXp())
				+ " gp a point for " + xp + ".";
		}
		return "Buying is cheaper by " + Format.coins(-q.saving()) + ".";
	}

	/**
	 * The verdict when making uses ingredients already held. Those cost no coins, but buying
	 * finished would leave them to sell, so the fair comparison counts them at their sale value;
	 * the coins alone are said too when the two disagree.
	 */
	static String withHeld(Costing.Quote q, String xp)
	{
		String gives = xp.isEmpty() ? "." : ", and gives " + xp + ".";
		if (q.makeIsCheaper())
		{
			return "Making saves " + Format.coins(q.saving()) + " even counting what your ingredients would sell for" + gives;
		}
		if (q.cashSaving() > 0)
		{
			return "Making spends " + Format.coins(q.cashSaving()) + " fewer coins, but selling what you hold and buying them finished comes out "
				+ Format.coins(-q.saving()) + " ahead.";
		}
		return "Buying is cheaper by " + Format.coins(-q.cashSaving()) + ", even counting what you hold as free.";
	}

	/** "4.2k Herblore xp", or "4.2k Herblore and 2k Cooking xp". */
	static String xpText(Costing.Quote q)
	{
		StringBuilder text = new StringBuilder();
		for (java.util.Map.Entry<net.runelite.api.Skill, Double> e : q.getXp().entrySet())
		{
			if (e.getValue() < 1)
			{
				continue;
			}
			if (text.length() > 0)
			{
				text.append(" and ");
			}
			text.append(Format.amount(Math.round(e.getValue()))).append(' ').append(Ui.skill(e.getKey()));
		}
		return text.length() == 0 ? "" : text.append(" xp").toString();
	}

	/** Buy against make, side by side, the cheaper one lit. */
	private static final class Ledger extends JComponent
	{
		private final Costing.Quote quote;
		private final boolean bought;

		Ledger(Costing.Quote quote, boolean bought)
		{
			this.quote = quote;
			this.bought = bought;
			setOpaque(false);
			setPreferredSize(new Dimension(10, 30));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int half = (w - 6) / 2;
			boolean makeWins = quote.makeIsCheaper() || quote.getBuy() < 0;
			cell(g, 0, half, "BUY", quote.getBuy() < 0 ? "-" : Format.coins(quote.getBuy()), bought, !makeWins && quote.known());
			cell(g, half + 6, half, "MAKE", quote.getMake() < 0 ? "-" : Format.coins(quote.getMake()), !bought, makeWins && quote.getMake() >= 0);
			g.dispose();
		}

		private static void cell(Graphics2D g, int x, int w, String label, String value, boolean chosen, boolean cheaper)
		{
			java.awt.geom.RoundRectangle2D r = new java.awt.geom.RoundRectangle2D.Float(x + 0.5f, 0.5f, w - 1, 29, 6, 6);
			g.setColor(Theme.INK_DEEP);
			g.fill(r);
			g.setColor(chosen ? Theme.alpha(Theme.GOLD, 170) : Theme.STROKE_SOFT);
			g.draw(r);
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			g.setColor(chosen ? Theme.GOLD : Theme.TEXT_FAINT);
			g.drawString(label, x + 6, 3 + small.getAscent());
			if (chosen)
			{
				// The way the chain is planned: a small gilt pip beside the label.
				g.setColor(Theme.GOLD);
				g.fillOval(x + 6 + small.stringWidth(label) + 4, 6, 5, 5);
			}
			g.setFont(Theme.title());
			FontMetrics bold = g.getFontMetrics();
			Ui.shadowed(g, Ui.clip(value, bold, w - 12), x + 6, 26, cheaper ? Theme.GREEN : Theme.TEXT);
		}
	}

	private static final class Gauge extends JComponent
	{
		private final Chain.Line line;
		private final String name;

		Gauge(Chain.Line line, View view)
		{
			this.line = line;
			this.name = view.name(line.getSupply());
			setOpaque(false);
			setPreferredSize(new Dimension(10, 26));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			double share = line.getWant() <= 0 ? 1 : Math.min(1, line.getHave() / (double) line.getWant());
			Color color = share >= 1 ? Theme.GREEN : Theme.GOLD;
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			String figures = Format.amount(line.getHave()) + " / " + Format.amount(line.getWant()) + View.unit(line.getSupply());
			int fw = small.stringWidth(figures);
			g.setFont(Theme.body());
			FontMetrics fm = g.getFontMetrics();
			Ui.shadowed(g, Ui.clip(name, fm, w - fw - 8), 0, fm.getAscent(), Theme.TEXT);
			g.setFont(Theme.small());
			g.setColor(color);
			g.drawString(figures, w - fw, fm.getAscent());
			Bar.paint(g, 0, 20, w, 4, share);
			g.dispose();
		}
	}
}
