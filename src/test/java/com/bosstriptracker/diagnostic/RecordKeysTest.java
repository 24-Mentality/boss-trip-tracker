package com.bosstriptracker.diagnostic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RecordKeysTest
{
	@Test
	public void findsTheNightmareNexAndTheatreKeys()
	{
		// Keys seen in the RS profile config (2026-09-28)
		assertTrue(RecordKeys.isPlannedBoss("drops_NPC_Phosani's Nightmare"));
		assertTrue(RecordKeys.isPlannedBoss("drops_NPC_The Nightmare"));
		assertTrue(RecordKeys.isPlannedBoss("phosani's nightmare"));
		assertTrue(RecordKeys.isPlannedBoss("nightmare"));
		assertTrue(RecordKeys.isPlannedBoss("drops_NPC_Nex"));
		assertTrue(RecordKeys.isPlannedBoss("nex"));
		assertTrue(RecordKeys.isPlannedBoss("drops_EVENT_Theatre of Blood"));
		assertTrue(RecordKeys.isPlannedBoss("theatre of blood hard mode"));
		// The Fractured Archive's keys are assumed from RuneLite's naming until a raid is completed
		assertTrue(RecordKeys.isPlannedBoss("drops_EVENT_Fractured Archive"));
		assertTrue(RecordKeys.isPlannedBoss("fractured archive"));
		assertTrue(RecordKeys.isPlannedBoss("the fractured archive"));
		// The Forgotten Four: Loot Tracker names from the log of 2026-10-22, kill-count keys from the messages
		assertTrue(RecordKeys.isPlannedBoss("drops_NPC_Vardorvis"));
		assertTrue(RecordKeys.isPlannedBoss("drops_NPC_Duke Sucellus"));
		assertTrue(RecordKeys.isPlannedBoss("drops_NPC_The Whisperer"));
		assertTrue(RecordKeys.isPlannedBoss("drops_NPC_The Leviathan"));
		assertTrue(RecordKeys.isPlannedBoss("vardorvis"));
		assertTrue(RecordKeys.isPlannedBoss("duke sucellus (awakened)"));
		assertTrue(RecordKeys.isPlannedBoss("whisperer"));
		assertTrue(RecordKeys.isPlannedBoss("leviathan"));
	}

	@Test
	public void ignoresOtherBosses()
	{
		assertFalse(RecordKeys.isPlannedBoss("drops_NPC_Maggot King"));
		assertFalse(RecordKeys.isPlannedBoss("annex"));
		assertFalse(RecordKeys.isPlannedBoss("drops_NPC_Nexling"));
		assertFalse(RecordKeys.isPlannedBoss("vorkath"));
		assertFalse(RecordKeys.isPlannedBoss("drops_NPC_Lil'viathan"));
	}

	@Test
	public void readsTheKillCountFromALootTrackerRecord()
	{
		assertEquals(Integer.valueOf(127), RecordKeys.lootTrackerKills(
			"{\"type\":\"NPC\",\"name\":\"Phosani's Nightmare\",\"kills\":127,\"first\":1782960000000,\"drops\":[]}"));
		assertNull(RecordKeys.lootTrackerKills("{}"));
		assertNull(RecordKeys.lootTrackerKills(null));
	}
}
