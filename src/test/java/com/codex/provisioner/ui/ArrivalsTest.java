package com.codex.provisioner.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;

public class ArrivalsTest
{
	/** A picture still loading; it has no client thread, so a late ask on it would fail the test. */
	private static AsyncBufferedImage loading()
	{
		return new AsyncBufferedImage(null, 1, 1, BufferedImage.TYPE_INT_ARGB);
	}

	@Test
	public void waitsOnAPictureOnlyUntilItArrives()
	{
		Arrivals arrivals = new Arrivals();
		AtomicInteger refreshes = new AtomicInteger();
		AsyncBufferedImage whip = loading();
		arrivals.ask(whip, refreshes::incrementAndGet);
		arrivals.ask(whip, refreshes::incrementAndGet);
		assertFalse(arrivals.arrived(whip));
		whip.loaded();
		// Both asks made while it loaded are answered, and after that nothing more waits on it.
		assertEquals(2, refreshes.get());
		assertTrue(arrivals.arrived(whip));
		arrivals.ask(whip, refreshes::incrementAndGet);
		assertEquals(2, refreshes.get());
	}

	@Test
	public void waitsAgainOnAFreshPictureOfTheSameItem()
	{
		Arrivals arrivals = new Arrivals();
		AtomicInteger refreshes = new AtomicInteger();
		AsyncBufferedImage first = loading();
		arrivals.ask(first, refreshes::incrementAndGet);
		first.loaded();
		// RuneLite dropped the picture from its cache and made a new, blank one for the same item.
		AsyncBufferedImage again = loading();
		arrivals.ask(again, refreshes::incrementAndGet);
		again.loaded();
		assertEquals(2, refreshes.get());
	}

	@Test
	public void toleratesNoCallbackAndNoPicture()
	{
		Arrivals arrivals = new Arrivals();
		AsyncBufferedImage image = loading();
		arrivals.ask(image, null);
		arrivals.ask(null, null);
		image.loaded();
		assertTrue(arrivals.arrived(image));
	}
}
