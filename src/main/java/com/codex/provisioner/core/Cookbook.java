package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;

/**
 * Every recipe the Provisioner can walk, from the raw fish or grimy herb to the supply a kit
 * carries, and for each pooled supply the items it can be made as. Herblore makes 3-dose
 * potions; where a recipe wants 4-dose ones, a decanting step sits in between.
 */
public final class Cookbook
{
	private static final Map<Integer, Recipe> RECIPES = new HashMap<>();
	private static final Map<String, List<Integer>> PRODUCTS = new LinkedHashMap<>();

	static
	{
		food();
		herbs();
		potions();
		bolts();
	}

	private Cookbook()
	{
	}

	private static Recipe add(Verb verb, int output)
	{
		Recipe r = new Recipe(verb, output);
		RECIPES.put(output, r);
		return r;
	}

	private static void products(String supply, int... items)
	{
		List<Integer> list = new ArrayList<>();
		for (int i : items)
		{
			list.add(i);
		}
		PRODUCTS.put(supply, list);
	}

	private static void food()
	{
		// Best healing first, so the first one the player's levels reach is the one picked.
		products("FOOD", ItemID.ANGLERFISH, ItemID.SHARK, ItemID.TBWT_COOKED_KARAMBWAN, ItemID.MONKFISH, ItemID.SWORDFISH, ItemID.LOBSTER);

		add(Verb.CATCH, ItemID.RAW_ANGLERFISH).from(ItemID.PISCARILIUS_SANDWORMS, 1).needs(Skill.FISHING, 82).tool(ItemID.FISHING_ROD)
			.how("Fish at Port Piscarilius with a fishing rod, one sandworm a catch.");
		add(Verb.BUY, ItemID.PISCARILIUS_SANDWORMS)
			.how("Buy from Tynan's fishing shop in Port Piscarilius.");
		add(Verb.COOK, ItemID.ANGLERFISH).from(ItemID.RAW_ANGLERFISH, 1).needs(Skill.COOKING, 84)
			.how("Cook on a range; the Hosidius kitchen burns least. Burnt ones don't count, so the step stays open until enough are cooked.");

		add(Verb.CATCH, ItemID.RAW_SHARK).needs(Skill.FISHING, 76).tool(ItemID.HARPOON)
			.how("Harpoon sharks at Catherby or the Fishing Guild.");
		add(Verb.COOK, ItemID.SHARK).from(ItemID.RAW_SHARK, 1).needs(Skill.COOKING, 80)
			.how("Cook on a range; the Hosidius kitchen burns least. Burnt ones don't count, so the step stays open until enough are cooked.");

		add(Verb.CATCH, ItemID.TBWT_RAW_KARAMBWAN).from(ItemID.TBWT_RAW_KARAMBWANJI, 1).needs(Skill.FISHING, 65).tool(ItemID.TBWT_KARAMBWAN_VESSEL)
			.how("Fish at the fairy ring DKP with a karambwan vessel baited with karambwanji. Needs Tai Bwo Wannai Trio.");
		add(Verb.CATCH, ItemID.TBWT_RAW_KARAMBWANJI).needs(Skill.FISHING, 5).tool(ItemID.NET)
			.how("Net karambwanji at the lake south of Tai Bwo Wannai.");
		add(Verb.COOK, ItemID.TBWT_COOKED_KARAMBWAN).from(ItemID.TBWT_RAW_KARAMBWAN, 1).needs(Skill.COOKING, 30)
			.how("Cook them properly on a range (not the quick way, which poisons). Burnt ones don't count.");

		add(Verb.CATCH, ItemID.RAW_MONKFISH).needs(Skill.FISHING, 62).tool(ItemID.NET)
			.how("Net monkfish at the Piscatoris Fishing Colony. Needs Swan Song.");
		add(Verb.COOK, ItemID.MONKFISH).from(ItemID.RAW_MONKFISH, 1).needs(Skill.COOKING, 62)
			.how("Cook on a range; the colony has one beside the bank. Burnt ones don't count.");

		add(Verb.CATCH, ItemID.RAW_SWORDFISH).needs(Skill.FISHING, 50).tool(ItemID.HARPOON)
			.how("Harpoon swordfish at Catherby, Karamja or the Fishing Guild.");
		add(Verb.COOK, ItemID.SWORDFISH).from(ItemID.RAW_SWORDFISH, 1).needs(Skill.COOKING, 45)
			.how("Cook on a range. Burnt ones don't count.");

		add(Verb.CATCH, ItemID.RAW_LOBSTER).needs(Skill.FISHING, 40).tool(ItemID.LOBSTER_POT)
			.how("Cage lobsters at Catherby, Karamja or the Fishing Guild.");
		add(Verb.COOK, ItemID.LOBSTER).from(ItemID.RAW_LOBSTER, 1).needs(Skill.COOKING, 40)
			.how("Cook on a range. Burnt ones don't count.");
	}

