package com.codex.provisioner.overlay;

import com.codex.provisioner.ui.Theme;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines in the bank what the step in hand needs for one inventory, each with a small tab saying
 * how many to take, and the tools to bring. Teal, so it reads apart from Codex Quartermaster's
 * gilt packing marks. It only draws; nothing is clicked or moved for the player.
 */
public class BankMarks extends WidgetItemOverlay
{
	private final ItemManager itemManager;
	private volatile Map<Integer, Long> marks = Collections.emptyMap();

	public BankMarks(ItemManager itemManager)
	{
		this.itemManager = itemManager;
		// Only the player's own bank, not group storage.
		drawAfterLayer(InterfaceID.Bankmain.ITEMS);
	}

	/** @param items bank items to mark with how many to take, or empty to mark nothing */
	public void show(Map<Integer, Long> items)
	{
		marks = items == null ? Collections.emptyMap() : items;
	}

	@Override
	public void renderItemOverlay(Graphics2D g, int itemId, WidgetItem widgetItem)
	{
		Long count = marks.get(itemId);
		// A placeholder has nothing to take.
		if (count == null || count <= 0 || widgetItem.getQuantity() <= 0)
		{
			return;
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), Theme.TEAL);
		g.drawImage(outline, bounds.x, bounds.y, null);

		String text = "x" + (count > 999 ? "999+" : String.valueOf(count));
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 4;
		int h = fm.getAscent() + 1;
		int x = bounds.x + bounds.width - w;
		int y = bounds.y + bounds.height - h;
		g.setColor(Theme.alpha(Theme.INK_DEEP, 220));
		g.fillRoundRect(x, y, w, h, 3, 3);
		g.setColor(Theme.alpha(Theme.TEAL, 200));
		g.drawRoundRect(x, y, w - 1, h - 1, 3, 3);
		g.setColor(Theme.TEAL);
		g.drawString(text, x + 2, y + fm.getAscent());
	}
}
