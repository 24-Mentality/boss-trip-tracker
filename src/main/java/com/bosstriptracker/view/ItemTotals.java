package com.bosstriptracker.view;

import com.bosstriptracker.model.ItemEntry;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item lines combined per item, as the panel shows them: quantities and values added up, with pending tarnished drops,
 * potion doses, charges and polished drops kept apart. Can be built up a trip at a time.
 */
final class ItemTotals
{
	static final class Line
	{
		/**
		 * The first line seen for the item, for its flags.
		 */
		final ItemEntry first;
		long quantity;
		long value;

		Line(ItemEntry first)
		{
			this.first = first;
		}
	}

	private final Map<String, Line> lines = new LinkedHashMap<>();

	static ItemTotals of(Collection<ItemEntry> entries)
	{
		ItemTotals totals = new ItemTotals();
		totals.addAll(entries);
		return totals;
	}

	void add(ItemEntry entry)
	{
		Line line = lines.computeIfAbsent(key(entry), k -> new Line(entry));
		line.quantity += entry.getQuantity();
		line.value += entry.totalValue();
	}

	void addAll(Collection<ItemEntry> entries)
	{
		for (ItemEntry entry : entries)
		{
			add(entry);
		}
	}

	void addAll(ItemTotals other)
	{
		for (Map.Entry<String, Line> e : other.lines.entrySet())
		{
			Line line = lines.computeIfAbsent(e.getKey(), k -> new Line(e.getValue().first));
			line.quantity += e.getValue().quantity;
			line.value += e.getValue().value;
		}
	}

	ItemTotals copy()
	{
		ItemTotals copy = new ItemTotals();
		copy.addAll(this);
		return copy;
	}

	Collection<Line> lines()
	{
		return lines.values();
	}

	private static String key(ItemEntry entry)
	{
		return entry.getItemId() + (entry.isPerDose() ? "d" : "") + (entry.isPending() ? "p" : "")
			+ (entry.isCharges() ? "c" + entry.getChargeItemId() : "")
			+ (entry.getPolishedFrom() > 0 ? "f" + entry.getPolishedFrom() : "");
	}
}
