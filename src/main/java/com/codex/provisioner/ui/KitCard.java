package com.codex.provisioner.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JPanel;

/** A kit to provision for, with what its chain would take for the chosen number of trips. */
class KitCard extends Card
{
	KitCard(View.KitSummary summary, boolean chosen, int trips, Runnable onChoose, int width)
	{
		super(new BorderLayout(0, 2));
		Color accent = chosen ? Theme.GOLD : summary.kit.isPreset() ? Theme.GOLD_DIM : Theme.TEAL;
		accent(accent);
		highlighted(chosen);
		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		top.add(Ui.label(summary.kit.getName(), Theme.body(), chosen ? Theme.GOLD_BRIGHT : Theme.TEXT), BorderLayout.WEST);
		String tag = chosen ? "chosen" : summary.kit.isPreset() ? "preset" : "your kit";
		top.add(Ui.label(tag, Theme.small(), chosen ? Theme.GOLD : Theme.TEXT_FAINT), BorderLayout.EAST);
		add(top, BorderLayout.NORTH);
		String detail;
		if (summary.shortSupplies == 0)
		{
			detail = "Stocked for " + trips + " trips";
		}
		else
		{
			detail = summary.shortSupplies + (summary.shortSupplies == 1 ? " supply" : " supplies") + " short  -  "
				+ summary.steps + (summary.steps == 1 ? " step" : " steps");
		}
		if (summary.ready > 0 && summary.shortSupplies > 0)
		{
			detail += "  -  ready for " + summary.ready + (summary.ready == 1 ? " trip" : " trips");
		}
		if (summary.measured >= 2)
		{
			detail += "  -  measured";
		}
		add(Ui.wrapped(detail, Theme.small(), summary.shortSupplies == 0 ? Theme.GREEN : Theme.TEXT_MUTED, width), BorderLayout.CENTER);
		if (!chosen)
		{
			// After the children are in, so the whole card answers the click.
			onClick(onChoose);
		}
	}
}
