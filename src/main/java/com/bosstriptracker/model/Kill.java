package com.bosstriptracker.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class Kill
{
	/**
	 * Kill count from the game's kill-count message; null if the message was missed. For the Theatre of Blood, the
	 * Normal and Hard Mode completions together (the raids that can give purples); null for Entry Mode.
	 */
	private Integer killCount;
	private long endedAt;
	/**
	 * Fight duration reported by the game, in milliseconds; null if unknown.
	 */
	private Long durationMs;
	/**
	 * Key of the loot choice made (e.g. "STOMACH" for the Maggot King's Open-stomach); null if the boss has none.
	 */
	private String choice;
	/**
	 * Boss variant id (e.g. Theatre of Blood Hard); null for bosses without variants.
	 */
	private String variant;
	/**
	 * Party size at the start of the kill; null if unknown or the boss is solo-only.
	 */
	private Integer partySize;
	private List<ItemEntry> loot = new ArrayList<>();
	/**
	 * Uniques anyone in the raid received (Theatre of Blood purples, yours included), from the game's broadcast.
	 * Item ids only: who received them is never stored. Added in schema 4.
	 */
	private List<Integer> teamUniques = new ArrayList<>();
	private boolean pet;

	public Kill copy()
	{
		Kill copy = new Kill();
		copy.killCount = killCount;
		copy.endedAt = endedAt;
		copy.durationMs = durationMs;
		copy.choice = choice;
		copy.variant = variant;
		copy.partySize = partySize;
		copy.loot = copies(loot);
		copy.teamUniques = teamUniques == null ? new ArrayList<>() : new ArrayList<>(teamUniques);
		copy.pet = pet;
		return copy;
	}

	static List<ItemEntry> copies(List<ItemEntry> entries)
	{
		List<ItemEntry> copies = new ArrayList<>();
		if (entries != null)
		{
			for (ItemEntry entry : entries)
			{
				copies.add(entry.copy());
			}
		}
		return copies;
	}
}
