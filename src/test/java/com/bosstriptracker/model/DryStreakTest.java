package com.bosstriptracker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.junit.Test;

public class DryStreakTest
{
	private static final Predicate<Kill> LUCK = k -> "STOMACH".equals(k.getChoice());
	private static final Predicate<Kill> UNIQUE = k -> !k.getLoot().isEmpty();

	@Test
	public void withoutAnEnteredKcCountsTrackedLuckKillsSinceTheLastTrackedUnique()
	{
		List<Kill> kills = kills(151, 160);
		unique(kills, 155);
		kills.get(7).setChoice("EGGS");

		DryStreak.Result result = DryStreak.compute(kills, LUCK, UNIQUE, null, null);
		// 156..160 minus the Take-eggs kill at 158
		assertEquals(4, result.getSince());
		assertEquals(Integer.valueOf(155), result.getLastUniqueKc());
		assertFalse(result.isFromEnteredKc());
	}

	@Test
	public void noUniqueAndNoEnteredKcCountsFromTheStartOfTracking()
	{
		DryStreak.Result result = DryStreak.compute(kills(151, 160), LUCK, UNIQUE, null, null);
		assertEquals(10, result.getSince());
		assertNull(result.getLastUniqueKc());
	}

	@Test
	public void enteredKcAddsTheUntrackedGapAsKills()
	{
		List<Kill> kills = kills(151, 160);
		kills.get(0).setChoice("EGGS");

		DryStreak.Result result = DryStreak.compute(kills, LUCK, UNIQUE, 100, null);
		// 101..150 untracked (all counted) + 152..160 tracked Open-stomach kills
		assertEquals(50 + 9, result.getSince());
		assertEquals(Integer.valueOf(100), result.getLastUniqueKc());
		assertTrue(result.isFromEnteredKc());
	}

	@Test
	public void newerTrackedUniqueWinsOverTheEnteredKc()
	{
		List<Kill> kills = kills(151, 160);
		unique(kills, 155);

		DryStreak.Result result = DryStreak.compute(kills, LUCK, UNIQUE, 100, null);
		assertEquals(5, result.getSince());
		assertEquals(Integer.valueOf(155), result.getLastUniqueKc());
		assertFalse(result.isFromEnteredKc());
	}

	@Test
	public void enteredKcNewerThanTrackedUniqueWins()
	{
		// A unique the plugin missed, entered by hand
		List<Kill> kills = kills(151, 210);
		unique(kills, 155);

		DryStreak.Result result = DryStreak.compute(kills, LUCK, UNIQUE, 200, null);
		assertEquals(10, result.getSince());
		assertEquals(Integer.valueOf(200), result.getLastUniqueKc());
		assertTrue(result.isFromEnteredKc());
	}

	@Test
	public void killsWithoutAKillCountFollowThePreviousKill()
	{
		List<Kill> kills = kills(199, 202);
		kills.get(2).setKillCount(null);

		// 200 is at the entered KC; 201 (unknown, after 200) and 202 count
		DryStreak.Result result = DryStreak.compute(kills, LUCK, UNIQUE, 200, null);
		assertEquals(2, result.getSince());
	}

	@Test
	public void noTrackedKillCountsUsesTheKnownKc()
	{
		assertEquals(50, DryStreak.compute(new ArrayList<>(), LUCK, UNIQUE, 200, 250).getSince());
		assertEquals(0, DryStreak.compute(new ArrayList<>(), LUCK, UNIQUE, 200, null).getSince());
	}

	@Test
	public void longestIsTheBiggestGapBetweenKnownUniquesOrTheCurrentStreak()
	{
		// Entered unique at 1,920, tracked kisten at 2,767: the 847 kc dry spell from the diagnostic logs
		assertEquals(847, DryStreak.longest(Collections.singletonList(2767), 1920, 4));
		// A current streak longer than any past gap
		assertEquals(900, DryStreak.longest(Arrays.asList(2767, 2800), 1920, 900));
		// Order doesn't matter, and without known uniques it's the current streak
		assertEquals(200, DryStreak.longest(Arrays.asList(2500, 2200, 2400), null, 50));
		assertEquals(12, DryStreak.longest(new ArrayList<>(), null, 12));
	}

	private static List<Kill> kills(int fromKc, int toKc)
	{
		List<Kill> kills = new ArrayList<>();
		for (int kc = fromKc; kc <= toKc; kc++)
		{
			Kill kill = new Kill();
			kill.setKillCount(kc);
			kill.setChoice("STOMACH");
			kills.add(kill);
		}
		return kills;
	}

	private static void unique(List<Kill> kills, int kc)
	{
		for (Kill kill : kills)
		{
			if (kill.getKillCount() != null && kill.getKillCount() == kc)
			{
				kill.getLoot().add(new ItemEntry(1, 1, 1));
			}
		}
	}
}
