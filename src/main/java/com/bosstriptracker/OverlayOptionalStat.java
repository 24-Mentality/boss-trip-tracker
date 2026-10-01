package com.bosstriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The second and third overlay rows: any {@link OverlayStat}, or nothing. Same names as OverlayStat, so a stat can
 * be moved between rows in the config.
 */
@Getter
@RequiredArgsConstructor
public enum OverlayOptionalStat
{
	NOTHING(null),
	KILLS_PER_HOUR(OverlayStat.KILLS_PER_HOUR),
	TIME_TO_GOAL(OverlayStat.TIME_TO_GOAL),
	KILLS_DONE(OverlayStat.KILLS_DONE),
	KILLS_LEFT(OverlayStat.KILLS_LEFT),
	CURRENT_KILL(OverlayStat.CURRENT_KILL),
	TRIP_TIME(OverlayStat.TRIP_TIME),
	KILLS(OverlayStat.KILLS),
	AVERAGE_KILL(OverlayStat.AVERAGE_KILL),
	PB(OverlayStat.PB),
	NET_PROFIT(OverlayStat.NET_PROFIT),
	NET_GP_PER_HOUR(OverlayStat.NET_GP_PER_HOUR),
	LUCK(OverlayStat.LUCK);

	/**
	 * Null for NOTHING.
	 */
	private final OverlayStat stat;

	public static OverlayOptionalStat of(OverlayStat stat)
	{
		return stat == null ? NOTHING : valueOf(stat.name());
	}

	@Override
	public String toString()
	{
		return stat == null ? "Nothing" : stat.getLabel();
	}
}
