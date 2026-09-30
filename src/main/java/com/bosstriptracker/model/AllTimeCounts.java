package com.bosstriptracker.model;

import java.util.Collections;
import java.util.Map;
import lombok.Value;

/**
 * A boss's all-time records from RuneLite's core plugins: the Loot Tracker's kills and drops, and the kill count
 * saved by Chat Commands.
 */
@Value
public class AllTimeCounts
{
	/**
	 * Kills recorded by the Loot Tracker (loot-dropping kills; for the Maggot King, Open-stomach).
	 */
	int lootKills;
	/**
	 * The game's kill count, if Chat Commands has seen it; null otherwise.
	 */
	Integer killCount;
	long firstRecordedAt;
	/**
	 * When the Loot Tracker last saved the record; 0 if unknown. It saves some seconds after a drop, so kills
	 * after this aren't in it yet.
	 */
	long lastRecordedAt;
	Map<Integer, Integer> drops;
	/**
	 * Kill counts per mode, for a record shared by several modes (see AllTimeSource#getVariantKillCountKeys); empty
	 * otherwise. {@link #getLootKills()} and {@link #getKillCount()} are then their total.
	 */
	Map<String, Integer> variantKillCounts;

	public AllTimeCounts(int lootKills, Integer killCount, long firstRecordedAt, long lastRecordedAt, Map<Integer, Integer> drops)
	{
		this(lootKills, killCount, firstRecordedAt, lastRecordedAt, drops, Collections.emptyMap());
	}

	public AllTimeCounts(int lootKills, Integer killCount, long firstRecordedAt, long lastRecordedAt, Map<Integer, Integer> drops,
		Map<String, Integer> variantKillCounts)
	{
		this.lootKills = lootKills;
		this.killCount = killCount;
		this.firstRecordedAt = firstRecordedAt;
		this.lastRecordedAt = lastRecordedAt;
		this.drops = drops;
		this.variantKillCounts = variantKillCounts;
	}

	public int dropped(int itemId)
	{
		return drops.getOrDefault(itemId, 0);
	}
}
