package com.codex.provisioner;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CodexProvisionerPluginRunner
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CodexProvisionerPlugin.class);
		RuneLite.main(args);
	}
}
