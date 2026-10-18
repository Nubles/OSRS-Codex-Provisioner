package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;

/**
 * Kits for popular content, so the panel is useful before the player has saved one of their own.
 * The amounts are a typical trip of several kills; once Codex Quartermaster has measured a few
 * trips, the Provisioner plans from what the player really used instead.
 */
public final class Presets
{
	private static final List<Kit> ALL = new ArrayList<>();

	static
	{
		add("vorkath", "Vorkath",
			"FOOD", 200, "PRAYER", 12, "RANGING", 4, "SUPER_ANTIFIRE", 4, "ANTIVENOM", 4, "DRAGON_BOLTS", 150,
			item(ItemID.POH_TABLET_TELEPORTTOHOUSE), 1);
		add("zulrah", "Zulrah",
			"FOOD", 220, "PRAYER", 12, "RANGING", 4, "MAGIC", 4, "ANTIVENOM", 4,
			item(ItemID.TELEPORTSCROLL_ZULANDRA), 1, item(ItemID.POH_TABLET_TELEPORTTOHOUSE), 1);
		add("barrows", "Barrows",
			"FOOD", 60, "PRAYER", 12, item(ItemID.TELETAB_BARROWS), 1);
		add("graardor", "General Graardor",
			"BREW", 16, "PRAYER", 16, "COMBAT", 4);
		add("vardorvis", "Vardorvis",
			"FOOD", 160, "PRAYER", 12, "COMBAT", 4);
		add("nex", "Nex",
			"BREW", 24, "PRAYER", 24, "RANGING", 8);
		add("tob", "Theatre of Blood",
			"BREW", 32, "PRAYER", 32, "COMBAT", 4, "STAMINA", 4);
		add("slayer", "Slayer task",
			"FOOD", 160, "PRAYER", 8, "COMBAT", 4);
		add("wintertodt", "Wintertodt",
			"FOOD", 120);
	}

	private Presets()
	{
	}

	private static String item(int itemId)
	{
		return Supply.itemKey(itemId);
	}

	private static void add(String id, String name, Object... pairs)
	{
		List<Need> needs = new ArrayList<>();
		for (int i = 0; i + 1 < pairs.length; i += 2)
		{
			needs.add(new Need((String) pairs[i], (Integer) pairs[i + 1]));
		}
		ALL.add(new Kit("preset:" + id, name, needs, true));
	}

	public static List<Kit> all()
	{
		return Collections.unmodifiableList(ALL);
	}
}
