package com.bosstriptracker.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A target number of kills, counted from when the goal was set or last reset. The start can be backdated to the
 * start of a trip, so kills already made count.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KillGoal
{
	private int target;
	private long startedAt;
	/**
	 * Fighting time since startedAt, for kills per hour.
	 */
	private long activeMs;

	/**
	 * A goal starting at {@code startedAt}, the start of one of {@code trips}: the fighting time already logged by
	 * the trips from then on is credited to it. A running segment is added when the trip clock stops.
	 */
	public static KillGoal startingAt(int target, long startedAt, List<Trip> trips)
	{
		long activeMs = 0;
		for (Trip trip : trips)
		{
			if (trip.getStartedAt() >= startedAt)
			{
				activeMs += trip.getActiveMs();
			}
		}
		return new KillGoal(target, startedAt, activeMs);
	}
}
