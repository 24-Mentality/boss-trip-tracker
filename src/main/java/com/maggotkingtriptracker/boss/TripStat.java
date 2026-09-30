package com.maggotkingtriptracker.boss;

import com.maggotkingtriptracker.model.Trip;
import java.util.function.Function;
import lombok.Value;

/**
 * A boss-specific number for a trip: the third cell of the profit card, or an extra CSV column.
 */
@Value
public class TripStat
{
	String label;
	String help;
	Function<Trip, String> value;

	public String valueOf(Trip trip)
	{
		return value.apply(trip);
	}
}
