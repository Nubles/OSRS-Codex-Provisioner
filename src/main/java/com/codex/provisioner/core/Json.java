package com.codex.provisioner.core;

import com.google.gson.JsonElement;
import java.math.BigDecimal;

/** Careful reading of numbers from saves this plugin did not write, or that may be damaged. */
final class Json
{
	private static final BigDecimal MIN = BigDecimal.valueOf(Long.MIN_VALUE);
	private static final BigDecimal MAX = BigDecimal.valueOf(Long.MAX_VALUE);

	private Json()
	{
	}

	/**
	 * A whole number, held to what a long can hold and rounded down from a fraction; null when
	 * the value is not a number at all. Gson's own reading of a number too big for a long gives
	 * back whatever its low bits happen to be, which can be any count at all.
	 */
	static Long whole(JsonElement e)
	{
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())
		{
			return null;
		}
		try
		{
			BigDecimal d = e.getAsBigDecimal();
			return d.compareTo(MAX) >= 0 ? Long.MAX_VALUE : d.compareTo(MIN) <= 0 ? Long.MIN_VALUE : d.longValue();
		}
		catch (NumberFormatException | ArithmeticException ex)
		{
			return null;
		}
	}

	/** A count of items: at least 0 and no more than one stack can hold. */
	static long count(long n)
	{
		return Math.max(0, Math.min(Integer.MAX_VALUE, n));
	}
}
