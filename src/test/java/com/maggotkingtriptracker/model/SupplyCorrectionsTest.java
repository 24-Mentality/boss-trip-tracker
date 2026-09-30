package com.maggotkingtriptracker.model;

import static org.junit.Assert.assertEquals;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SupplyCorrectionsTest
{
	@Test
	public void scytheLinesLoseTheExtraHundredBloodRunes()
	{
		AccountHistory history = new AccountHistory();
		Trip trip = new Trip();
		// Saved on 2026-09-28: a vial of blood at 8,300 plus 300 blood runes at 341 per 100 charges
		trip.getSupplies().add(ItemEntry.charges(ItemID.SCYTHE_OF_VITUR, 245, ItemID.VIAL_BLOOD, 8_300 + 300 * 341, 100));
		trip.getSupplies().add(ItemEntry.charges(ItemID.TUMEKENS_SHADOW, 46, ItemID.SOULRUNE, 700, 1));
		trip.getSupplies().add(new ItemEntry(ItemID.BLOODRUNE, 10, 341));
		history.boss("nightmare").getTrips().add(trip);

		assertEquals(1, SupplyCorrections.repriceScythe(history, 341));

		ItemEntry scythe = trip.getSupplies().get(0);
		assertEquals(8_300 + 200 * 341, scythe.getPriceEach());
		// 245 charges were 270,970 gp; now 187,425
		assertEquals(187_425, scythe.totalValue());
		assertEquals(700, trip.getSupplies().get(1).getPriceEach());
		assertEquals(341, trip.getSupplies().get(2).getPriceEach());
	}
}
