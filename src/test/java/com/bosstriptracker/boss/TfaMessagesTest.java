package com.bosstriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.client.util.Text;
import org.junit.Test;

/**
 * Messages from the diagnostic raids of 2026-10-21, as the game sends them (tags included), with teammates' names
 * replaced. The completion count and total time messages are assumed (no raid was completed).
 */
public class TfaMessagesTest
{
	@Test
	public void challengesStartAndComplete()
	{
		assertEquals("Archive Guardian", TfaMessages.challengeStarted(text("Challenge started: Archive Guardian.")));
		assertEquals("Dreadnoth the Jellied", TfaMessages.challengeStarted(text("Challenge started: Dreadnoth the Jellied.")));
		assertNull(TfaMessages.challengeStarted(text("PLAYER9 has proceeded to face Bork. Join him...")));

		TfaMessages.ChallengeComplete first = TfaMessages.challengeComplete(text(
			"Challenge complete: Archive Guardian. Duration: <col=ef1020>2:40.80</col>. Total: <col=ef1020>2:40.80</col> (new personal best)"));
		assertEquals("Archive Guardian", first.getChallenge());
		assertEquals(2 * 60_000 + 40_800, first.getDurationMs());
		assertEquals(2 * 60_000 + 40_800, first.getTotalMs());

		TfaMessages.ChallengeComplete bork = TfaMessages.challengeComplete(text(
			"Challenge complete: Bork. Duration: <col=ef1020>4:27.00</col>. Total: <col=ef1020>6:59.40</col>. Personal best: 3:50.40"));
		assertEquals("Bork", bork.getChallenge());
		assertEquals(4 * 60_000 + 27_000, bork.getDurationMs());
		assertEquals(6 * 60_000 + 59_400, bork.getTotalMs());
		// The last challenge's running total stands in for the raid's time
		assertEquals(Long.valueOf(31 * 60_000 + 16_200), TfaMessages.totalTimeMs(text(
			"Challenge complete: Xarth. Duration: <col=ef1020>6:13.20</col>. Total: <col=ef1020>31:16.20</col> (new personal best)")));
		assertNull(TfaMessages.challengeComplete(text("Challenge started: Xarth.")));
	}

	@Test
	public void deathsWipesAndAbandoning()
	{
		assertTrue(TfaMessages.isOwnDeath(text("You have died. Total deaths: <col=ff0000>4</col>.")));
		assertFalse(TfaMessages.isOwnDeath(text("<col=ff0000>PLAYER12</col> has died. Total deaths: <col=ff0000>1</col>.")));
		assertFalse(TfaMessages.isOwnDeath(text("You will respawn when your party completes or fails the challenge.")));
		assertTrue(TfaMessages.isWipe(text("You failed to survive the Fractured Archive.")));
		assertTrue(TfaMessages.isAbandon(text("You abandon the raid and leave the Fractured Archive.")));
		assertFalse(TfaMessages.isWipe(text("You abandon the raid and leave the Fractured Archive.")));
	}

	@Test
	public void gravestonePayment()
	{
		assertEquals(Long.valueOf(125_000), TfaMessages.bankPayment(text("Payment has been taken from your bank: 125,000 x Coins")));
		// The Grand Exchange's version names no amount
		assertNull(TfaMessages.bankPayment(text("Payment has been taken from your bank.")));
	}

	@Test
	public void completionCountAndTotalTimeAssumedWording()
	{
		assertEquals(1, TfaMessages.completion(text("Your completed Fractured Archive count is: <col=ff0000>1</col>.")).getCount());
		assertEquals(12, TfaMessages.completion(text("Your completed The Fractured Archive count is: 12.")).getCount());
		assertEquals(1_234, TfaMessages.completion(text("Your Fractured Archive completion count is: <col=ff0000>1,234</col>.")).getCount());
		assertNull(TfaMessages.completion(text("Your completed Fractured Archive count is: <col=ff0000>1</col>.")).getVariant());
		assertNull(TfaMessages.completion(text("Your completed Theatre of Blood count is: <col=ff0000>6</col>.")));
		assertEquals(Long.valueOf(31 * 60_000 + 16_200), TfaMessages.totalTimeMs(text(
			"The Fractured Archive: Total completion time: <col=ff0000>31:16.20</col> (new personal best)")));
		assertEquals(Long.valueOf(3_600_000 + 60_000 + 500), TfaMessages.timeMs("1:01:00.50"));
		assertNull(TfaMessages.totalTimeMs(text("Challenge started: Ocellus.")));
	}

	private static String text(String message)
	{
		return Text.removeTags(message);
	}
}
