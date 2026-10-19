package com.codex.provisioner.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * What the Provisioner reads from other Codex plugins, against fixtures their real writers
 * produced: Quartermaster's published kits (kitsV1) with its own entry as fallback, and Bank
 * Steward's shared bank (bankV1).
 */
public class SharedSavesTest
{
	private static final Gson GSON = new Gson();

	private static String fixture(String name) throws IOException
	{
		try (InputStream in = SharedSavesTest.class.getResourceAsStream(name))
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

	private static Kit kit(Quarters q, String id)
	{
		for (Kit k : q.allKits())
		{
			if (k.getId().equals(id))
			{
				return k;
			}
		}
		return null;
	}

	@Test
	public void publishedKitsAreReadWithTheirMeasuredUse() throws IOException
	{
		Quarters q = Quarters.read(GSON, fixture("quartermaster-measured-kitsV1.json"), fixture("quartermaster-measured-quarters.json"));
		assertTrue(q.hasSavedKits());
		assertEquals("kit:1", q.getActive());
		Kit mine = kit(q, "kit:1");
		assertEquals("My Vorkath", mine.getName());
		assertEquals(200, mine.need("FOOD").getPerTrip());
		assertEquals(150.0, q.measured("kit:1").get("FOOD"), 1e-9);
		assertEquals(9.0, q.measured("kit:1").get("PRAYER"), 1e-9);
		// The count of trips and the potion storage come from Quartermaster's own entry.
		assertEquals(3, q.measuredTrips("kit:1"));
		assertEquals(Long.valueOf(24), q.getStored().get(ItemID._1DOSEPRAYERRESTORE));
		// Presets are the Provisioner's own, listed once after the player's kits.
		assertEquals(1 + Presets.all().size(), q.allKits().size());
	}

	@Test
	public void publishedKitsAloneStillPlanFromMeasuredUse() throws IOException
	{
		Quarters q = Quarters.read(GSON, fixture("quartermaster-measured-kitsV1.json"), null);
		assertEquals("kit:1", q.getActive());
		assertEquals(150.0, q.measured("kit:1").get("FOOD"), 1e-9);
		// Published only once trusted, so at least the trusted count.
		assertEquals(Quarters.TRUSTED, q.measuredTrips("kit:1"));
		assertNull(q.getStored());
	}

	@Test
	public void quartermasterTooOldToPublishIsReadFromItsOwnEntry() throws IOException
	{
		Quarters q = Quarters.read(GSON, null, fixture("quartermaster-measured-quarters.json"));
		assertEquals("kit:1", q.getActive());
		assertEquals(150.0, q.measured("kit:1").get("FOOD"), 1e-9);
		assertEquals(3, q.measuredTrips("kit:1"));
		Quarters saved = Quarters.read(GSON, null, fixture("quartermaster-saved-quarters.json"));
		assertEquals("kit:1", saved.getActive());
		assertTrue(saved.measured("kit:1").isEmpty());
	}

	@Test
	public void aPresetIsChosenOnlyWhenQuartermasterSaysThePlayerPickedIt() throws IOException
	{
		// An older Quartermaster published the preset it showed on a fresh install as active.
		Quarters fresh = Quarters.read(GSON, fixture("quartermaster-fresh-kitsV1.json"), fixture("quartermaster-fresh-quarters.json"));
		assertNull(fresh.getActive());
		assertFalse(fresh.hasSavedKits());
		Quarters unchosen = Quarters.read(GSON, fixture("quartermaster-unchosen-kitsV1.json"), fixture("quartermaster-fresh-quarters.json"));
		assertNull(unchosen.getActive());
		Quarters saved = Quarters.read(GSON, fixture("quartermaster-saved-kitsV1.json"), fixture("quartermaster-saved-quarters.json"));
		assertEquals("kit:1", saved.getActive());
		// A carried item with no use per trip is kept, and plans nothing.
		assertEquals(0, kit(saved, "kit:1").need("item:952").getPerTrip());
	}

	@Test
	public void nothingOrRubbishFromQuartermasterLeavesThePresets()
	{
		assertEquals(Presets.all().size(), Quarters.read(GSON, null, null).allKits().size());
		assertEquals(Presets.all().size(), Quarters.read(GSON, "not json {", "[1,2]").allKits().size());
		Quarters odd = Quarters.read(GSON, "{\"kits\":[{\"id\":\"k\",\"name\":\"K\",\"needs\":[{\"supply\":\"FOOD\",\"perTrip\":\"lots\"},5]},7]}", null);
		assertEquals(0, kit(odd, "k").getNeeds().size());
	}

	@Test
	public void sharedBankIsReadFromBankStewardsSnapshot() throws IOException
	{
		SharedBank bank = SharedBank.read(GSON, fixture("bank-steward-bankV1.json"));
		assertEquals(1760000000000L, bank.getSavedAt());
		assertEquals(Long.valueOf(240), bank.getItems().get(ItemID.SHARK));
		assertEquals(Long.valueOf(1234567), bank.getItems().get(ItemID.COINS));
		assertNull(SharedBank.read(GSON, "{\"items\":{}}"));
		assertNull(SharedBank.read(GSON, "nonsense"));
		assertNull(SharedBank.read(GSON, null));
	}

	@Test
	public void sharedBankIsTakenOnlyWhenNewer() throws IOException
	{
		SharedBank shared = SharedBank.read(GSON, fixture("bank-steward-bankV1.json"));
		Provisions p = new Provisions();
		assertTrue("never seen here: taken", p.adoptShared(shared));
		assertTrue(p.isBankShared());
		assertEquals(shared.getSavedAt(), p.getBankSeen());
		assertEquals(Long.valueOf(240), p.getBank().get(ItemID.SHARK));

		Map<Integer, Long> own = new HashMap<>();
		own.put(ItemID.SHARK, 5L);
		p.observeBank(own, shared.getSavedAt() + 10_000);
		assertFalse(p.isBankShared());
		assertFalse("seen here later: kept", p.adoptShared(shared));
		p.observeBank(own, shared.getSavedAt() - 30_000);
		assertFalse("seen at much the same time: kept", p.adoptShared(shared));
		p.observeBank(own, shared.getSavedAt() - 3_600_000);
		assertTrue("seen here an hour before: taken", p.adoptShared(shared));
		assertFalse(p.adoptShared(null));
	}

	@Test
	public void sharedBankSurvivesASaveAndLoad() throws IOException
	{
		Provisions p = new Provisions();
		p.adoptShared(SharedBank.read(GSON, fixture("bank-steward-bankV1.json")));
		Provisions back = GSON.fromJson(GSON.toJson(p), Provisions.class).repair();
		assertTrue(back.isBankShared());
		assertTrue(back.hasBank());
		List<Kit> kits = Quarters.none().allKits();
		assertFalse(kits.isEmpty());
	}
}
