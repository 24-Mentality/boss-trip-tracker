package com.bosstriptracker.ui;

import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.view.TripView;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * Where a kill goal's count starts, as offered in the Set goal and Reset dialogs: now, the start of the trip in
 * progress, or the start of a recent trip (that trip and every one after it count).
 */
@Value
class GoalStart
{
	/**
	 * Earlier trips are offered for this long after they started.
	 */
	static final long RECENT_MS = 12 * 3_600_000L;
	static final int MAX_RECENT = 6;

	enum Kind
	{
		/**
		 * Leave a running goal's count as it is.
		 */
		KEEP,
		NOW,
		TRIP
	}

	Kind kind;
	String label;
	/**
	 * The trip's start for TRIP, otherwise null.
	 */
	Long startedAt;

	@Override
	public String toString()
	{
		return label;
	}

	/**
	 * The choices for the shown boss, the default first: keep (when a goal is running), the trip in progress, now,
	 * then recent trips from the History tab.
	 */
	static List<GoalStart> options(PanelState state, long now, boolean goalRunning)
	{
		List<GoalStart> options = new ArrayList<>();
		if (goalRunning)
		{
			options.add(new GoalStart(Kind.KEEP, "Keep the current count", null));
		}
		TripView live = state.getStatus() == PanelState.Status.IN_TRIP || state.getStatus() == PanelState.Status.AFK_PAUSED
			|| state.getStatus() == PanelState.Status.PAUSED ? state.getCurrentTrip() : null;
		if (live != null)
		{
			options.add(new GoalStart(Kind.TRIP, "This trip (" + kills(live) + " so far)", live.getStartedAt()));
		}
		options.add(new GoalStart(Kind.NOW, "Now", null));
		int recent = 0;
		for (TripView trip : state.getHistory())
		{
			if (trip.getStartedAt() < now - RECENT_MS || recent == MAX_RECENT)
			{
				break;
			}
			options.add(new GoalStart(Kind.TRIP, "Trip at " + UiFormat.dateTime(trip.getStartedAt()) + " (" + kills(trip)
				+ ") and later", trip.getStartedAt()));
			recent++;
		}
		return options;
	}

	private static String kills(TripView trip)
	{
		return trip.getWords().count(trip.getKills());
	}
}
