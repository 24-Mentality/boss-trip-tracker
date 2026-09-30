package com.maggotkingtriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class PolishResultTest
{
	@Test
	public void gearEquippedInTheSameTickIsNotTheResult()
	{
		// 2026-09-27 16:02:23: polishing a Tarnished necklace while switching to the Inquisitor's great helm.
		// The Loot Tracker reported both; only the necklace is a net gain across inventory and equipment.
		Integer result = PolishTracker.choosePolishResult(
			ImmutableSet.of(ItemID.INQUISITORS_HELM, ItemID.DIAMOND_NECKLACE),
			ImmutableSet.of(ItemID.DIAMOND_NECKLACE));

		assertEquals(Integer.valueOf(ItemID.DIAMOND_NECKLACE), result);
	}

	@Test
	public void singleNetGainWithoutLootTracker()
	{
		assertEquals(Integer.valueOf(ItemID.RUNE_SPEAR),
			PolishTracker.choosePolishResult(Collections.emptySet(), ImmutableSet.of(ItemID.RUNE_SPEAR)));
	}

	@Test
	public void ambiguousStaysPending()
	{
		assertNull(PolishTracker.choosePolishResult(Collections.emptySet(),
			ImmutableSet.of(ItemID.RUNE_SPEAR, ItemID.ADAMANT_SPEAR)));
	}
}
