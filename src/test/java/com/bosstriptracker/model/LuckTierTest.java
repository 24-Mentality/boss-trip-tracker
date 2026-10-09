package com.bosstriptracker.model;

import static org.junit.Assert.assertEquals;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

public class LuckTierTest
{
	@Test
	public void tailBoundariesAreInclusive()
	{
		// Dry side: chance of this many or fewer
		assertEquals(LuckTier.DRY_AS_RUCK, LuckTier.of(0.10, 1, 5, 0));
		assertEquals(LuckTier.DRY, LuckTier.of(0.1001, 1, 5, 0));
		assertEquals(LuckTier.DRY, LuckTier.of(0.35, 1, 5, 0));
		assertEquals(LuckTier.ON_RATE, LuckTier.of(0.3501, 0.8, 5, 0));
		// Lucky side: chance of this many or more
		assertEquals(LuckTier.LUCKY_AS_RUCK, LuckTier.of(1, 0.10, 5, 0));
		assertEquals(LuckTier.LUCKY, LuckTier.of(1, 0.35, 5, 0));
		assertEquals(LuckTier.ON_RATE, LuckTier.of(0.7, 0.3501, 5, 0));
	}

	@Test
	public void noUniquesAtTheMaggotKing()
	{
		double rate = 1 / 205.6;
		// 331 kills without a unique: 1 player in 5 is as dry, so Dry rather than DRY AS RUCK
		assertEquals(LuckTier.DRY, tier(0, rate, 331));
		assertEquals(LuckTier.DRY_AS_RUCK, tier(0, rate, 474));
		// Under one unique expected: too early, unless already far out
		assertEquals(LuckTier.TOO_EARLY, tier(0, rate, 100));
		assertEquals(LuckTier.LUCKY_AS_RUCK, tier(1, rate, 10));
		// The example from the review: 11 uniques against 13.13 expected stays Dry
		assertEquals(LuckTier.DRY, tier(11, rate, 2700));
	}

	@Test
	public void ruckTiersCanNeedMoreExpected()
	{
		// The Theatre of Blood: under 3 purples expected, at most Dry
		assertEquals(LuckTier.DRY, LuckTier.of(0.05, 1, 2.9, 3));
		assertEquals(LuckTier.DRY_AS_RUCK, LuckTier.of(0.05, 1, 3, 3));
	}

	@Test
	public void labels()
	{
		assertEquals("LUCKY AS RUCK", LuckTier.LUCKY_AS_RUCK.getLabel());
		assertEquals("Lucky", LuckTier.LUCKY.getLabel());
		assertEquals("On Rate", LuckTier.ON_RATE.getLabel());
		assertEquals("Dry", LuckTier.DRY.getLabel());
		assertEquals("DRY AS RUCK", LuckTier.DRY_AS_RUCK.getLabel());
		assertEquals("Too early to tell", LuckTier.TOO_EARLY.getLabel());
	}

	private static LuckTier tier(int received, double rate, int kills)
	{
		ImmutableMap<Double, Integer> chances = ImmutableMap.of(rate, kills);
		return LuckTier.of(LuckOdds.atMost(chances, received), LuckOdds.atLeast(chances, received), rate * kills, 0);
	}
}
