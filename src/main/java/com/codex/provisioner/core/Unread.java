package com.codex.provisioner.core;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Map;

/**
 * Tells whether a save holds anything its read-back lacks: a field or an entry of a kind a later
 * version keeps, or something damage put in that reading had to leave out. Such a save reads
 * without an error, but the next save would quietly drop what this version did not understand,
 * so it is worth setting aside first. Figures set right in reading (a count below zero brought
 * up to it) are not counted; nor is a nothing (zero, false, empty) that the read-back leaves out.
 */
final class Unread
{
	private Unread()
	{
	}

	/** True when the save holds anything that reading it back left out. */
	static boolean any(Gson gson, String saved, Object read)
	{
		try
		{
			return lost(gson.fromJson(saved, JsonElement.class), gson.toJsonTree(read));
		}
		catch (RuntimeException e)
		{
			return true;
		}
	}

	private static boolean lost(JsonElement saved, JsonElement back)
	{
		if (nothing(saved))
		{
			return false;
		}
		if (back == null || back.isJsonNull())
		{
			return true;
		}
		if (saved.isJsonObject())
		{
			if (!back.isJsonObject())
			{
				return true;
			}
			JsonObject b = back.getAsJsonObject();
			for (Map.Entry<String, JsonElement> e : saved.getAsJsonObject().entrySet())
			{
				if (lost(e.getValue(), b.get(e.getKey())))
				{
					return true;
				}
			}
			return false;
		}
		if (saved.isJsonArray())
		{
			if (!back.isJsonArray())
			{
				return true;
			}
			JsonArray s = saved.getAsJsonArray();
			JsonArray b = back.getAsJsonArray();
			if (s.size() > b.size())
			{
				return true;
			}
			// Reading can add to a list (a field an older save had not yet) but not take from it;
			// a list read back the same length is checked item by item.
			if (s.size() == b.size())
			{
				for (int i = 0; i < s.size(); i++)
				{
					if (lost(s.get(i), b.get(i)))
					{
						return true;
					}
				}
			}
			return false;
		}
		// A figure: kept when the read-back has one in its place, set right or not.
		return !back.isJsonPrimitive();
	}

	private static boolean nothing(JsonElement e)
	{
		if (e == null || e.isJsonNull())
		{
			return true;
		}
		if (e.isJsonPrimitive())
		{
			JsonPrimitive p = e.getAsJsonPrimitive();
			if (p.isBoolean())
			{
				return !p.getAsBoolean();
			}
			if (p.isNumber())
			{
				return p.getAsBigDecimal().signum() == 0;
			}
			return p.getAsString().isEmpty();
		}
		return e.isJsonArray() ? e.getAsJsonArray().size() == 0 : e.getAsJsonObject().size() == 0;
	}
}
