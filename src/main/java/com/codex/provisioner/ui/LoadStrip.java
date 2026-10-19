package com.codex.provisioner.ui;

import com.codex.provisioner.core.Loads;
import com.codex.provisioner.core.Step;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComponent;

/**
 * One inventory of the step in hand, drawn as bank slots: what to take out of the bank for each
 * trip, with the stacks in the game's own colours, and how many trips the step has left. For a
 * catch, the slot shows what one inventory brings back.
 */
class LoadStrip extends JComponent
{
	private static final int GAP = 14;

	private final Map<Integer, Long> slots;
	private final String label;
	private final long loads;
	private final ItemIcons icons;
	private final String tip;

	LoadStrip(Step step, Loads loads, View view, ItemIcons icons)
	{
		this.icons = icons;
		this.loads = loads.getLoads();
		boolean catching = loads.getWithdraw().isEmpty();
		if (catching)
		{
			slots = new LinkedHashMap<>();
			slots.put(step.getItem(), loads.getPerLoad());
			label = "EACH INVENTORY";
		}
		else
		{
			slots = loads.getWithdraw();
			label = "TAKE OUT EACH TRIP";
		}
		List<String> parts = new ArrayList<>();
		slots.forEach((id, n) -> parts.add(Format.amount(n) + " " + view.name(id).toLowerCase(Locale.ENGLISH)));
		tip = (catching ? "Each inventory holds " : "Each trip, take out ") + String.join(" and ", parts) + "; "
			+ this.loads + (this.loads == 1 ? " trip" : " trips") + " to go.";
		setToolTipText(tip);
		setOpaque(false);
		setPreferredSize(new Dimension(10, 14 + Well.SIZE + 2));
	}

	String tip()
	{
		return tip;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setColor(Theme.GOLD_DIM);
		g.drawString(label, 0, small.getAscent());

		// The trips left, large on the right.
		String count = String.valueOf(loads);
		String unit = loads == 1 ? "trip" : "trips";
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		int top = 14;
		int right = Math.max(bold.stringWidth(count), small.stringWidth(unit));
		Ui.shadowed(g, count, w - (right + bold.stringWidth(count)) / 2, top + bold.getAscent() + 1, Theme.GOLD_BRIGHT);
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(unit, w - (right + small.stringWidth(unit)) / 2, top + Well.SIZE - 2);

		int x = 0;
		int room = w - right - 8;
		int i = 0;
		for (Map.Entry<Integer, Long> e : slots.entrySet())
		{
			if (x + Well.SIZE + 4 > room)
			{
				break;
			}
			if (i++ > 0)
			{
				g.setFont(Theme.small());
				g.setColor(Theme.GOLD_DIM);
				g.drawString("+", x - GAP + (GAP - small.stringWidth("+")) / 2 + 2, top + Well.SIZE / 2 + small.getAscent() / 2);
			}
			BufferedImage icon = icons == null ? null : icons.icon(e.getKey(), this::repaint);
			Well.paint(g, x, top, icon, Theme.alpha(Theme.TEAL, 150));
			String stack = Format.stack(e.getValue());
			g.setFont(Theme.small());
			Ui.shadowed(g, stack, x + 3, top + small.getAscent(), Format.stackColor(e.getValue()));
			x += Well.SIZE + 4 + GAP;
		}
		g.dispose();
	}
}
