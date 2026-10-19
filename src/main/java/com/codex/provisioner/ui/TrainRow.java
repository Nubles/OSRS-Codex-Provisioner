package com.codex.provisioner.ui;

import com.codex.provisioner.core.Training;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/**
 * One skill the rest of the chain trains: the experience it gives, the level now and the level it
 * ends at, and a bar for how far into that level the chain leaves the skill.
 */
class TrainRow extends JComponent
{
	private final Training.Gain gain;
	private final boolean last;

	TrainRow(Training.Gain gain, boolean last)
	{
		this.gain = gain;
		this.last = last;
		int before = gain.levelBefore();
		int after = gain.levelAfter();
		setToolTipText(Ui.skill(gain.getSkill()) + ": " + Format.amount(Math.round(gain.getXp())) + " xp, "
			+ (after > before ? "level " + before + " to " + after : "still level " + before));
		setOpaque(false);
		setPreferredSize(new Dimension(10, last ? 30 : 34));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int before = gain.levelBefore();
		int after = gain.levelAfter();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		String levels = after > before ? before + " to " + after : "stays " + before;
		int lw = small.stringWidth(levels);
		g.setFont(Theme.body());
		FontMetrics body = g.getFontMetrics();
		Ui.shadowed(g, Ui.clip(Ui.skill(gain.getSkill()), body, w - lw - 8), 0, body.getAscent(), Theme.TEXT);
		g.setFont(Theme.small());
		g.setColor(after > before ? Theme.GOLD : Theme.TEXT_FAINT);
		g.drawString(levels, w - lw, body.getAscent());

		// The experience, with a bar for how far into the level it ends at; teal throughout, since this is no warning.
		int base = body.getHeight() + small.getAscent();
		String xp = "+" + Format.amount(Math.round(gain.getXp())) + " xp";
		int xw = small.stringWidth(xp);
		g.setColor(Theme.TEAL);
		g.drawString(xp, w - xw, base);
		int bw = w - xw - 8;
		int y = base - small.getAscent() / 2 - 1;
		g.setColor(Theme.INK_DEEP);
		g.fill(new java.awt.geom.RoundRectangle2D.Float(0, y, bw, 4, 4, 4));
		float fill = (float) (bw * gain.progressAfter());
		if (fill > 0)
		{
			g.setColor(Theme.alpha(Theme.TEAL, 200));
			g.fill(new java.awt.geom.RoundRectangle2D.Float(0, y, Math.max(4, fill), 4, 4, 4));
		}
		if (!last)
		{
			g.setColor(Theme.STROKE_SOFT);
			g.drawLine(0, getHeight() - 1, w, getHeight() - 1);
		}
		g.dispose();
	}
}
