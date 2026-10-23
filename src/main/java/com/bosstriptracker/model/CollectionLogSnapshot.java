package com.bosstriptracker.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * A boss's Collection Log page as it was last read: every obtained item's count and the page's counts (its kill
 * count, a raid's completions by mode, the Doom's delves). Read when the player opens the log at that boss; it fills
 * in all-time uniques RuneLite's Loot Tracker has lost (it forgets a boss a year after the last drop). Added in
 * schema 7.
 */
@Data
public class CollectionLogSnapshot
{
	/**
	 * When the page was read (epoch milliseconds).
	 */
	private long readAt;
	/**
	 * Item id to the count obtained; items not obtained aren't listed.
	 */
	private Map<Integer, Integer> items = new HashMap<>();
	/**
	 * The page's header counts by their label, e.g. "Vorkath kills" to 4340, "Theatre of Blood (Hard) completions"
	 * to 0, "Total delves" to 1446.
	 */
	private Map<String, Integer> counts = new LinkedHashMap<>();

	public int count(int itemId)
	{
		Integer count = items.get(itemId);
		return count == null ? 0 : count;
	}

	public CollectionLogSnapshot copy()
	{
		CollectionLogSnapshot copy = new CollectionLogSnapshot();
		copy.readAt = readAt;
		copy.items = new HashMap<>(items);
		copy.counts = new LinkedHashMap<>(counts);
		return copy;
	}
}
