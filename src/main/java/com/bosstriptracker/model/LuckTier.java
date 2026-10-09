package com.bosstriptracker.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Luck tiers from the tail you're in: on the dry side, the chance of this many uniques or fewer; on the lucky side,
 * the chance of this many or more (see {@link LuckOdds}). 10% or less is AS RUCK, 35% or less is Dry or Lucky.
 */
@Getter
@RequiredArgsConstructor
public enum LuckTier
{
	LUCKY_AS_RUCK("LUCKY AS RUCK"),
	LUCKY("Lucky"),
	ON_RATE("On Rate"),
	DRY("Dry"),
	DRY_AS_RUCK("DRY AS RUCK"),
	/**
	 * Under one unique expected, and not already in a 10% tail.
	 */
	TOO_EARLY("Too early to tell");

	public static final double RUCK_TAIL = 0.10;
	public static final double TAIL = 0.35;

	private final String label;

	/**
	 * @param atMost             chance of this many uniques or fewer
	 * @param atLeast            chance of this many or more
	 * @param expected           uniques expected
	 * @param minExpectedForRuck the AS RUCK tiers need at least this many uniques expected (the Theatre of Blood's
	 *                           numbers are approximate); 0 for none
	 */
	public static LuckTier of(double atMost, double atLeast, double expected, double minExpectedForRuck)
	{
		// Rounding avoids floating point putting an exact boundary on the wrong side
		atMost = round(atMost);
		atLeast = round(atLeast);
		boolean ruck = expected >= minExpectedForRuck;
		LuckTier tier = ON_RATE;
		if (atMost <= TAIL)
		{
			tier = atMost <= RUCK_TAIL && ruck ? DRY_AS_RUCK : DRY;
		}
		else if (atLeast <= TAIL)
		{
			tier = atLeast <= RUCK_TAIL && ruck ? LUCKY_AS_RUCK : LUCKY;
		}
		boolean farOut = atMost <= RUCK_TAIL || atLeast <= RUCK_TAIL;
		return expected < 1 && !farOut ? TOO_EARLY : tier;
	}

	private static double round(double chance)
	{
		return Math.round(chance * 1_000_000) / 1_000_000.0;
	}
}
