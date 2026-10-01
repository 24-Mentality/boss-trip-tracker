package com.bosstriptracker.ui;

import static org.junit.Assert.assertEquals;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.view.TripView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class GoalStartTest
{
	private static final long NOW = 1_700_000_000_000L;
	private static final long HOUR = 3_600_000L;

	@Test
	public void onATripTheTripComesFirstThenNowThenRecentTrips()
	{
		List<GoalStart> options = GoalStart.options(state(PanelState.Status.IN_TRIP, trip(NOW - HOUR, 3),
			trips(NOW - 3 * HOUR, NOW - 5 * HOUR, NOW - 13 * HOUR)), NOW, false);

		assertEquals(4, options.size());
		assertEquals(GoalStart.Kind.TRIP, options.get(0).getKind());
		assertEquals("This trip (3 kills so far)", options.get(0).getLabel());
		assertEquals(Long.valueOf(NOW - HOUR), options.get(0).getStartedAt());
		assertEquals(GoalStart.Kind.NOW, options.get(1).getKind());
		// The trip from 13 hours ago is too old
		assertEquals(Long.valueOf(NOW - 3 * HOUR), options.get(2).getStartedAt());
		assertEquals(Long.valueOf(NOW - 5 * HOUR), options.get(3).getStartedAt());
	}

	@Test
	public void idleShowsNoThisTripAndARunningGoalOffersKeep()
	{
		// When idle the panel shows the last trip; it mustn't be offered as "this trip"
		List<GoalStart> options = GoalStart.options(state(PanelState.Status.IDLE, trip(NOW - 2 * HOUR, 7),
			trips(NOW - 2 * HOUR)), NOW, true);

		assertEquals(3, options.size());
		assertEquals(GoalStart.Kind.KEEP, options.get(0).getKind());
		assertEquals(GoalStart.Kind.NOW, options.get(1).getKind());
		assertEquals(GoalStart.Kind.TRIP, options.get(2).getKind());
		assertEquals("Trip at " + UiFormat.dateTime(NOW - 2 * HOUR) + " (7 kills) and later", options.get(2).getLabel());
	}

	@Test
	public void recentTripsAreCapped()
	{
		long[] starts = new long[10];
		for (int i = 0; i < starts.length; i++)
		{
			starts[i] = NOW - (i + 1) * HOUR;
		}
		List<GoalStart> options = GoalStart.options(state(PanelState.Status.IDLE, null, trips(starts)), NOW, false);
		assertEquals(1 + GoalStart.MAX_RECENT, options.size());
	}

	private static List<TripView> trips(long... startedAt)
	{
		List<TripView> trips = new ArrayList<>();
		for (long start : startedAt)
		{
			trips.add(trip(start, 7));
		}
		return trips;
	}

	private static TripView trip(long startedAt, int kills)
	{
		return TripView.builder()
			.id("t" + startedAt)
			.startedAt(startedAt)
			.kills(kills)
			.loot(Collections.emptyList())
			.supplies(Collections.emptyList())
			.dropped(Collections.emptyList())
			.supplyCategories(Collections.emptyList())
			.build();
	}

	private static PanelState state(PanelState.Status status, TripView current, List<TripView> history)
	{
		return PanelState.builder()
			.boss(new MaggotKingBoss())
			.status(status)
			.currentTrip(current)
			.history(history)
			.build();
	}
}