	/** A grimy herb, cleaning it, and the unfinished potion made from it. */
	private static void herb(int grimy, int clean, int unf, int farming, int cleanLevel, int unfLevel)
	{
		add(Verb.GATHER, grimy).grows(Skill.FARMING, farming)
			.how("Grow in herb patches, or take from monster drops.");
		add(Verb.CLEAN, clean).from(grimy, 1).needs(Skill.HERBLORE, cleanLevel)
			.how("Clean the grimy herbs; anywhere will do.");
		if (unf > 0)
		{
			add(Verb.MIX, unf).from(clean, 1).from(ItemID.VIAL_WATER, 1).needs(Skill.HERBLORE, unfLevel)
				.how("Add each clean herb to a vial of water.");
		}
	}

	private static void herbs()
	{
		add(Verb.BUY, ItemID.VIAL_EMPTY)
			.how("Buy empty vials from a herblore shop.");
		add(Verb.FILL, ItemID.VIAL_WATER).from(ItemID.VIAL_EMPTY, 1)
			.how("Fill the vials at any fountain, sink or well.");

		herb(ItemID.UNIDENTIFIED_RANARR, ItemID.RANARR_WEED, ItemID.RANARRVIAL, 32, 25, 30);
		herb(ItemID.UNIDENTIFIED_TOADFLAX, ItemID.TOADFLAX, ItemID.TOADFLAXVIAL, 38, 30, 34);
		herb(ItemID.UNIDENTIFIED_IRIT, ItemID.IRIT_LEAF, ItemID.IRITVIAL, 44, 40, 45);
		herb(ItemID.UNIDENTIFIED_AVANTOE, ItemID.AVANTOE, ItemID.AVANTOEVIAL, 50, 48, 50);
		herb(ItemID.UNIDENTIFIED_KWUARM, ItemID.KWUARM, ItemID.KWUARMVIAL, 56, 54, 55);
		herb(ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.SNAPDRAGON, ItemID.SNAPDRAGONVIAL, 62, 59, 63);
		herb(ItemID.UNIDENTIFIED_CADANTINE, ItemID.CADANTINE, ItemID.CADANTINEVIAL, 67, 65, 66);
		herb(ItemID.UNIDENTIFIED_LANTADYME, ItemID.LANTADYME, ItemID.LANTADYMEVIAL, 73, 67, 69);
		herb(ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.DWARF_WEED, ItemID.DWARFWEEDVIAL, 79, 70, 72);
		herb(ItemID.UNIDENTIFIED_TORSTOL, ItemID.TORSTOL, 0, 85, 75, 0);
	}

	/** A finished potion from an unfinished one and its secondary. */
	private static void potion(int potion, int unf, int secondary, int level)
	{
		add(Verb.MIX, potion).from(unf, 1).from(secondary, 1).needs(Skill.HERBLORE, level)
			.how("Add the secondary to each unfinished potion.");
	}

	/** Four 3-dose potions decanted into three 4-dose ones. */
	private static void decant(int four, int three)
	{
		add(Verb.DECANT, four).from(three, 4).makes(3)
			.how("Combine doses by hand, or ask Bob Barter at the Grand Exchange to decant.");
	}

