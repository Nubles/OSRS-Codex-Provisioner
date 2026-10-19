package com.codex.provisioner.ui;

import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Req;
import com.codex.provisioner.core.Step;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComponent;

/**
 * The step in hand, given the room it deserves: what to do and to what, how many are left, how far
 * along it is, the levels it asks for, what goes in and how much of it is held, and what to bring.
 */
class NowCard extends Card
{
	NowCard(Chain chain, Step step, View view, ItemIcons icons, int width)
	{
		super(new BorderLayout());
		accent(StepRow.color(step, true));
		highlighted(true);
		StackPanel stack = new StackPanel(5, 0);
		stack.add(new Headline(chain, step, view, icons));
		stack.add(Ui.wrapped(step.getRecipe().getHow(), Theme.small(), Theme.TEXT_MUTED, width));

		for (Req req : step.getRecipe().getReqs())
		{
			Integer have = view.level(req.getSkill());
			boolean met = req.met(view.levels);
			String text = Ui.skill(req.getSkill()) + " " + req.getLevel() + (req.isSoft() ? " to grow" : "")
				+ (have == null ? "" : met ? (req.isSoft() ? "; you have " : " \u00b7 you have ") + have : " \u00b7 you have " + have + ", " + (req.getLevel() - have) + " to go");
			stack.add(new Fact(met ? Fact.TICK : Fact.CROSS, text, req.isSoft() ? Theme.TEXT_FAINT : met ? Theme.TEAL : Theme.BLOOD));
		}
		for (Map.Entry<Integer, Integer> in : step.getRecipe().getInputs().entrySet())
		{
			long held = view.held(in.getKey());
			long need = (step.getNet() + step.getRecipe().getMakes() - 1) / step.getRecipe().getMakes() * in.getValue();
			String each = in.getValue() == 1 && step.getRecipe().getMakes() == 1 ? "" : "  (" + in.getValue() + " a go)";
			String[] pair = Format.pair(held, need);
			String text = view.name(in.getKey()) + each + ": " + pair[0] + " of " + pair[1];
			stack.add(new Fact(Fact.DIAMOND, text, held >= need ? Theme.GREEN : held > 0 ? Theme.GOLD : Theme.TEXT_FAINT));
		}
		if (view.loads != null && step.getState() == Step.State.READY)
		{
			stack.add(new LoadStrip(step, view.loads, view, icons));
		}
		for (int tool : step.getRecipe().getTools())
		{
			boolean has = view.held(tool) > 0;
			stack.add(new Fact(has ? Fact.TICK : Fact.CROSS, "Bring a " + view.name(tool).toLowerCase(Locale.ENGLISH) + (has ? "" : "; none in your bank"),
				has ? Theme.TEAL : Theme.EMBER));
		}
		com.codex.provisioner.core.HerbRuns.Plan herbs = view.herbRuns(step.getItem());
		if (herbs != null)
		{
			int used = herbs.getPatchesUsed();
			String planted = used == 1 ? "1 patch" : used + " patches";
			stack.add(new Fact(Fact.CLOCK, View.runs(herbs.getRuns()) + " on " + planted + " \u00b7 "
				+ Format.estimate(herbs.getSoonestMs()), Theme.TEXT_MUTED,
				"About " + View.runs(herbs.getRuns()) + " planting " + (used < herbs.getPatches() ? planted + " of your " + herbs.getPatches() : "all " + planted)
					+ "; herbs take 80 minutes to grow, so "
					+ Format.estimate(herbs.getSoonestMs()) + " at the soonest. Set your patches and yield in the plugin's settings."));
			long seeds = herbs.getSeeds();
			long held = herbs.getSeedsHeld();
			stack.add(new Fact(held >= seeds ? Fact.TICK : Fact.DIAMOND, view.name(herbs.getSeed()) + ": " + Format.amount(seeds) + " to plant, " + Format.amount(held) + " held",
				held >= seeds ? Theme.GREEN : held > 0 ? Theme.GOLD : Theme.TEXT_FAINT));
		}
		long time = view.estimate(step.getItem());
		if (time >= 0 && step.getState() != Step.State.LOCKED && step.getRecipe().getVerb() != com.codex.provisioner.core.Verb.BUY)
		{
			boolean own = view.ownPace(step.getItem());
			stack.add(new Fact(Fact.CLOCK, "About " + Format.estimate(time) + (own ? " at your pace" : " at a typical pace"),
				own ? Theme.TEAL : Theme.TEXT_FAINT));
		}
		if (step.getState() == Step.State.READY && !step.getRecipe().isSource() && step.getCanNow() < step.getNet())
		{
			stack.add(Ui.wrapped("What you hold makes " + Format.amount(step.getCanNow()) + " now; the rest waits on earlier steps.",
				Theme.small(), Theme.TEXT_FAINT, width));
		}
		add(stack, BorderLayout.CENTER);
	}

	/** The big line: the item, the verb, what is left and the bar. */
	private static final class Headline extends JComponent
	{
		private final Step step;
		private final String name;
		private final String position;
		private final ItemIcons icons;

