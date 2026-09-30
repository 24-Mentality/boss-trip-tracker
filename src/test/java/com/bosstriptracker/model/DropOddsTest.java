package com.bosstriptracker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DropOddsTest
{
	private static final double ANY_UNIQUE = 1 / 205.6;

	@Test
	public void chanceByNow()
	{
		assertEquals(0, DropOdds.chanceByNow(ANY_UNIQUE, 0), 1e-9);
		// About 63% of players have a unique by the drop rate
		assertEquals(0.632, DropOdds.chanceByNow(ANY_UNIQUE, 206), 0.002);
	}

	@Test
	public void killsForChance()
	{
		assertEquals(143, DropOdds.killsForChance(ANY_UNIQUE, 0.5));
		assertEquals(473, DropOdds.killsForChance(ANY_UNIQUE, 0.9));
	}

	@Test
	public void luckPercentile()
	{
		// Nothing expected and nothing received is exactly average
		assertEquals(0.5, DropOdds.luckPercentile(0, 0), 1e-9);
		// Going unique-less over twice the drop rate is dry; two early uniques is lucky
		assertTrue(DropOdds.luckPercentile(0, 2.0) < 0.1);
		assertTrue(DropOdds.luckPercentile(2, 0.5) > 0.9);
	}
}
