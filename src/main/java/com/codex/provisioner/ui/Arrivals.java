package com.codex.provisioner.ui;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * Which item pictures have arrived. RuneLite runs a picture's loaded callback again, a moment
 * later, for every ask after it has loaded; a panel that rebuilds or repaints on that callback
 * and asks again as it does would go round for as long as it is shown. Once a picture is in,
 * nothing waits on it any more.
 *
 * <p>Pictures are told apart by the picture itself, not the item: RuneLite keeps only the last
 * 128 item pictures, so one asked for again after a busy while is a new, blank picture that has
 * to be waited on like the first. They are held weakly, so a forgotten picture is not kept.
 * Safe from any thread.
 */
public final class Arrivals
{
	private final Set<AsyncBufferedImage> arrived = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	/** Whether this picture has arrived, so there is nothing to wait for. */
	public boolean arrived(AsyncBufferedImage image)
	{
		return arrived.contains(image);
	}

	/**
	 * Runs onLoaded once the picture is in, unless it already is. A picture that arrives between
	 * the check and the ask still answers: RuneLite then runs the callback a moment later.
	 */
	public void ask(AsyncBufferedImage image, Runnable onLoaded)
	{
		if (image == null || arrived.contains(image))
		{
			return;
		}
		image.onLoaded(() ->
		{
			arrived.add(image);
			if (onLoaded != null)
			{
				onLoaded.run();
			}
		});
	}
}
