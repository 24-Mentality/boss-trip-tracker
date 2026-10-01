package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class KillLootSourcesTest
{
	private final KillLootSources sources = new KillLootSources();

	@Test
	public void lateLootEventReplacesTheFallback()
	{
		sources.fallbackUsed(ImmutableMap.of(ItemID.UNCUT_RUBY, 11L));

		Map<Integer, Long> replaced = sources.eventReceived(ImmutableMap.of(ItemID.UNCUT_RUBY, 11L));

		assertEquals(ImmutableMap.of(ItemID.UNCUT_RUBY, 11L), replaced);
		// Only once
		assertTrue(sources.eventReceived(ImmutableMap.of()).isEmpty());
	}

	@Test
	public void eventWithoutFallbackReplacesNothing()
	{
		assertTrue(sources.eventReceived(ImmutableMap.of(ItemID.UNCUT_RUBY, 11L)).isEmpty());
	}

	/**
	 * Maggot King, 2026-09-27: with a full inventory, 3 Stymphike tartare landed on the floor and the Loot Tracker
	 * event didn't list them.
	 */
	@Test
	public void overflowTheEventLeftOutIsLoot()
	{
		sources.inventoryGained(ItemID.DULL_ZAROSIAN_MEDAL, 1);
		sources.eventReceived(ImmutableMap.of(ItemID.DULL_ZAROSIAN_MEDAL, 1L));

		assertEquals(3, sources.overflowPickedUp(ItemID.STYMPHIKE_TARTARE, 3));
	}

	@Test
	public void overflowTheEventListedIsNotCountedTwice()
	{
		// 5 listed, 2 reached the inventory, so 3 were on the floor
		sources.inventoryGained(ItemID.STYMPHIKE_TARTARE, 2);
		sources.eventReceived(ImmutableMap.of(ItemID.STYMPHIKE_TARTARE, 5L));

		assertEquals(0, sources.overflowPickedUp(ItemID.STYMPHIKE_TARTARE, 2));
		assertEquals(0, sources.overflowPickedUp(ItemID.STYMPHIKE_TARTARE, 1));
		// More than the event listed
		assertEquals(1, sources.overflowPickedUp(ItemID.STYMPHIKE_TARTARE, 1));
	}
}
