package com.maggotkingtriptracker.tracking;

import static org.junit.Assert.assertEquals;
import com.maggotkingtriptracker.model.ItemEntry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ItemEntriesTest
{
	@Test
	public void mergesSameItemWithWeightedPrice()
	{
		List<ItemEntry> entries = new ArrayList<>();
		ItemEntries.merge(entries, 13441, 2, 1_000, false);
		ItemEntries.merge(entries, 13441, 2, 1_200, false);

		assertEquals(1, entries.size());
		assertEquals(4, entries.get(0).getQuantity());
		assertEquals(1_100, entries.get(0).getPriceEach());
	}

	@Test
	public void keepsDoseAndItemLinesSeparate()
	{
		List<ItemEntry> entries = new ArrayList<>();
		ItemEntries.merge(entries, 2434, 3, 2_000, true);
		ItemEntries.merge(entries, 2434, 1, 8_000, false);

		assertEquals(2, entries.size());
	}

	@Test
	public void chargeLinesAreValuedPerCharge()
	{
		List<ItemEntry> entries = new ArrayList<>();
		// 312 blood fury charges with a blood shard at 1.3M and 10,000 charges per shard
		ItemEntries.merge(entries, ItemEntry.charges(24780, 300, 24777, 1_300_000, 10_000));
		ItemEntries.merge(entries, ItemEntry.charges(24780, 12, 24777, 1_300_000, 10_000));

		assertEquals(1, entries.size());
		assertEquals(312, entries.get(0).getQuantity());
		assertEquals(40_560, entries.get(0).totalValue());
	}

	@Test
	public void chargeLinesStaySeparateFromItemLines()
	{
		List<ItemEntry> entries = new ArrayList<>();
		ItemEntries.merge(entries, 20714, 1, 500_000, false);
		ItemEntries.merge(entries, ItemEntry.charges(20714, 40, 28931, 100, 20));
		ItemEntries.merge(entries, ItemEntry.charges(20714, 20, 20718, 60, 20));

		assertEquals(3, entries.size());
		assertEquals(200, entries.get(1).totalValue());
		assertEquals(60, entries.get(2).totalValue());
	}

	@Test
	public void ignoresZeroQuantity()
	{
		List<ItemEntry> entries = new ArrayList<>();
		ItemEntries.merge(entries, 2434, 0, 2_000, true);
		assertEquals(0, entries.size());
	}
}
