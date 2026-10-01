package com.bosstriptracker.boss;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class TheatreOfBloodInsideTest
{
	private static final int UNLISTED_ROOM = 13379 + 1;

	private final TheatreOfBloodBoss tob = new TheatreOfBloodBoss();

	@Test
	public void stillInsideWhileThePartyIsInTheRaid()
	{
		assertTrue(tob.isStillInside(status(2), UNLISTED_ROOM, true));
		// The vault
		assertTrue(tob.isStillInside(status(3), UNLISTED_ROOM, true));
	}

	@Test
	public void outsideOnceThePartyLeftOrOutsideAnInstance()
	{
		assertFalse(tob.isStillInside(status(1), UNLISTED_ROOM, true));
		assertFalse(tob.isStillInside(status(0), UNLISTED_ROOM, true));
		assertFalse(tob.isStillInside(status(2), UNLISTED_ROOM, false));
		assertFalse(tob.isStillInside(status(2), TheatreOfBloodBoss.VER_SINHAZA_REGION_ID, true));
		// Other bosses never bridge regions
		assertFalse(new MaggotKingBoss().isStillInside(status(2), UNLISTED_ROOM, true));
	}

	private static IntUnaryOperator status(int value)
	{
		return varbit -> varbit == VarbitID.TOB_CLIENT_PARTYSTATUS ? value : 0;
	}
}
