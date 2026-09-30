package com.maggotkingtriptracker.model;

/**
 * The trip's active-time clock, shared with the kill goal. Time only counts while a segment is running:
 * in the lair, not paused and not idle. The goal gets the same time, but only from when it was set.
 */
public final class TripClock
{
	private TripClock()
	{
	}

	/**
	 * Starts a segment at {@code now} unless one is already running.
	 */
	public static void start(Trip trip, long now)
	{
		if (trip.getSegmentStartedAt() == null)
		{
			trip.setSegmentStartedAt(now);
		}
	}

	/**
	 * Stops the running segment at {@code end} (never before it started) and adds its length to the trip and,
	 * for the part after the goal was set, to the goal. Does nothing if no segment is running.
	 */
	public static void stop(Trip trip, KillGoal goal, long end)
	{
		Long start = trip.getSegmentStartedAt();
		if (start == null)
		{
			return;
		}
		long stop = Math.max(start, end);
		trip.setActiveMs(trip.getActiveMs() + (stop - start));
		trip.setSegmentStartedAt(null);
		if (goal != null)
		{
			long from = Math.max(start, goal.getStartedAt());
			if (stop > from)
			{
				goal.setActiveMs(goal.getActiveMs() + (stop - from));
			}
		}
	}

	/**
	 * @return true once the player has done nothing for {@code idleMs}; never when {@code idleMs} is 0
	 */
	public static boolean idle(long lastActivityAt, long now, long idleMs)
	{
		return idleMs > 0 && now - lastActivityAt >= idleMs;
	}

	/**
	 * @return when the goal's share of the running segment started, or null if the clock isn't running
	 */
	public static Long goalSegmentStart(KillGoal goal, Trip trip)
	{
		if (goal == null || trip == null || trip.getSegmentStartedAt() == null)
		{
			return null;
		}
		return Math.max(trip.getSegmentStartedAt(), goal.getStartedAt());
	}
}
