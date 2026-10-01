package com.bosstriptracker.tracking;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Where one kill's loot came from, so the same items aren't counted twice:
 * <ul>
 * <li>the inventory fallback, used when no Loot Tracker event came in time, is replaced if the event comes
 * later</li>
 * <li>ground overflow picked up only adds what the Loot Tracker event didn't already list. The event can list
 * items that landed on the floor (they never reached the inventory); at the Maggot King it didn't (2026-09-27,
 * three Stymphike tartare on the floor were left out), so both cases are handled.</li>
 * </ul>
 */
class KillLootSources
{
	private Map<Integer, Long> fallback = Collections.emptyMap();
	private final Map<Integer, Long> eventItems = new HashMap<>();
	/**
	 * Gained in the inventory after the corpse click (not picked up from the floor).
	 */
	private final Map<Integer, Long> inventoryGains = new HashMap<>();
	/**
	 * Ground pickups already matched against event items that didn't reach the inventory.
	 */
	private final Map<Integer, Long> pickupsCovered = new HashMap<>();

	void clear()
	{
		fallback = Collections.emptyMap();
		eventItems.clear();
		inventoryGains.clear();
		pickupsCovered.clear();
	}

	/**
	 * The inventory gains were recorded as the kill's loot.
	 */
	void fallbackUsed(Map<Integer, Long> gains)
	{
		fallback = new HashMap<>(gains);
	}

	/**
	 * The Loot Tracker reported the kill's loot.
	 *
	 * @return loot recorded from the inventory fallback, to take back off the kill (empty if none)
	 */
	Map<Integer, Long> eventReceived(Map<Integer, Long> items)
	{
		items.forEach((id, q) -> eventItems.merge(id, q, Long::sum));
		Map<Integer, Long> replaced = fallback;
		fallback = Collections.emptyMap();
		return replaced;
	}

	boolean usedFallback(int itemId)
	{
		return fallback.containsKey(itemId);
	}

	void inventoryGained(int itemId, long quantity)
	{
		inventoryGains.merge(itemId, quantity, Long::sum);
	}

	/**
	 * Overflow picked up from the floor.
	 *
	 * @return how much of it is new loot: what goes beyond the event's items that never reached the inventory
	 */
	long overflowPickedUp(int itemId, long quantity)
	{
		long onFloor = eventItems.getOrDefault(itemId, 0L) - inventoryGains.getOrDefault(itemId, 0L)
			- pickupsCovered.getOrDefault(itemId, 0L);
		long covered = Math.max(0, Math.min(onFloor, quantity));
		if (covered > 0)
		{
			pickupsCovered.merge(itemId, covered, Long::sum);
		}
		return quantity - covered;
	}
}
