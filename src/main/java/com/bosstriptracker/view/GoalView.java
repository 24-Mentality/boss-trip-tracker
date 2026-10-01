package com.bosstriptracker.view;

import lombok.Value;

@Value
public class GoalView
{
	int target;
	int done;
	long activeMs;
	/**
	 * When activeMs was read, so the panel can keep the clock running while logged in.
	 */
	long asOf;
	boolean running;
	/**
	 * When the count started: when the goal was set or reset, or a trip's start.
	 */
	long startedAt;

	public long activeMsAt(long now)
	{
		return activeMs + (running ? Math.max(0, now - asOf) : 0);
	}

	public int getRemaining()
	{
		return Math.max(0, target - done);
	}

	/**
	 * @return kills per hour of fighting time, or 0 until there is a kill and at least a minute of time
	 */
	public double killsPerHourAt(long now)
	{
		long ms = activeMsAt(now);
		return ms >= 60_000 && done > 0 ? done * 3_600_000.0 / ms : 0;
	}

	/**
	 * @return fighting time left to reach the goal at the current pace, 0 when it's done, or null when unknown
	 */
	public Long msToGoalAt(long now)
	{
		if (getRemaining() == 0)
		{
			return 0L;
		}
		double killsPerHour = killsPerHourAt(now);
		return killsPerHour > 0 ? (long) (getRemaining() / killsPerHour * 3_600_000) : null;
	}
}
