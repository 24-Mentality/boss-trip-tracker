package com.maggotkingtriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.client.util.Text;
import org.junit.Test;

/**
 * Messages from the Normal Mode raid of 2026-09-29, as the game sends them (tags included), with teammates' names
 * replaced.
 */
public class TobMessagesTest
{
	@Test
	public void modeFromEnteringAndFromEachRoom()
	{
		assertEquals("normal", TobMessages.mode(text("You enter the Theatre of Blood (Normal Mode)...")));
		assertEquals("normal", TobMessages.mode(text("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>"
			+ "Duration: <col=ff0000>1:51.60</col> Total: <col=ff0000>1:51.60</col>")));
		// Assumed wording for the other modes
		assertEquals("hard", TobMessages.mode(text("You enter the Theatre of Blood (Hard Mode)...")));
		assertEquals("entry", TobMessages.mode(text("Wave 'Xarpus' (Entry Mode) complete!<br>Duration: 1:00.00")));
		assertTrue(TobMessages.isRoomComplete(text("Wave 'Sotetseg' (Normal Mode) complete!<br>Duration: <col=ff0000>2:10.20</col>")));
		assertFalse(TobMessages.isRoomComplete(text("You enter the Theatre of Blood (Normal Mode)...")));
		// A teammate entering first isn't you entering
		assertNull(TobMessages.mode(text("PLAYER1 has entered the Theatre of Blood (Normal Mode). Step inside to join him...")));
	}

	@Test
	public void completionCountAndTime()
	{
		RaidCompletion normal = TobMessages.completion(text("Your completed Theatre of Blood count is: <col=ff0000>6</col>."));
		assertEquals("normal", normal.getVariant());
		assertEquals(6, normal.getCount());
		RaidCompletion hard = TobMessages.completion(text("Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>1,234</col>."));
		assertEquals("hard", hard.getVariant());
		assertEquals(1234, hard.getCount());
		assertEquals("entry", TobMessages.completion(text("Your completed Theatre of Blood: Entry Mode count is: 2.")).getVariant());
		assertNull(TobMessages.completion(text("Your Maggot King kill count is: <col=ff0000>2,565</col>.")));

		assertEquals(Long.valueOf(19 * 60_000 + 51_600), TobMessages.totalTimeMs(text(
			"Theatre of Blood total completion time: <col=ff0000>19:51.60</col> (new personal best)")));
		assertNull(TobMessages.totalTimeMs(text("Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: "
			+ "<col=ff0000>4:52.80</col>")));
	}

	@Test
	public void onlyYourOwnDeathCounts()
	{
		assertTrue(TobMessages.isOwnDeath(text("You have died. Death count: <col=ff0000>2</col>.")));
		assertFalse(TobMessages.isOwnDeath(text("<col=ff0000>PLAYER1</col> has died. Death count: <col=ff0000>1</col>.")));
	}

	@Test
	public void purpleBroadcastKeepsOnlyTheItem()
	{
		assertEquals("Scythe of vitur (uncharged)",
			TobMessages.specialLootItem(text("PLAYER2 found something special: <col=ef20ff>Scythe of vitur (uncharged)</col>")));
		assertNull(TobMessages.specialLootItem(text("<col=ef1020>Valuable drop: 3 x Rune platebody (115,638 coins)</col>")));
	}

	@Test
	public void gameDryStreak()
	{
		assertEquals(Integer.valueOf(7), TobMessages.dryStreak(text("You are on a personal dry streak of <col=cf3f21>7</col>. ")));
		assertNull(TobMessages.dryStreak(text("You have completed <col=cf3f21>7</col> raids since you've seen any purple.")));
		assertEquals(Integer.valueOf(7), TobMessages.teamDryStreak(text("You have completed <col=cf3f21>7</col> raids since you've seen any purple.")));
		assertNull(TobMessages.teamDryStreak(text("You are on a personal dry streak of <col=cf3f21>7</col>. ")));
	}

	private static String text(String message)
	{
		return Text.removeTags(message);
	}
}
