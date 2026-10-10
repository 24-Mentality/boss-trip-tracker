package com.bosstriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Everything an overlay row can show. Goal stats are hidden while no goal is set, trip stats while no trip is
 * shown. The names are also the saved config values, so don't rename them.
 */
@Getter
@RequiredArgsConstructor
public enum OverlayStat
{
	KILLS_PER_HOUR("Kills per hour (KPH)"),
	TIME_TO_GOAL("Time to goal (TTG)"),
	KILLS_DONE("Goal KC done"),
	KILLS_LEFT("Goal KC left"),
	CURRENT_KILL("Current KC"),
	TRIP_TIME("Trip time"),
	KILLS("Trip KC"),
	AVERAGE_KILL("Avg KC"),
	PB("PB (fastest this trip)"),
	NET_PROFIT("Net profit"),
	NET_GP_PER_HOUR("Net GP/hr"),
	LUCK("Luck status");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
