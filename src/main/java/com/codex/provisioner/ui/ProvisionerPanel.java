package com.codex.provisioner.ui;

import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Sitting;
import com.codex.provisioner.core.Step;
import com.codex.provisioner.core.Tree;
import com.codex.provisioner.core.Supplies;
import com.codex.provisioner.core.Supply;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;

/**
 * The Provisioner side panel: the crest, then three pages. Chain is the step in hand and every
 * step after it, Stores is each supply against the target with what it is made as, and Kits is
 * where the content and its kit are chosen.
 */
public class ProvisionerPanel extends PluginPanel
{
	static final int WIDTH = 200;
	private static final String[] TABS = {"chain", "tree", "stores", "kits"};
	private static final int[] TRIPS = {5, 10, 25, 50, 100};

	private final ItemIcons icons;
	private final Actions actions;
	private final Crest crest = new Crest();
	private final Segmented tabs = new Segmented(true, "Chain", "Tree", "Stores", "Kits");
	private final Segmented trips = new Segmented(false, "5", "10", "25", "50", "100");
	/** How long a sitting the Chain page plans, in minutes. */
	private static final int[] SITTINGS = {30, 60, 120, 180};
	private final Segmented sittingLength = new Segmented(false, "30m", "1h", "2h", "3h");
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane chainPage = scroller();
	private final JScrollPane treePage = scroller();
	private final JScrollPane storesPage = scroller();
	private final JScrollPane kitsPage = scroller();

	private View view = View.loggedOut();
	/** Which pages still show an older view; a page is only rebuilt when it is looked at. */
	private final boolean[] stale = {true, true, true, true};

	public ProvisionerPanel(ItemIcons icons, Actions actions)
	{
		super(false);
		this.icons = icons;
		this.actions = actions;
		setLayout(new BorderLayout());
		setBackground(Theme.INK);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(crest, BorderLayout.NORTH);
		JPanel tabHolder = new JPanel(new BorderLayout());
		tabHolder.setOpaque(false);
		tabHolder.setBorder(new EmptyBorder(8, 8, 2, 8));
		tabHolder.add(tabs, BorderLayout.CENTER);
		north.add(tabHolder, BorderLayout.SOUTH);
		add(north, BorderLayout.NORTH);

		content.setOpaque(false);
		content.add(chainPage, TABS[0]);
		content.add(treePage, TABS[1]);
		content.add(storesPage, TABS[2]);
		content.add(kitsPage, TABS[3]);
		add(content, BorderLayout.CENTER);

		tabs.onChange(this::showTab);
		trips.setSelected(1, false);
		trips.onChange(i -> actions.trips(TRIPS[i]));
		sittingLength.setSelected(1, false);
		sittingLength.onChange(i ->
		{
			stale[0] = true;
			refresh(true);
		});
		refresh(true);
	}

	private static JScrollPane scroller()
	{
		JScrollPane scroll = new JScrollPane();
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		return scroll;
	}

	/** Called on the Swing thread whenever anything shown changes. */
	public void update(View view)
	{
		this.view = view;
		if (view.chain != null)
		{
			for (int i = 0; i < TRIPS.length; i++)
			{
				if (TRIPS[i] == view.chain.getTrips())
				{
					trips.setSelected(i, false);
				}
			}
		}
		updateCrest();
		java.util.Arrays.fill(stale, true);
		refresh(false);
	}

	/** Opens one tab. */
	public void showTab(int index)
	{
		tabs.setSelected(index, false);
		cards.show(content, TABS[index]);
		refresh(true);
	}

	@Override
	public void onActivate()
	{
		refresh(true);
	}

	/**
	 * Rebuilds the page on show if it is out of date. Every withdrawal changes the view, so while
	 * the panel is closed nothing is rebuilt at all, and while it is open only the page in view is.
	 */
	private void refresh(boolean force)
	{
		if (!force && !isShowing())
		{
			return;
		}
		int page = tabs.getSelected();
		if (!stale[page])
		{
			return;
		}
		stale[page] = false;
		if (page == 0)
		{
			rebuildChain();
		}
		else if (page == 1)
		{
			rebuildTree();
		}
		else if (page == 2)
		{
			rebuildStores();
		}
		else
		{
			rebuildKits();
		}
	}

