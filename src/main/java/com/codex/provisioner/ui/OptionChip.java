package com.codex.provisioner.ui;

import com.codex.provisioner.core.Chain;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import javax.swing.JComponent;
import net.runelite.api.Skill;

/** One item a supply can be made as, in its slot: gilt when chosen, dimmed when the levels fall short. */
class OptionChip extends JComponent
{
	private final int itemId;
	private final boolean chosen;
	private final boolean reachable;
	private final ItemIcons icons;
	private boolean hover;

	OptionChip(int itemId, boolean chosen, View view, ItemIcons icons, Runnable onPick)
	{
		this.itemId = itemId;
		this.chosen = chosen;
		this.icons = icons;
		this.reachable = Chain.reachable(itemId, view.levels, new java.util.HashSet<>());
		setOpaque(false);
		Dimension d = new Dimension(Well.SIZE + 5, Well.SIZE + 1);
		setPreferredSize(d);
		setMinimumSize(d);
		setMaximumSize(d);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		StringBuilder tip = new StringBuilder("<html><b>").append(Ui.escape(view.name(itemId))).append("</b>");
		for (Map.Entry<Skill, Integer> e : Chain.requirements(itemId).entrySet())
		{
			Integer have = view.level(e.getKey());
			boolean met = have == null || have >= e.getValue();
			tip.append("<br>").append(met ? "" : "<font color='#cc463c'>").append(Ui.skill(e.getKey())).append(' ').append(e.getValue())
				.append(met ? "" : "</font>");
		}
		setToolTipText(tip.append("</html>").toString());
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				repaint();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (contains(e.getPoint()) && !chosen)
				{
					onPick.run();
				}
			}
		});
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		Color rim = chosen ? Theme.GOLD_BRIGHT : hover ? Theme.alpha(Theme.GOLD, 140) : null;
		if (!reachable && !chosen)
		{
			g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.45f));
		}
		Well.paint(g, 0, 0, icons == null ? null : icons.icon(itemId, this::repaint), rim);
		g.setComposite(java.awt.AlphaComposite.SrcOver);
		if (chosen)
		{
			g.setColor(Theme.GOLD_BRIGHT);
			g.fillRect(Well.SIZE / 2 - 4, Well.SIZE - 2, 14, 2);
		}
		if (!reachable)
		{
			g.setColor(Theme.BLOOD);
			g.fillOval(Well.SIZE - 4, 3, 6, 6);
		}
		g.dispose();
	}
}
