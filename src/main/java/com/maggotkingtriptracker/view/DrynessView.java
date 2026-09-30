package com.maggotkingtriptracker.view;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Luck and dryness for one boss, from the kills this plugin tracked and, when available, RuneLite's all-time
 * records. Rates and expected counts are worked out here so the panel doesn't need the drop tables.
 */
@Value
@Builder(toBuilder = true)
public class DrynessView
{
	/**
	 * A drop followed on the Expected / Received card.
	 */
	@Value
	public static class Drop
	{
		int itemId;
		String name;
		/**
		 * Average chance per luck kill, for "1/340" style labels.
		 */
		double rate;
		double expected;
		int received;
		/**
		 * Kill counts it was received at, oldest first; null entries mean the kill count wasn't seen.
		 * Empty for all-time figures.
		 */
		List<Integer> killCounts;
	}

	@Value
	public static class EggTier
	{
		int itemId;
		String name;
		int popped;
		int pets;
		double petRate;
	}

	/**
	 * Tracked kills that can roll uniques (for the Maggot King, Open-stomach kills).
	 */
	int luckKills;
	int killsSinceUnique;
	/**
	 * The dry streak starts from the kill count you entered for your last unique, not a tracked one.
	 */
	boolean sinceFromEnteredKc;
	/**
	 * The dry streak starts from where the game's own count ("You are on a personal dry streak of 7.", Theatre of
	 * Blood) places your last unique. That count includes Entry Mode raids.
	 */
	boolean sinceFromGameCount;
	/**
	 * Raids since anyone in the team got a unique (Theatre of Blood); null for bosses without one. Not your own dry
	 * streak: a teammate's unique resets it.
	 */
	Integer teamDryStreak;
	/**
	 * The team dry streak starts from the game's own count; otherwise it's counted from the raids this plugin
	 * tracked.
	 */
	boolean teamDryStreakFromGame;
	/**
	 * No unique has ever been received (RuneLite's all-time record included), so the dry streak is the whole kill
	 * count from Chat Commands.
	 */
	boolean sinceWholeKillCount;
	/**
	 * Longest gap between uniques whose kill count is known (tracked, or entered by you), by kill count, or the
	 * current streak if that is longer.
	 */
	int longestDryStreak;
	/**
	 * Chance of going this many kills without a unique.
	 */
	double chanceThisDry;
	/**
	 * Average chance of any unique per luck kill, for the "next unique" and progress numbers.
	 */
	double anyUniqueRate;
	int uniquesReceived;
	double expectedUniques;
	List<Drop> uniques;
	/**
	 * The pet from kills only (eggs are separate); null if the boss has no pet.
	 */
	Drop pet;
	List<EggTier> eggTiers;
	/**
	 * Chance of at least one pet from all the eggs popped so far.
	 */
	double eggPetChance;
	double eggPetExpected;
	int petsFromEggs;
	/**
	 * Highest kill count seen in tracked kills; null if none reported one.
	 */
	Integer currentKc;
	/**
	 * Kill count of the most recent unique (tracked, or entered by you); null if unknown.
	 */
	Integer lastUniqueKc;
	/**
	 * Kill count of the first tracked kill, the start of the plugin's records.
	 */
	Integer firstTrackedKc;
	/**
	 * The kill count you entered for your last unique; null if none.
	 */
	Integer enteredLastUniqueKc;
	/**
	 * All-time figures from RuneLite's Loot Tracker; null when it has no record for this account.
	 */
	AllTime allTime;

	@Value
	@Builder(toBuilder = true)
	public static class AllTime
	{
		/**
		 * Kills recorded by the Loot Tracker (loot kills only).
		 */
		int lootKills;
		Integer killCount;
		long firstRecordedAt;
		int uniquesReceived;
		double expectedUniques;
		/**
		 * Same order as the tracked uniques.
		 */
		List<Drop> uniques;
		/**
		 * Pets from kills and eggs: the larger of the Loot Tracker's count and the pets this plugin tracked.
		 * Expected includes eggs popped.
		 */
		Drop pet;
	}
}
