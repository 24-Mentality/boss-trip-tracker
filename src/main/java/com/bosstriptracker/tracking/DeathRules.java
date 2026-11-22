package com.bosstriptracker.tracking;

/**
 * Which death signals count. The game reports one death twice: the ActorDeath event, then "Oh dear, you are dead!"
 * some ticks later (7 in the Doom of Mokhaiotl logs of 2026-10-23 and 2026-11-22), as you respawn.
 */
final class DeathRules
{
	private DeathRules()
	{
	}

	/**
	 * Where you respawn inside the boss's area (the Doom of Mokhaiotl's lobby), the first signal ends the trip there
	 * and then, so the second finds no trip. It must not count: it would mark you dead with nothing to clear the mark
	 * (only entering or leaving the area does), and the next run's supplies and charges would all be ignored.
	 *
	 * @param tripOpen       a trip is in progress
	 * @param respawnsInside dying puts you back inside the boss's area
	 * @return whether a death signal seen in the area, while not already marked dead, is a death to act on
	 */
	static boolean isNewDeath(boolean tripOpen, boolean respawnsInside)
	{
		return tripOpen || !respawnsInside;
	}
}