	private void updateCrest()
	{
		Chain chain = view.chain;
		if (chain == null)
		{
			crest.update("PROVISIONS", null, 0, false, -1, "Log in to plan your supplies", null, Theme.TEXT_MUTED, Theme.GOLD);
			return;
		}
		// A long kit name keeps the heading to itself; the trips are on the Stores tab, and a cut "100 TR..." helps no one.
		String full = chain.getKit().getName() + " \u00b7 " + chain.getTrips() + " trips";
		String heading = (full.length() > 24 ? chain.getKit().getName() : full).toUpperCase(Locale.ENGLISH);
		if (view.state == View.State.WAITING_FOR_BANK)
		{
			crest.update(heading, null, 0, false, -1, "Open your bank to begin", null, Theme.mix(Theme.TEXT_MUTED, Theme.GOLD, 0.3), Theme.GOLD);
			return;
		}
		if (chain.provisioned())
		{
			crest.update(heading, chain.getSteps().size(), chain.getSteps().size(), true, 1, "Every supply is stocked", "All stocked", Theme.GREEN, Theme.GREEN);
			return;
		}
		Step now = chain.current();
		String status;
		String shortStatus;
		Color color;
		if (now == null)
		{
			status = "Nothing left to do";
			shortStatus = null;
			color = Theme.TEXT_MUTED;
		}
		else if (now.getState() == Step.State.LOCKED)
		{
			status = "Needs " + Ui.skill(now.getUnmet().getSkill()) + " " + now.getUnmet().getLevel();
			shortStatus = null;
			color = Theme.BLOOD;
		}
		else
		{
			status = "Now: " + now.getRecipe().getVerb().label().toLowerCase(Locale.ENGLISH) + " " + view.name(now.getItem()).toLowerCase(Locale.ENGLISH);
			shortStatus = now.getRecipe().getVerb().label() + " " + Format.amount(now.getNet()) + " more";
			color = Theme.mix(Theme.TEXT_MUTED, Theme.GOLD, 0.45);
		}
		crest.update(heading, chain.doneCount(), chain.getSteps().size(), false, chain.progress(), status, shortStatus, color, Theme.GOLD);
	}

	private boolean waiting(StackPanel stack)
	{
		if (view.chain == null)
		{
			stack.add(note("The larder is closed", "Log in and Codex Provisioner walks you through gathering and making the supplies a kit needs, one step at a time, from the raw fish and grimy herbs to the finished potions."));
			return true;
		}
		if (view.state == View.State.WAITING_FOR_BANK)
		{
			stack.add(note("Open your bank once", "The Provisioner counts what you already hold at every stage of the chain. After the first look it remembers the bank as last seen, so the panel works anywhere. With Codex Bank Steward sharing its bank, this step is done for you."));
			return true;
		}
		return false;
	}

	// The Tree tab.

