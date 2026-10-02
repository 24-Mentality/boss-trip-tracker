package com.bosstriptracker.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.view.ItemView;
import com.google.common.collect.ImmutableList;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ItemGridTest
{
	@Test
	public void newQuantitiesKeepTheGrid()
	{
		assertTrue(ItemGrid.sameLayout(
			ImmutableList.of(item(ItemID.ANGLERFISH, 3, false), item(ItemID._4DOSEPRAYERRESTORE, 10, true)),
			ImmutableList.of(item(ItemID.ANGLERFISH, 5, false), item(ItemID._4DOSEPRAYERRESTORE, 12, true))));
	}

	@Test
	public void newOrMovedItemsNeedANewGrid()
	{
		ImmutableList<ItemView> before = ImmutableList.of(item(ItemID.ANGLERFISH, 3, false), item(ItemID.SHARK, 1, false));
		assertFalse(ItemGrid.sameLayout(before, ImmutableList.of(item(ItemID.ANGLERFISH, 3, false))));
		assertFalse(ItemGrid.sameLayout(before, ImmutableList.of(item(ItemID.SHARK, 1, false), item(ItemID.ANGLERFISH, 3, false))));
		// The same potion counted per dose is another cell
		assertFalse(ItemGrid.sameLayout(ImmutableList.of(item(ItemID._4DOSEPRAYERRESTORE, 1, false)),
			ImmutableList.of(item(ItemID._4DOSEPRAYERRESTORE, 1, true))));
	}

	private static ItemView item(int itemId, long quantity, boolean perDose)
	{
		return new ItemView(itemId, "Item " + itemId, quantity, quantity * 1_000, perDose, false, false, null, 0, null, null);
	}
}
