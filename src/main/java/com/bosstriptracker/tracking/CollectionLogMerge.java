package com.bosstriptracker.tracking;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.ExpectedDrop;
import com.bosstriptracker.model.AllTimeCounts;
import com.bosstriptracker.model.CollectionLogSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adds a boss's Collection Log page to RuneLite's all-time records. The log is the game's own count of every unique,
 * pet and tertiary the player has ever had from the boss, and of its kills, so for each drop the plugin follows it
 * takes the larger of the Loot Tracker's count and the log's, and the larger kill count. Common loot stays the Loot
 * Tracker's: the log only lists its own items, and caps stacks.
 */
public final class CollectionLogMerge
{
	private CollectionLogMerge()
	{
	}

	/**
	 * @param counts   RuneLite's records for the boss and variant, or null if the Loot Tracker has none
	 * @param snapshot the boss's Collection Log page, or null if never read
	 * @param variant  the variant shown, or null for all of the boss
	 * @return the records with the log added, or null when there is neither
	 */
	public static AllTimeCounts merge(AllTimeCounts counts, CollectionLogSnapshot snapshot, BossDefinition boss, String variant)
	{
		if (snapshot == null)
		{
			return counts;
		}
		Integer logKills = boss.collectionLogKills(snapshot.getCounts(), variant);
		Map<Integer, Integer> drops = new LinkedHashMap<>(counts == null ? Collections.emptyMap() : counts.getDrops());
		for (ExpectedDrop drop : boss.getDrops())
		{
			int fromLog = snapshot.count(drop.getItemId());
			if (fromLog > drops.getOrDefault(drop.getItemId(), 0))
			{
				drops.put(drop.getItemId(), fromLog);
			}
		}
		if (counts == null)
		{
			if (logKills == null && drops.isEmpty())
			{
				return null;
			}
			int kills = logKills == null ? 0 : logKills;
			return new AllTimeCounts(kills, logKills, 0, snapshot.getReadAt(), Collections.unmodifiableMap(drops),
				Collections.emptyMap(), snapshot.getReadAt());
		}
		int lootKills = logKills == null ? counts.getLootKills() : Math.max(counts.getLootKills(), logKills);
		Integer killCount = counts.getKillCount() == null ? logKills
			: logKills == null ? counts.getKillCount() : Integer.valueOf(Math.max(counts.getKillCount(), logKills));
		return new AllTimeCounts(lootKills, killCount, counts.getFirstRecordedAt(),
			Math.max(counts.getLastRecordedAt(), snapshot.getReadAt()), Collections.unmodifiableMap(drops),
			counts.getVariantKillCounts(), snapshot.getReadAt());
	}
}
