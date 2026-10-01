package com.bosstriptracker.tracking;

import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.pricing.ItemInfo;
import com.bosstriptracker.pricing.PriceService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns one tick's change to everything the player carries into supply lines: what was used up, dropped, converted
 * into something else or obtained. Holds no trip state, so it can be tested by replaying diagnostic logs.
 */
class SupplyAccounting
{
	/**
	 * Dropped items worth less than this each (empty vials are 2 gp) are junk, not a cost.
	 */
	static final long JUNK_PRICE = 100;

	static final String OPTION_DROP = "Drop";
	static final String OPTION_POLISH = PolishTracker.OPTION_POLISH;
	static final String OPTION_CAST = "Cast";

	private final ItemInfo items;

	SupplyAccounting(ItemInfo items)
	{
		this.items = items;
	}

	/**
	 * One tick's change, split into what left and what arrived. The steps below take matched items out of it.
	 */
	static final class Change
	{
		final Map<Integer, Long> removed = new HashMap<>();
		final Map<Integer, Long> gained = new HashMap<>();

		static Change of(Map<Integer, Long> delta)
		{
			Change change = new Change();
			for (Map.Entry<Integer, Long> e : delta.entrySet())
			{
				if (e.getValue() < 0)
				{
					change.removed.put(e.getKey(), -e.getValue());
				}
				else if (e.getValue() > 0)
				{
					change.gained.put(e.getKey(), e.getValue());
				}
			}
			return change;
		}
	}

	/**
	 * Takes items that left right after a Drop click on them out of the change.
	 *
	 * @return the dropped items and quantities
	 */
	Map<Integer, Long> takeDrops(Change change, RecentClicks clicks, int tick)
	{
		Map<Integer, Long> dropped = new HashMap<>();
		for (RecentClicks.Click click : clicks.all())
		{
			if (click.consumed || !OPTION_DROP.equals(click.option) || tick - click.tick > RecentClicks.MATCH_TICKS)
			{
				continue;
			}
			Long quantity = change.removed.remove(click.itemId);
			if (quantity != null)
			{
				click.consumed = true;
				dropped.merge(click.itemId, quantity, Long::sum);
			}
		}
		return dropped;
	}

	/**
	 * Takes items that were converted rather than used up out of the change: popping eggs, polishing (tarnished
	 * items, dull ancient medals) and casting a spell on an item (e.g. High Level Alchemy).
	 *
	 * @param convertedItems items some boss always converts, wherever it happens
	 */
	void removeConversions(Change change, RecentClicks clicks, int tick, Set<Integer> convertedItems)
	{
		change.removed.keySet().removeAll(convertedItems);
		for (RecentClicks.Click click : clicks.all())
		{
			if (tick - click.tick <= RecentClicks.MATCH_TICKS && click.itemId > 0
				&& (OPTION_POLISH.equals(click.option) || OPTION_CAST.equals(click.option)))
			{
				change.removed.remove(click.itemId);
			}
		}
	}

	/**
	 * What was used up: the items that left. Potions are counted in doses: a Prayer potion(4) becoming a Prayer
	 * potion(3) is one dose, priced from the highest-dose variant.
	 */
	List<ItemEntry> consumption(Change change)
	{
		return net(change.removed, change.gained);
	}

	/**
	 * Items gained that weren't carried before (not a potion going down a dose), counted the way
	 * {@link #consumption} counts supplies: per dose for potions.
	 */
	List<ItemEntry> acquisitions(Change change)
	{
		return net(change.gained, change.removed);
	}

	/**
	 * Dropped supplies left behind, counted as used (per dose for potions). Dropped equipment and junk never are.
	 */
	List<ItemEntry> droppedAsUsed(Map<Integer, Long> dropped)
	{
		Change change = new Change();
		for (Map.Entry<Integer, Long> e : dropped.entrySet())
		{
			int itemId = e.getKey();
			if (e.getValue() <= 0 || items.isEquipable(itemId)
				|| (items.doseInfo(itemId) == null && items.price(itemId) < JUNK_PRICE))
			{
				continue;
			}
			change.removed.put(itemId, e.getValue());
		}
		return consumption(change);
	}

	/**
	 * Lines for {@code moved}, with potions netted per dose against {@code against} (doses that went the other way).
	 */
	private List<ItemEntry> net(Map<Integer, Long> moved, Map<Integer, Long> against)
	{
		List<ItemEntry> lines = new ArrayList<>();
		// family -> [net doses, a variant id seen, its dose count]
		Map<String, long[]> doseFamilies = new HashMap<>();

		for (Map.Entry<Integer, Long> e : moved.entrySet())
		{
			int itemId = e.getKey();
			PriceService.DoseInfo dose = items.doseInfo(itemId);
			if (dose != null)
			{
				long[] family = doseFamilies.computeIfAbsent(dose.getFamily(), f -> new long[]{0, itemId, dose.getDoses()});
				family[0] += e.getValue() * dose.getDoses();
			}
			else
			{
				lines.add(new ItemEntry(itemId, e.getValue(), items.price(itemId)));
			}
		}

		for (Map.Entry<Integer, Long> e : against.entrySet())
		{
			PriceService.DoseInfo dose = items.doseInfo(e.getKey());
			if (dose != null && doseFamilies.containsKey(dose.getFamily()))
			{
				doseFamilies.get(dose.getFamily())[0] -= e.getValue() * dose.getDoses();
			}
		}

		for (Map.Entry<String, long[]> e : doseFamilies.entrySet())
		{
			long[] family = e.getValue();
			if (family[0] > 0)
			{
				PriceService.FullDose full = items.fullDose(e.getKey(), (int) family[1], (int) family[2]);
				ItemEntry entry = new ItemEntry(full.getItemId(), family[0], items.pricePerDose(full));
				entry.setPerDose(true);
				lines.add(entry);
			}
		}
		return lines;
	}
}
