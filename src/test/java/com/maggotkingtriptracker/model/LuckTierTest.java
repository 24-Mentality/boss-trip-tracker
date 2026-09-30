package com.maggotkingtriptracker.model;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class LuckTierTest
{
	@Test
	public void boundariesAreInclusiveAndSymmetric()
	{
		assertEquals(LuckTier.LUCKY_AS_RUCK, LuckTier.of(1.0));
		assertEquals(LuckTier.LUCKY_AS_RUCK, LuckTier.of(0.90));
		assertEquals(LuckTier.LUCKY, LuckTier.of(0.8999));
		assertEquals(LuckTier.LUCKY, LuckTier.of(0.65));
		assertEquals(LuckTier.ON_RATE, LuckTier.of(0.6499));
		assertEquals(LuckTier.ON_RATE, LuckTier.of(0.5));
		assertEquals(LuckTier.ON_RATE, LuckTier.of(0.3501));
		assertEquals(LuckTier.DRY, LuckTier.of(0.35));
		assertEquals(LuckTier.DRY, LuckTier.of(0.1001));
		assertEquals(LuckTier.DRY_AS_RUCK, LuckTier.of(0.10));
		assertEquals(LuckTier.DRY_AS_RUCK, LuckTier.of(0.0));
	}

	@Test
	public void mirroredPercentilesGetMirroredTiers()
	{
		for (int i = 0; i <= 100; i++)
		{
			double p = i / 100.0;
			assertEquals("at " + p, mirror(LuckTier.of(p)), LuckTier.of(1 - p));
		}
	}

	@Test
	public void labelsMatchTheRoadmap()
	{
		assertEquals("LUCKY AS RUCK", LuckTier.LUCKY_AS_RUCK.getLabel());
		assertEquals("Lucky", LuckTier.LUCKY.getLabel());
		assertEquals("On Rate", LuckTier.ON_RATE.getLabel());
		assertEquals("Dry", LuckTier.DRY.getLabel());
		assertEquals("DRY AS RUCK", LuckTier.DRY_AS_RUCK.getLabel());
	}

	private static LuckTier mirror(LuckTier tier)
	{
		switch (tier)
		{
			case LUCKY_AS_RUCK:
				return LuckTier.DRY_AS_RUCK;
			case LUCKY:
				return LuckTier.DRY;
			case DRY:
				return LuckTier.LUCKY;
			case DRY_AS_RUCK:
				return LuckTier.LUCKY_AS_RUCK;
			default:
				return LuckTier.ON_RATE;
		}
	}
}
