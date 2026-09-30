package com.maggotkingtriptracker.boss;

import lombok.Value;

/**
 * A menu option on the loot source (e.g. the Maggot King's corpse) that decides what the kill drops.
 */
@Value
public class LootChoice
{
	/**
	 * Menu option text, compared ignoring case.
	 */
	String option;
	/**
	 * Stored on the kill. Never change it: saved history uses it.
	 */
	String key;
	/**
	 * Short name for summaries, e.g. "Stomach".
	 */
	String label;
	/**
	 * Uniques and the kill pet can only come from this choice, so it counts toward luck and dryness.
	 */
	boolean countsForLuck;
}
