package com.bosstriptracker.view;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class LifetimeView
{
	int trips;
	/**
	 * When the first tracked trip began (this plugin only knows trips since it was installed); 0 without trips.
	 */
	long trackedSince;
	int kills;
	/**
	 * Kills per loot choice, e.g. "Stomach 10 · Eggs 2"; empty for bosses without choices.
	 */
	String choiceSummary;
	int deaths;
	int pets;
	long activeMs;
	long lootValue;
	long supplyCost;
	long droppedCost;
	long deathCost;
	long netProfit;
	Long averageKillMs;
	/**
	 * All loot valued at today's GE prices; null unless enabled in config.
	 */
	Long lootValueToday;
	/**
	 * Net profit of completed trips, oldest first.
	 */
	List<Long> netPerTrip;
	/**
	 * Completed trips' day, kills and net, oldest first, matching {@link #netPerTrip}.
	 */
	@Builder.Default
	List<TripPoint> tripPoints = Collections.emptyList();
	/**
	 * Tracked kills per calendar day (only kills this plugin saw have a date).
	 */
	@Builder.Default
	Map<LocalDate, Integer> killsByDay = Collections.emptyMap();
	DrynessView dryness;
	/**
	 * Every tracked trip's loot, supplies and items left behind added together, at the prices recorded then.
	 */
	@Builder.Default
	List<ItemView> loot = Collections.emptyList();
	/**
	 * Tracked loot by category; see {@link TripView#getLootCategories()}.
	 */
	@Builder.Default
	List<LootCategory> lootCategories = Collections.emptyList();
	@Builder.Default
	List<ItemView> supplies = Collections.emptyList();
	@Builder.Default
	List<SupplyCategory> supplyCategories = Collections.emptyList();
	@Builder.Default
	List<ItemView> dropped = Collections.emptyList();
	/**
	 * Everything RuneLite's Loot Tracker has recorded for this boss, at today's prices; null without a record (or
	 * with a mode chip selected, when the record can't be split by mode).
	 */
	List<ItemView> allTimeLoot;
	long allTimeLootValue;
	/**
	 * The Loot Tracker record by category; empty when there is no record.
	 */
	@Builder.Default
	List<LootCategory> allTimeLootCategories = Collections.emptyList();
	/**
	 * When the Loot Tracker's record starts; 0 if unknown.
	 */
	long allTimeSince;
	/**
	 * Tarnished items polished; empty for bosses without them.
	 */
	List<PolishView> polish;

	/**
	 * One completed trip on the profit chart.
	 */
	@Value
	public static class TripPoint
	{
		/**
		 * The day the trip ended.
		 */
		LocalDate day;
		int kills;
		long net;
	}
}
