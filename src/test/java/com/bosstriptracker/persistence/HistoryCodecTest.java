package com.bosstriptracker.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.model.TripEndReason;
import com.bosstriptracker.model.TripMath;
import org.junit.Test;

public class HistoryCodecTest
{
	private final Gson gson = new Gson();

	@Test
	public void v1FileMovesUnderTheMaggotKing() throws Exception
	{
		HistoryCodec.Decoded decoded = HistoryCodec.decode(gson, Fixtures.historyV1());

		assertEquals(1, decoded.getSourceVersion());
		assertTrue(decoded.isMigrated());
		assertFalse(decoded.isNewer());

		AccountHistory history = decoded.getHistory();
		assertEquals(AccountHistory.CURRENT_SCHEMA_VERSION, history.getSchemaVersion());
		assertEquals(42, history.getAccountHash());
		assertEquals("Example", history.getLastDisplayName());
		assertEquals(1, history.getBosses().size());

		BossHistory boss = history.getBosses().get(MaggotKingBoss.ID);
		assertEquals(3, boss.getTrips().size());
		Trip first = boss.getTrips().get(0);
		assertEquals(TripEndReason.WALKED_OUT, first.getEndReason());
		assertEquals(4, first.getKills().size());
		assertEquals("STOMACH", first.getKills().get(0).getChoice());
		assertEquals("EGGS", first.getKills().get(1).getChoice());
		assertNull(first.getKills().get(0).getVariant());
		// Loot 20,001,500 (the pending tarnished spear is worth nothing yet); costs 66,000 supplies + 1,500 dropped
		assertEquals(20_001_500, TripMath.lootValue(first));
		assertEquals(66_000, TripMath.supplyCost(first));
		assertEquals(19_934_000, TripMath.netProfit(first));

		ItemEntry pending = first.getKills().get(3).getLoot().get(0);
		assertTrue(pending.isPending());
		assertEquals("pending-1", pending.getPendingId());
		assertEquals(33691, first.getKills().get(2).getLoot().get(0).getPolishedFrom());

		Trip died = boss.getTrips().get(1);
		assertEquals(50_000, TripMath.deathCost(died));

		Trip open = boss.getTrips().get(2);
		assertTrue(open.isOpen());
		assertEquals(0, open.getKills().size());

		assertEquals(150, boss.getGoal().getTarget());
		assertEquals(10_119_930, boss.getGoal().getActiveMs());
		assertEquals(2, boss.getEggPops().size());
		assertTrue(boss.getEggPops().get(1).isPet());
		assertEquals(Integer.valueOf(3), boss.getPolishOutcomes().get(33679).get(1245));
		assertEquals(Integer.valueOf(1), boss.getPolishOutcomes().get(33691).get(1662));
		assertNull(boss.getLastUniqueKc());
	}

	@Test
	public void migrationCarriesEveryV1FieldOverUnchanged() throws Exception
	{
		JsonObject original = gson.fromJson(Fixtures.historyV1(), JsonObject.class);
		JsonObject migrated = original.deepCopy();

		assertEquals(1, HistoryMigrator.migrate(migrated));

		JsonObject boss = migrated.getAsJsonObject("bosses").getAsJsonObject(MaggotKingBoss.ID);
		for (String field : new String[]{"trips", "goal", "eggPops", "polishOutcomes"})
		{
			assertEquals(field, original.get(field), boss.get(field));
			assertFalse(field + " left at the top level", migrated.has(field));
		}
		assertEquals(original.get("accountHash"), migrated.get("accountHash"));
		assertEquals(original.get("lastDisplayName"), migrated.get("lastDisplayName"));
		assertEquals(AccountHistory.CURRENT_SCHEMA_VERSION, migrated.get("schemaVersion").getAsInt());
	}

	@Test
	public void v2RoundTripsWithoutMigrating() throws Exception
	{
		AccountHistory history = HistoryCodec.decode(gson, Fixtures.historyV1()).getHistory();
		String v2 = gson.toJson(history);

		HistoryCodec.Decoded again = HistoryCodec.decode(gson, v2);
		assertEquals(AccountHistory.CURRENT_SCHEMA_VERSION, again.getSourceVersion());
		assertFalse(again.isMigrated());
		assertEquals(v2, gson.toJson(again.getHistory()));
	}

	@Test
	public void filesWithoutAVersionAreV1()
	{
		HistoryCodec.Decoded decoded = HistoryCodec.decode(gson, "{\"trips\":[{\"id\":\"a\",\"startedAt\":1,\"endedAt\":2}]}");
		assertEquals(1, decoded.getSourceVersion());
		BossHistory boss = decoded.getHistory().getBosses().get(MaggotKingBoss.ID);
		assertEquals(1, boss.getTrips().size());
		// Missing lists are filled in
		assertEquals(0, boss.getEggPops().size());
		assertEquals(0, boss.getPolishOutcomes().size());
	}

