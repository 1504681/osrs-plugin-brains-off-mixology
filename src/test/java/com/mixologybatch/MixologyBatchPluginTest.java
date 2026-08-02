package com.mixologybatch;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MixologyBatchPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MixologyBatchPlugin.class);
		RuneLite.main(args);
	}
}
