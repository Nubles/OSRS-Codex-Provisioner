package com.codex.provisioner.ui;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which item pictures have arrived. RuneLite runs a picture's loaded callback again, a moment
 * later, for every ask after it has loaded; a panel that rebuilds or repaints on that callback
 * and asks again as it does would go round for as long as it is shown. Once a picture is in,
 * nothing waits on it any more. Safe from any thread.
 */
public final class Arrivals
{
	private final Set<Integer> arrived = ConcurrentHashMap.newKeySet();

	/** Whether a picture has arrived, so there is nothing to wait for. */
	public boolean arrived(int itemId)
	{
		return arrived.contains(itemId);
	}

	/** The callback to hand to the picture: it notes the arrival, then runs the panel's own. */
	public Runnable watch(int itemId, Runnable onLoaded)
	{
		return () ->
		{
			arrived.add(itemId);
			if (onLoaded != null)
			{
				onLoaded.run();
			}
		};
	}
}
