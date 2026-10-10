package com.bosstriptracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.junit.Test;

public class OverlayRowsTest
{
	@Test
	public void everyStatHasAnOptionalTwin()
	{
		for (OverlayStat stat : OverlayStat.values())
		{
			assertEquals(stat, OverlayOptionalStat.of(stat).getStat());
		}
		assertEquals(OverlayStat.values().length + 1, OverlayOptionalStat.values().length);
	}

	@Test
	public void offShowsNothingAndEmptyRowsAreSkipped()
	{
		OverlayRows off = new OverlayRows(false, OverlayStat.KILLS_PER_HOUR, OverlayOptionalStat.CURRENT_KILL, OverlayOptionalStat.NET_PROFIT);
		assertTrue(off.stats().isEmpty());

		OverlayRows gaps = new OverlayRows(true, OverlayStat.LUCK, OverlayOptionalStat.NOTHING, OverlayOptionalStat.PB);
		assertEquals(Arrays.asList(OverlayStat.LUCK, OverlayStat.PB), gaps.stats());
	}
}
