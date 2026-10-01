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
	KILLS_PER_HOUR("Kills per hour (KPH)", CanvasSection.GOAL),
	TIME_TO_GOAL("Time to goal (TTG)", CanvasSection.GOAL),
	KILLS_DONE("Goal kills done", CanvasSection.GOAL),
	KILLS_LEFT("Goal kills left", CanvasSection.GOAL),
	CURRENT_KILL("Current kill", CanvasSection.TRIP),
	TRIP_TIME("Trip time", CanvasSection.TRIP),
	KILLS("Trip kills", CanvasSection.TRIP),
	AVERAGE_KILL("Average kill", CanvasSection.TRIP),
	PB("PB (fastest this trip)", CanvasSection.TRIP),
	NET_PROFIT("Net profit", CanvasSection.LOOT),
	NET_GP_PER_HOUR("Net GP/hr", CanvasSection.LOOT),
	LUCK("Luck status", null);

	private final String label;
	/**
	 * The panel card whose right-click menu adds or removes this stat; null for the luck status, which has none.
	 */
	private final CanvasSection section;

	@Override
	public String toString()
	{
		return label;
	}
}
