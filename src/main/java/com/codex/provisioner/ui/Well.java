package com.codex.provisioner.ui;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** The dark slot an item sits in, like a bank slot, with the stack drawn in the game's colours. */
final class Well
{
	static final int SIZE = 32;

	private Well()
	{
	}

	static void paint(Graphics2D g, int x, int y, BufferedImage icon, Color rim)
	{
		RoundRectangle2D well = new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, SIZE + 4, SIZE - 1, 7, 7);
		g.setPaint(new GradientPaint(0, y, Theme.INK_DEEP, 0, y + SIZE, Theme.mix(Theme.INK_DEEP, Theme.CARD, 0.6)));
		g.fill(well);
		g.setColor(rim == null ? Theme.STROKE_SOFT : rim);
		g.draw(well);
		if (icon != null)
		{
			g.drawImage(icon, x + 2 + (SIZE - icon.getWidth()) / 2, y + (SIZE - icon.getHeight()) / 2 + 1, null);
		}
	}
}
