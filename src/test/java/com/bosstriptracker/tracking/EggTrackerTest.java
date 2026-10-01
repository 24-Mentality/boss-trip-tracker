package com.bosstriptracker.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class EggTrackerTest
{
	@Test
	public void onlyThePopOptionPopsAnEgg()
	{
		assertTrue(EggTracker.isPopOption("Pop"));
		assertFalse(EggTracker.isPopOption("Drop"));
		assertFalse(EggTracker.isPopOption("Examine"));
		// An option the game adds later isn't a pop
		assertFalse(EggTracker.isPopOption("Inspect"));
		assertFalse(EggTracker.isPopOption(null));
	}
}
