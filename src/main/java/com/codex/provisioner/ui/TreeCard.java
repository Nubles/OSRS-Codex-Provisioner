package com.codex.provisioner.ui;

import com.codex.provisioner.core.Step;
import com.codex.provisioner.core.Tree;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;

/**
 * One supply's tree on a card: the finished product at the top and, branching beneath on gilt
 * lines, everything that goes into it down to what is gathered by hand. Each part shows its
 * picture ringed in its state's colour, its name, what is done to it and how many are still to
 * get, or a tick once enough is stocked. Painted in one pass, so a deep tree costs one component.
 */
class TreeCard extends JComponent
{
	static final int ROW = 30;
	private static final int PAD = 8;
	private static final int INDENT = 12;
	private static final int ICON = 22;

	private final List<Tree.Node> nodes;
	private final View view;
	private final ItemIcons icons;
	private final Step current;

	TreeCard(List<Tree.Node> nodes, View view, ItemIcons icons, Step current)
	{
		this.nodes = nodes;
		this.view = view;
		this.icons = icons;
		this.current = current;
		setOpaque(false);
		int h = nodes.size() * ROW + PAD * 2;
		setPreferredSize(new Dimension(10, h));
		setMinimumSize(new Dimension(10, h));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		StringBuilder tip = new StringBuilder("<html>");
		for (Tree.Node n : nodes)
		{
			tip.append("&nbsp;".repeat(n.getDepth() * 3)).append(Ui.escape(view.name(n.getItem()))).append(": ").append(Ui.escape(line(n))).append("<br>");
		}
		setToolTipText(tip.append("</html>").toString());
	}

