package com.bosstriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The overlay's trip row.
 */
@Getter
@RequiredArgsConstructor
public enum OverlayTripStat
{
	CURRENT_KILL("Current kill"),
	TRIP_TIME("Trip time"),
	KILLS("Trip kills"),
	AVERAGE_KILL("Average kill"),
	PB("PB (fastest this trip)");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
