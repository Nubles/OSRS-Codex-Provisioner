package com.codex.provisioner.ui;

import static org.junit.Assert.assertEquals;
import com.codex.provisioner.core.Chain;
import com.codex.provisioner.core.Kit;
import com.codex.provisioner.core.Loads;
import com.codex.provisioner.core.Need;
import com.codex.provisioner.core.Step;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/** The bank-trip strip on the step card, at the width it has in the sidebar. */
public class LoadStripTest
{
	@Test
	public void everyIngredientOfASuperCombatFitsTheSidebar()
	{
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill s : Skill.values())
		{
			levels.put(s, 99);
		}
		Kit kit = new Kit("k", "K", Collections.singletonList(new Need("COMBAT", 12)), false);
		Chain chain = Chain.build(kit, Collections.emptyMap(), 10, new HashMap<>(), new HashMap<>(), levels, Collections.emptyMap());
		Step mix = null;
		for (Step s : chain.getSteps())
		{
			if (s.getItem() == ItemID._4DOSE2COMBAT)
			{
				mix = s;
			}
		}
		Loads loads = Loads.of(mix, id -> false);
		assertEquals(4, loads.getWithdraw().size());
		View view = new View(View.State.READY, chain, Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
			levels, false, 0, 0, false, 0);

		// The icons asked for while painting are the slots drawn.
		Set<Integer> drawn = new LinkedHashSet<>();
		LoadStrip strip = new LoadStrip(mix, loads, view, (id, onLoaded) ->
		{
			drawn.add(id);
			return null;
		});
		// The step card's inner width in a 225px sidebar is about 181px, less with a scroll bar showing.
		strip.setSize(170, strip.getPreferredSize().height);
		BufferedImage img = new BufferedImage(170, strip.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		strip.paint(g);
		g.dispose();
		assertEquals(loads.getWithdraw().keySet(), drawn);
	}
}
