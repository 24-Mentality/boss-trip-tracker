package com.bosstriptracker.model;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import org.junit.Test;

public class KillGoalTest
{
	@Test
	public void startingAtATripCreditsThatTripsFightingTimeAndLater()
	{
		Trip earlier = trip(0, 600_000);
		Trip chosen = trip(3_600_000, 900_000);
		Trip later = trip(7_200_000, 300_000);

		KillGoal goal = KillGoal.startingAt(50, chosen.getStartedAt(), Arrays.asList(earlier, chosen, later));

		assertEquals(50, goal.getTarget());
		assertEquals(chosen.getStartedAt(), goal.getStartedAt());
		assertEquals(1_200_000, goal.getActiveMs());
	}

	@Test
	public void startingNowCreditsNothing()
	{
		Trip open = trip(0, 600_000);
		KillGoal goal = KillGoal.startingAt(50, 1_000_000, Arrays.asList(open));
		assertEquals(0, goal.getActiveMs());
	}

	@Test
	public void runningSegmentOfABackdatedGoalCountsInFull()
	{
		Trip open = trip(0, 600_000);
		// A segment running since the trip's second pause ended
		TripClock.start(open, 900_000);
		KillGoal goal = KillGoal.startingAt(50, open.getStartedAt(), Arrays.asList(open));

		TripClock.stop(open, goal, 1_200_000);

		assertEquals(900_000, open.getActiveMs());
		assertEquals(900_000, goal.getActiveMs());
	}

	private static Trip trip(long startedAt, long activeMs)
	{
		Trip trip = new Trip();
		trip.setStartedAt(startedAt);
		trip.setActiveMs(activeMs);
		return trip;
	}
}
