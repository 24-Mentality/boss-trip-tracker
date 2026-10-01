package com.bosstriptracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
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
		assertFalse(off.shows(CanvasSection.GOAL));

		OverlayRows gaps = new OverlayRows(true, OverlayStat.LUCK, OverlayOptionalStat.NOTHING, OverlayOptionalStat.PB);
		assertEquals(Arrays.asList(OverlayStat.LUCK, OverlayStat.PB), gaps.stats());
		assertTrue(gaps.shows(CanvasSection.TRIP));
		assertFalse(gaps.shows(CanvasSection.GOAL));
	}

	@Test
	public void addToCanvasTurnsOnAndFillsTheFirstEmptyRow()
	{
		OverlayRows off = new OverlayRows(false, OverlayStat.KILLS_PER_HOUR, OverlayOptionalStat.NOTHING, OverlayOptionalStat.NOTHING);
		OverlayRows goal = off.with(CanvasSection.GOAL);
		// Already showing a goal stat: just turned on
		assertEquals(new OverlayRows(true, OverlayStat.KILLS_PER_HOUR, OverlayOptionalStat.NOTHING, OverlayOptionalStat.NOTHING), goal);

		OverlayRows trip = goal.with(CanvasSection.TRIP);
		assertEquals(OverlayOptionalStat.CURRENT_KILL, trip.getRow2());
		OverlayRows loot = trip.with(CanvasSection.LOOT);
		assertEquals(OverlayOptionalStat.NET_PROFIT, loot.getRow3());

		// No empty row: the last one is replaced
		OverlayRows full = new OverlayRows(true, OverlayStat.LUCK, OverlayOptionalStat.PB, OverlayOptionalStat.TRIP_TIME);
		assertEquals(OverlayOptionalStat.NET_PROFIT, full.with(CanvasSection.LOOT).getRow3());
	}

	@Test
	public void removeFromCanvasMovesRowsUpAndTurnsOffWhenEmpty()
	{
		OverlayRows all = new OverlayRows(true, OverlayStat.KILLS_PER_HOUR, OverlayOptionalStat.CURRENT_KILL, OverlayOptionalStat.NET_PROFIT);
		OverlayRows noGoal = all.without(CanvasSection.GOAL);
		assertEquals(new OverlayRows(true, OverlayStat.CURRENT_KILL, OverlayOptionalStat.NET_PROFIT, OverlayOptionalStat.NOTHING), noGoal);

		// Both trip stats go
		OverlayRows twoTrip = new OverlayRows(true, OverlayStat.TRIP_TIME, OverlayOptionalStat.LUCK, OverlayOptionalStat.PB);
		assertEquals(Collections.singletonList(OverlayStat.LUCK), twoTrip.without(CanvasSection.TRIP).stats());

		// Nothing left: off, rows kept
		OverlayRows only = new OverlayRows(true, OverlayStat.NET_GP_PER_HOUR, OverlayOptionalStat.NOTHING, OverlayOptionalStat.NOTHING);
		OverlayRows off = only.without(CanvasSection.LOOT);
		assertFalse(off.isEnabled());
		assertEquals(OverlayStat.NET_GP_PER_HOUR, off.getRow1());
	}
}
