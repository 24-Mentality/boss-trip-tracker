package com.maggotkingtriptracker.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class VariantFilterTest
{
	@Test
	public void allMatchesEverything()
	{
		Kill hard = kill("hard");
		assertTrue(VariantFilter.matches(hard, null));
		assertTrue(VariantFilter.matches(new Kill(), null));
		assertTrue(VariantFilter.matches(new Trip(), null));
	}

	@Test
	public void aVariantMatchesItsOwnKillsAndTheTripsHoldingThem()
	{
		Kill hard = kill("hard");
		assertTrue(VariantFilter.matches(hard, "hard"));
		assertFalse(VariantFilter.matches(hard, "normal"));
		assertFalse(VariantFilter.matches(new Kill(), "hard"));

		Trip trip = new Trip();
		trip.getKills().add(kill("normal"));
		assertFalse(VariantFilter.matches(trip, "hard"));
		trip.getKills().add(hard);
		assertTrue(VariantFilter.matches(trip, "hard"));
		assertFalse(VariantFilter.matches(new Trip(), "hard"));
	}

	private static Kill kill(String variant)
	{
		Kill kill = new Kill();
		kill.setVariant(variant);
		return kill;
	}
}
