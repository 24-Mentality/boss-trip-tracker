package com.bosstriptracker.boss;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

/**
 * One boss as data: everything that isn't a mechanic of its own. A boss made only of this is a {@link TableBoss};
 * bosses with mechanics (a corpse choice, polishing, raid messages) add them as overrides.
 */
@Value
@Builder
public class BossData
{
	String id;
	String displayName;
	/**
	 * Item shown as the boss's icon (usually its pet).
	 */
	int iconItemId;
	/**
	 * NPC whose name the game uses in kill-count messages and Loot Tracker events.
	 */
	int nameNpcId;
	@Builder.Default
	Set<Integer> bossNpcIds = ImmutableSet.of();
	/**
	 * Modes of the boss (e.g. Phosani's and the regular Nightmare); empty for none.
	 */
	@Builder.Default
	List<BossVariant> variants = ImmutableList.of();
	/**
	 * Names the game's kill-count message uses, to the variant each is ("Phosani's Nightmare" to Phosani's); empty to
	 * use the boss NPC's name.
	 */
	@Builder.Default
	Map<String, String> killNames = ImmutableMap.of();
	/**
	 * Template regions of the boss's area (instances included).
	 */
	@Builder.Default
	Set<Integer> regions = ImmutableSet.of();
	/**
	 * Regions just outside, where a trip waits (paused) for a while after walking out.
	 */
	@Builder.Default
	Set<Integer> waitingRegions = ImmutableSet.of();
	/**
	 * Several kills in an instance per trip, or one raid per trip.
	 */
	@Builder.Default
	TripModel tripModel = TripModel.INSTANCE_KILLS;
	/**
	 * Uniques, the pet and tertiaries followed by the luck numbers, each with your chance per kill.
	 */
	@Builder.Default
	List<ExpectedDrop> drops = ImmutableList.of();
	@Builder.Default
	DropModel dropModel = DropModel.SUM_OF_UNIQUES;
	/**
	 * Where RuneLite keeps its all-time records for the boss.
	 */
	@Builder.Default
	List<AllTimeSource> allTimeSources = ImmutableList.of();
	/**
	 * Item to loot box category (the OSRS Wiki's drop table groups).
	 */
	@Builder.Default
	Map<Integer, String> lootCategories = ImmutableMap.of();
	/**
	 * "kill", or "raid" for a raid.
	 */
	@Builder.Default
	String unitNoun = "kill";
	/**
	 * Where the fight is: "lair", "dream", "Theatre".
	 */
	@Builder.Default
	String areaNoun = "area";
	/**
	 * Shown on the Trip tab before the first trip; null for the default.
	 */
	String emptyStateText;
	/**
	 * The third cell of the profit card; null for the uniques this trip.
	 */
	TripStat profitCell;
}
