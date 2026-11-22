package com.bosstriptracker.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DeathRulesTest
{
	/**
	 * Doom of Mokhaiotl, 2026-11-22: the ActorDeath event at tick 1194 ended the run, and "Oh dear, you are dead!"
	 * followed at tick 1201 in the lobby, still the boss's area. Counting the second marked the player dead for the
	 * whole next run, which then recorded no supplies.
	 */
	@Test
	public void theSecondSignalOfADeathThatEndedTheTripInPlaceDoesntCount()
	{
		assertTrue(DeathRules.isNewDeath(true, true));
		assertFalse(DeathRules.isNewDeath(false, true));
	}

	@Test
	public void whereYouRespawnOutsideEverySignalMarksYouDeadUntilYouLeave()
	{
		// Leaving the area ends the trip and clears the mark, so a signal with no trip open is harmless there
		assertTrue(DeathRules.isNewDeath(true, false));
		assertTrue(DeathRules.isNewDeath(false, false));
	}
}
