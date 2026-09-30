package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ChatPatternsTest
{
	// Raw messages as recorded by diagnostic mode on 2026-09-27
	private static final String KC = "Your Maggot King kill count is: <col=ff0000>2,565</col>.";
	private static final String DURATION = "Fight duration: <col=ff0000>1:48.00</col>. Personal best: 1:19.20";

	@Test
	public void parsesKillCount()
	{
		assertEquals(Integer.valueOf(2565), ChatPatterns.killCount(KC, "Maggot King"));
	}

	@Test
	public void ignoresOtherBossesKillCount()
	{
		assertNull(ChatPatterns.killCount("Your Vorkath kill count is: <col=ff0000>12</col>.", "Maggot King"));
	}

	@Test
	public void parsesFightDuration()
	{
		assertEquals(Long.valueOf(108_000), ChatPatterns.fightDurationMs(DURATION));
		assertEquals(Long.valueOf(3_600_000 + 2 * 60_000 + 3_500),
			ChatPatterns.fightDurationMs("Fight duration: <col=ff0000>1:02:03.50</col>."));
		assertNull(ChatPatterns.fightDurationMs(KC));
	}

	@Test
	public void recognisesPetAndDeathMessages()
	{
		assertTrue(ChatPatterns.isPetMessage("You have a funny feeling like you're being followed."));
		assertTrue(ChatPatterns.isPetMessage("You feel something weird sneaking into your backpack."));
		assertTrue(ChatPatterns.isPetMessage("You have a funny feeling like you would have been followed..."));
		assertFalse(ChatPatterns.isPetMessage("The eggs pop as you try to take them."));
		assertTrue(ChatPatterns.isDeathMessage("Oh dear, you are dead!"));
	}

	@Test
	public void phosanisKillMessages()
	{
		ChatPatterns.KillCount kc = ChatPatterns.killCount("Your Phosani's Nightmare kill count is: <col=ff0000>129</col>.");
		assertEquals("Phosani's Nightmare", kc.getName());
		assertEquals(129, kc.getCount());
		// The team size comes first, so the duration isn't at the start of the message
		assertEquals(Long.valueOf(334_200), ChatPatterns.fightDurationMs(
			"Team size: <col=ff0000>Solo</col> Fight duration: <col=ff0000>5:34.20</col>. Personal best: 5:02.40"));
	}

	@Test
	public void thrallMessages()
	{
		// Raw text from the diagnostic log, colour marker included
		assertEquals(Boolean.TRUE, ChatPatterns.thrall("@mes_hl_mag@You resurrect a greater ghostly thrall.</col>"));
		assertEquals(Boolean.FALSE, ChatPatterns.thrall("@mes_hl_mag@Your greater ghostly thrall returns to the grave.</col>"));
		assertNull(ChatPatterns.thrall("The south west totem is fully charged."));
	}
}
