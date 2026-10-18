package com.codex.provisioner.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing a trip uses up, counted in its own unit: food in hitpoints healed, potions in doses,
 * bolts one by one. A supply can be met by several items, so four doses of prayer potion and four
 * of super restore count the same, and a shark and a manta ray both count as food.
 */
public final class Supply
{
	private static final String ITEM = "item:";

	private final String key;
	private final String name;
	private final String unit;
	private final int icon;
	private final Map<Integer, Integer> units;

	Supply(String key, String name, String unit, int icon, Map<Integer, Integer> units)
	{
		this.key = key;
		this.name = name;
		this.unit = unit;
		this.icon = icon;
		this.units = Collections.unmodifiableMap(new LinkedHashMap<>(units));
	}

	/** A supply that is one item and nothing else, such as a teleport tablet or a rune. */
	public static Supply item(int itemId)
	{
		return new Supply(itemKey(itemId), null, null, itemId, Collections.singletonMap(itemId, 1));
	}

	public static String itemKey(int itemId)
	{
		return ITEM + itemId;
	}

	public String getKey()
	{
		return key;
	}

	/** @return the supply's name, or null for a single item, which is named after the item */
	public String getName()
	{
		return name;
	}

	/** @return "hp", "doses" and the like, or null when it is counted in items */
	public String getUnit()
	{
		return unit;
	}

	public int getIcon()
	{
		return icon;
	}

	public boolean isItem()
	{
		return key.startsWith(ITEM);
	}

	/** Every item that counts, with how many units one of it is worth. */
	public Map<Integer, Integer> getUnits()
	{
		return units;
	}

	/** How much of this supply a set of items holds, in units. */
	public long count(Map<Integer, Long> items)
	{
		long total = 0;
		for (Map.Entry<Integer, Integer> e : units.entrySet())
		{
			Long qty = items.get(e.getKey());
			if (qty != null && qty > 0)
			{
				total += qty * e.getValue();
			}
		}
		return total;
	}
}
