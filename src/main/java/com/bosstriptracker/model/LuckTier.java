package com.bosstriptracker.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Five luck tiers from a luck percentile (see {@link DropOdds#luckPercentile}), symmetric around 50%:
 * 90% and up / 65% and up / between / 35% and down / 10% and down.
 */
@Getter
@RequiredArgsConstructor
public enum LuckTier
{
	LUCKY_AS_RUCK("LUCKY AS RUCK"),
	LUCKY("Lucky"),
	ON_RATE("On Rate"),
	DRY("Dry"),
	DRY_AS_RUCK("DRY AS RUCK");

	/**
	 * Distance from 50% at which a tier starts: 0.15 gives 65% / 35%, 0.40 gives 90% / 10%.
	 */
	public static final double LUCKY_DISTANCE = 0.15;
	public static final double RUCK_DISTANCE = 0.40;

	private final String label;

	/**
	 * @param percentile share of players with the same kills who have fewer drops (0.5 is average)
	 */
	public static LuckTier of(double percentile)
	{
		// Rounding avoids floating point putting an exact boundary on the wrong side
		double distance = Math.round((percentile - 0.5) * 1_000_000) / 1_000_000.0;
		if (distance >= RUCK_DISTANCE)
		{
			return LUCKY_AS_RUCK;
		}
		if (distance >= LUCKY_DISTANCE)
		{
			return LUCKY;
		}
		if (distance <= -RUCK_DISTANCE)
		{
			return DRY_AS_RUCK;
		}
		if (distance <= -LUCKY_DISTANCE)
		{
			return DRY;
		}
		return ON_RATE;
	}
}
