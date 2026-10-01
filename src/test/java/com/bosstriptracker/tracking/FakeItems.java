package com.bosstriptracker.tracking;

import com.bosstriptracker.pricing.ItemInfo;
import com.bosstriptracker.pricing.PriceService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Item facts for tests: names, prices (1,000 gp unless set), and which items are stackable, equipable or food.
 * Potions are recognised from their names, e.g. "Prayer potion(3)".
 */
class FakeItems implements ItemInfo
{
	private static final Pattern DOSE_NAME = Pattern.compile("^(.+)\\((\\d)\\)$");

	final Map<Integer, String> names = new HashMap<>();
	final Map<Integer, Long> prices = new HashMap<>();
	final Set<Integer> stackable = new HashSet<>();
	final Set<Integer> equipable = new HashSet<>();
	final Set<Integer> food = new HashSet<>();
	boolean pricesLoaded = true;

	FakeItems item(int itemId, String name, long price)
	{
		names.put(itemId, name);
		prices.put(itemId, price);
		return this;
	}

	FakeItems gear(int itemId, String name, long price)
	{
		equipable.add(itemId);
		return item(itemId, name, price);
	}

	FakeItems ammo(int itemId, String name, long price)
	{
		equipable.add(itemId);
		stackable.add(itemId);
		return item(itemId, name, price);
	}

	FakeItems food(int itemId, String name, long price)
	{
		food.add(itemId);
		return item(itemId, name, price);
	}

	@Override
	public long price(int itemId)
	{
		return pricesLoaded ? prices.getOrDefault(itemId, 1_000L) : 0;
	}

	@Override
	public boolean pricesLoaded()
	{
		return pricesLoaded;
	}

	@Override
	public String name(int itemId)
	{
		return names.getOrDefault(itemId, "Item " + itemId);
	}

	@Override
	public PriceService.DoseInfo doseInfo(int itemId)
	{
		Matcher m = DOSE_NAME.matcher(name(itemId));
		return m.matches() ? new PriceService.DoseInfo(m.group(1), Integer.parseInt(m.group(2))) : null;
	}

	@Override
	public PriceService.FullDose fullDose(String family, int seenItemId, int seenDoses)
	{
		PriceService.FullDose best = new PriceService.FullDose(seenItemId, seenDoses);
		for (Map.Entry<Integer, String> e : names.entrySet())
		{
			PriceService.DoseInfo dose = doseInfo(e.getKey());
			if (dose != null && dose.getFamily().equals(family) && dose.getDoses() > best.getDoses())
			{
				best = new PriceService.FullDose(e.getKey(), dose.getDoses());
			}
		}
		return best;
	}

	@Override
	public boolean isEquipable(int itemId)
	{
		return equipable.contains(itemId);
	}

	@Override
	public boolean isStackable(int itemId)
	{
		return stackable.contains(itemId);
	}

	@Override
	public boolean isFood(int itemId)
	{
		return food.contains(itemId);
	}
}
