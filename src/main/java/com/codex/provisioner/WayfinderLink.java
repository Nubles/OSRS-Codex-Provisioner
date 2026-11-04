package com.codex.provisioner;

import com.codex.provisioner.core.Travel;
import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

/**
 * The link to Codex Wayfinder: whether it is installed and switched on, the travel atlas it shares
 * ({@code codexwayfinder.atlasV1}, read only), and the Route button, which asks it to plan the way
 * somewhere ("route", answered with "routed") and to show that route in its own panel ("show").
 * Everything fails safe: with Wayfinder absent there is no atlas and no Route button.
 */
final class WayfinderLink
{
	/** Ids this app puts on its route requests, so replies to other apps are passed over. */
	static final String ID_PREFIX = "codexprovisioner:";

	private final PluginManager plugins;
	private final EventBus eventBus;
	private final ConfigManager configManager;
	private final ClientThread clientThread;
	private final Gson gson;
	private final Map<String, Travel.Routed> replies = new ConcurrentHashMap<>();
	private volatile boolean on;
	private volatile Travel atlas;

	WayfinderLink(PluginManager plugins, EventBus eventBus, ConfigManager configManager, ClientThread clientThread, Gson gson)
	{
		this.plugins = plugins;
		this.eventBus = eventBus;
		this.configManager = configManager;
		this.clientThread = clientThread;
		this.gson = gson;
	}

	/** Looks again for Wayfinder and rereads its atlas. */
	void refresh()
	{
		boolean enabled = enabled();
		on = enabled;
		Travel t = null;
		if (enabled)
		{
			try
			{
				t = Travel.read(gson, configManager.getRSProfileConfiguration(Travel.GROUP, Travel.KEY));
			}
			catch (RuntimeException e)
			{
				t = null;
			}
		}
		atlas = t;
		if (!enabled)
		{
			replies.clear();
		}
	}

	private boolean enabled()
	{
		try
		{
			for (Plugin p : plugins.getPlugins())
			{
				PluginDescriptor d = p.getClass().getAnnotation(PluginDescriptor.class);
				if (d != null && Travel.PLUGIN_NAME.equals(d.name()))
				{
					return plugins.isPluginEnabled(p);
				}
			}
		}
		catch (RuntimeException e)
		{
			return false;
		}
		return false;
	}

	/** Whether the Route button should show. */
	boolean on()
	{
		return on;
	}

	/** The atlas, or null without Wayfinder. */
	Travel atlas()
	{
		return on ? atlas : null;
	}

	/** Asks Wayfinder for the way to a tile and to show it; does nothing without Wayfinder. */
	void route(int x, int y, int plane, String name, String key)
	{
		if (!on)
		{
			return;
		}
		String id = ID_PREFIX + key;
		Map<String, Object> data = Travel.request(x, y, plane, name, id);
		clientThread.invokeLater(() ->
		{
			eventBus.post(new PluginMessage(Travel.NAMESPACE, Travel.ROUTE, data));
			eventBus.post(new PluginMessage(Travel.NAMESPACE, Travel.SHOW, new LinkedHashMap<>(data)));
		});
	}

	/** Takes a "routed" reply meant for this app; true when one was taken. */
	boolean heard(PluginMessage e)
	{
		if (!Travel.NAMESPACE.equals(e.getNamespace()) || !Travel.ROUTED.equals(e.getName()))
		{
			return false;
		}
		Travel.Routed r = Travel.routed(e.getData());
		if (r == null || !r.id.startsWith(ID_PREFIX))
		{
			return false;
		}
		replies.put(r.id.substring(ID_PREFIX.length()), r);
		return true;
	}

	/** Wayfinder's last answer for a place this app asked about, or null. */
	Travel.Routed reply(String key)
	{
		return replies.get(key);
	}
}
