package com.bosstriptracker.diagnostic;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PlayerNameScrubberTest
{
	@Test
	public void sameNameKeepsItsStandIn()
	{
		PlayerNameScrubber scrubber = new PlayerNameScrubber();
		scrubber.learn("Alice");
		scrubber.learn("Bob");

		assertEquals("PLAYER1 has died. Death count: 1.", scrubber.scrub("Bob has died. Death count: 1."));
		assertEquals("PLAYER2 and PLAYER1", scrubber.scrub("Alice and Bob"));
		assertEquals("PLAYER1 again", scrubber.scrub("bob again"));
	}

	@Test
	public void spacesUnderscoresAndHyphensMatchEachOther()
	{
		PlayerNameScrubber scrubber = new PlayerNameScrubber();
		scrubber.learn("<img=2>Iron\u00A0Man");

		assertEquals("name=\"PLAYER1\"", scrubber.scrub("name=\"Iron Man\""));
		assertEquals("PLAYER1 received", scrubber.scrub("Iron_Man received"));
		assertEquals("PLAYER1 received", scrubber.scrub("iron-man received"));
		assertEquals("<col=ff0000>PLAYER1</col>", scrubber.scrub("<col=ff0000>Iron\u00A0Man</col>"));
	}

	@Test
	public void onlyWholeWords()
	{
		PlayerNameScrubber scrubber = new PlayerNameScrubber();
		scrubber.learn("Tim");

		assertEquals("Time to go", scrubber.scrub("Time to go"));
		assertEquals("Message PLAYER1", scrubber.scrub("Message Tim"));
		assertEquals("PLAYER1's drop", scrubber.scrub("Tim's drop"));
	}

	@Test
	public void longerNameWins()
	{
		PlayerNameScrubber scrubber = new PlayerNameScrubber();
		scrubber.learn("Bob");
		scrubber.learn("Bob Smith");

		assertEquals("PLAYER1 met PLAYER2", scrubber.scrub("Bob Smith met Bob"));
	}

	@Test
	public void unknownNamesAndEmptyTextAreLeftAlone()
	{
		PlayerNameScrubber scrubber = new PlayerNameScrubber();
		assertEquals("Carol has died.", scrubber.scrub("Carol has died."));
		scrubber.learn("Alice");
		assertEquals("Carol has died.", scrubber.scrub("Carol has died."));
		assertEquals("", scrubber.scrub(""));
	}

	@Test
	public void namesWithoutLettersOrTooLongAreIgnored()
	{
		PlayerNameScrubber scrubber = new PlayerNameScrubber();
		scrubber.learn("1234");
		scrubber.learn("");
		scrubber.learn(null);
		scrubber.learn("Thirteen chars");

		assertEquals("Rune platebody (1127) x1234", scrubber.scrub("Rune platebody (1127) x1234"));
		assertEquals("Thirteen chars", scrubber.scrub("Thirteen chars"));
	}

	@Test
	public void newFileStartsNumberingAgain()
	{
		PlayerNameScrubber scrubber = new PlayerNameScrubber();
		scrubber.learn("Alice");
		scrubber.learn("Bob");
		assertEquals("PLAYER1", scrubber.scrub("Alice"));
		assertEquals("PLAYER2", scrubber.scrub("Bob"));

		scrubber.startNewFile();
		assertEquals("PLAYER1", scrubber.scrub("Bob"));
		assertEquals("PLAYER2", scrubber.scrub("Alice"));
	}

	@Test
	public void replacementTextIsLiteral()
	{
		PlayerNameScrubber scrubber = new PlayerNameScrubber();
		scrubber.learn("Bob");
		assertEquals("cost $5 PLAYER1 \\o/", scrubber.scrub("cost $5 Bob \\o/"));
	}
}