		Headline(Chain chain, Step step, View view, ItemIcons icons)
		{
			this.step = step;
			this.name = view.shortName(step.getItem());
			this.position = "STEP " + chain.number(step) + " OF " + chain.getSteps().size();
			this.icons = icons;
			setOpaque(false);
			setPreferredSize(new Dimension(10, Well.SIZE + 40));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			Color color = StepRow.color(step, true);
			g.setFont(Theme.small());
			FontMetrics small = g.getFontMetrics();
			g.setColor(Theme.GOLD_DIM);
			g.drawString("NOW", 0, small.getAscent());
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(position, w - small.stringWidth(position), small.getAscent());

			int top = small.getHeight() + 2;
			Well.paint(g, 0, top, icons == null ? null : icons.icon(step.getItem(), this::repaint), Theme.alpha(color, 200));
			int tx = Well.SIZE + 12;

			String left = step.getState() == Step.State.LOCKED ? "Lv " + step.getUnmet().getLevel() : Format.amount(step.getNet());
			// The count is drawn large unless that would cut the name short; then the name comes first.
			int large = largeWidth(g, left);
			boolean squeezed = g.getFontMetrics(Theme.body()).stringWidth(name) > w - tx - large - 6;
			int lw;
			if (squeezed)
			{
				g.setFont(Theme.title());
				FontMetrics fig = g.getFontMetrics();
				lw = fig.stringWidth(left);
				Ui.shadowed(g, left, w - lw, top + fig.getAscent() + 6, color);
			}
			else
			{
				lw = Crest.paintLarge(g, left, w - large, top + 2, color);
			}
			g.setFont(Theme.small());
			g.setColor(Theme.TEXT_FAINT);
			String to = step.getState() == Step.State.LOCKED ? "locked" : "to go";
			g.drawString(to, w - small.stringWidth(to), top + Well.SIZE + 2);

			g.setFont(Theme.small());
			g.setColor(color);
			g.drawString(step.getRecipe().getVerb().label().toUpperCase(Locale.ENGLISH), tx, top + small.getAscent() + 1);
			// A long name beside a big count steps down to the body face before it is cut short.
			int room = w - tx - lw - 6;
			g.setFont(Theme.title());
			if (g.getFontMetrics().stringWidth(name) > room)
			{
				g.setFont(Theme.body());
			}
			FontMetrics bold = g.getFontMetrics();
			Ui.shadowed(g, Ui.clip(name, bold, room), tx, top + small.getHeight() + g.getFontMetrics(Theme.title()).getAscent() + 2, Theme.TEXT);

			int by = top + Well.SIZE + 8;
			Bar.paint(g, 0, by, w, 5, step.progress());
			String[] pair = Format.pair(step.getDone(), step.getBaseline());
			String count = pair[0] + " / " + pair[1];
			g.setFont(Theme.small());
			g.setColor(Theme.TEXT_FAINT);
			g.drawString(count, 0, by + 16);
			String pct = Format.percent(step.progress());
			g.drawString(pct, w - small.stringWidth(pct), by + 16);
			g.dispose();
		}

		private static int largeWidth(Graphics2D g, String text)
		{
			return (g.getFontMetrics(Theme.title()).stringWidth(text) + 1) * 2;
		}
	}

	/** A small marked line: a requirement, an input, or a tool. */
	private static final class Fact extends JComponent
	{
		static final int TICK = 0;
		static final int CROSS = 1;
		static final int DIAMOND = 2;
		static final int CLOCK = 3;

		private final int mark;
		private final String text;
		private final Color color;

		Fact(int mark, String text, Color color)
		{
			this(mark, text, color, text);
		}

		Fact(int mark, String text, Color color, String tip)
		{
			this.mark = mark;
			this.text = text;
			this.color = color;
			setOpaque(false);
			setPreferredSize(new Dimension(10, 14));
			setToolTipText(tip);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			g.setFont(Theme.small());
			FontMetrics fm = g.getFontMetrics();
			int base = fm.getAscent();
			int cy = base - 4;
			g.setColor(color);
			g.setStroke(new java.awt.BasicStroke(1.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
			java.awt.geom.Path2D p = new java.awt.geom.Path2D.Float();
			if (mark == TICK)
			{
				p.moveTo(1, cy);
				p.lineTo(3.5f, cy + 2.5f);
				p.lineTo(8, cy - 3);
				g.draw(p);
			}
			else if (mark == CROSS)
			{
				g.drawLine(2, cy - 3, 7, cy + 2);
				g.drawLine(7, cy - 3, 2, cy + 2);
			}
			else if (mark == CLOCK)
			{
				g.draw(new java.awt.geom.Ellipse2D.Float(1, cy - 3.5f, 7, 7));
				g.drawLine(4, cy, 4, cy - 2);
				g.drawLine(4, cy, 6, cy);
			}
			else
			{
				p.moveTo(4.5f, cy - 3.5f);
				p.lineTo(8, cy);
				p.lineTo(4.5f, cy + 3.5f);
				p.lineTo(1, cy);
				p.closePath();
				g.fill(p);
			}
			g.setColor(Theme.mix(Theme.TEXT_MUTED, color, 0.25));
			g.drawString(Ui.clip(text, fm, getWidth() - 12), 12, base);
			g.dispose();
		}
	}
}
