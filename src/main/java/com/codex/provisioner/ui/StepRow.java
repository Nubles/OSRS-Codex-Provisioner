package com.codex.provisioner.ui;

import com.codex.provisioner.core.Step;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * One step of the chain: a mark for its state down the left like beads on a string, the item in
 * its slot, what to do and how far along it is, what is left on the right, and a slim bar.
 */
class StepRow extends JComponent
{
	static final int HEIGHT = Well.SIZE + 12;

	private final Step step;
	private final String name;
	private final boolean current;
	private final boolean last;
	private final ItemIcons icons;
	/** Time left on this step, or -1 when it cannot be told. */
	private final long estimate;
	/** Herb runs for a herb still to grow, or null. */
	private final com.codex.provisioner.core.HerbRuns.Plan herbs;

	StepRow(Step step, View view, boolean current, boolean last, ItemIcons icons)
	{
		this.step = step;
		this.name = view.shortName(step.getItem());
		this.herbs = view.herbRuns(step.getItem());
		this.current = current;
		this.last = last;
		this.icons = icons;
		this.estimate = view.estimate(step.getItem());
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		String time = estimate < 0 || step.getState() == Step.State.DONE ? ""
			: "<br>About " + Format.estimate(estimate) + (view.ownPace(step.getItem()) ? " at your own pace" : " at a typical pace");
		if (herbs != null && step.getState() != Step.State.DONE)
		{
			time = "<br>About " + View.runs(herbs.getRuns()) + " on " + herbs.getPatchesUsed() + (herbs.getPatchesUsed() == 1 ? " patch, " : " patches, ")
				+ Format.amount(herbs.getSeeds()) + " seeds";
		}
		setToolTipText("<html><b>" + Ui.escape(step.getRecipe().getVerb().label() + " " + view.name(step.getItem())) + "</b><br>"
			+ Ui.escape(step.getRecipe().getHow()) + time + "</html>");
	}

	static Color color(Step step, boolean current)
	{
		switch (step.getState())
		{
			case DONE:
				return Theme.GREEN;
			case LOCKED:
				return Theme.BLOOD;
			case WAITING:
				return Theme.TEXT_FAINT;
			default:
				return current ? Theme.GOLD_BRIGHT : Theme.GOLD;
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		Color color = color(step, current);
		boolean done = step.getState() == Step.State.DONE;

		// The string between beads, then this step's bead.
		int cx = 6;
		int cy = 4 + Well.SIZE / 2;
		g.setColor(Theme.STROKE);
		g.drawLine(cx, 0, cx, cy - 6);
		if (!last)
		{
			g.drawLine(cx, cy + 6, cx, HEIGHT);
		}
		paintBead(g, cx, cy, step.getState(), current, color);

		int wx = 16;
		Well.paint(g, wx, 4, icons == null ? null : icons.icon(step.getItem(), this::repaint), current ? Theme.alpha(Theme.GOLD, 200) : null);
		int tx = wx + Well.SIZE + 10;

		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		int base = 4 + bold.getAscent();
		String right;
		Color rightColor = color;
		if (done)
		{
			right = "done";
		}
		else if (step.getState() == Step.State.LOCKED)
		{
			right = "Lv " + step.getUnmet().getLevel();
		}
		else
		{
			right = Format.amount(step.getNet());
		}
		g.setFont(done || step.getState() == Step.State.LOCKED ? Theme.small() : Theme.title());
		FontMetrics rf = g.getFontMetrics();
		int rw = rf.stringWidth(right);
		Ui.shadowed(g, right, w - rw, base, rightColor);

		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		Color nameColor = done ? Theme.TEXT_MUTED : current ? Theme.TEXT : Theme.mix(Theme.TEXT, Theme.TEXT_MUTED, 0.3);
		Ui.shadowed(g, Ui.clip(name, fm, w - tx - rw - 8), tx, base, nameColor);

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		String verb = step.getRecipe().getVerb().label().toUpperCase(Locale.ENGLISH);
		g.setColor(done ? Theme.TEXT_FAINT : Theme.mix(color, Theme.TEXT_FAINT, 0.35));
		g.drawString(verb, tx, base + 13);
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(Ui.clip("  " + detail(), small, w - tx - small.stringWidth(verb)), tx + small.stringWidth(verb), base + 13);

		if (!done)
		{
			Bar.paint(g, tx, Well.SIZE + 5, w - tx, 3, step.progress());
		}
		g.dispose();
	}

	private String detail()
	{
		switch (step.getState())
		{
			case DONE:
				return step.getBaseline() > 0 ? Format.amount(step.getBaseline()) + " ready" : "enough in hand";
			case LOCKED:
				return "needs " + Ui.skill(step.getUnmet().getSkill()) + " " + step.getUnmet().getLevel();
			case WAITING:
				return "waits for inputs";
			default:
				long done = step.getDone();
				String text = done > 0 ? Format.amount(done) + " of " + Format.amount(step.getBaseline()) : Format.amount(step.getNet()) + " to go";
				if (herbs != null)
				{
					return View.runs(herbs.getRuns());
				}
				return estimate >= 0 ? text + " \u00b7 " + Format.estimate(estimate) : text;
		}
	}

	static void paintBead(Graphics2D g, int cx, int cy, Step.State state, boolean current, Color color)
	{
		g.setStroke(new BasicStroke(1.4f));
		if (current)
		{
			g.setColor(Theme.alpha(Theme.GOLD, 60));
			g.fill(new Ellipse2D.Float(cx - 7, cy - 7, 14, 14));
			Path2D diamond = new Path2D.Float();
			diamond.moveTo(cx, cy - 5);
			diamond.lineTo(cx + 5, cy);
			diamond.lineTo(cx, cy + 5);
			diamond.lineTo(cx - 5, cy);
			diamond.closePath();
			g.setColor(Theme.GOLD_BRIGHT);
			g.fill(diamond);
			g.setColor(Theme.INK_DEEP);
			g.draw(diamond);
			return;
		}
		Ellipse2D bead = new Ellipse2D.Float(cx - 4.5f, cy - 4.5f, 9, 9);
		switch (state)
		{
			case DONE:
				g.setColor(color);
				g.fill(bead);
				g.setColor(Theme.INK_DEEP);
				Path2D tick = new Path2D.Float();
				tick.moveTo(cx - 2.4f, cy);
				tick.lineTo(cx - 0.6f, cy + 1.8f);
				tick.lineTo(cx + 2.6f, cy - 1.8f);
				g.draw(tick);
				break;
			case LOCKED:
				g.setColor(Theme.alpha(color, 60));
				g.fill(bead);
				g.setColor(color);
				g.draw(bead);
				g.drawLine(cx - 2, cy, cx + 2, cy);
				break;
			default:
				g.setColor(Theme.INK_DEEP);
				g.fill(bead);
				g.setColor(color);
				g.draw(bead);
		}
		g.setStroke(new BasicStroke(1f));
	}
}
