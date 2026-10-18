package com.codex.provisioner.ui;

import com.codex.provisioner.core.Chain;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
		String made = stocked
			? "Stocked for " + view.chain.getTrips() + " trips."
			: "Make " + Format.amount(line.getToMake()) + " " + view.name(line.getProduct()) + ".";
		add(Ui.wrapped(made, Theme.small(), stocked ? Theme.GREEN : Theme.TEXT_MUTED, width), BorderLayout.SOUTH);
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
