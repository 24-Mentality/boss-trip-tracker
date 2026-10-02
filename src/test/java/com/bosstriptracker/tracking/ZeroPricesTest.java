package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ZeroPricesTest
{
	private static final Map<Integer, Long> PRICES = ImmutableMap.of(ItemID.ANGLERFISH, 1_600L, ItemID.UNCUT_RUBY, 900L,
		ItemID._4DOSEPRAYERRESTORE, 8_000L, ItemID.BLOOD_SHARD, 2_000_000L, ItemID.VIAL_EMPTY, 2L);
	private static final Set<Integer> UNTRADEABLE = ImmutableSet.of(ItemID.PRIF_CRYSTAL_SHARD, ItemID.MAGGOTKINGPET);

	private final ZeroPrices.Pricing pricing = new ZeroPrices.Pricing()
	{
		@Override
		public boolean tradeable(ItemEntry entry)
		{
			return !UNTRADEABLE.contains(entry.isCharges() ? entry.getChargeItemId() : entry.getItemId());
		}

		@Override
		public long price(ItemEntry entry)
		{
			if (entry.isCharges())
			{
				return PRICES.getOrDefault(entry.getChargeItemId(), 0L);
			}
			long price = PRICES.getOrDefault(entry.getItemId(), 0L);
			return entry.isPerDose() ? price / 4 : price;
		}
	};

	@Test
	public void linesSavedWithoutPricesGetTodaysPrice()
	{
		Trip trip = new Trip();
		Kill kill = new Kill();
		kill.getLoot().add(new ItemEntry(ItemID.UNCUT_RUBY, 11, 0));
		ItemEntry tarnished = new ItemEntry(ItemID.TARNISHED_SPEAR, 1, 0);
		tarnished.setPending(true);
		kill.getLoot().add(tarnished);
		kill.getLoot().add(new ItemEntry(ItemID.MAGGOTKINGPET, 1, 0));
		trip.getKills().add(kill);
		// Recorded at 0 while prices were missing, then priced once they loaded: kept apart until repriced
		ItemEntries.merge(trip.getSupplies(), ItemID.ANGLERFISH, 2, 0, false);
		ItemEntries.merge(trip.getSupplies(), ItemID.ANGLERFISH, 3, 1_600, false);
		ItemEntries.merge(trip.getSupplies(), ItemID._4DOSEPRAYERRESTORE, 5, 0, true);
		trip.getSupplies().add(ItemEntry.charges(ItemID.BLOOD_AMULET, 500, ItemID.BLOOD_SHARD, 0, 10_000));
		trip.getSupplies().add(ItemEntry.charges(ItemID.CRYSTAL_HALBERD, 300, ItemID.PRIF_CRYSTAL_SHARD, 0, 100));
		trip.getDropped().add(new ItemEntry(ItemID.VIAL_EMPTY, 4, 0));
		trip.getDropped().add(new ItemEntry(ItemID.ANGLERFISH, 1, 0));
		assertEquals(2, trip.getSupplies().stream().filter(e -> e.getItemId() == ItemID.ANGLERFISH).count());

		int repriced = ZeroPrices.reprice(Collections.singletonList(trip), t -> t, pricing, 100);

		assertEquals(6, repriced);
		assertEquals(900, kill.getLoot().get(0).getPriceEach());
		// Tarnished drops wait for polishing; a pet is untradeable
		assertEquals(0, kill.getLoot().get(1).getPriceEach());
		assertEquals(0, kill.getLoot().get(2).getPriceEach());
		ItemEntry angler = trip.getSupplies().get(0);
		assertEquals(5, angler.getQuantity());
		assertEquals(1_600, angler.getPriceEach());
		assertEquals(4, trip.getSupplies().size());
		assertEquals(2_000, trip.getSupplies().get(1).getPriceEach());
		assertEquals(100_000, trip.getSupplies().get(2).totalValue());
		assertEquals(0, trip.getSupplies().get(3).getPriceEach());
		// The vials turn out to be junk
		assertEquals(1, trip.getDropped().size());
		assertEquals(ItemID.ANGLERFISH, trip.getDropped().get(0).getItemId());
	}
}
