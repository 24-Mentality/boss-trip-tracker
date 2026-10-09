package com.bosstriptracker.model;

import static org.junit.Assert.assertEquals;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;

public class LuckOddsTest
{
	private static final double DELTA = 1e-12;

	@Test
	public void matchesEveryOutcomeWorkedOutByHand()
	{
		// Three kills at 1/2, 1/3 and 1/3
		Map<Double, Integer> chances = ImmutableMap.of(0.5, 1, 1 / 3.0, 2);
		double[] exact = bruteForce(new double[]{0.5, 1 / 3.0, 1 / 3.0});
		double atMost = 0;
		for (int k = 0; k <= 3; k++)
		{
			atMost += exact[k];
			assertEquals(atMost, LuckOdds.atMost(chances, k), DELTA);
			double atLeast = 0;
			for (int j = k; j <= 3; j++)
			{
				atLeast += exact[j];
			}
			assertEquals(atLeast, LuckOdds.atLeast(chances, k), DELTA);
		}
		assertEquals(0.5 + 2 / 3.0, LuckOdds.expected(chances), DELTA);
	}

	@Test
	public void noUniquesAtTheMaggotKing()
	{
		// 331 Open-stomach kills at 1/205.6 without a unique: about 1 player in 5 is as dry
		Map<Double, Integer> chances = ImmutableMap.of(1 / 205.6, 331);
		assertEquals(Math.pow(1 - 1 / 205.6, 331), LuckOdds.atMost(chances, 0), DELTA);
		assertEquals(0.2, LuckOdds.atMost(chances, 0), 0.002);
	}

	@Test
	public void longRunsDontUnderflow()
	{
		Map<Double, Integer> chances = ImmutableMap.of(1 / 9.1, 20_000);
		// About 2,198 expected: the median is close by
		assertEquals(0.5, LuckOdds.atMost(chances, 2_198), 0.02);
		assertEquals(1, LuckOdds.atMost(chances, 2_100) + LuckOdds.atLeast(chances, 2_101), 1e-9);
	}

	private static double[] bruteForce(double[] p)
	{
		double[] exact = new double[p.length + 1];
		for (int mask = 0; mask < 1 << p.length; mask++)
		{
			double chance = 1;
			int hits = 0;
			for (int i = 0; i < p.length; i++)
			{
				boolean hit = (mask & 1 << i) != 0;
				chance *= hit ? p[i] : 1 - p[i];
				hits += hit ? 1 : 0;
			}
			exact[hits] += chance;
		}
		return exact;
	}
}
