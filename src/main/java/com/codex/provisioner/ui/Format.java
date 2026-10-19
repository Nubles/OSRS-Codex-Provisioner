package com.codex.provisioner.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Coins, changes, durations and dates in the short forms the panel uses. */
public final class Format
{
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

	private Format()
	{
	}

	/**
	 * As every Codex plugin writes figures: in full below 10,000, whole k below a million, M to one
	 * decimal, B to two: "8,240", "41k", "412k", "1.2M", "2.10B". Rounding that reaches the next
	 * unit is written in it, so 999,600 reads "1.0M", never "1000k".
	 */
	public static String amount(long n)
	{
		if (n < 0)
		{
			return "-" + amount(n == Long.MIN_VALUE ? Long.MAX_VALUE : -n);
		}
		if (n < 10_000)
		{
			return String.format(Locale.ENGLISH, "%,d", n);
		}
		long k = Math.round(n / 1_000.0);
		if (k < 1_000)
		{
			return k + "k";
		}
		long tenths = Math.round(n / 100_000.0);
		if (tenths < 10_000)
		{
			return tenths / 10 + "." + tenths % 10 + "M";
		}
		long hundredths = Math.round(n / 10_000_000.0);
		return String.format(Locale.ENGLISH, "%,d", hundredths / 100) + "." + String.format(Locale.ENGLISH, "%02d", hundredths % 100) + "B";
	}

	/**
	 * Held against wanted, {held, wanted}, in the short form; while short, never two figures that
	 * read the same, so 10,450 of 10,499 is written in full rather than "10k / 10k".
	 */
	public static String[] pair(long have, long want)
	{
		String h = amount(have);
		String w = amount(want);
		if (have < want && h.equals(w))
		{
			return new String[]{String.format(Locale.ENGLISH, "%,d", have), String.format(Locale.ENGLISH, "%,d", want)};
		}
		return new String[]{h, w};
	}

	/** Coins in the short form, negative amounts with a minus: "812.4M", "-45k". */
	public static String coins(long gp)
	{
		return gp < 0 ? "-" + amount(-gp) : amount(gp);
	}

	/** A change with its sign: "+3.2M", "-412k", "0". */
	public static String signed(long gp)
	{
		return gp > 0 ? "+" + amount(gp) : gp < 0 ? "-" + amount(-gp) : "0";
	}

	/** A quantity change with its sign, written in full: "+1,240", "-6". */
	public static String count(long n)
	{
		return (n > 0 ? "+" : n < 0 ? "-" : "") + String.format(Locale.ENGLISH, "%,d", Math.abs(n));
	}

	/** A count of trips, or "999+" past what is worth counting. */
	public static String trips(int n)
	{
		if (n >= 1000)
		{
			return "999+";
		}
		return String.valueOf(n);
	}

	/** A trip's use of a supply: whole when it is, otherwise to one place, "16", "13.5". */
	public static String perTrip(double n)
	{
		if (n >= 100 || Math.abs(n - Math.round(n)) < 0.05)
		{
			return String.format(Locale.ENGLISH, "%,d", Math.round(n));
		}
		return trim(n, 1);
	}

	/** A share as a whole percent, or "<1%". */
	public static String percent(double share)
	{
		if (share > 0 && share < 0.01)
		{
			return "<1%";
		}
		// Just short of whole is not whole: 99.6% reads 99%.
		return Math.min(share < 1 ? 99 : 100, Math.round(share * 100)) + "%";
	}

	/** "just now", "12m ago", "3h ago", "2d ago". */
	public static String ago(long ms)
	{
		long minutes = Math.max(0, ms) / 60_000;
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + "m ago";
		}
		if (minutes < 48 * 60)
		{
			return minutes / 60 + "h ago";
		}
		return minutes / (24 * 60) + "d ago";
	}

	private static String trim(double value, int places)
	{
		String text = String.format(Locale.ENGLISH, "%." + places + "f", value);
		while (text.contains(".") && (text.endsWith("0") || text.endsWith(".")))
		{
			text = text.substring(0, text.length() - 1);
		}
		return text;
	}

	/** "2h 14m", "38m", "under a minute". */
	public static String duration(long ms)
	{
		long minutes = ms / 60_000;
		if (minutes < 1)
		{
			return "under a minute";
		}
		if (minutes < 60)
		{
			return minutes + "m";
		}
		return minutes / 60 + "h " + minutes % 60 + "m";
	}

	/** A rough time to come: "<1m", "25m", "3h 10m", "41h". */
	public static String estimate(long ms)
	{
		long minutes = Math.round(ms / 60_000.0);
		if (minutes < 1)
		{
			return "<1m";
		}
		if (minutes < 60)
		{
			return minutes + "m";
		}
		if (minutes < 10 * 60)
		{
			return minutes / 60 + "h " + minutes % 60 + "m";
		}
		return Math.round(minutes / 60.0) + "h";
	}

	/** "14:02" in the player's own time. */
	public static String time(long epochMs)
	{
		return CLOCK.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "Thu 16 Oct". */
	public static String day(long epochMs)
	{
		return DAY.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "16 Oct". */
	public static String shortDay(long epochMs)
	{
		return SHORT.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "Oct 26". */
	public static String month(long epochMs)
	{
		return MONTH.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** "Thu 16 Oct" for a day book date. */
	public static String day(java.time.LocalDate date)
	{
		return DAY.format(date);
	}

	/** "Thursday 16 October 2026". */
	public static String date(long epochMs)
	{
		return DATE.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/** An item stack the way the game writes it: "950", "45K", "12M". */
	public static String stack(long n)
	{
		if (n < 100_000)
		{
			return String.valueOf(n);
		}
		if (n < 10_000_000)
		{
			return n / 1_000 + "K";
		}
		return n / 1_000_000 + "M";
	}

	/** The colour the game writes a stack of that size in. */
	public static java.awt.Color stackColor(long n)
	{
		return n < 100_000 ? Theme.QTY_YELLOW : n < 10_000_000 ? Theme.QTY_WHITE : Theme.QTY_GREEN;
	}
}