	/** The node's centre line for a depth: where its picture sits and its branch hangs from. */
	private static int column(int depth)
	{
		return PAD + depth * INDENT + ICON / 2;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, getHeight() - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, getHeight(), Theme.CARD));
		g.fill(shape);
		g.setColor(Theme.STROKE_SOFT);
		g.draw(shape);

		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setFont(Theme.body());
		FontMetrics body = g.getFontMetrics();
		for (int i = 0; i < nodes.size(); i++)
		{
			Tree.Node n = nodes.get(i);
			int top = PAD + i * ROW;
			int mid = top + ROW / 2;
			int d = n.getDepth();
			boolean opened = i + 1 < nodes.size() && nodes.get(i + 1).getDepth() == d + 1;

			// The lines: rails of branches still running, this node's elbow, and its own branch down.
			g.setStroke(new BasicStroke(1f));
			g.setColor(Theme.alpha(Theme.GOLD_DIM, 150));
			for (int k = 0; k < d - 1; k++)
			{
				if (n.rail(k))
				{
					g.drawLine(column(k), top, column(k), top + ROW);
				}
			}
			if (d > 0)
			{
				int cx = column(d - 1);
				g.drawLine(cx, top, cx, n.isLast() ? mid : top + ROW);
				g.drawLine(cx, mid, PAD + d * INDENT - 1, mid);
			}
			if (opened)
			{
				g.drawLine(column(d), mid + ICON / 2, column(d), top + ROW);
			}

			Color tone = tone(n);
			int ix = PAD + d * INDENT;
			int iy = mid - ICON / 2;
			chip(g, ix, iy, tone, n);
			BufferedImage icon = icons == null ? null : icons.icon(n.getItem(), this::repaint);
			if (icon != null)
			{
				double scale = Math.min(1, (ICON - 2) / (double) Math.max(icon.getWidth(), icon.getHeight()));
				int iw = (int) Math.round(icon.getWidth() * scale);
				int ih = (int) Math.round(icon.getHeight() * scale);
				java.awt.Composite old = g.getComposite();
				if (n.isRepeat())
				{
					g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.55f));
				}
				g.drawImage(icon, ix + (ICON - iw) / 2, iy + (ICON - ih) / 2, iw, ih, null);
				g.setComposite(old);
			}

			int tx = ix + ICON + 6;
			String figure = figure(n);
			g.setFont(Theme.body());
			int fw = body.stringWidth(figure);
			Color figureColor = n.isDone() ? Theme.GREEN : n.getStep() != null && n.getStep().getState() == Step.State.LOCKED ? Theme.BLOOD
				: n.getStep() != null && n.getStep().getState() == Step.State.WAITING ? Theme.TEXT_MUTED : Theme.GOLD_BRIGHT;
			if (n.isRepeat())
			{
				figureColor = Theme.TEXT_FAINT;
			}
			Ui.shadowed(g, figure, w - PAD - fw, mid - 1, figureColor);
			int room = w - PAD - fw - 6 - tx;
			String name = view.shortName(n.getItem());
			// Deep in a tree a long name steps down a face before it is cut short.
			FontMetrics face = body;
			if (body.stringWidth(name) > room)
			{
				g.setFont(Theme.small());
				face = small;
			}
			Ui.shadowed(g, Ui.clip(name, face, room), tx, mid - 1, n.isRepeat() || n.isDone() ? Theme.TEXT_MUTED : Theme.TEXT);
			g.setFont(Theme.small());
			g.setColor(n.isRepeat() ? Theme.TEXT_FAINT : Theme.mix(tone, Theme.TEXT_MUTED, 0.45));
			g.drawString(Ui.clip(line(n), small, w - PAD - tx), tx, mid + small.getAscent() + 1);
			g.setFont(Theme.body());
		}
		g.dispose();
	}

	/** The colour of a part: done in green, the step in hand bright, ready in gold, waiting faint, locked red. */
	private Color tone(Tree.Node n)
	{
		if (n.getStep() == null)
		{
			return Theme.GREEN;
		}
		return StepRow.color(n.getStep(), n.getStep() == current);
	}

	/** The count to the right: how many are still to get, or a tick once enough is stocked. */
	private static String figure(Tree.Node n)
	{
		if (n.isDone())
		{
			return "✓";
		}
		return Format.amount(n.getStep().getNet());
	}

	/** The line under the name: what is done to the part and where it stands. */
	private String line(Tree.Node n)
	{
		Step s = n.getStep();
		if (s == null)
		{
			return "stocked";
		}
		String verb = s.getRecipe().getVerb().label().toLowerCase(Locale.ENGLISH);
		com.codex.provisioner.core.HerbRuns.Plan herbs = s.getState() == Step.State.DONE ? null : view.herbRuns(s.getItem());
		if (herbs != null && !n.isRepeat())
		{
			// Herbs still to grow are herb runs, as the Chain tab plans them.
			return "grow, " + View.runs(herbs.getRuns());
		}
		if (n.isRepeat())
		{
			return verb + ", counted above";
		}
		switch (s.getState())
		{
			case DONE:
				return "enough in hand";
			case LOCKED:
				return "needs " + Ui.skill(s.getUnmet().getSkill()) + " " + s.getUnmet().getLevel();
			case WAITING:
				return verb + ", waiting";
			default:
				return s == current ? verb + ", the step in hand" : verb + ", ready";
		}
	}

	/** The picture's setting: a dark round well with a ring in the part's colour, glowing for the step in hand. */
	private void chip(Graphics2D g, int x, int y, Color tone, Tree.Node n)
	{
		boolean now = n.getStep() != null && n.getStep() == current;
		if (now)
		{
			g.setColor(Theme.alpha(tone, 50));
			g.fill(new Ellipse2D.Float(x - 3, y - 3, ICON + 6, ICON + 6));
		}
		g.setColor(Theme.INK_DEEP);
		g.fill(new Ellipse2D.Float(x, y, ICON, ICON));
		g.setStroke(new BasicStroke(now ? 1.4f : 1f));
		g.setColor(Theme.alpha(tone, n.isRepeat() ? 70 : 170));
		g.draw(new Ellipse2D.Float(x + 0.5f, y + 0.5f, ICON - 1, ICON - 1));
		if (n.isDone() && !n.isRepeat())
		{
			// A small green seal on the rim: this part is stocked.
			float cx = x + ICON - 3;
			float cy = y + ICON - 3;
			g.setColor(Theme.INK_DEEP);
			g.fill(new Ellipse2D.Float(cx - 4, cy - 4, 8, 8));
			Path2D d = new Path2D.Float();
			d.moveTo(cx, cy - 3);
			d.lineTo(cx + 3, cy);
			d.lineTo(cx, cy + 3);
			d.lineTo(cx - 3, cy);
			d.closePath();
			g.setColor(Theme.GREEN);
			g.fill(d);
		}
	}
}
