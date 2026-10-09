package com.bosstriptracker.view;

import com.bosstriptracker.model.TripEndReason;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TripView
{
	String id;
	long startedAt;
	Long endedAt;
	TripEndReason endReason;
	long activeMs;
	/**
	 * Start of the running segment, for the live timer; null when the clock isn't running.
	 */
	Long segmentStartedAt;
	int kills;
	/**
	 * A boss's short description of the trip for the History card (e.g. "Normal · team of 4"); null to
	 * show the kill count.
	 */
	String detail;
	/**
	 * The boss-specific third cell of the profit card (for the Maggot King, Stom / Eggs).
	 */
	StatView bossStat;
	int deaths;
	boolean pet;
	long lootValue;
	long supplyCost;
	long droppedCost;
	long deathCost;
	long netProfit;
	Long averageKillMs;
	Long fastestKillMs;
	/**
	 * Fight duration of the trip's most recent kill; null if unknown.
	 */
	Long lastKillMs;
	/**
	 * How the boss's numbers are worded ("kill" or "raid", "lair" or "dream").
	 */
	@Builder.Default
	Words words = Words.KILLS;
	List<ItemView> loot;
	/**
	 * Loot grouped by the boss's drop table categories, highest value first, at most five (the smallest folded
	 * into Other, which always comes last).
	 */
	@Builder.Default
	List<LootCategory> lootCategories = Collections.emptyList();
	List<ItemView> supplies;
	List<ItemView> dropped;
	/**
	 * Supply cost split into charges, runes, potions, food and other; only non-zero categories.
	 */
	List<SupplyCategory> supplyCategories;

	public long activeMsAt(long now)
	{
		return activeMs + (segmentStartedAt != null ? Math.max(0, now - segmentStartedAt) : 0);
	}
}
