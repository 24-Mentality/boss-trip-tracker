package com.bosstriptracker.tracking;

/**
 * You've left a boss's area only after {@link #TICKS} ticks in a row outside it, so one odd tick between rooms
 * doesn't end a trip. The trip then ends from the first of those ticks.
 */
class LeaveDelay
{
	static final int TICKS = 3;

	private int ticks;
	private long since;

	/**
	 * A tick inside the area.
	 */
	void inside()
	{
		ticks = 0;
	}

	/**
	 * A tick outside the area.
	 *
	 * @param now  this tick's time
	 * @param dead leave at once: a death always takes you out
	 * @return when you left (the first tick outside), or null while you may still be inside
	 */
	Long outside(long now, boolean dead)
	{
		if (ticks++ == 0)
		{
			since = now;
		}
		if (ticks >= TICKS || dead)
		{
			ticks = 0;
			return since;
		}
		return null;
	}
}
