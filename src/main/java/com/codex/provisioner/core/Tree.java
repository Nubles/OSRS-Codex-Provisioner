package com.codex.provisioner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A supply's chain drawn as the tree it is: the finished product at the root, what goes into it
 * beneath, down to the things gathered by hand at the leaves. A part already stocked is a leaf of
 * its own, since nothing beneath it is needed; a part the tree has already shown, such as vials of
 * water shared by two potions, is named again but not opened twice. Tools are not inputs and stay out.
 */
public final class Tree
{
	/** No chain the Cookbook knows is deeper than this; a guard against a recipe that loops. */
	static final int MAX_DEPTH = 8;

	/** One line of the tree. */
	public static final class Node
	{
		private final int item;
		private final int depth;
		private final Step step;
		private final boolean repeat;
		private final boolean last;
		private final boolean[] rails;

		Node(int item, int depth, Step step, boolean repeat, boolean last, boolean[] rails)
		{
			this.item = item;
			this.depth = depth;
			this.step = step;
			this.repeat = repeat;
			this.last = last;
			this.rails = rails;
		}

		public int getItem()
		{
			return item;
		}

		/** How far below the root it sits; the root is 0. */
		public int getDepth()
		{
			return depth;
		}

		/** Its step in the chain, or null when the chain holds none, as for a product already stocked. */
		public Step getStep()
		{
			return step;
		}

		/** Whether the tree has already shown this part, here named again without what goes into it. */
		public boolean isRepeat()
		{
			return repeat;
		}

		/** Whether it is the last input of its parent, so its branch ends here. */
		public boolean isLast()
		{
			return last;
		}

		/** Whether the branch at a depth above it still runs on below it, for drawing the lines. */
		public boolean rail(int depth)
		{
			return depth >= 0 && depth < rails.length && rails[depth];
		}

		/** Whether nothing more of it is needed. */
		public boolean isDone()
		{
			return step == null || step.getState() == Step.State.DONE;
		}
	}

	private Tree()
	{
	}

	/** Every supply's tree, in the kit's order, with each shared part opened only the first time. */
	public static Map<Chain.Line, List<Node>> of(Chain chain)
	{
		Map<Integer, Step> steps = new HashMap<>();
		for (Step s : chain.getSteps())
		{
			steps.put(s.getItem(), s);
		}
		Set<Integer> shown = new HashSet<>();
		Map<Chain.Line, List<Node>> trees = new java.util.LinkedHashMap<>();
		for (Chain.Line line : chain.getLines())
		{
			List<Node> nodes = new ArrayList<>();
			walk(line.getProduct(), 0, true, new boolean[0], steps, shown, nodes);
			trees.put(line, Collections.unmodifiableList(nodes));
		}
		return trees;
	}

	private static void walk(int item, int depth, boolean last, boolean[] rails, Map<Integer, Step> steps, Set<Integer> shown, List<Node> out)
	{
		Step step = steps.get(item);
		boolean repeat = !shown.add(item);
		out.add(new Node(item, depth, step, repeat, last, rails));
		// A stocked part needs nothing beneath it; a repeated one was opened where it first came.
		if (repeat || step == null || step.getState() == Step.State.DONE || depth >= MAX_DEPTH)
		{
			return;
		}
		Recipe recipe = step.getRecipe();
		List<Integer> inputs = new ArrayList<>(recipe.getInputs().keySet());
		boolean[] below = new boolean[depth + 1];
		System.arraycopy(rails, 0, below, 0, rails.length);
		// The line from this node down to its inputs runs on past each one but the last.
		for (int i = 0; i < inputs.size(); i++)
		{
			boolean lastInput = i == inputs.size() - 1;
			boolean[] r = below.clone();
			r[depth] = !lastInput;
			walk(inputs.get(i), depth + 1, lastInput, r, steps, shown, out);
		}
	}
}
