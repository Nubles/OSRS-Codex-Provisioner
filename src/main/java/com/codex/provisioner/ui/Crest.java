package com.codex.provisioner.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;

/**
 * The crest at the top of the panel: the Provisioner's medallion, how far through the chain the
 * player is in large gilt figures, a thin bar of overall progress, and the step in hand.
 */
public class Crest extends JComponent
{
	private static final int HEIGHT = 104;

	private final Animator count = new Animator(this);
	private String heading = "PROVISIONS";
	private String status = "Log in to plan your supplies";
	private String shortStatus;
	private Color statusColor = Theme.TEXT_MUTED;
	private boolean known;
	private boolean unlimited;
	private Color figureTint = Theme.GOLD;
	private String unit = "steps";
	private double progress = -1;

	public Crest()
	{
		setOpaque(false);
		setPreferredSize(new Dimension(10, HEIGHT));
		setMinimumSize(new Dimension(10, HEIGHT));
	}

	/**
	 * @param done steps done, or null when not known
	 * @param total steps in the chain
	 * @param ready true when everything is provisioned, which shows a word instead of a count
	 * @param progress overall progress from 0 to 1, or negative to hide the bar
	 * @param status the line under the figure, such as the step in hand
	 * @param shortStatus a shorter line for when the status does not fit, or null
	 */
	public void update(String heading, Integer done, int total, boolean ready, double progress, String status, String shortStatus, Color statusColor, Color tint)
	{
		this.heading = heading;
		this.status = status;
		this.shortStatus = shortStatus;
		this.statusColor = statusColor;
		this.known = done != null;
		this.unlimited = ready;
		this.figureTint = tint;
		this.progress = progress;
		this.unit = "of " + total + (total == 1 ? " step" : " steps");
		if (done != null)
		{
			count.set(done, count.get() > 0);
		}
		repaint();
	}

	/**
	 * Draws text at twice the size of the game's bold font by doubling every pixel, the way the
	 * game itself draws large numbers, so the figures stay crisp instead of being re-rasterised.
	 *
	 * @return the width drawn
	 */
	static int paintLarge(Graphics2D g, String text, int x, int y, Color tint)
	{
		Font font = Theme.title();
		FontMetrics fm = g.getFontMetrics(font);
		int w = Math.max(1, fm.stringWidth(text) + 1);
		int h = fm.getHeight() + 1;
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = small.createGraphics();
		sg.setFont(font);
		sg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		int base = fm.getAscent();
		sg.setColor(Color.BLACK);
		sg.drawString(text, 1, base + 1);
		sg.setPaint(new GradientPaint(0, base - fm.getAscent() + 3, Theme.mix(tint, Color.WHITE, 0.35), 0, base, tint));
		sg.drawString(text, 0, base);
		sg.dispose();
		Graphics2D lg = (Graphics2D) g.create();
		lg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		lg.drawImage(small, x, y - 2 * fm.getAscent() + 22, w * 2, h * 2, null);
		lg.dispose();
		return w * 2;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.INK, Theme.GOLD_DIM, 0.22), 0, h, Theme.INK));
		g.fillRect(0, 0, w, h);

		// The medallion: a gilt ring with the mark inside, lit from above.
		int mx = 10;
		int my = 14;
		int md = 54;
		g.setPaint(new RadialGradientPaint(mx + md / 2f, my + md / 3f, md * 0.7f, new float[]{0, 1},
			new Color[]{Theme.mix(Theme.CARD_TOP, Theme.GOLD_DIM, 0.35), Theme.INK_DEEP}));
		g.fill(new Ellipse2D.Float(mx, my, md, md));
		g.setStroke(new BasicStroke(2f));
		g.setPaint(new GradientPaint(mx, my, Theme.GOLD_BRIGHT, mx, my + md, Theme.GOLD_DIM));
		g.draw(new Ellipse2D.Float(mx + 1, my + 1, md - 2, md - 2));
		g.setStroke(new BasicStroke(1f));
		g.setColor(Theme.alpha(Theme.GOLD, 70));
		g.draw(new Ellipse2D.Float(mx + 5, my + 5, md - 10, md - 10));
		Sigil.paint(g, mx + 11, my + 11, md - 22);

		int tx = mx + md + 12;
		int room = w - tx - 8;
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		g.setColor(Theme.TEXT_MUTED);
		g.drawString(Ui.clip(heading, small, room), tx, 22);

		String figure = !known ? "-" : unlimited ? "Ready" : String.valueOf(Math.round(count.get()));
		int top = 27;
		int figureW = paintLarge(g, figure, tx, top, known ? figureTint : Theme.GOLD_DIM);
		if (known && !unlimited)
		{
			g.setFont(Theme.small());
			g.setColor(Theme.GOLD_DIM);
			g.drawString(unit, tx + figureW + 3, top + 18);
		}
		if (progress >= 0)
		{
			Bar.paint(g, tx, top + 30, room, 4, progress);
		}
		int cy = top + 50;
		g.setFont(Theme.small());
		if (status != null)
		{
			String line = shortStatus != null && small.stringWidth(status) > room ? shortStatus : status;
			Ui.shadowed(g, Ui.clip(line, small, room), tx, cy, statusColor);
		}

		// The gilt rule under the crest, with a diamond at its middle.
		int y = h - 2;
		g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.GOLD, 0), w / 2f, 0, Theme.alpha(Theme.GOLD, 150), true));
		g.drawLine(0, y, w, y);
		Path2D diamond = new Path2D.Float();
		diamond.moveTo(w / 2f, y - 3);
		diamond.lineTo(w / 2f + 3, y);
		diamond.lineTo(w / 2f, y + 3);
		diamond.lineTo(w / 2f - 3, y);
		diamond.closePath();
		g.setColor(Theme.GOLD);
		g.fill(diamond);
		g.dispose();
	}
}
