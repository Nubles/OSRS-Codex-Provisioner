package com.codex.provisioner.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class ArrivalsTest
{
	@Test
	public void waitsOnAPictureOnlyUntilItArrives()
	{
		Arrivals arrivals = new Arrivals();
		AtomicInteger refreshes = new AtomicInteger();
		assertFalse(arrivals.arrived(4151));
		Runnable first = arrivals.watch(4151, refreshes::incrementAndGet);
		Runnable second = arrivals.watch(4151, refreshes::incrementAndGet);
		first.run();
		second.run();
		// Both asks made while it loaded are answered, and after that nothing more waits on it.
		assertEquals(2, refreshes.get());
		assertTrue(arrivals.arrived(4151));
		assertFalse(arrivals.arrived(11286));
	}

	@Test
	public void toleratesNoCallback()
	{
		Arrivals arrivals = new Arrivals();
		arrivals.watch(1, null).run();
		assertTrue(arrivals.arrived(1));
	}
}
