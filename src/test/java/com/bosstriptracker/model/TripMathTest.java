package com.bosstriptracker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class TripMathTest
{
	@Test
	public void netProfitSubtractsAllCosts()
	{
		Trip trip = sampleTrip();

		assertEquals(1_100_000, TripMath.lootValue(trip));
		assertEquals(30_000, TripMath.supplyCost(trip));
		assertEquals(5_000, TripMath.droppedCost(trip));
		assertEquals(50_000, TripMath.deathCost(trip));
		assertEquals(1_015_000, TripMath.netProfit(trip));
	}

	@Test
	public void pendingTarnishedDropsHaveNoValue()
	{
		ItemEntry pending = new ItemEntry(33679, 1, 500);
		pending.setPending(true);
		assertEquals(0, pending.totalValue());
	}

	@Test
	public void gpPerHourScalesByActiveTime()
	{
		assertEquals(2_000_000, TripMath.gpPerHour(1_000_000, 30 * 60_000));
		assertEquals(0, TripMath.gpPerHour(1_000_000, 0));
	}

	@Test
	public void averageKillIgnoresUnknownDurations()
	{
		Trip trip = new Trip();
		Kill known = new Kill();
		known.setDurationMs(100_000L);
		Kill unknown = new Kill();
		trip.setKills(Arrays.asList(known, unknown));

		assertEquals(Long.valueOf(100_000), TripMath.averageKillMs(Collections.singletonList(trip)));
		assertNull(TripMath.averageKillMs(Collections.singletonList(new Trip())));
	}

	@Test
	public void historyRoundTripsThroughJson()
	{
		AccountHistory history = new AccountHistory();
		history.setAccountHash(42);
		history.boss("maggot_king").getTrips().add(sampleTrip());

		Gson gson = new Gson();
		AccountHistory copy = gson.fromJson(gson.toJson(history), AccountHistory.class);

		assertEquals(AccountHistory.CURRENT_SCHEMA_VERSION, copy.getSchemaVersion());
		Trip trip = copy.getBosses().get("maggot_king").getTrips().get(0);
		assertEquals(1_015_000, TripMath.netProfit(trip));
		assertEquals("STOMACH", trip.getKills().get(0).getChoice());
		// Optional fields for other bosses aren't written for the Maggot King
		assertEquals(false, gson.toJson(history).contains("variant"));
	}

	@Test
	public void polishTallyAndEggPopsRoundTrip()
	{
		String json = "{\"trips\":[],\"eggPops\":[{\"eggItemId\":33665,\"at\":5,\"pet\":true}],"
			+ "\"polishOutcomes\":{\"33679\":{\"1245\":1,\"1243\":2}}}";
		BossHistory history = new Gson().fromJson(json, BossHistory.class);

		assertEquals(Integer.valueOf(2), history.getPolishOutcomes().get(33679).get(1243));
		assertEquals(33665, history.getEggPops().get(0).getEggItemId());
	}

	@Test
	public void tripsWithoutKillsDeathsOrDropsAreEmpty()
	{
		Trip trip = new Trip();
		trip.getSupplies().add(new ItemEntry(2434, 1, 2_000));
		assertEquals(true, TripMath.isEmpty(trip));

		Trip died = new Trip();
		died.getDeaths().add(new DeathRecord());
		assertEquals(false, TripMath.isEmpty(died));

		Trip dropped = new Trip();
		dropped.getDropped().add(new ItemEntry(13441, 1, 1_500));
		assertEquals(false, TripMath.isEmpty(dropped));

		assertEquals(false, TripMath.isEmpty(sampleTrip()));
	}

	@Test
	public void missingListsDefaultToEmpty()
	{
		Trip trip = new Gson().fromJson("{\"id\":\"x\",\"startedAt\":1}", Trip.class);
		assertEquals(0, trip.getKills().size());
		assertEquals(0, TripMath.netProfit(trip));
	}

	private static Trip sampleTrip()
	{
		Kill kill = new Kill();
		kill.setChoice("STOMACH");
		kill.getLoot().add(new ItemEntry(1620, 11, 100_000));
		kill.getLoot().add(new ItemEntry(33627, 1, 0));

		Trip trip = new Trip();
		trip.setId("trip");
		trip.getKills().add(kill);
		trip.getSupplies().add(new ItemEntry(13441, 10, 3_000));
		trip.getDropped().add(new ItemEntry(13441, 1, 5_000));
		DeathRecord death = new DeathRecord();
		death.setGraveMoveCost(50_000);
		trip.getDeaths().add(death);
		return trip;
	}
}
