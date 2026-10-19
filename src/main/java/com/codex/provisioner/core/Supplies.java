package com.codex.provisioner.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

/**
 * The supplies Codex Quartermaster and the Provisioner pool. Anything else a kit carries is counted as
 * its own item. Potions are pooled across their strengths where one stands in for another, so a
 * divine ranging potion and a plain one both count toward the same doses.
 */
public final class Supplies
{
	private static final Map<String, Supply> GROUPS = new LinkedHashMap<>();
	/** The first group each item belongs to, which is the one a captured kit files it under. */
	private static final Map<Integer, String> HOME = new LinkedHashMap<>();

	static
	{
		group("FOOD", "Food", "hp", ItemID.SHARK,
			ItemID.MANTARAY, 22,
			ItemID.ANGLERFISH, 22,
			ItemID.DARK_CRAB, 22,
			ItemID.POTATO_TUNA_SWEETCORN, 22,
			ItemID.SEATURTLE, 21,
			ItemID.SHARK, 20,
			ItemID.TBWT_COOKED_KARAMBWAN, 18,
			ItemID.MONKFISH, 16,
			ItemID.POTATO_CHEESE, 16,
			ItemID.SWORDFISH, 14,
			ItemID.BASS, 13,
			ItemID.CAKE, 12,
			ItemID.LOBSTER, 12,
			ItemID.JUG_WINE, 11,
			ItemID.PARTIAL_CAKE, 8,
			ItemID.CAKE_SLICE, 4);
		group("BREW", "Saradomin brew", "doses", ItemID._4DOSEPOTIONOFSARADOMIN,
			ItemID._4DOSEPOTIONOFSARADOMIN, 4,
			ItemID._3DOSEPOTIONOFSARADOMIN, 3,
			ItemID._2DOSEPOTIONOFSARADOMIN, 2,
			ItemID._1DOSEPOTIONOFSARADOMIN, 1);
		group("PRAYER", "Prayer and restores", "doses", ItemID._4DOSEPRAYERRESTORE,
			ItemID._4DOSEPRAYERRESTORE, 4,
			ItemID._3DOSEPRAYERRESTORE, 3,
			ItemID._2DOSEPRAYERRESTORE, 2,
			ItemID._1DOSEPRAYERRESTORE, 1,
			ItemID._4DOSE2RESTORE, 4,
			ItemID._3DOSE2RESTORE, 3,
			ItemID._2DOSE2RESTORE, 2,
			ItemID._1DOSE2RESTORE, 1,
			ItemID.SANFEW_SALVE_4_DOSE, 4,
			ItemID.SANFEW_SALVE_3_DOSE, 3,
			ItemID.SANFEW_SALVE_2_DOSE, 2,
			ItemID.SANFEW_SALVE_1_DOSE, 1);
		group("RANGING", "Ranging potions", "doses", ItemID._4DOSERANGERSPOTION,
			ItemID._4DOSEDIVINERANGE, 4,
			ItemID._3DOSEDIVINERANGE, 3,
			ItemID._2DOSEDIVINERANGE, 2,
			ItemID._1DOSEDIVINERANGE, 1,
			ItemID._4DOSERANGERSPOTION, 4,
			ItemID._3DOSERANGERSPOTION, 3,
			ItemID._2DOSERANGERSPOTION, 2,
			ItemID._1DOSERANGERSPOTION, 1,
			ItemID._4DOSEDIVINEBASTION, 4,
			ItemID._3DOSEDIVINEBASTION, 3,
			ItemID._2DOSEDIVINEBASTION, 2,
			ItemID._1DOSEDIVINEBASTION, 1,
			ItemID._4DOSEBASTION, 4,
			ItemID._3DOSEBASTION, 3,
			ItemID._2DOSEBASTION, 2,
			ItemID._1DOSEBASTION, 1);
		group("MAGIC", "Magic potions", "doses", ItemID._4DOSE1MAGIC,
			ItemID._4DOSEDIVINEMAGIC, 4,
			ItemID._3DOSEDIVINEMAGIC, 3,
			ItemID._2DOSEDIVINEMAGIC, 2,
			ItemID._1DOSEDIVINEMAGIC, 1,
			ItemID._4DOSE1MAGIC, 4,
			ItemID._3DOSE1MAGIC, 3,
			ItemID._2DOSE1MAGIC, 2,
			ItemID._1DOSE1MAGIC, 1);
		group("COMBAT", "Super combat", "doses", ItemID._4DOSE2COMBAT,
			ItemID._4DOSEDIVINECOMBAT, 4,
			ItemID._3DOSEDIVINECOMBAT, 3,
			ItemID._2DOSEDIVINECOMBAT, 2,
			ItemID._1DOSEDIVINECOMBAT, 1,
			ItemID._4DOSE2COMBAT, 4,
			ItemID._3DOSE2COMBAT, 3,
			ItemID._2DOSE2COMBAT, 2,
			ItemID._1DOSE2COMBAT, 1);
		group("SUPER_ANTIFIRE", "Super antifire", "doses", ItemID._4DOSE4ANTIDRAGON,
			ItemID._4DOSE4ANTIDRAGON, 4,
			ItemID._3DOSE4ANTIDRAGON, 3,
			ItemID._2DOSE4ANTIDRAGON, 2,
			ItemID._1DOSE4ANTIDRAGON, 1,
			ItemID._4DOSE3ANTIDRAGON, 4,
			ItemID._3DOSE3ANTIDRAGON, 3,
			ItemID._2DOSE3ANTIDRAGON, 2,
			ItemID._1DOSE3ANTIDRAGON, 1);
		group("ANTIFIRE", "Antifire", "doses", ItemID._4DOSE1ANTIDRAGON,
			ItemID._4DOSE4ANTIDRAGON, 4,
			ItemID._3DOSE4ANTIDRAGON, 3,
			ItemID._2DOSE4ANTIDRAGON, 2,
			ItemID._1DOSE4ANTIDRAGON, 1,
			ItemID._4DOSE3ANTIDRAGON, 4,
			ItemID._3DOSE3ANTIDRAGON, 3,
			ItemID._2DOSE3ANTIDRAGON, 2,
			ItemID._1DOSE3ANTIDRAGON, 1,
			ItemID._4DOSE2ANTIDRAGON, 4,
			ItemID._3DOSE2ANTIDRAGON, 3,
			ItemID._2DOSE2ANTIDRAGON, 2,
			ItemID._1DOSE2ANTIDRAGON, 1,
			ItemID._4DOSE1ANTIDRAGON, 4,
			ItemID._3DOSE1ANTIDRAGON, 3,
			ItemID._2DOSE1ANTIDRAGON, 2,
			ItemID._1DOSE1ANTIDRAGON, 1);
		group("ANTIVENOM", "Anti-venom", "doses", ItemID.ANTIVENOM_4,
			ItemID.ANTIVENOM_4, 4,
			ItemID.ANTIVENOM_3, 3,
			ItemID.ANTIVENOM_2, 2,
			ItemID.ANTIVENOM_1, 1,
			ItemID.ANTIVENOM4, 4,
			ItemID.ANTIVENOM3, 3,
			ItemID.ANTIVENOM2, 2,
			ItemID.ANTIVENOM1, 1);
		group("STAMINA", "Stamina", "doses", ItemID._4DOSESTAMINA,
			ItemID._4DOSESTAMINA, 4,
			ItemID._3DOSESTAMINA, 3,
			ItemID._2DOSESTAMINA, 2,
			ItemID._1DOSESTAMINA, 1);
		group("DRAGON_BOLTS", "Dragon bolts (e)", "bolts", ItemID.DRAGON_BOLTS_ENCHANTED_RUBY,
			ItemID.DRAGON_BOLTS_ENCHANTED_RUBY, 1,
			ItemID.DRAGON_BOLTS_ENCHANTED_DIAMOND, 1);
	}

