package com.codex.provisioner.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Costing;
import com.codex.provisioner.core.HerbRuns;
import com.codex.provisioner.core.Kit;
import com.codex.provisioner.core.Loads;
import com.codex.provisioner.core.Provisions;
import com.codex.provisioner.core.Quarters;
import com.codex.provisioner.core.Restock;
import com.codex.provisioner.core.SharedBank;
import com.codex.provisioner.core.Step;
import com.codex.provisioner.core.Training;
import com.codex.provisioner.core.Verb;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Every saved key the Provisioner reads, damaged every way a save can be: its own provisions,
 * Quartermaster's kitsV1 and quarters, and Bank Steward's bankV1. Empty, cut short, the wrong
 * shape, figures below zero or too big, a later version's, or not JSON at all: none may throw,
 * leave the panel unable to draw, or cost the player what still reads.
 */
public class DamagedSavesTest
{
	private static final Gson GSON = new Gson();
	private static final long NOW = 1_800_000_000_000L;

	private static String fixture(String name) throws IOException
	{
		try (InputStream in = Quarters.class.getResourceAsStream(name))
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[4096];
			for (int n; (n = in.read(buf)) > 0; )
			{
				out.write(buf, 0, n);
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	/** A save of the Provisioner's own, as the plugin writes it after some play. */
	private static String ownSave()
	{
		Provisions p = new Provisions();
		Map<Integer, Long> bank = new HashMap<>();
		bank.put(ItemID.RAW_SHARK, 46L);
		bank.put(ItemID.UNIDENTIFIED_RANARR, 18L);
		bank.put(ItemID.VIAL_WATER, 60L);
		bank.put(ItemID.SNAPE_GRASS, 40L);
		p.observeBank(bank, NOW - 60_000);
		Map<Skill, Integer> levels = new HashMap<>();
		Map<Skill, Long> xp = new HashMap<>();
		for (Skill s : Skill.values())
		{
			levels.put(s, 80);
			xp.put(s, 2_000_000L);
		}
		p.observeLevels(levels);
		p.observeXp(xp);
		p.choose("FOOD", ItemID.SHARK);
		p.setTrips(12);
		p.setKit("kit:1");
		p.observeStorage(Collections.singletonMap(ItemID._1DOSEPRAYERRESTORE, 12L));
		p.plan(Quarters.none().allKits().get(0), Collections.emptyMap(), new HashMap<>());
		return GSON.toJson(p);
	}

	/** The same save damaged every way we can think of. */
	static List<String> damaged(String save)
	{
		List<String> out = new ArrayList<>();
		out.add(null);
		out.add("");
		out.add("   ");
		out.add("null");
		out.add("{");
		out.add("}");
		out.add("[]");
		out.add("[1,2,3]");
		out.add("5");
		out.add("-1e400");
		out.add("\"a save\"");
		out.add("true");
		out.add("{\u0000\uFFFF}{][");
		out.add("\uFEFF" + save);
		out.add(save.replaceFirst("^\\{", "{\"version\":99,\"shelf\":{\"a\":[1,{\"b\":2}]},"));
		// Every number in the save in turn too big, below zero, a fraction or a word.
		for (String n : new String[]{"99999999999999999999", "-5", "-9223372036854775808", "2147483648", "1e308", "0.5", "\"many\"", "null", "{}", "[]"})
		{
			out.add(save.replaceAll("(?<=[:\\[,])-?\\d+(\\.\\d+)?(?=[,\\]}])", n));
			out.add(save.replaceFirst("(?<=[:\\[,])-?\\d+(\\.\\d+)?(?=[,\\]}])", n));
		}
		// Every string in the save in turn a number, empty or nonsense.
		out.add(save.replaceAll("(?<=:)\"[^\"]*\"", "7"));
		out.add(save.replaceAll("(?<=:)\"[^\"]*\"", "\"\""));
		out.add(save.replaceAll("(?<=:)\"[^\"]*\"", "\"NOT_A_THING\""));
		// Every list and object the wrong shape.
		out.add(save.replaceAll("\\[", "{").replaceAll("]", "}"));
		out.add(save.replaceAll("(?<=:)\\{", "[").replaceAll("}(?=[,}])", "]"));
		for (int cut = 1; cut < 24; cut++)
		{
			out.add(save.substring(0, save.length() * cut / 24));
		}
		return out;
	}

	@Test
	public void theProvisionersOwnSaveSurvivesAnyDamage() throws IOException
	{
		String own = ownSave();
		for (String bad : damaged(own))
		{
			Provisions p = Provisions.read(GSON, bad, NOW).provisions;
			draw(p, Quarters.read(GSON, fixture("quartermaster-measured-kitsV1.json"), fixture("quartermaster-measured-quarters.json")),
				SharedBank.read(GSON, fixture("bank-steward-bankV1.json")), bad);
			// What was read saves and reads back whole.
			assertFalse(String.valueOf(bad), Provisions.read(GSON, GSON.toJson(p), NOW).damaged);
		}
	}

	@Test
	public void quartermastersKitsSurviveAnyDamage() throws IOException
	{
		String kits = fixture("quartermaster-measured-kitsV1.json");
		String quarters = fixture("quartermaster-measured-quarters.json");
		Provisions p = Provisions.read(GSON, ownSave(), NOW).provisions;
		for (String bad : damaged(kits))
		{
			draw(p, Quarters.read(GSON, bad, quarters), null, bad);
			draw(p, Quarters.read(GSON, bad, null), null, bad);
		}
		for (String bad : damaged(quarters))
		{
			draw(p, Quarters.read(GSON, kits, bad), null, bad);
			draw(p, Quarters.read(GSON, null, bad), null, bad);
		}
	}

	@Test
	public void bankStewardsBankSurvivesAnyDamage() throws IOException
	{
		for (String bad : damaged(fixture("bank-steward-bankV1.json")))
		{
			Provisions p = Provisions.read(GSON, "{\"bankSeen\":1}", NOW).provisions;
			SharedBank shared = SharedBank.read(GSON, bad);
			p.adoptShared(shared);
			for (long q : p.getBank().values())
			{
				assertTrue(bad, q > 0 && q <= Integer.MAX_VALUE);
			}
			draw(p, Quarters.none(), shared, bad);
		}
	}

	@Test
	public void aKitAskingTooMuchIsNeverReadAsBelowZero()
	{
		// A kit published with an absurd amount a trip, past what an int holds.
		Quarters q = Quarters.read(GSON, "{\"version\":1,\"kits\":[{\"id\":\"kit:9\",\"name\":\"Greedy\",\"preset\":false,"
			+ "\"needs\":[{\"supply\":\"FOOD\",\"perTrip\":3000000000},{\"supply\":\"PRAYER\",\"perTrip\":1e300}]}]}", null);
		Kit greedy = q.allKits().get(0);
		assertEquals("Greedy", greedy.getName());
		// An item supply with no real item behind it is never asked of the game's item list.
		Quarters odd = Quarters.read(GSON, "{\"version\":1,\"kits\":[{\"id\":\"kit:8\",\"name\":\"Odd\",\"needs\":["
			+ "{\"supply\":\"item:-5\",\"perTrip\":2},{\"supply\":\"item:0\",\"perTrip\":2},{\"supply\":\"item:385\",\"perTrip\":2}]}]}", null);
		assertEquals(1, odd.allKits().get(0).getNeeds().size());
		for (com.codex.provisioner.core.Need n : greedy.getNeeds())
		{
			assertTrue(n.getSupply() + " " + n.getPerTrip(), n.getPerTrip() >= 0);
		}
	}

	@Test
	public void oneDamagedFigureNeverCostsTheRestOfTheSave()
	{
		String own = ownSave();
		// The trips are no longer a number; the bank, choices and levels must still read.
		String bad = own.replaceFirst("\"trips\":\\d+", "\"trips\":\"twelve\"");
		Provisions.Loaded read = Provisions.read(GSON, bad, NOW);
		assertTrue(read.damaged);
		assertEquals(4, read.provisions.getBank().size());
		assertEquals(Integer.valueOf(ItemID.SHARK), read.provisions.getChoices().get("FOOD"));
		assertEquals("kit:1", read.provisions.getKit());
		assertEquals(Provisions.DEFAULT_TRIPS, read.provisions.getTrips());
		// One bad entry in the bank costs that entry only.
		Provisions one = Provisions.read(GSON, own.replaceFirst("(\"bank\":\\{\"\\d+\":)\\d+", "$1\"lots\""), NOW).provisions;
		assertEquals(3, one.getBank().size());
		// A bank seen in the future is taken as seen now, so a newer shared bank is still taken.
		Provisions future = Provisions.read(GSON, "{\"bankSeen\":" + (NOW * 2) + "}", NOW).provisions;
		assertTrue(future.getBankSeen() <= NOW);
		// Nothing readable at all starts afresh, and says the save was damaged.
		assertTrue(Provisions.read(GSON, "[1,2]", NOW).damaged);
		assertFalse(Provisions.read(GSON, null, NOW).damaged);
		assertFalse(Provisions.read(GSON, own, NOW).damaged);
	}

	@Test
	public void extremeAccountsDrawAndAreQuickOnTheClientThread()
	{
		// A maxed account with a full bank of 1,500 kinds of item, thirty kits of every supply and a thousand trips.
		Provisions maxed = new Provisions();
		Map<Integer, Long> bank = new HashMap<>();
		for (int id = 1; id <= 1500; id++)
		{
			bank.put(id * 7, (long) (id * 104_729 % Integer.MAX_VALUE));
		}
		maxed.observeBank(bank, NOW - 1_000);
		Map<Skill, Integer> levels = new HashMap<>();
		Map<Skill, Long> xp = new HashMap<>();
		for (Skill s : Skill.values())
		{
			levels.put(s, 99);
			xp.put(s, 200_000_000L);
		}
		maxed.observeLevels(levels);
		maxed.observeXp(xp);
		maxed.setTrips(1000);
		StringBuilder kits = new StringBuilder("{\"version\":1,\"kits\":[");
		for (int k = 0; k < 30; k++)
		{
			kits.append(k == 0 ? "" : ",").append("{\"id\":\"kit:").append(k).append("\",\"name\":\"Kit ").append(k).append("\",\"needs\":[");
			int n = 0;
			for (com.codex.provisioner.core.Supply supply : com.codex.provisioner.core.Supplies.groups())
			{
				kits.append(n++ == 0 ? "" : ",").append("{\"supply\":\"").append(supply.getKey()).append("\",\"perTrip\":").append(5 + k).append("}");
			}
			kits.append("]}");
		}
		Quarters many = Quarters.read(GSON, kits.append("]}").toString(), null);
		assertEquals(30 + com.codex.provisioner.core.Presets.all().size(), many.allKits().size());
		draw(maxed, many, null, "maxed");
		for (int i = 0; i < 3; i++)
		{
			work(maxed, many);
		}
		long best = Long.MAX_VALUE;
		for (int i = 0; i < 5; i++)
		{
			long t0 = System.nanoTime();
			work(maxed, many);
			best = Math.min(best, System.nanoTime() - t0);
		}
		System.out.println("Maxed account, 1,500 items, 30 kits: a refresh takes " + best / 1000 + "us");
		assertTrue(best / 1000 + "us", best < 8_000_000L);

		// A brand-new account: level 1 everywhere, no bank seen, no Quartermaster.
		Provisions fresh = new Provisions();
		Map<Skill, Integer> ones = new HashMap<>();
		for (Skill s : Skill.values())
		{
			ones.put(s, 1);
		}
		fresh.observeLevels(ones);
		draw(fresh, Quarters.none(), null, "fresh");
	}

	/** What the plugin works out and shows on every refresh, from these saves, on every tab. */
	static void draw(Provisions p, Quarters quarters, SharedBank shared, String why)
	{
		if (shared != null)
		{
			p.adoptShared(shared);
		}
		View view = work(p, quarters);
		ProvisionerPanel panel = new ProvisionerPanel((id, cb) -> null, new Actions()
		{
			public void kit(String k)
			{
			}

			public void trips(int t)
			{
			}

			public void choose(String s, int product)
			{
			}

			public void buy(String s, boolean b)
			{
			}

			public void restart()
			{
			}
		});
		panel.update(view);
		for (int tab = 0; tab < 4; tab++)
		{
			panel.showTab(tab);
			paint(panel);
		}
		assertNotNull(why, view);
	}

	/** The client thread's part of a refresh, as the plugin's: every kit planned, the view made. */
	static View work(Provisions p, Quarters quarters)
	{
		List<Kit> kits = quarters.allKits();
		Kit kit = kits.get(0);
		for (Kit k : kits)
		{
			kit = k.getId().equals(p.getKit()) ? k : kit;
		}
		Map<Integer, Long> carried = new HashMap<>();
		Map<Integer, Long> fallback = quarters.getStored();
		Chain chain = p.plan(kit, quarters.measured(kit.getId()), carried, fallback);
		Map<Integer, Long> held = p.holdings(carried, fallback);
		List<View.KitSummary> sums = new ArrayList<>();
		for (Kit k : kits)
		{
			Chain c = Chain.build(k, quarters.measured(k.getId()), p.getTrips(), p.getChoices(), p.getBought(), held, p.levels(), Collections.emptyMap());
			sums.add(new View.KitSummary(k, c.getSteps().size() - c.doneCount(), 0, quarters.measuredTrips(k.getId())).ready(c.readyTrips()));
		}
		Map<Integer, HerbRuns.Plan> herbs = new HashMap<>();
		Map<Integer, Long> est = new HashMap<>();
		Map<Integer, Boolean> own = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			if (s.getState() != Step.State.DONE && s.getRecipe().getVerb() == Verb.GATHER)
			{
				HerbRuns.Plan hp = HerbRuns.plan(s.getItem(), s.getNet(), 7, 8, held);
				if (hp != null)
				{
					herbs.put(s.getItem(), hp);
				}
			}
			if (s.getState() != Step.State.DONE)
			{
				est.put(s.getItem(), p.getPace().estimate(s));
				own.put(s.getItem(), p.getPace().measured(s.getItem()) > 0);
			}
		}
		Map<String, Costing.Quote> quotes = new HashMap<>();
		Map<Integer, Long> pool = new HashMap<>(held);
		for (Chain.Line l : chain.getLines())
		{
			if (!l.getSupply().isItem() && l.getToMake() > 0)
			{
				quotes.put(l.getSupply().getKey(), Costing.quote(l.getProduct(), l.getToMake(), id -> 1_000L, pool));
			}
		}
		new Restock().onBank(chain, kit.getId(), 3, id -> "Item " + id, NOW);
		Step current = chain.current();
		View view = new View(View.State.READY, chain, sums, new HashMap<>(), held, p.levels(), quarters.hasSavedKits(),
			quarters.measuredTrips(kit.getId()), p.getBankSeen(), false, NOW)
			.trade(p.isIron(), p.getBought(), quotes).pace(est, own)
			.work(current == null ? null : Loads.of(current, id -> false), Training.of(chain, p.xp(), p.levels()))
			.herbs(herbs).shared(p.isBankShared());
		return view;
	}

	private static void paint(Component c)
	{
		c.setSize(225, 2400);
		layout(c);
		BufferedImage img = new BufferedImage(225, 2400, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		c.printAll(g);
		g.dispose();
	}

	private static void layout(Component c)
	{
		c.doLayout();
		if (c instanceof Container)
		{
			for (Component k : ((Container) c).getComponents())
			{
				layout(k);
			}
		}
	}
}
