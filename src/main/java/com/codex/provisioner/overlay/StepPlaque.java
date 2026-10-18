package com.codex.provisioner.overlay;

import com.codex.provisioner.ui.Sigil;
import com.codex.provisioner.ui.Theme;
import com.codex.provisioner.ui.Ui;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A gilt plaque in the game view with the step in hand: what to do, how many are left, and a thin
 * bar of how far along it is. It draws what the plugin hands it.
 */
public class StepPlaque extends Overlay
{
	private static final int PAD = 7;
	private static final int ICON = 16;

	private volatile Shown shown;

	private static final class Shown
	{
		final String title;
		final String figure;
		final String note;
		final double progress;
		final Color color;

		Shown(String title, String figure, String note, double progress, Color color)
		{
			this.title = title;
			this.figure = figure;
			this.note = note;
			this.progress = progress;
			this.color = color;
		}
	}

	public StepPlaque()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	/**
	 * @param title what to do, such as "Clean ranarr weed", or null to hide the plaque
	 * @param figure how many are left
	 * @param note a quiet second line, such as the step's place in the chain
	 * @param progress how far along the step is, 0 to 1
	 */
	public void show(String title, String figure, String note, double progress, Color color)
	{
		shown = title == null ? null : new Shown(title, figure, note, progress, color);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Shown s = shown;
		if (s == null)
		{
			return null;
		}
		Theme.smooth(g);
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int textW = Math.max(bold.stringWidth(s.title) + 10 + bold.stringWidth(s.figure), small.stringWidth(s.note));
		int width = PAD + ICON + 7 + textW + PAD;
		int height = PAD + bold.getHeight() + small.getHeight() + 6 + PAD - 2;

		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, 10, 10);
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.CARD_TOP, 230), 0, height, Theme.alpha(Theme.INK_DEEP, 230)));
		g.fill(shape);
		g.setColor(Theme.alpha(s.color, 170));
		g.draw(shape);

		Sigil.paint(g, PAD, PAD, ICON);
		int x = PAD + ICON + 7;
		int base = PAD + bold.getAscent() - 2;
		g.setFont(Theme.title());
		Ui.shadowed(g, s.title, x, base, Theme.TEXT);
		Ui.shadowed(g, s.figure, x + textW - bold.stringWidth(s.figure), base, s.color);
		g.setFont(Theme.small());
		Ui.shadowed(g, s.note, x, base + small.getHeight() + 1, Theme.TEXT_MUTED);

		int by = height - PAD - 1;
		g.setColor(Theme.INK_DEEP);
		g.fill(new RoundRectangle2D.Float(x, by, textW, 3, 3, 3));
		float fill = (float) (textW * Math.max(0, Math.min(1, s.progress)));
		if (fill > 0)
		{
			Color c = Theme.progressColor(s.progress);
			g.setColor(c);
			g.fill(new RoundRectangle2D.Float(x, by, Math.max(3, fill), 3, 3, 3));
		}
		return new Dimension(width, height);
	}
}
