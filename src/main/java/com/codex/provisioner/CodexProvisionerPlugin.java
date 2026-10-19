package com.codex.provisioner;

import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Costing;
import com.codex.provisioner.core.Kit;
import com.codex.provisioner.core.Prices;
import com.codex.provisioner.core.Provisions;
import com.codex.provisioner.core.Quarters;
import com.codex.provisioner.core.Step;
import com.codex.provisioner.overlay.StepPlaque;
import com.codex.provisioner.ui.Actions;
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
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ScriptID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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
	/** Where Codex Quartermaster keeps its kits; read, never written. */
	private static final String QUARTERMASTER_GROUP = "codexquartermaster";
	private static final String QUARTERMASTER_KEY = "quarters";
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

	private final StepPlaque plaque = new StepPlaque();
	private final Map<Integer, String> names = new HashMap<>();

	private ProvisionerPanel panel;
	private NavigationButton navButton;

	private String profile;
	private Provisions provisions;
	private Quarters quarters = Quarters.none();
	/** What was carried when last seen, so the panel still counts it while logged out. */
	private Map<Integer, Long> carried = Collections.emptyMap();
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
	private int ticks;

	@Provides
	CodexProvisionerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CodexProvisionerConfig.class);
	}

	@Override
	protected void startUp()
	{
		ItemIcons icons = (itemId, onLoaded) ->
		{
			AsyncBufferedImage image = itemManager.getImage(itemId);
			image.onLoaded(onLoaded);
			return image;
		};
		panel = new ProvisionerPanel(icons, new PanelActions());
		navButton = NavigationButton.builder()
			.tooltip("Codex Provisioner")
			.icon(Sigil.icon(16))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(plaque);
		clientThread.invokeLater(() ->
		{
			loadProfile();
			if (client.getGameState() == GameState.LOGGED_IN && provisions != null)
			{
				carried = readCarried();
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
		clientToolbar.removeNavigation(navButton);
		clientThread.invokeLater(() ->
		{
			save();
			provisions = null;
			profile = null;
			names.clear();
			bankOpen = false;
			lastStates = null;
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
			readStorage = false;
			storageGrace = 0;
			save();
			publish();
		}
		else if (event.getGameState() == GameState.LOGGED_IN && provisions != null)
		{
			readLevels();
			dirty = true;
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		save();
		loadProfile();
		bankOpen = false;
		readStorage = false;
		storageGrace = 0;
		carried = client.getGameState() == GameState.LOGGED_IN ? readCarried() : Collections.emptyMap();
		publish();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (CodexProvisionerConfig.GROUP.equals(event.getGroup()))
		{
			dirty = true;
		}
		else if (QUARTERMASTER_GROUP.equals(event.getGroup()) && event.getKey() != null && event.getKey().endsWith(QUARTERMASTER_KEY))
		{
			// Quartermaster saved a kit or measured a trip.
			clientThread.invokeLater(() ->
			{
				loadQuarters();
				dirty = true;
			});
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (provisions != null && provisions.observeLevels(Collections.singletonMap(event.getSkill(), event.getLevel())))
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
			dirty = true;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
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

	private void readLevels()
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			int level = client.getRealSkillLevel(skill);
			if (level > 0)
			{
				levels.put(skill, level);
			}
		}
		if (!levels.isEmpty() && provisions.observeLevels(levels))
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
		dirty = true;
	}

	private void loadQuarters()
	{
		quarters = Quarters.none();
		if (profile == null)
		{
			return;
		}
		try
		{
			String saved = configManager.getRSProfileConfiguration(QUARTERMASTER_GROUP, QUARTERMASTER_KEY);
			if (saved != null && !saved.isEmpty())
			{
				Quarters read = gson.fromJson(saved, Quarters.class);
				quarters = read == null ? Quarters.none() : read;
			}
		}
		catch (JsonParseException e)
		{
			log.debug("Could not read Codex Quartermaster's kits", e);
		}
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
			summaries.add(new View.KitSummary(k, open, shortSupplies, quarters.measuredTrips(k.getId())));
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

		if (provisions.hasBank())
		{
			announce(chain);
			learnPace(chain, now);
		}
		// Buy or make, for accounts that can trade.
		Map<String, Costing.Quote> quotes = new HashMap<>();
		if (!provisions.isIron())
		{
			for (Chain.Line l : chain.getLines())
			{
				if (!l.getSupply().isItem() && l.getToMake() > 0)
				{
					quotes.put(l.getSupply().getKey(), Costing.quote(l.getProduct(), l.getToMake(), prices));
				}
			}
		}
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
		View view = new View(state, chain, summaries, new HashMap<>(names), held, provisions.levels(), quarters.hasSavedKits(),
			config.measured() ? quarters.measuredTrips(kit.getId()) : 0, provisions.getBankSeen(), bankOpen, now)
			.trade(provisions.isIron(), provisions.getBought(), quotes)
			.pace(estimates, measuredPace);

		Step current = chain.current();
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
		String plan = chain.getKit().getId() + "/" + chain.getTrips() + "/" + provisions.getChoices() + "/" + provisions.getBought();
		if (provisions.getPace().observe(plan, nets, makes, now))
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