	private static void potions()
	{
		products("PRAYER", ItemID._3DOSEPRAYERRESTORE, ItemID._3DOSE2RESTORE);
		products("BREW", ItemID._3DOSEPOTIONOFSARADOMIN);
		products("RANGING", ItemID._3DOSERANGERSPOTION);
		products("MAGIC", ItemID._3DOSE1MAGIC);
		products("COMBAT", ItemID._4DOSE2COMBAT);
		products("SUPER_ANTIFIRE", ItemID._4DOSE3ANTIDRAGON);
		products("ANTIFIRE", ItemID._3DOSE1ANTIDRAGON);
		products("ANTIVENOM", ItemID.ANTIVENOM4);
		products("STAMINA", ItemID._3DOSESTAMINA);

		potion(ItemID._3DOSEPRAYERRESTORE, ItemID.RANARRVIAL, ItemID.SNAPE_GRASS, 38);
		add(Verb.GATHER, ItemID.SNAPE_GRASS).grows(Skill.FARMING, 61)
			.how("Pick on Waterbirth Island, or grow from seed in an allotment.");

		potion(ItemID._3DOSE2RESTORE, ItemID.SNAPDRAGONVIAL, ItemID.RED_SPIDERS_EGGS, 63);
		add(Verb.GATHER, ItemID.RED_SPIDERS_EGGS)
			.how("Pick up where red spiders live, such as the Edgeville Dungeon.");

		potion(ItemID._3DOSEPOTIONOFSARADOMIN, ItemID.TOADFLAXVIAL, ItemID.CRUSHED_BIRD_NEST, 81);
		add(Verb.CRUSH, ItemID.CRUSHED_BIRD_NEST).from(ItemID.BIRD_NEST_EMPTY, 1).tool(ItemID.PESTLE_AND_MORTAR)
			.how("Grind empty bird nests with a pestle and mortar.");
		add(Verb.GATHER, ItemID.BIRD_NEST_EMPTY)
			.how("Search bird nests from Woodcutting and birdhouses; the empty nest is left behind.");

		potion(ItemID._3DOSERANGERSPOTION, ItemID.DWARFWEEDVIAL, ItemID.WINE_OF_ZAMORAK, 72);
		add(Verb.GATHER, ItemID.WINE_OF_ZAMORAK).needs(Skill.MAGIC, 33)
			.how("Telegrab it from the table in the Chaos Temple in Asgarnia.");

		potion(ItemID._3DOSE1MAGIC, ItemID.LANTADYMEVIAL, ItemID.CACTUS_POTATO, 76);
		add(Verb.GATHER, ItemID.CACTUS_POTATO).grows(Skill.FARMING, 64)
			.how("Kalphites drop them, or grow them in a cactus patch.");

		potion(ItemID._3DOSE2ATTACK, ItemID.IRITVIAL, ItemID.EYE_OF_NEWT, 45);
		add(Verb.BUY, ItemID.EYE_OF_NEWT)
			.how("Buy from a herblore or magic shop, such as Betty's in Port Sarim.");
		potion(ItemID._3DOSE2STRENGTH, ItemID.KWUARMVIAL, ItemID.LIMPWURT_ROOT, 55);
		add(Verb.GATHER, ItemID.LIMPWURT_ROOT).grows(Skill.FARMING, 26)
			.how("Hill giants drop them, or grow them in a flower patch.");
		potion(ItemID._3DOSE2DEFENSE, ItemID.CADANTINEVIAL, ItemID.WHITE_BERRIES, 66);
		add(Verb.GATHER, ItemID.WHITE_BERRIES).grows(Skill.FARMING, 59)
			.how("Pick on Lava Dragon Isle, or grow them in a bush patch.");
		decant(ItemID._4DOSE2ATTACK, ItemID._3DOSE2ATTACK);
		decant(ItemID._4DOSE2STRENGTH, ItemID._3DOSE2STRENGTH);
		decant(ItemID._4DOSE2DEFENSE, ItemID._3DOSE2DEFENSE);
		add(Verb.MIX, ItemID._4DOSE2COMBAT).from(ItemID.TORSTOL, 1).from(ItemID._4DOSE2ATTACK, 1)
			.from(ItemID._4DOSE2STRENGTH, 1).from(ItemID._4DOSE2DEFENSE, 1).needs(Skill.HERBLORE, 90)
			.how("Mix a clean torstol with a 4-dose super attack, strength and defence.");

		potion(ItemID._3DOSE2ENERGY, ItemID.AVANTOEVIAL, ItemID.MORTMYREMUSHROOM, 52);
		add(Verb.GATHER, ItemID.MORTMYREMUSHROOM)
			.how("Cast Bloom with a blessed silver sickle in Mort Myre Swamp.");
		add(Verb.MIX, ItemID._3DOSESTAMINA).from(ItemID._3DOSE2ENERGY, 1).from(ItemID.AMYLASE, 3).needs(Skill.HERBLORE, 77)
			.how("Add one amylase crystal a dose to super energy.");
		add(Verb.BUY, ItemID.AMYLASE)
			.how("Trade marks of grace to Grace for amylase packs.");

		add(Verb.MIX, ItemID.UNFINISHED_ANTIDOTE__).from(ItemID.VIAL_COCONUT_MILK, 1).from(ItemID.IRIT_LEAF, 1).needs(Skill.HERBLORE, 79)
			.how("Add a clean irit leaf to coconut milk.");
		add(Verb.GATHER, ItemID.VIAL_COCONUT_MILK)
			.how("Crack coconuts with a hammer and pour the milk into empty vials.");
		add(Verb.MIX, ItemID.ANTIDOTE__4).from(ItemID.UNFINISHED_ANTIDOTE__, 1).from(ItemID.MAGIC_ROOTS, 1).needs(Skill.HERBLORE, 79)
			.how("Add magic roots to the unfinished antidote++.");
		add(Verb.GATHER, ItemID.MAGIC_ROOTS).grows(Skill.FARMING, 75)
			.how("Chop down a grown magic tree from your patch, then dig up its roots.");
		add(Verb.MIX, ItemID.ANTIVENOM4).from(ItemID.ANTIDOTE__4, 1).from(ItemID.SNAKEBOSS_SCALE, 20).needs(Skill.HERBLORE, 87)
			.how("Add five Zulrah's scales a dose to antidote++.");
		add(Verb.GATHER, ItemID.SNAKEBOSS_SCALE)
			.how("Kill Zulrah.");

		potion(ItemID._3DOSE1ANTIDRAGON, ItemID.LANTADYMEVIAL, ItemID.DRAGON_SCALE_DUST, 69);
		add(Verb.CRUSH, ItemID.DRAGON_SCALE_DUST).from(ItemID.BLUE_DRAGON_SCALE, 1).tool(ItemID.PESTLE_AND_MORTAR)
			.how("Grind blue dragon scales with a pestle and mortar.");
		add(Verb.GATHER, ItemID.BLUE_DRAGON_SCALE)
			.how("Pick up in the Taverley Dungeon blue dragon lair.");
		decant(ItemID._4DOSE1ANTIDRAGON, ItemID._3DOSE1ANTIDRAGON);
		add(Verb.MIX, ItemID._4DOSE3ANTIDRAGON).from(ItemID._4DOSE1ANTIDRAGON, 1).from(ItemID.CRUSHED_DRAGON_BONES, 1).needs(Skill.HERBLORE, 92)
			.how("Add crushed superior dragon bones to a 4-dose antifire.");
		add(Verb.CRUSH, ItemID.CRUSHED_DRAGON_BONES).from(ItemID.DRAGON_BONES_SUPERIOR, 1).needs(Skill.PRAYER, 70).tool(ItemID.PESTLE_AND_MORTAR)
			.how("Grind superior dragon bones with a pestle and mortar.");
		add(Verb.GATHER, ItemID.DRAGON_BONES_SUPERIOR)
			.how("Vorkath and other high-level dragons drop them.");
	}