	/** Each supply as the tree of parts it is made from, root to leaves. */
	private void rebuildTree()
	{
		StackPanel stack = new StackPanel(8, 8);
		if (waiting(stack))
		{
			replace(treePage, stack);
			return;
		}
		Chain chain = view.chain;
		Step now = chain.current();
		stack.add(Ui.wrapped("Each supply from the finished product down to what is gathered by hand. A part already stocked needs nothing beneath it.",
			Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		for (Map.Entry<Chain.Line, List<Tree.Node>> tree : Tree.of(chain).entrySet())
		{
			Chain.Line line = tree.getKey();
			String trailing = line.getShort() <= 0 ? "stocked" : Format.amount(line.getShort()) + " short";
			stack.add(new SectionHeader(view.name(line.getSupply()), trailing));
			stack.add(new TreeCard(tree.getValue(), view, icons, now));
		}
		replace(treePage, stack);
	}

	// The Chain tab.

	private void rebuildChain()
	{
		StackPanel stack = new StackPanel(8, 8);
		if (waiting(stack))
		{
			replace(chainPage, stack);
			return;
		}
		Chain chain = view.chain;
		Step now = chain.current();
		if (chain.provisioned())
		{
			Card done = note("Provisioned", "Your bank holds everything " + chain.getTrips() + " trips of " + chain.getKit().getName()
				+ " take. Pick more trips in Stores to plan the next batch.");
			done.accent(Theme.GREEN);
			stack.add(done);
		}
		else if (now != null)
		{
			stack.add(new NowCard(chain, now, view, icons, WIDTH - 30));
		}
		String runway = runway(chain);
		if (runway != null && !chain.provisioned())
		{
			stack.add(Ui.wrapped(runway, Theme.small(), chain.readyTrips() == 0 ? Theme.EMBER : Theme.TEAL, WIDTH - 16));
		}
		if (!chain.provisioned())
		{
			sitting(stack, chain);
		}

		Map<String, List<Step>> groups = new LinkedHashMap<>();
		for (Step s : chain.getSteps())
		{
			groups.computeIfAbsent(s.getSupply(), k -> new ArrayList<>()).add(s);
		}
		for (Map.Entry<String, List<Step>> group : groups.entrySet())
		{
			Supply supply = Supplies.get(group.getKey());
			Chain.Line line = line(group.getKey());
			String trailing = line == null ? null : line.getShort() <= 0 ? "stocked" : Format.amount(line.getShort()) + " short";
			stack.add(new SectionHeader(supply == null ? "Supplies" : view.name(supply), trailing));
			Card card = new Card(new BorderLayout());
			StackPanel rows = new StackPanel(0, 0);
			List<Step> steps = group.getValue();
			for (int i = 0; i < steps.size(); i++)
			{
				rows.add(new StepRow(steps.get(i), view, steps.get(i) == now, i == steps.size() - 1, icons));
			}
			card.add(rows, BorderLayout.CENTER);
			stack.add(card);
		}
		if (!view.training.isEmpty())
		{
			double total = 0;
			for (com.codex.provisioner.core.Training.Gain g : view.training)
			{
				total += g.getXp();
			}
			stack.add(new SectionHeader("What it trains", Format.amount(Math.round(total)) + " xp"));
			Card card = new Card(new BorderLayout());
			StackPanel rows = new StackPanel(4, 0);
			for (int i = 0; i < view.training.size(); i++)
			{
				rows.add(new TrainRow(view.training.get(i), i == view.training.size() - 1));
			}
			card.add(rows, BorderLayout.CENTER);
			stack.add(card);
		}
		long work = 0;
		int untimed = 0;
		long herbSeeds = 0;
		int patches = 0;
		for (Step s : chain.getSteps())
		{
			if (s.getState() == Step.State.DONE)
			{
				continue;
			}
			com.codex.provisioner.core.HerbRuns.Plan herbs = view.herbRuns(s.getItem());
			long t = view.estimate(s.getItem());
			if (herbs != null)
			{
				// Herbs of different kinds share a run, each patch planted with whichever is needed.
				herbSeeds += herbs.getSeeds();
				patches = herbs.getPatches();
			}
			else if (t >= 0)
			{
				work += t;
			}
			else
			{
				untimed++;
			}
		}
		long herbRuns = patches == 0 ? 0 : (herbSeeds + patches - 1) / patches;
		if (work > 0 || herbRuns > 0)
		{
			List<String> parts = new ArrayList<>();
			if (herbRuns > 0)
			{
				parts.add(View.runs(herbRuns));
			}
			if (untimed > 0)
			{
				parts.add(untimed + (untimed == 1 ? " step" : " steps") + " that cannot be timed, such as drops and shop runs");
			}
			String time = (work > 0 ? "About " + Format.estimate(work) + " of work left" : "No timed work left")
				+ (parts.isEmpty() ? "." : ", plus " + String.join(" and ", parts) + ".");
			stack.add(Ui.wrapped(time, Theme.small(), Theme.TEXT_MUTED, WIDTH - 16));
		}
		String foot = view.measured >= 2
			? "Planned from " + view.measured + " trips Codex Quartermaster measured with this kit."
			: "Counts what your bank and inventory hold at every stage, so each step shrinks as you work.";
		stack.add(Ui.wrapped(foot, Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		if (!view.bankOpen && view.bankSeen > 0)
		{
			stack.add(Ui.wrapped(view.bankShared
					? "Bank as Codex Bank Steward saw it " + Format.ago(view.now - view.bankSeen) + "; open it once to count it here."
					: "Bank as last seen " + Format.ago(view.now - view.bankSeen),
				Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		}
		replace(chainPage, stack);
	}

	/** What one sitting of the chosen length gets through, the step in hand first. */
	private void sitting(StackPanel stack, Chain chain)
	{
		long budget = SITTINGS[Math.max(0, Math.min(SITTINGS.length - 1, sittingLength.getSelected()))] * 60_000L;
		java.util.Set<Integer> grown = new java.util.HashSet<>(view.herbRuns.keySet());
		Sitting plan = Sitting.plan(chain, view.estimates, grown, budget);
		boolean own = false;
		for (Sitting.Piece p : plan.getPieces())
		{
			own |= view.ownPace(p.getStep().getItem());
		}
		stack.add(new SectionHeader("In one sitting", own ? "at your pace" : "at a typical pace"));
		stack.add(sittingLength);
		Card card = new Card(new BorderLayout());
		StackPanel rows = new StackPanel(4, 0);
		if (plan.getPieces().isEmpty())
		{
			rows.add(Ui.wrapped("Nothing left can be worked in a sitting yet; it waits on what is grown, dropped or above your levels.",
				Theme.small(), Theme.TEXT_MUTED, WIDTH - 30));
		}
		for (Sitting.Piece p : plan.getPieces())
		{
			Step s = p.getStep();
			String what = s.getRecipe().getVerb().label() + " " + Format.amount(p.getAmount())
				+ (p.finishes() ? "" : " of " + Format.amount(s.getNet())) + " " + view.name(s.getItem()).toLowerCase(Locale.ENGLISH);
			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.add(Ui.wrapped(what, Theme.small(), p.finishes() ? Theme.TEXT : Theme.mix(Theme.TEXT, Theme.GOLD, 0.35), WIDTH - 80), BorderLayout.CENTER);
			JPanel time = new JPanel(new BorderLayout());
			time.setOpaque(false);
			boolean shop = s.getRecipe().getVerb() == com.codex.provisioner.core.Verb.BUY;
			// Buying is quickly done, so it is given no time of its own.
			time.add(Ui.label(shop ? "" : Format.estimate(p.getMs()), Theme.small(), Theme.GOLD), BorderLayout.NORTH);
			row.add(time, BorderLayout.EAST);
			rows.add(row);
		}
		card.add(rows, BorderLayout.CENTER);
		stack.add(card);
		String foot;
		if (plan.isEverything())
		{
			foot = "That is every timed step, in about " + Format.estimate(plan.getUsed()) + ".";
		}
		else
		{
			int later = 0;
			int locked = 0;
			int untimed = 0;
			int waiting = 0;
			for (Step s : plan.getLeft())
			{
				switch (plan.reason(s, view.estimates, grown))
				{
					case LOCKED:
						locked++;
						break;
					case UNTIMED:
						untimed++;
						break;
					case WAITING:
						waiting++;
						break;
					default:
						later++;
						break;
				}
			}
			List<String> parts = new ArrayList<>();
			if (later > 0)
			{
				parts.add(later + (later == 1 ? " step" : " steps") + " for another sitting");
			}
			if (untimed > 0)
			{
				parts.add(untimed + (untimed == 1 ? " grown or untimed step" : " grown or untimed steps"));
			}
			if (waiting > 0)
			{
				parts.add(waiting + (waiting == 1 ? " step waiting on those" : " steps waiting on those"));
			}
			if (locked > 0)
			{
				parts.add(locked + (locked == 1 ? " step" : " steps") + " above your levels");
			}
			foot = parts.isEmpty() ? null : "Left after it: " + String.join(", ", parts) + ".";
		}
		if (foot != null)
		{
			stack.add(Ui.wrapped(foot, Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		}
	}

	/** "Ready for 4 of 10 trips now; prayer runs out first.", or null when the kit uses nothing up. */
	private String runway(Chain chain)
	{
		Chain.Line first = chain.limiting();
		if (first == null)
		{
			return null;
		}
		long ready = first.tripsHeld();
		String trips = ready >= chain.getTrips() ? ready + (ready == 1 ? " trip" : " trips")
			: ready + " of " + chain.getTrips() + " trips";
		String head = ready == 0 ? "Not stocked for a single trip yet." : "Ready for " + trips + " now.";
		return head + " First to run out: " + view.name(first.getSupply()).toLowerCase(java.util.Locale.ENGLISH) + ".";
	}

	private Chain.Line line(String supply)
	{
		for (Chain.Line l : view.chain.getLines())
		{
			if (l.getSupply().getKey().equals(supply))
			{
				return l;
			}
		}
		return null;
	}

	// The Stores tab.

	private void rebuildStores()
	{
		StackPanel stack = new StackPanel(8, 8);
		stack.add(new SectionHeader("Provision for", "trips"));
		stack.add(trips);
		if (waiting(stack))
		{
			replace(storesPage, stack);
			return;
		}
		Chain chain = view.chain;
		JPanel tiles = new JPanel(new GridLayout(1, 2, 6, 0));
		tiles.setOpaque(false);
		int done = chain.doneCount();
		int total = chain.getSteps().size();
		Chain.Line first = chain.limiting();
		if (first != null)
		{
			long ready = first.tripsHeld();
			tiles.add(new StatTile(String.valueOf(ready), "Ready for", ready >= chain.getTrips() ? "trips, all stocked"
				: "of " + chain.getTrips() + " trips", ready >= chain.getTrips() ? Theme.GREEN : ready == 0 ? Theme.EMBER : Theme.GOLD));
		}
		else
		{
			tiles.add(new StatTile(total == 0 ? "-" : done + " / " + total, "Steps", chain.provisioned() ? "all done" : Format.percent(chain.progress()) + " of the way", Theme.GOLD));
		}
		List<Step> sources = chain.sources();
		tiles.add(new StatTile(String.valueOf(sources.size()), "To gather", sources.isEmpty() ? "nothing left" : sources.size() == 1 ? "one source" : "sources", sources.isEmpty() ? Theme.GREEN : Theme.TEAL));
		stack.add(tiles);

		stack.add(new SectionHeader("Supplies", chain.getKit().getName()));
		for (Chain.Line line : chain.getLines())
		{
			stack.add(new SupplyCard(line, view, icons, actions, WIDTH - 30));
		}

		if (!sources.isEmpty())
		{
			stack.add(new SectionHeader("Gathering list"));
			Card list = new Card(new BorderLayout());
			StackPanel rows = new StackPanel(2, 0);
			for (Step s : sources)
			{
				com.codex.provisioner.core.HerbRuns.Plan herbs = view.herbRuns(s.getItem());
				String note = s.getState() == Step.State.LOCKED
					? "Needs " + Ui.skill(s.getUnmet().getSkill()) + " " + s.getUnmet().getLevel()
					: herbs != null ? View.runs(herbs.getRuns()) + ", " + Format.amount(herbs.getSeeds()) + (herbs.getSeeds() == 1 ? " seed" : " seeds")
					: s.getRecipe().getVerb().label() + (s.getHeld() > 0 ? ", " + Format.amount(s.getHeld()) + " held" : "");
				rows.add(new SourceRow(s.getItem(), view.name(s.getItem()), note, Format.amount(s.getNet()),
					s.getState() == Step.State.LOCKED ? Theme.BLOOD : Theme.GOLD, s.getRecipe().getHow(), icons));
			}
			list.add(rows, BorderLayout.CENTER);
			stack.add(list);
		}
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.setOpaque(false);
		buttons.add(new CodexButton("Restart progress bars", false, actions::restart));
		stack.add(buttons);
		stack.add(Ui.wrapped(view.iron
				? "Pick what each supply is made as; a red dot means a level in its chain is not reached yet. Iron account: no step needs trading with anyone."
				: "Pick what each supply is made as; a red dot means a level in its chain is not reached yet. Making is priced from bought ingredients at Grand Exchange prices.",
			Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		replace(storesPage, stack);
	}

	// The Kits tab.

	private void rebuildKits()
	{
		StackPanel stack = new StackPanel(8, 8);
		if (view.chain == null)
		{
			waiting(stack);
			replace(kitsPage, stack);
			return;
		}
		Card intro = new Card(new BorderLayout(0, 4));
		intro.accent(Theme.TEAL);
		intro.add(Ui.label(view.quartermaster ? "Kits from Codex Quartermaster" : "Pick what you are stocking for", Theme.body(), Theme.GOLD), BorderLayout.NORTH);
		intro.add(Ui.wrapped(view.quartermaster
				? "Your saved Quartermaster kits are here, and kits it has measured on real trips are planned from what you really used."
				: "Choose the content and the Provisioner plans every supply it takes. Kits you save in Codex Quartermaster show up here too.",
			Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		stack.add(intro);

		String chosen = view.chain.getKit().getId();
		boolean anySaved = false;
		for (View.KitSummary k : view.kits)
		{
			if (!k.kit.isPreset())
			{
				if (!anySaved)
				{
					stack.add(new SectionHeader("Your kits"));
					anySaved = true;
				}
				stack.add(new KitCard(k, k.kit.getId().equals(chosen), view.chain.getTrips(), () -> actions.kit(k.kit.getId()), WIDTH - 30));
			}
		}
		stack.add(new SectionHeader("Presets"));
		for (View.KitSummary k : view.kits)
		{
			if (k.kit.isPreset())
			{
				stack.add(new KitCard(k, k.kit.getId().equals(chosen), view.chain.getTrips(), () -> actions.kit(k.kit.getId()), WIDTH - 30));
			}
		}
		replace(kitsPage, stack);
	}

	static Card note(String title, String text)
	{
		Card card = new Card(new BorderLayout(0, 4));
		card.accent(Theme.GOLD_DIM);
		card.add(Ui.label(title, Theme.body(), Theme.GOLD), BorderLayout.NORTH);
		card.add(Ui.wrapped(text, Theme.small(), Theme.TEXT_MUTED, WIDTH - 30), BorderLayout.CENTER);
		return card;
	}

	private static void replace(JScrollPane scroll, JComponent view)
	{
		int position = scroll.getVerticalScrollBar().getValue();
		scroll.setViewportView(view);
		scroll.revalidate();
		SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(position));
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		g.setColor(Theme.INK);
		g.fillRect(0, 0, getWidth(), getHeight());
	}
}
