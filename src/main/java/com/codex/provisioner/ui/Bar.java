package com.codex.provisioner.ui;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/** A slim engraved progress bar, ember when just begun, gold midway, teal near the end and green when done. */
final class Bar
{
	private Bar()
	{
	}

	static void paint(Graphics2D g, int x, int y, int width, int height, double progress)
	{
		g.setColor(Theme.INK_DEEP);
		g.fill(new RoundRectangle2D.Float(x, y, width, height, height, height));
		float fill = (float) (width * Math.max(0, Math.min(1, progress)));
		if (fill > 0)
		{
			Color c = Theme.progressColor(progress);
			g.setPaint(new GradientPaint(x, 0, Theme.mix(c, Theme.INK, 0.4), x + width, 0, c));
			g.fill(new RoundRectangle2D.Float(x, y, Math.max(height, fill), height, height, height));
			g.setColor(Theme.alpha(Color.WHITE, 40));
			g.drawLine(x + 1, y, Math.round(x + Math.max(height, fill)) - 2, y);
		}
	}
}
