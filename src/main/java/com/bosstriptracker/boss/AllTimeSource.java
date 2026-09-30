package com.bosstriptracker.boss;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import lombok.Value;

/**
 * Where RuneLite's core plugins keep all-time records for a boss in the RS profile config.
 */
@Value
public class AllTimeSource
{
	/**
	 * Key in the Loot Tracker's "loottracker" group, e.g. "drops_NPC_Maggot King".
	 */
	String lootTrackerKey;
	/**
	 * Key in Chat Commands' "killcount" group, e.g. "maggot king"; null if none.
	 */
	String killCountKey;
	/**
	 * The variant these records belong to, or null for all of the boss.
	 */
	String variant;
	/**
	 * For one loot record shared by several modes (the Theatre of Blood's): each mode that counts for luck, to its
	 * Chat Commands kill-count key. Its kill counts replace the record's kill total, so modes without uniques (Entry)
	 * are left out, and each mode is expected at its own rate. The record then only shows under All. Empty otherwise.
	 */
	Map<String, String> variantKillCountKeys;

	public AllTimeSource(String lootTrackerKey, String killCountKey, String variant)
	{
		this(lootTrackerKey, killCountKey, variant, ImmutableMap.of());
	}

	public AllTimeSource(String lootTrackerKey, String killCountKey, String variant, Map<String, String> variantKillCountKeys)
	{
		this.lootTrackerKey = lootTrackerKey;
		this.killCountKey = killCountKey;
		this.variant = variant;
		this.variantKillCountKeys = ImmutableMap.copyOf(variantKillCountKeys);
	}
}
