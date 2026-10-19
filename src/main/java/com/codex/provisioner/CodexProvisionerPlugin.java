package com.codex.provisioner;

import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Costing;
import com.codex.provisioner.core.HerbRuns;
import com.codex.provisioner.core.Kit;
import com.codex.provisioner.core.Loads;
import com.codex.provisioner.core.Prices;
import com.codex.provisioner.core.Provisions;
import com.codex.provisioner.core.Quarters;
import com.codex.provisioner.core.Settle;
import com.codex.provisioner.core.SharedBank;
import com.codex.provisioner.core.Step;
import com.codex.provisioner.core.Training;
import com.codex.provisioner.core.Verb;
import com.codex.provisioner.overlay.BankMarks;
import com.codex.provisioner.overlay.StepPlaque;
import com.codex.provisioner.ui.Actions;
import com.codex.provisioner.ui.Arrivals;
import com.codex.provisioner.ui.Format;
import com.codex.provisioner.ui.ItemIcons;
import com.codex.provisioner.ui.ProvisionerPanel;
import com.codex.provisioner.ui.Sigil;
import com.codex.provisioner.ui.Theme;
import com.codex.provisioner.ui.View;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ScriptID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Codex Provisioner",
	description = "Pick a kit and get walked through gathering and making its supplies one step at a time, from raw fish and grimy herbs to finished potions",
	tags = {"ironman", "supplies", "herblore", "cooking", "fishing", "potions", "food", "guide", "codex"}
)
public class CodexProvisionerPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CodexProvisionerPlugin.class);
	private static final String PROVISIONS_KEY = "provisions";
	/** Ticks between saves while something has changed. */
	private static final int SAVE_TICKS = 50;
	private static final int[] POUCH_RUNES = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
	};
	private static final int[] POUCH_AMOUNTS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	};
	private static final int[] POTION_STORE_VARS = {
		VarPlayerID.POTIONSTORE_BASE_VAR_1, VarPlayerID.POTIONSTORE_BASE_VAR_2, VarPlayerID.POTIONSTORE_BASE_VAR_3,
		VarPlayerID.POTIONSTORE_BASE_VAR_4, VarPlayerID.POTIONSTORE_BASE_VAR_5, VarPlayerID.POTIONSTORE_BASE_VAR_6
	};
	private static final int[] POUCHES = {
		ItemID.BH_RUNE_POUCH, ItemID.BH_RUNE_POUCH_TROUVER, ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Gson gson;

	@Inject
	private CodexProvisionerConfig config;

	/** Windows in which items change hands by trade, so a step shrinking then is not work. */
	private static final Set<Integer> TRADE_WINDOWS = new HashSet<>(java.util.Arrays.asList(
		InterfaceID.GE_OFFERS, InterfaceID.GE_COLLECT, InterfaceID.SHOPMAIN, InterfaceID.TRADEMAIN, InterfaceID.TRADECONFIRM));

	private final StepPlaque plaque = new StepPlaque();
	private BankMarks bankMarks;
	/** Whether each item stacks in one slot, asked of the item manager once. */
	private final Map<Integer, Boolean> stackable = new HashMap<>();
	/** The trading windows open now. */
	private final Set<Integer> tradeOpen = new HashSet<>();
	private final Map<Integer, String> names = new HashMap<>();

	private ProvisionerPanel panel;
	private NavigationButton navButton;

	private String profile;
	private Provisions provisions;
	private Quarters quarters = Quarters.none();
	/** What was carried when last seen, so the panel still counts it while logged out. */
	private Map<Integer, Long> carried = Collections.emptyMap();
	/** What is carried and usable as it is: noted items left out, since they cannot be worked or used as tools. */
	private Map<Integer, Long> inHand = Collections.emptyMap();
	/** Each step's state at the last count, to notice a step finishing; null after a change of plan. */
	private Map<Integer, Step.State> lastStates;
	private String lastPlan;
	private boolean bankOpen;
	/** The potion storage needs reading on the next tick the bank is open. */
	private boolean readStorage;
	/** The varps the potion storage keeps its counts in, taken from the storage widget as RuneLite's bank tags do. */
	private Set<Integer> storageVars;
	/** Ticks after the bank closes in which a storage change, such as a last withdrawal, is still read. */
	private int storageGrace;
	private final Prices prices = id -> itemManager.getItemPrice(id);
	private boolean dirty;
	private boolean unsaved;
	/** The next count of the steps is not work, such as a bank taken from Codex Bank Steward. */
	private boolean quietPace;
	private int ticks;
	/** The ticks after a login or hop before the levels and the ironman varbit can be trusted. */
	private final Settle settle = new Settle();

	@Provides
	CodexProvisionerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexProvisionerConfig.class);
	}

	@Override
	protected void startUp()
	{
		Arrivals arrivals = new Arrivals();
		ItemIcons icons = (itemId, onLoaded) ->
		{
			AsyncBufferedImage image = itemManager.getImage(itemId);
			// A loaded picture would run the callback again at every ask, and the panel asks as it redraws.
			arrivals.ask(image, onLoaded);
			return image;
		};
		panel = new ProvisionerPanel(icons, new PanelActions());
		bankMarks = new BankMarks(itemManager);
		navButton = NavigationButton.builder()
			.tooltip("Codex Provisioner")
			.icon(Sigil.icon(16))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(plaque);
		overlayManager.add(bankMarks);
		clientThread.invokeLater(() ->
		{
			loadProfile();
			if (client.getGameState() == GameState.LOGGED_IN && provisions != null)
			{
				// Turned on while logged in: everything the game sends at login is already here.
				carried = readCarried();
				inHand = readInHand();
				readLevels();
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				if (bank != null)
				{
					provisions.observeBank(contents(bank), System.currentTimeMillis());
					unsaved = true;
				}
			}
			publish();
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(plaque);
		overlayManager.remove(bankMarks);
		bankMarks.show(Collections.emptyMap());
		clientToolbar.removeNavigation(navButton);
		clientThread.invokeLater(() ->
		{
			save();
			provisions = null;
			profile = null;
			names.clear();
			bankOpen = false;
			lastStates = null;
			tradeOpen.clear();
		});
		panel = null;
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		// ConfigManager writes its changes to disk in this same event, before a save queued on the
		// client thread runs, so a save made here has to be sent on by hand or it is never written.
		CompletableFuture<Void> done = new CompletableFuture<>();
		clientThread.invokeLater(() ->
		{
			try
			{
				if (save())
				{
					configManager.sendConfig();
				}
			}
			finally
			{
				done.complete(null);
			}
		});
		event.waitFor(done);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			bankOpen = false;
			tradeOpen.clear();
			readStorage = false;
			storageGrace = 0;
			save();
			publish();
		}
		else if (event.getGameState() == GameState.HOPPING)
		{
			// A hop shuts every window; the next world opens with none, whether or not each close was heard.
			bankOpen = false;
			tradeOpen.clear();
			readStorage = false;
			storageGrace = 0;
			settle.restart();
		}
		else if (event.getGameState() == GameState.LOGGING_IN)
		{
			// Levels and varbits are sent over the first ticks after the login; they are read once they are in.
			settle.restart();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		save();
		loadProfile();
		bankOpen = false;
		tradeOpen.clear();
		readStorage = false;
		storageGrace = 0;
		carried = client.getGameState() == GameState.LOGGED_IN ? readCarried() : Collections.emptyMap();
		inHand = client.getGameState() == GameState.LOGGED_IN ? readInHand() : Collections.emptyMap();
		publish();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CodexProvisionerConfig.GROUP.equals(event.getGroup()))
		{
			dirty = true;
		}
		else if (Quarters.GROUP.equals(event.getGroup()) && event.getKey() != null
			&& (event.getKey().endsWith(Quarters.KITS_KEY) || event.getKey().endsWith(Quarters.QUARTERS_KEY)))
		{
			// Quartermaster saved a kit or measured a trip.
			clientThread.invokeLater(() ->
			{
				loadQuarters();
				dirty = true;
			});
		}
		else if (SharedBank.GROUP.equals(event.getGroup()) && event.getKey() != null && event.getKey().endsWith(SharedBank.KEY))
		{
			// Codex Bank Steward saw the bank; worth taking only if this plugin did not see it too.
			clientThread.invokeLater(this::adoptSharedBank);
		}
	}

	/** Takes Codex Bank Steward's bank when it is newer than the one last seen here. */
	private void adoptSharedBank()
	{
		if (provisions == null || bankOpen || profile == null)
		{
			return;
		}
		SharedBank shared = SharedBank.read(gson, configManager.getRSProfileConfiguration(SharedBank.GROUP, SharedBank.KEY));
		if (provisions.adoptShared(shared))
		{
			// Not work done: no step is announced as finished and no pace is learned from the jump.
			lastStates = null;
			quietPace = true;
			unsaved = true;
			dirty = true;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (provisions == null)
		{
			return;
		}
		boolean level = provisions.observeLevels(Collections.singletonMap(event.getSkill(), event.getLevel()));
		boolean xp = provisions.observeXp(Collections.singletonMap(event.getSkill(), (long) event.getXp()));
		if (level || xp)
		{
			unsaved = true;
			dirty = true;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (client.getGameState() != GameState.LOGGED_IN || provisions == null)
		{
			return;
		}
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			provisions.observeBank(contents(event.getItemContainer()), System.currentTimeMillis());
			unsaved = true;
			dirty = true;
		}
		else if (id == InventoryID.INV || id == InventoryID.WORN)
		{
			carried = readCarried();
			inHand = readInHand();
			dirty = true;
		}
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
	{
		GrandExchangeOffer offer = event.getOffer();
		if (provisions == null || offer == null || offer.getQuantitySold() <= 0)
		{
			return;
		}
		GrandExchangeOfferState state = offer.getState();
		if (state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT)
		{
			// Bought items reach the bank or pack when collected, perhaps much later; that drop is a purchase.
			provisions.getPace().bought(itemManager.canonicalize(offer.getItemId()), System.currentTimeMillis());
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (TRADE_WINDOWS.contains(event.getGroupId()))
		{
			tradeOpen.add(event.getGroupId());
		}
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankOpen = true;
			readStorage = true;
			dirty = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.isUnload())
		{
			tradeOpen.remove(event.getGroupId());
		}
		if (event.getGroupId() == InterfaceID.BANKMAIN && event.isUnload())
		{
			bankOpen = false;
			// A withdrawal made just before closing reaches the storage varps a tick later.
			storageGrace = 2;
			dirty = true;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// The per-potion varps change when doses go in or out; the base vars when doses are rearranged.
		int id = event.getVarpId();
		Set<Integer> vars = storageVars;
		boolean storage = vars != null && vars.contains(id);
		for (int varp : POTION_STORE_VARS)
		{
			storage |= id == varp;
		}
		if (storage && (bankOpen || storageGrace > 0))
		{
			readStorage = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (provisions == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		ticks++;
		if (settle.tick())
		{
			readLevels();
			dirty = true;
		}
		if (!settle.isSettled())
		{
			return;
		}
		boolean iron = client.getVarbitValue(VarbitID.IRONMAN) > 0;
		if (provisions.isIron() != iron)
		{
			provisions.setIron(iron);
			unsaved = true;
			dirty = true;
		}
		if (readStorage && (bankOpen || storageGrace > 0))
		{
			readStorage = false;
			readPotionStorage();
		}
		if (storageGrace > 0 && !bankOpen)
		{
			storageGrace--;
		}
		if (unsaved && ticks % SAVE_TICKS == 0)
		{
			save();
		}
		if (dirty)
		{
			publish();
		}
	}

	/**
	 * Reads the doses kept in the bank's potion storage, which the bank itself does not list, the
	 * same way Codex Quartermaster and RuneLite's bank tags do: the game's own script says how many
	 * doses each stored potion has, filed under the potion's 1-dose item. Only called on the client
	 * thread with the bank open, or just after it closes.
	 */
	private void readPotionStorage()
	{
		EnumComposition potions = client.getEnum(EnumID.POTIONSTORE_POTIONS);
		if (potions == null)
		{
			return;
		}
		if (storageVars == null)
		{
			Widget w = client.getWidget(InterfaceID.Bankmain.POTIONSTORE_ITEMS);
			int[] trigger = w == null ? null : w.getVarTransmitTrigger();
			if (trigger != null)
			{
				Set<Integer> vars = new HashSet<>();
				for (int v : trigger)
				{
					vars.add(v);
				}
				storageVars = vars;
			}
		}
		Map<Integer, Long> doses = new HashMap<>();
		for (int potionEnum : potions.getIntVals())
		{
			EnumComposition potion = client.getEnum(potionEnum);
			if (potion == null)
			{
				continue;
			}
			client.runScript(ScriptID.POTIONSTORE_DOSES, potionEnum);
			int stored = client.getIntStack()[0];
			int oneDose = potion.getIntValue(1);
			if (stored > 0 && oneDose > 0)
			{
				doses.merge(oneDose, (long) stored, Long::sum);
			}
		}
		if (provisions.observeStorage(doses))
		{
			unsaved = true;
			dirty = true;
		}
	}

	private Map<Integer, Long> contents(ItemContainer container)
	{
		Map<Integer, Long> items = new HashMap<>();
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() < 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			items.merge(itemManager.canonicalize(item.getId()), (long) item.getQuantity(), Long::sum);
		}
		return items;
	}

	/** The inventory, worn gear (bolts in the quiver slot count) and the runes in a carried rune pouch. */
	private Map<Integer, Long> readCarried()
	{
		Map<Integer, Long> items = contents(client.getItemContainer(InventoryID.INV));
		contents(client.getItemContainer(InventoryID.WORN)).forEach((id, q) -> items.merge(id, q, Long::sum));
		boolean pouch = false;
		for (int id : POUCHES)
		{
			pouch |= items.containsKey(id);
		}
		if (pouch)
		{
			EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
			for (int i = 0; i < POUCH_RUNES.length; i++)
			{
				int type = client.getVarbitValue(POUCH_RUNES[i]);
				int amount = client.getVarbitValue(POUCH_AMOUNTS[i]);
				int itemId = type > 0 ? runes.getIntValue(type) : -1;
				if (itemId > 0 && amount > 0)
				{
					items.merge(itemId, (long) amount, Long::sum);
				}
			}
		}
		return items;
	}

	/**
	 * What is carried as it can be used: herbs withdrawn as notes still count as held, but not as
	 * taken out for the step, so their bank marks stay until real ones come out.
	 */
	private Map<Integer, Long> readInHand()
	{
		Map<Integer, Long> items = new HashMap<>();
		for (int container : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer c = client.getItemContainer(container);
			for (Item item : c == null ? new Item[0] : c.getItems())
			{
				if (item.getId() >= 0 && item.getQuantity() > 0 && itemManager.getItemComposition(item.getId()).getNote() != -1)
				{
					items.merge(itemManager.canonicalize(item.getId()), (long) -item.getQuantity(), Long::sum);
				}
			}
		}
		// Everything carried, the rune pouch's runes too, less the notes.
		Map<Integer, Long> usable = new HashMap<>(carried);
		items.forEach((id, less) -> usable.merge(id, less, Long::sum));
		usable.values().removeIf(q -> q <= 0);
		return usable;
	}

	private void readLevels()
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		Map<Skill, Long> xp = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			int level = client.getRealSkillLevel(skill);
			if (level > 0)
			{
				levels.put(skill, level);
				xp.put(skill, (long) client.getSkillExperience(skill));
			}
		}
		if (!levels.isEmpty() && provisions.observeLevels(levels))
		{
			unsaved = true;
		}
		if (!xp.isEmpty() && provisions.observeXp(xp))
		{
			unsaved = true;
		}
	}

	private void loadProfile()
	{
		profile = configManager.getRSProfileKey();
		provisions = null;
		lastStates = null;
		if (profile == null)
		{
			quarters = Quarters.none();
			return;
		}
		try
		{
			String saved = configManager.getRSProfileConfiguration(CodexProvisionerConfig.GROUP, PROVISIONS_KEY);
			if (saved != null && !saved.isEmpty())
			{
				Provisions read = gson.fromJson(saved, Provisions.class);
				provisions = read == null ? null : read.repair();
			}
		}
		catch (JsonParseException e)
		{
			log.warn("Could not read the saved provisions, starting afresh", e);
		}
		if (provisions == null)
		{
			provisions = new Provisions();
		}
		loadQuarters();
		if (provisions.getKit() == null)
		{
			// Start with the kit last chosen in Quartermaster, when there is one.
			provisions.setKit(quarters.getActive() != null ? quarters.getActive() : quarters.allKits().get(0).getId());
		}
		unsaved = false;
		adoptSharedBank();
		dirty = true;
	}

	private void loadQuarters()
	{
		quarters = Quarters.none();
		if (profile == null)
		{
			return;
		}
		// Read only: the published kits first, Quartermaster's own entry for what they leave out.
		quarters = Quarters.read(gson,
			configManager.getRSProfileConfiguration(Quarters.GROUP, Quarters.KITS_KEY),
			configManager.getRSProfileConfiguration(Quarters.GROUP, Quarters.QUARTERS_KEY));
	}

	/** Writes to the profile if anything changed, and says whether it did. */
	private boolean save()
	{
		if (profile == null || provisions == null || !unsaved)
		{
			return false;
		}
		unsaved = false;
		configManager.setConfiguration(CodexProvisionerConfig.GROUP, profile, PROVISIONS_KEY, gson.toJson(provisions));
		return true;
	}

	private Kit activeKit(List<Kit> kits)
	{
		for (Kit k : kits)
		{
			if (k.getId().equals(provisions.getKit()))
			{
				return k;
			}
		}
		return kits.get(0);
	}

	private Map<String, Double> perTrip(Kit kit)
	{
		return config.measured() ? quarters.measured(kit.getId()) : Collections.emptyMap();
	}

	/** Works out the chain and hands the panel and the plaque what they show. */
	private void publish()
	{
		dirty = false;
		ProvisionerPanel target = panel;
		long now = System.currentTimeMillis();
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		if (provisions == null)
		{
			plaque.show(null, null, null, 0, null);
			bankMarks.show(Collections.emptyMap());
			if (target != null)
			{
				SwingUtilities.invokeLater(() -> target.update(View.loggedOut()));
			}
			return;
		}
		List<Kit> kits = quarters.allKits();
		Kit kit = activeKit(kits);
		Map<Integer, Long> storageFallback = quarters.getStored();
		Chain chain = provisions.plan(kit, perTrip(kit), carried, storageFallback);
		unsaved |= provisions.baselinesChanged();
		Map<Integer, Long> held = provisions.holdings(carried, storageFallback);

		List<View.KitSummary> summaries = new ArrayList<>();
		for (Kit k : kits)
		{
			Chain c = k == kit ? chain
				: Chain.build(k, perTrip(k), provisions.getTrips(), provisions.getChoices(), provisions.getBought(), held,
					provisions.levels(), Collections.emptyMap());
			int shortSupplies = 0;
			for (Chain.Line l : c.getLines())
			{
				if (l.getShort() > 0)
				{
					shortSupplies++;
				}
			}
			int open = c.getSteps().size() - c.doneCount();
			summaries.add(new View.KitSummary(k, open, shortSupplies, quarters.measuredTrips(k.getId())).ready(c.readyTrips()));
		}
		for (Step s : chain.getSteps())
		{
			name(s.getItem());
			s.getRecipe().getInputs().keySet().forEach(this::name);
			s.getRecipe().getTools().forEach(this::name);
		}
		for (Chain.Line l : chain.getLines())
		{
			name(l.getSupply().getIcon());
			name(l.getProduct());
			l.getOptions().forEach(this::name);
		}

		// Herbs still to grow, as herb runs on the patches the player runs.
		Map<Integer, HerbRuns.Plan> herbRuns = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			if (s.getState() != Step.State.DONE && s.getRecipe().getVerb() == Verb.GATHER)
			{
				HerbRuns.Plan plan = HerbRuns.plan(s.getItem(), s.getNet(), config.herbPatches(), config.herbsPerPatch(), held);
				if (plan != null)
				{
					herbRuns.put(s.getItem(), plan);
					name(plan.getSeed());
				}
			}
		}

		if (provisions.hasBank())
		{
			announce(chain);
			learnPace(chain, now);
		}
		// Buy or make, for accounts that can trade.
		Map<String, Costing.Quote> quotes = provisions.isIron() ? new HashMap<>() : quotes(chain, held);
		Map<Integer, Long> estimates = new HashMap<>();
		Map<Integer, Boolean> measuredPace = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			if (s.getState() != Step.State.DONE)
			{
				estimates.put(s.getItem(), provisions.getPace().estimate(s));
				measuredPace.put(s.getItem(), provisions.getPace().measured(s.getItem()) > 0);
			}
		}
		View.State state = !provisions.hasBank() ? View.State.WAITING_FOR_BANK : View.State.READY;
		Step current = chain.current();
		Loads loads = current == null ? null : Loads.of(current, this::stacks);
		List<Training.Gain> training = Training.of(chain, provisions.xp(), provisions.levels());
		// One inventory's inputs and the tools, less what is already in the pack, so the marks go as items come out.
		bankMarks.show(loggedIn && current != null && current.getState() == Step.State.READY && config.bankMarks()
			? Loads.toTake(loads, current.getRecipe().getTools(), inHand) : Collections.emptyMap());
		View view = new View(state, chain, summaries, new HashMap<>(names), held, provisions.levels(), quarters.hasSavedKits(),
			config.measured() ? quarters.measuredTrips(kit.getId()) : 0, provisions.getBankSeen(), bankOpen, now)
			.trade(provisions.isIron(), provisions.getBought(), quotes)
			.pace(estimates, measuredPace)
			.work(loads, training)
			.herbs(herbRuns)
			.shared(provisions.isBankShared());

		if (loggedIn && config.plaque() && provisions.hasBank() && current != null)
		{
			String verb = current.getRecipe().getVerb().label();
			String title = verb + " " + name(current.getItem()).toLowerCase(Locale.ENGLISH);
			boolean locked = current.getState() == Step.State.LOCKED;
			String figure = locked ? "Lv " + current.getUnmet().getLevel() : Format.amount(current.getNet());
			String note = locked
				? "Needs " + current.getUnmet().getSkill().getName() + " " + current.getUnmet().getLevel()
				: "Step " + chain.number(current) + " of " + chain.getSteps().size() + "  -  " + kit.getName();
			plaque.show(title, figure, note, current.progress(), locked ? Theme.BLOOD : Theme.GOLD);
		}
		else
		{
			plaque.show(null, null, null, 0, null);
		}
		if (target != null)
		{
			SwingUtilities.invokeLater(() -> target.update(view));
		}
	}

	/** Watches the steps shrink to learn how fast the player really works each one. */
	private void learnPace(Chain chain, long now)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Map<Integer, Long> nets = new HashMap<>();
		Map<Integer, Integer> makes = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			nets.put(s.getItem(), s.getNet());
			makes.put(s.getItem(), s.getRecipe().getMakes());
		}
		Set<Integer> untimed = new HashSet<>();
		for (Step s : chain.getSteps())
		{
			Verb verb = s.getRecipe().getVerb();
			if (verb == Verb.BUY || verb == Verb.STOCK)
			{
				untimed.add(s.getItem());
			}
		}
		String plan = chain.getKit().getId() + "/" + chain.getTrips() + "/" + provisions.getChoices() + "/" + provisions.getBought();
		boolean quiet = quietPace;
		quietPace = false;
		if (provisions.getPace().observe(plan, nets, makes, untimed, quiet || !tradeOpen.isEmpty(), now))
		{
			unsaved = true;
		}
	}

	/** Says in the chat box when a step finishes, and what comes next. */
	private void announce(Chain chain)
	{
		String plan = chain.getKit().getId() + "/" + chain.getTrips() + "/" + provisions.getChoices() + "/" + provisions.getBought();
		Map<Integer, Step.State> states = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			states.put(s.getItem(), s.getState());
		}
		Map<Integer, Step.State> before = plan.equals(lastPlan) ? lastStates : null;
		lastPlan = plan;
		lastStates = states;
		if (before == null || !config.announce() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Step finished = null;
		for (Step s : chain.getSteps())
		{
			Step.State was = before.get(s.getItem());
			if (s.getState() == Step.State.DONE && was != null && was != Step.State.DONE)
			{
				finished = s;
			}
		}
		if (finished == null)
		{
			return;
		}
		Step next = chain.current();
		String text = name(finished.getItem()) + " " + finished.getRecipe().getVerb().past() + ".";
		if (next == null)
		{
			text += " Everything for " + chain.getTrips() + " trips of " + chain.getKit().getName() + " is stocked.";
		}
		else
		{
			text += " Next: " + next.getRecipe().getVerb().label().toLowerCase(Locale.ENGLISH) + " "
				+ Format.amount(next.getNet()) + " " + name(next.getItem()).toLowerCase(Locale.ENGLISH) + ".";
		}
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT).append("Codex Provisioner: ")
				.append(ChatColorType.NORMAL).append(text)
				.build())
			.build());
	}

	/**
	 * Buy or make for each supply still to fill. Supplies being made are quoted first, each taking
	 * the held ingredients it uses out of one pool, so two potions never both count the same
	 * vials; supplies bought instead are then quoted from what is left, as the cost of making them.
	 */
	private Map<String, Costing.Quote> quotes(Chain chain, Map<Integer, Long> held)
	{
		Map<String, Costing.Quote> quotes = new HashMap<>();
		Map<Integer, Long> pool = new HashMap<>(held);
		Set<String> bought = provisions.getBought();
		for (boolean buying : new boolean[]{false, true})
		{
			for (Chain.Line l : chain.getLines())
			{
				String key = l.getSupply().getKey();
				if (!l.getSupply().isItem() && l.getToMake() > 0 && bought.contains(key) == buying)
				{
					quotes.put(key, Costing.quote(l.getProduct(), l.getToMake(), prices, buying ? new HashMap<>(pool) : pool));
				}
			}
		}
		return quotes;
	}

	private boolean stacks(int itemId)
	{
		return stackable.computeIfAbsent(itemId, id -> itemManager.getItemComposition(id).isStackable());
	}

	private String name(int itemId)
	{
		return names.computeIfAbsent(itemId, id -> itemManager.getItemComposition(id).getName());
	}

	/** The panel's buttons; each change is made on the client thread, where the provisions live. */
	private final class PanelActions implements Actions
	{
		@Override
		public void kit(String kitId)
		{
			change(() -> provisions.setKit(kitId));
		}

		@Override
		public void trips(int trips)
		{
			change(() -> provisions.setTrips(trips));
		}

		@Override
		public void choose(String supply, int product)
		{
			change(() -> provisions.choose(supply, product));
		}

		@Override
		public void buy(String supply, boolean buy)
		{
			change(() -> provisions.setBought(supply, buy));
		}

		@Override
		public void restart()
		{
			change(() -> provisions.restart());
		}

		private void change(Runnable edit)
		{
			clientThread.invokeLater(() ->
			{
				if (provisions != null)
				{
					edit.run();
					unsaved = true;
					publish();
				}
			});
		}
	}
}