	private static void bolts()
	{
		products("DRAGON_BOLTS", ItemID.DRAGON_BOLTS_ENCHANTED_RUBY, ItemID.DRAGON_BOLTS_ENCHANTED_DIAMOND);

		add(Verb.ENCHANT, ItemID.DRAGON_BOLTS_ENCHANTED_RUBY).makes(10).from(ItemID.DRAGON_BOLTS_UNENCHANTED_RUBY, 10)
			.from(ItemID.COSMICRUNE, 1).from(ItemID.FIRERUNE, 5).from(ItemID.BLOODRUNE, 1).needs(Skill.MAGIC, 49)
			.how("Cast Enchant Crossbow Bolt (Ruby); each cast enchants ten.");
		add(Verb.GATHER, ItemID.DRAGON_BOLTS_UNENCHANTED_RUBY)
			.how("Fletch dragon bolts with ruby bolt tips.");
		add(Verb.ENCHANT, ItemID.DRAGON_BOLTS_ENCHANTED_DIAMOND).makes(10).from(ItemID.DRAGON_BOLTS_UNENCHANTED_DIAMOND, 10)
			.from(ItemID.COSMICRUNE, 1).from(ItemID.EARTHRUNE, 10).from(ItemID.LAWRUNE, 2).needs(Skill.MAGIC, 57)
			.how("Cast Enchant Crossbow Bolt (Diamond); each cast enchants ten.");
		add(Verb.GATHER, ItemID.DRAGON_BOLTS_UNENCHANTED_DIAMOND)
			.how("Fletch dragon bolts with diamond bolt tips.");
		for (int rune : new int[]{ItemID.COSMICRUNE, ItemID.FIRERUNE, ItemID.BLOODRUNE, ItemID.EARTHRUNE, ItemID.LAWRUNE})
		{
			add(Verb.GATHER, rune).how("Craft them at the altar, or buy from a rune shop.");
		}
	}

	/** The recipe for an item, or a plain stocking step for anything the Provisioner cannot make. */
	public static Recipe recipe(int itemId)
	{
		Recipe r = RECIPES.get(itemId);
		return r != null ? r : Recipe.stock(itemId);
	}

	public static boolean knows(int itemId)
	{
		return RECIPES.containsKey(itemId);
	}

	/** The items a pooled supply can be made as, best first; empty for supplies with no recipe. */
	public static List<Integer> products(String supply)
	{
		List<Integer> list = PRODUCTS.get(supply);
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
	}
}
