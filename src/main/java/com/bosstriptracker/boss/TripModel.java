package com.bosstriptracker.boss;

/**
 * How a boss's trips are shaped.
 */
public enum TripModel
{
	/**
	 * Several kills inside an area or instance per trip, from entering it until leaving (Maggot King, Nightmare).
	 */
	INSTANCE_KILLS,
	/**
	 * One raid per trip, from entering until leaving the raid (Theatre of Blood). A completed raid is one kill, with
	 * its loot claimable after the trip ends; the clock doesn't pause while idle, since time between rooms is part of
	 * the raid.
	 */
	ONE_RAID,
}
