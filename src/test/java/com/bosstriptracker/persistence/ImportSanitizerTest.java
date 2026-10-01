package com.bosstriptracker.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.model.TripMath;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

public class ImportSanitizerTest
{
	private final Gson gson = new Gson();

	@Test
	public void onlyHistoriesAreAccepted()
	{
		assertTrue(ImportSanitizer.isHistory(gson.fromJson("{\"schemaVersion\": 1, \"trips\": []}", JsonObject.class)));
		assertTrue(ImportSanitizer.isHistory(gson.fromJson("{\"bosses\": {}}", JsonObject.class)));
		assertFalse(ImportSanitizer.isHistory(gson.fromJson("{\"name\": \"something else\"}", JsonObject.class)));
		assertFalse(ImportSanitizer.isHistory(null));
	}

	@Test
	public void invalidEntriesAreLeftOutAndNumbersClamped()
	{
		String json = "{\"schemaVersion\": 5, \"bosses\": {"
			+ "\"" + MaggotKingBoss.ID + "\": {\"trips\": ["
			// A trip whose kills are null, with a broken kill, item lines and a death
			+ "{\"id\": \"a\", \"startedAt\": 1000, \"endedAt\": 61000, \"activeMs\": 999999999, \"kills\": null,"
			+ " \"supplies\": [{\"itemId\": 13441, \"quantity\": 2, \"priceEach\": 1600}, {\"itemId\": 0, \"quantity\": 1},"
			+ " {\"itemId\": 13441, \"quantity\": -5}, null, {\"itemId\": 385, \"quantity\": 1, \"priceEach\": 99999999999}],"
			+ " \"deaths\": [{\"at\": 5, \"reclaimFee\": -10}, null]},"
			+ "{\"id\": \"b\", \"startedAt\": 1000, \"endedAt\": 2000, \"kills\": [null, {\"killCount\": -3, \"durationMs\": -1,"
			+ " \"partySize\": 500, \"loot\": null, \"teamUniques\": null}]},"
			// No id; ends before it starts
			+ "{\"startedAt\": 1000}, {\"id\": \"c\", \"startedAt\": 5000, \"endedAt\": 1000}, null],"
			+ " \"eggPops\": [null, {\"eggItemId\": 33665, \"at\": 7}]},"
			+ "\"some_future_boss\": {\"trips\": []}}}";
		AccountHistory history = HistoryCodec.decode(gson, json).getHistory();

		int leftOut = ImportSanitizer.sanitize(history, ImmutableSet.of(MaggotKingBoss.ID));

		// The unknown boss, 3 trips, 1 kill, 3 supply lines, 1 death and 1 egg pop
		assertEquals(10, leftOut);
		assertEquals(1, history.getBosses().size());
		BossHistory boss = history.getBosses().get(MaggotKingBoss.ID);
		assertEquals(2, boss.getTrips().size());
		assertEquals(1, boss.getEggPops().size());

		Trip a = boss.getTrips().get(0);
		assertTrue(a.getKills().isEmpty());
		assertEquals(60_000, a.getActiveMs());
		assertEquals(2, a.getSupplies().size());
		assertEquals(Integer.MAX_VALUE, a.getSupplies().get(1).getPriceEach());
		assertEquals(0, a.getDeaths().get(0).getReclaimFee());
		// Every total can be worked out without an error
		TripMath.netProfit(a);

		Kill kill = boss.getTrips().get(1).getKills().get(0);
		assertNull(kill.getKillCount());
		assertNull(kill.getDurationMs());
		assertNull(kill.getPartySize());
		assertTrue(kill.getLoot().isEmpty());
		assertTrue(kill.getTeamUniques().isEmpty());
	}
}
