package com.codex.provisioner.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

/** A row deep in a tree keeps its name whole at the side panel's width. */
public class TreeRowTest
{
	@Test
	public void aLongNameDeepInATreeTakesTheWholeLine()
	{
		Graphics2D g = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB).createGraphics();
		FontMetrics body = g.getFontMetrics(Theme.body());
		FontMetrics small = g.getFontMetrics(Theme.small());
		// Five levels down at 225px, about 96px is left right of the picture.
		int room = 96;
		TreeCard.Layout deep = TreeCard.layout("Grimy cadantine", "grow, 4 herb runs", "200", body, small, room);
		assertTrue(deep.figureBelow);
		assertTrue(small.stringWidth("Grimy cadantine") <= deep.nameRoom);
		// The line under it gives up its verb rather than its point.
		assertEquals("4 herb runs", TreeCard.fit("grow, 4 herb runs", small, deep.lineRoom));

		TreeCard.Layout shallow = TreeCard.layout("Shark", "cook, ready", "80", body, small, 150);
		assertFalse(shallow.figureBelow);
		assertFalse(shallow.smallName);
		assertEquals("cook, ready", TreeCard.fit("cook, ready", small, shallow.lineRoom));
		g.dispose();
	}
}
