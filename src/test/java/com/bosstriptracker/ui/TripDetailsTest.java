package com.bosstriptracker.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.view.ItemView;
import com.bosstriptracker.view.SupplyCategory;
import com.bosstriptracker.view.TripView;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class TripDetailsTest
{
	@Test
	public void newQuantitiesUpdateInPlace()
	{
		// Boxes start closed, so no icons are loaded
		TripDetails details = new TripDetails(null, new SectionStates("", saved -> { }), "trip", trip(3, false));

		assertTrue(details.update(trip(5, false)));
		// A Dropped box appearing needs new details
		assertFalse(details.update(trip(5, true)));
	}

	private static TripView trip(long anglerfish, boolean dropped)
	{
		List<ItemView> supplies = ImmutableList.of(new ItemView(ItemID.ANGLERFISH, "Anglerfish", anglerfish,
			anglerfish * 1_600, false, false, false, null, 0, null, null));
		return TripView.builder()
			.id("trip")
			.kills(1)
			.loot(Collections.emptyList())
			.supplies(supplies)
			.supplyCost(anglerfish * 1_600)
			.supplyCategories(ImmutableList.of(new SupplyCategory("Food", anglerfish * 1_600)))
			.dropped(dropped ? supplies : Collections.emptyList())
			.build();
	}
}
