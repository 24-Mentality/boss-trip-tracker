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

	/**
	 * Two Doom of Mokhaiotl runs of 2026-11-22 (one ended by a death, one by claiming) were saved finished with the
	 * clock still running and no active time: both ended while still in the boss's area.
	 */
	@Test
	public void aFinishedTripSavedWithItsClockRunningGetsItsTime()
	{
		Trip trip = new Trip();
		trip.setStartedAt(1_795_324_725_705L);
		trip.setSegmentStartedAt(1_795_324_725_705L);
		trip.setEndedAt(1_795_325_322_193L);
		KillGoal goal = new KillGoal(100, 1_795_325_000_000L, 0);

		assertTrue(TripClock.closeFinished(trip, goal));

		assertEquals(596_488, trip.getActiveMs());
		assertNull(trip.getSegmentStartedAt());
		// The goal was set partway through the run
		assertEquals(322_193, goal.getActiveMs());
		assertFalse(TripClock.closeFinished(trip, goal));
		assertEquals(596_488, trip.getActiveMs());
	}

	@Test
	public void aTripInProgressOrAlreadyStoppedIsLeftAlone()
	{
		Trip open = new Trip();
		TripClock.start(open, 1_000);
		assertFalse(TripClock.closeFinished(open, null));
		assertEquals(Long.valueOf(1_000), open.getSegmentStartedAt());

		Trip finished = new Trip();
		TripClock.start(finished, 1_000);
		TripClock.stop(finished, null, 61_000);
		finished.setEndedAt(90_000L);
		assertFalse(TripClock.closeFinished(finished, null));
		assertEquals(60_000, finished.getActiveMs());
	}
}
