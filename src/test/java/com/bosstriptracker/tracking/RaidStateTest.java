package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.model.TripEndReason;
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

	/**
	 * The Fractured Archive says when the team has wiped, and lets you give the raid up.
	 */
	@Test
	public void theGamesOwnWipeAndAbandonMessagesDecide()
	{
		RaidState raid = new RaidState();
		raid.start(null);
		raid.teamSeen(5);
		raid.roomCleared();
		raid.roomCleared();
		// Died at Dreadnoth, respawned when the challenge was won, then the team fell at Xarth without you dying again
		assertTrue(raid.died(28_772));
		raid.roomCleared();
		assertNull(raid.endReason());
		raid.wiped();
		assertEquals(TripEndReason.WIPED, raid.endReason());
		assertTrue(raid.isWiped());

		// Abandoning after a death in the room isn't a wipe
		raid.start(null);
		assertTrue(raid.died(100));
		raid.abandoned();
		assertNull(raid.endReason());
		assertTrue(raid.isAbandoned());

		// The next raid starts clean
		raid.start(null);
		assertFalse(raid.isWiped());
		assertFalse(raid.isAbandoned());
		assertNull(raid.endReason());
	}
}
