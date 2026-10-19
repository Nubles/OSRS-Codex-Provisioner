package com.codex.provisioner.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import org.junit.Test;

public class FormatTest
{
	@Test
	public void amountsFollowTheSuiteRule()
	{
		// In full below 10,000; whole k below a million; M to one decimal; B to two.
		assertEquals("8,240", Format.amount(8_240));
		assertEquals("10k", Format.amount(10_000));
		assertEquals("41k", Format.amount(41_200));
		assertEquals("96k", Format.amount(95_700));
		assertEquals("412k", Format.amount(412_000));
		assertEquals("1.2M", Format.amount(1_240_000));
		assertEquals("26.0M", Format.amount(26_000_000));
		assertEquals("812.4M", Format.amount(812_400_000));
		assertEquals("2.10B", Format.amount(2_100_000_000L));
		// Rounding that reaches the next unit is written in it.
		assertEquals("100k", Format.amount(99_960));
		assertEquals("1.0M", Format.amount(999_600));
		assertEquals("999k", Format.amount(999_499));
		assertEquals("1.00B", Format.amount(999_960_000));
		assertEquals("-45k", Format.coins(-45_200));
		assertEquals("+1,240", Format.count(1_240));
	}

	@Test
	public void aShortfallNeverReadsAsMet()
	{
		// 10,450 of 10,499 would read "10k / 10k"; while short, the two figures always differ.
		String[] pair = Format.pair(10_450, 10_499);
		assertNotEquals(pair[0], pair[1]);
		assertEquals("10,450", pair[0]);
		assertEquals("10,499", pair[1]);
		String[] met = Format.pair(10_499, 10_499);
		assertEquals("10k", met[0]);
		assertEquals("10k", met[1]);
		assertEquals("12k", Format.pair(12_000, 40_000)[0]);
		// A share just short of whole is never rounded up to it.
		assertEquals("99%", Format.percent(0.996));
		assertEquals("100%", Format.percent(1));
		assertEquals("<1%", Format.percent(0.004));
	}
}