	@Test
	public void newerFilesAreLeftAlone()
	{
		HistoryCodec.Decoded decoded = HistoryCodec.decode(gson,
			"{\"schemaVersion\":99,\"bosses\":{\"maggot_king\":{\"trips\":[]}},\"somethingNew\":1}");
		assertTrue(decoded.isNewer());
		assertFalse(decoded.isMigrated());
		assertEquals(99, decoded.getHistory().getSchemaVersion());
	}

	@Test
	public void emptyFileHasNoHistory()
	{
		assertNull(HistoryCodec.decode(gson, ""));
	}

	@Test(expected = JsonParseException.class)
	public void corruptFileIsRejected()
	{
		HistoryCodec.decode(gson, "{\"trips\": [");
	}

	@Test
	public void schemaTwoFilesAreMigratedAndBackedUp()
	{
		// Schema 2: same layout, scythe lines still at the old price
		HistoryCodec.Decoded decoded = HistoryCodec.decode(gson, "{\"schemaVersion\":2,\"bosses\":{\"nightmare\":{\"trips\":"
			+ "[{\"id\":\"t\",\"startedAt\":1,\"supplies\":[{\"itemId\":22325,\"quantity\":100,\"priceEach\":110400,"
			+ "\"chargeItemId\":22446,\"chargesPerItem\":100}]}]}}}");
		assertEquals(2, decoded.getSourceVersion());
		assertTrue(decoded.isMigrated());
		assertEquals(AccountHistory.CURRENT_SCHEMA_VERSION, decoded.getHistory().getSchemaVersion());
		assertEquals(110_400, decoded.getHistory().getBosses().get("nightmare").getTrips().get(0).getSupplies().get(0)
			.getPriceEach());
	}

	@Test
	public void schemaThreeFilesGainTheRaidFieldsEmpty()
	{
		// Schema 3: a Phosani's kill and the last-unique KC, from before raids were tracked
		HistoryCodec.Decoded decoded = HistoryCodec.decode(gson, "{\"schemaVersion\":3,\"bosses\":{\"nightmare\":{"
			+ "\"lastUniqueKc\":120,\"trips\":[{\"id\":\"t\",\"startedAt\":1,\"endedAt\":2,\"endReason\":\"TELEPORT\","
			+ "\"kills\":[{\"killCount\":129,\"variant\":\"phosani\",\"loot\":[{\"itemId\":24422,\"quantity\":1,"
			+ "\"priceEach\":1}]}]}]}}}");
		assertEquals(3, decoded.getSourceVersion());
		assertTrue(decoded.isMigrated());
		assertEquals(AccountHistory.CURRENT_SCHEMA_VERSION, decoded.getHistory().getSchemaVersion());

		BossHistory nightmare = decoded.getHistory().getBosses().get("nightmare");
		assertEquals(Integer.valueOf(120), nightmare.getLastUniqueKc());
		assertNull(nightmare.getGameDryStreak());
		Kill kill = nightmare.getTrips().get(0).getKills().get(0);
		assertEquals(Integer.valueOf(129), kill.getKillCount());
		assertEquals(24422, kill.getLoot().get(0).getItemId());
		assertTrue(kill.getTeamUniques().isEmpty());
		assertEquals(TripEndReason.TELEPORT, nightmare.getTrips().get(0).getEndReason());
	}

	@Test
	public void schemaFourFilesGainTheTeamDryStreakEmpty()
	{
		HistoryCodec.Decoded decoded = HistoryCodec.decode(gson, "{\"schemaVersion\":4,\"bosses\":{\"theatre_of_blood\":{"
			+ "\"gameDryStreak\":7,\"gameDryStreakKc\":6,\"trips\":[{\"id\":\"r\",\"startedAt\":1,\"endedAt\":2,"
			+ "\"endReason\":\"COMPLETED\",\"kills\":[{\"killCount\":6,\"variant\":\"normal\",\"partySize\":4,"
			+ "\"teamUniques\":[22486]}]}]}}}");
		assertEquals(4, decoded.getSourceVersion());
		assertTrue(decoded.isMigrated());
		BossHistory tob = decoded.getHistory().getBosses().get("theatre_of_blood");
		assertEquals(Integer.valueOf(7), tob.getGameDryStreak());
		assertNull(tob.getGameTeamDryStreak());
		assertNull(tob.getGameTeamDryStreakAt());
		assertEquals(TripEndReason.COMPLETED, tob.getTrips().get(0).getEndReason());
		assertEquals(Integer.valueOf(22486), tob.getTrips().get(0).getKills().get(0).getTeamUniques().get(0));
	}
}
