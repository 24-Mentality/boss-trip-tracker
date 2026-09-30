package com.maggotkingtriptracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MaggotKingTripTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MaggotKingTripTrackerPlugin.class);
		RuneLite.main(args);
	}
}
