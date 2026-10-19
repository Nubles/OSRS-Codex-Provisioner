package com.codex.provisioner.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The whole bank as Codex Bank Steward last saw it, which it publishes for the other Codex
 * plugins under {@link #KEY} of its config group:
 * {@code {"version":1,"savedAt":ms,"items":{"id":qty},"truncated":false}}, item ids canonical,
 * potion storage and the inventory left out. Read only, and only taken when it is newer than
 * the bank the Provisioner saw itself, such as on the first login after installing.
 */
public final class SharedBank
{
	public static final String GROUP = "codexbanksteward";
	public static final String KEY = "bankV1";

	private final long savedAt;
	private final Map<Integer, Long> items;

	private SharedBank(long savedAt, Map<Integer, Long> items)
	{
		this.savedAt = savedAt;
		this.items = items;
	}

	/** Reads a published snapshot; null when there is none or it cannot be read. */
	public static SharedBank read(Gson gson, String json)
	{
		if (json == null || json.trim().isEmpty())
		{
			return null;
		}
		try
		{
			JsonElement root = gson.fromJson(json, JsonElement.class);
			if (root == null || !root.isJsonObject())
			{
				return null;
			}
			JsonObject o = root.getAsJsonObject();
			JsonElement at = o.get("savedAt");
			JsonElement list = o.get("items");
			if (at == null || !at.isJsonPrimitive() || !at.getAsJsonPrimitive().isNumber() || list == null || !list.isJsonObject())
			{
				return null;
			}
			Map<Integer, Long> items = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : list.getAsJsonObject().entrySet())
			{
				try
				{
					int id = Integer.parseInt(e.getKey());
					long qty = e.getValue().getAsLong();
					if (id > 0 && qty > 0)
					{
						items.put(id, qty);
					}
				}
				catch (NumberFormatException | IllegalStateException | UnsupportedOperationException | ClassCastException ignored)
				{
					// One unreadable entry does not spoil the rest.
				}
			}
			long savedAt = at.getAsLong();
			return savedAt > 0 ? new SharedBank(savedAt, items) : null;
		}
		catch (JsonParseException | IllegalStateException | NumberFormatException e)
		{
			return null;
		}
	}

	public long getSavedAt()
	{
		return savedAt;
	}

	public Map<Integer, Long> getItems()
	{
		return Collections.unmodifiableMap(items);
	}
}
