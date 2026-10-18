package com.codex.provisioner.ui;

import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Step;
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
	private static final String[] TABS = {"chain", "stores", "kits"};
	private static final int[] TRIPS = {5, 10, 25, 50, 100};

	private final ItemIcons icons;
	private final Actions actions;
	private final Crest crest = new Crest();
	private final Segmented tabs = new Segmented(true, "Chain", "Stores", "Kits");
	private final Segmented trips = new Segmented(false, "5", "10", "25", "50", "100");
	private final CardLayout cards = new CardLayout();
	private final JPanel content = new JPanel(cards);
	private final JScrollPane chainPage = scroller();
	private final JScrollPane storesPage = scroller();
	private final JScrollPane kitsPage = scroller();

	private View view = View.loggedOut();

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
		content.add(storesPage, TABS[1]);
		content.add(kitsPage, TABS[2]);
		add(content, BorderLayout.CENTER);

		tabs.onChange(this::showTab);
		trips.setSelected(1, false);
		trips.onChange(i -> actions.trips(TRIPS[i]));
		rebuild();
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
		rebuild();
	}

	/** Opens one tab, for previews and tests. */
	public void showTab(int index)
	{
		tabs.setSelected(index, false);
		cards.show(content, TABS[index]);
	}

	private void updateCrest()
	{
		Chain chain = view.chain;
		if (chain == null)
		{
			crest.update("PROVISIONS", null, 0, false, -1, "Log in to plan your supplies", null, Theme.TEXT_MUTED, Theme.GOLD);
			return;
		}
		String heading = (chain.getKit().getName() + "  -  " + chain.getTrips() + " trips").toUpperCase(Locale.ENGLISH);
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

	private void rebuild()
	{
		rebuildChain();
		rebuildStores();
		rebuildKits();
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
			stack.add(note("Open your bank once", "The Provisioner counts what you already hold at every stage of the chain. After the first look it remembers the bank as last seen, so the panel works anywhere."));
			return true;
		}
		return false;
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
		String foot = view.measured >= 2
			? "Planned from " + view.measured + " trips Codex Quartermaster measured with this kit."
			: "Counts what your bank and inventory hold at every stage, so each step shrinks as you work.";
		stack.add(Ui.wrapped(foot, Theme.small(), Theme.TEXT_FAINT, WIDTH - 16));
		if (!view.bankOpen && view.bankSeen > 0)
		{
			stack.add(Ui.label("Bank as last seen " + Format.ago(view.now - view.bankSeen), Theme.small(), Theme.TEXT_FAINT));
		}
		replace(chainPage, stack);
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
		tiles.add(new StatTile(total == 0 ? "-" : done + " / " + total, "Steps", chain.provisioned() ? "all done" : Format.percent(chain.progress()) + " of the way", Theme.GOLD));
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
				String note = s.getState() == Step.State.LOCKED
					? "Needs " + Ui.skill(s.getUnmet().getSkill()) + " " + s.getUnmet().getLevel()
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
		stack.add(Ui.wrapped("Pick what each supply is made as; a red dot means a level in its chain is not reached yet. Ironman friendly: no step needs trading with anyone.",
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
