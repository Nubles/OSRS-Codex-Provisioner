package com.codex.provisioner.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** One thing to gather: the item in its slot, how it is got, and how many are still needed. */
class SourceRow extends JComponent
{
	private final int itemId;
	private final String name;
	private final String note;
	private final String amount;
	private final Color color;
	private final ItemIcons icons;

	SourceRow(int itemId, String name, String note, String amount, Color color, String how, ItemIcons icons)
	{
		this.itemId = itemId;
		this.name = name;
		this.note = note;
		this.amount = amount;
		this.color = color;
		this.icons = icons;
		setOpaque(false);
		setPreferredSize(new Dimension(10, Well.SIZE + 4));
		setToolTipText("<html><b>" + Ui.escape(name) + "</b><br>" + Ui.escape(how) + "</html>");
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		Well.paint(g, 0, 2, icons == null || itemId < 0 ? null : icons.icon(itemId, this::repaint), null);
		int tx = Well.SIZE + 12;
		g.setFont(Theme.body());
		FontMetrics fm = g.getFontMetrics();
		int aw = fm.stringWidth(amount);
		int base = 2 + fm.getAscent() + 1;
		Ui.shadowed(g, amount, w - aw, base, color);
		Ui.shadowed(g, Ui.clip(name, fm, w - tx - aw - 8), tx, base, Theme.TEXT);
		g.setFont(Theme.small());
		g.setColor(Theme.TEXT_FAINT);
		g.drawString(Ui.clip(note, g.getFontMetrics(), w - tx), tx, 2 + Well.SIZE - 6);
		g.dispose();
	}
}