	private Supplies()
	{
	}

	private static void group(String key, String name, String unit, int icon, int... pairs)
	{
		Map<Integer, Integer> units = new LinkedHashMap<>();
		for (int i = 0; i + 1 < pairs.length; i += 2)
		{
			units.put(pairs[i], pairs[i + 1]);
			HOME.putIfAbsent(pairs[i], key);
		}
		GROUPS.put(key, new Supply(key, name, unit, icon, units));
	}

	/** The supply for a key: a pooled group, or a single item for "item:" keys. Null when unknown or no real item. */
	public static Supply get(String key)
	{
		Supply group = GROUPS.get(key);
		if (group != null)
		{
			return group;
		}
		if (key != null && key.startsWith("item:"))
		{
			try
			{
				int id = Integer.parseInt(key.substring(5));
				// No item has an id below one; a damaged kit asking for one is not asked of the game.
				return id > 0 ? Supply.item(id) : null;
			}
			catch (NumberFormatException e)
			{
				return null;
			}
		}
		return null;
	}

	/** The key an item is filed under when a kit is captured from what is carried. */
	public static String keyFor(int itemId)
	{
		String group = HOME.get(itemId);
		return group != null ? group : Supply.itemKey(itemId);
	}

	public static boolean pooled(int itemId)
	{
		return HOME.containsKey(itemId);
	}

	public static Collection<Supply> groups()
	{
		return Collections.unmodifiableCollection(GROUPS.values());
	}
}
