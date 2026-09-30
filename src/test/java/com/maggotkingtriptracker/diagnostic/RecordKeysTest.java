package com.maggotkingtriptracker.diagnostic;

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
	}

	@Test
	public void ignoresOtherBosses()
	{
		assertFalse(RecordKeys.isPlannedBoss("drops_NPC_Maggot King"));
		assertFalse(RecordKeys.isPlannedBoss("annex"));
		assertFalse(RecordKeys.isPlannedBoss("drops_NPC_Nexling"));
		assertFalse(RecordKeys.isPlannedBoss("vorkath"));
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
