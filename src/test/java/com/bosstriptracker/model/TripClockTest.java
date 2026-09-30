package com.bosstriptracker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TripClockTest
{
	@Test
	public void segmentsAddUpAndPausesAreLeftOut()
	{
		Trip trip = new Trip();
		TripClock.start(trip, 0);
		TripClock.stop(trip, null, 60_000);
		// Paused for 5 minutes, then another minute
		TripClock.start(trip, 360_000);
		TripClock.stop(trip, null, 420_000);

		assertEquals(120_000, trip.getActiveMs());
		assertNull(trip.getSegmentStartedAt());
	}

	@Test
	public void idleStopIsRetroactiveToTheLastHit()
	{
		Trip trip = new Trip();
		TripClock.start(trip, 0);
		long lastHit = 50_000;
		long now = lastHit + 30_000;
		assertTrue(TripClock.idle(lastHit, now, 30_000));

		TripClock.stop(trip, null, lastHit);
		assertEquals(50_000, trip.getActiveMs());
	}

	@Test
	public void stopNeverGoesBeforeTheSegmentStarted()
	{
		Trip trip = new Trip();
		TripClock.start(trip, 100_000);
		// Last activity was before this segment (e.g. before re-entering)
		TripClock.stop(trip, null, 40_000);
		assertEquals(0, trip.getActiveMs());
	}

	@Test
	public void goalOnlyCountsTimeAfterItWasSet()
	{
		Trip trip = new Trip();
		KillGoal goal = new KillGoal(100, 30_000, 0);
		TripClock.start(trip, 0);
		assertEquals(Long.valueOf(30_000), TripClock.goalSegmentStart(goal, trip));
		TripClock.stop(trip, goal, 90_000);

		assertEquals(90_000, trip.getActiveMs());
		assertEquals(60_000, goal.getActiveMs());
		assertNull(TripClock.goalSegmentStart(goal, trip));
	}

	@Test
	public void idleNeedsTheFullThresholdAndCanBeTurnedOff()
	{
		assertFalse(TripClock.idle(0, 29_999, 30_000));
		assertTrue(TripClock.idle(0, 30_000, 30_000));
		assertFalse(TripClock.idle(0, 999_999, 0));
	}

	@Test
	public void startingTwiceKeepsTheFirstStart()
	{
		Trip trip = new Trip();
		TripClock.start(trip, 10);
		TripClock.start(trip, 20);
		assertEquals(Long.valueOf(10), trip.getSegmentStartedAt());
	}
}
