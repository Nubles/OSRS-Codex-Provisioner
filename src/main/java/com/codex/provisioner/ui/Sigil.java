package com.codex.provisioner.ui;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** The Provisioner's mark: a gilt mortar with its pestle, and a teal herb leaf rising from the bowl. */
public final class Sigil
{
	private Sigil()
	{
	}

	public static BufferedImage icon(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		Theme.smooth(g);
		paint(g, 0, 0, size);
		g.dispose();
		return image;
	}

	/** Draws the mark in a size by size box. */
	public static void paint(Graphics2D g, float x, float y, float size)
	{
		float s = size / 16f;
		float stroke = Math.max(0.6f, 0.55f * s);
		g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		// The leaf, behind the bowl: a pointed teal blade with a pale vein.
		Path2D leaf = new Path2D.Float();
		leaf.moveTo(x + 6.2f * s, y + 8.4f * s);
		leaf.curveTo(x + 3.6f * s, y + 5.6f * s, x + 4.4f * s, y + 2.2f * s, x + 7.4f * s, y + 0.8f * s);
		leaf.curveTo(x + 8.6f * s, y + 3.4f * s, x + 8.4f * s, y + 6.4f * s, x + 6.2f * s, y + 8.4f * s);
		leaf.closePath();
		g.setPaint(new GradientPaint(x, y, Theme.mix(Theme.TEAL, java.awt.Color.WHITE, 0.25f), x, y + 9 * s, Theme.mix(Theme.TEAL, Theme.INK_DEEP, 0.35f)));
		g.fill(leaf);
		g.setColor(Theme.INK_DEEP);
		g.draw(leaf);
		g.setColor(Theme.alpha(java.awt.Color.WHITE, 120));
		g.drawLine(Math.round(x + 6.4f * s), Math.round(y + 7.6f * s), Math.round(x + 7.2f * s), Math.round(y + 2.4f * s));

		// The pestle, leaning out of the bowl to the right.
		Path2D pestle = new Path2D.Float();
		pestle.moveTo(x + 8.6f * s, y + 9.4f * s);
		pestle.lineTo(x + 13.4f * s, y + 2.6f * s);
		pestle.lineTo(x + 15.0f * s, y + 3.8f * s);
		pestle.lineTo(x + 10.4f * s, y + 10.4f * s);
		pestle.closePath();
		g.setPaint(new GradientPaint(x + 9 * s, y, Theme.GOLD_BRIGHT, x + 15 * s, y + 10 * s, Theme.GOLD_DIM));
		g.fill(pestle);
		g.setColor(Theme.INK_DEEP);
		g.draw(pestle);

		// The bowl: a half round of gilt with a dark mouth and a foot.
		Arc2D bowl = new Arc2D.Float(x + 1.2f * s, y + 3.6f * s, 13.6f * s, 11.2f * s, 180, 180, Arc2D.CHORD);
		g.setPaint(new GradientPaint(x, y + 8 * s, Theme.GOLD, x, y + 15 * s, Theme.mix(Theme.GOLD_DIM, Theme.INK_DEEP, 0.4f)));
		g.fill(bowl);
		g.setColor(Theme.INK_DEEP);
		g.draw(bowl);
		g.setColor(Theme.alpha(Theme.INK_DEEP, 200));
		g.fill(new RoundRectangle2D.Float(x + 1.6f * s, y + 8.6f * s, 12.8f * s, 1.4f * s, 1.4f * s, 1.4f * s));
		g.setColor(Theme.alpha(java.awt.Color.WHITE, 70));
		g.drawLine(Math.round(x + 3.4f * s), Math.round(y + 10.8f * s), Math.round(x + 5.4f * s), Math.round(y + 12.6f * s));
		RoundRectangle2D foot = new RoundRectangle2D.Float(x + 5.2f * s, y + 14.0f * s, 5.6f * s, 1.6f * s, 0.8f * s, 0.8f * s);
		g.setColor(Theme.GOLD_DIM);
		g.fill(foot);
		g.setColor(Theme.INK_DEEP);
		g.draw(foot);
	}
}
