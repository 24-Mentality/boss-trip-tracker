package com.maggotkingtriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.maggotkingtriptracker.model.TripEndReason;
import org.junit.Test;

/**
 * The raid of 2026-09-29: a team of 4, a death at Bloat (reported at ticks 912 and 920), completed.
 */
public class RaidStateTest
{
	@Test
	public void deathInAClearedRoomIsNotAWipe()
	{
		RaidState raid = new RaidState();
		raid.start("normal");
		raid.teamSeen(4);
		raid.roomCleared();
		// Someone leaves later: the team size stays what it was at the start
		raid.teamSeen(3);
		assertTrue(raid.died(912));
		// The same death, reported again by the message
		assertFalse(raid.died(920));
		raid.roomCleared();
		raid.completed();

		assertEquals(Integer.valueOf(4), raid.getTeamSize());
		assertEquals(TripEndReason.COMPLETED, raid.endReason());
	}

	@Test
	public void leavingAfterDyingInTheRoomIsAWipe()
	{
		RaidState raid = new RaidState();
		raid.start(null);
		raid.teamSeen(2);
		raid.roomCleared();
		assertTrue(raid.died(1_500));
		assertEquals(TripEndReason.WIPED, raid.endReason());

		// Teleporting out alive is neither
		raid.start("hard");
		assertNull(raid.endReason());
		assertNull(raid.getTeamSize());
		assertEquals("hard", raid.getMode());
		// A second death much later is a new one
		assertTrue(raid.died(100));
		assertTrue(raid.died(500));
	}
}
