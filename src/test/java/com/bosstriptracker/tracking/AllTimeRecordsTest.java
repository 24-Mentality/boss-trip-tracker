package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.bosstriptracker.model.AllTimeCounts;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class AllTimeRecordsTest
{
	@Test
	public void parsesLootTrackerRecord()
	{
		// Same shape as RuneLite's saved loottracker drops_NPC_Maggot King value
		String json = "{\"type\":\"NPC\",\"name\":\"Maggot King\",\"kills\":2630,\"first\":1785447588633,"
			+ "\"last\":1790544734203,\"drops\":[2971,9801,33634,6,33631,5,450,5443]}";
		AllTimeRecords.LootTrackerRecord record = new Gson().fromJson(json, AllTimeRecords.LootTrackerRecord.class);
		AllTimeCounts snapshot = AllTimeRecords.snapshot(record, 2636);

		assertEquals(2630, snapshot.getLootKills());
		assertEquals(Integer.valueOf(2636), snapshot.getKillCount());
		assertEquals(6, snapshot.dropped(ItemID.ELDER_VENATOR_FANG));
		assertEquals(5, snapshot.dropped(ItemID.CRIMSON_KISTEN));
		assertEquals(0, snapshot.dropped(ItemID.MAGGOTKINGPET));
		assertEquals(1790544734203L, snapshot.getLastRecordedAt());
	}

	@Test
	public void noRecordMeansNoAllTimeData()
	{
		assertNull(AllTimeRecords.snapshot(null, 100));
	}

	@Test
	public void combinesRecordsOfSeveralSources()
	{
		AllTimeCounts a = new AllTimeCounts(100, 110, 5, 50, Collections.singletonMap(ItemID.ELDER_VENATOR_FANG, 1));
		AllTimeCounts b = new AllTimeCounts(50, null, 3, 40, Collections.singletonMap(ItemID.ELDER_VENATOR_FANG, 2));
		AllTimeCounts both = AllTimeRecords.combine(a, b);

		assertEquals(150, both.getLootKills());
		assertEquals(Integer.valueOf(110), both.getKillCount());
		assertEquals(3, both.getFirstRecordedAt());
		// Saved no later than the older of the two
		assertEquals(40, both.getLastRecordedAt());
		assertEquals(3, both.dropped(ItemID.ELDER_VENATOR_FANG));
		assertEquals(a, AllTimeRecords.combine(a, null));
		assertEquals(b, AllTimeRecords.combine(null, b));
	}

	@Test
	public void theatreRecordCountsNormalAndHardRaidsOnly()
	{
		// The Theatre of Blood record from the diagnostic raid: 7 raids with loot, one of them Entry Mode
		String json = "{\"type\":\"EVENT\",\"name\":\"Theatre of Blood\",\"kills\":7,\"first\":1,\"last\":2,"
			+ "\"drops\":[1127,3,449,140]}";
		AllTimeCounts record = AllTimeRecords.snapshot(new Gson().fromJson(json, AllTimeRecords.LootTrackerRecord.class), null);
		AllTimeCounts byMode = AllTimeRecords.byMode(record, ImmutableMap.of("normal", 6, "hard", 0));

		assertEquals(6, byMode.getLootKills());
		assertEquals(Integer.valueOf(6), byMode.getKillCount());
		assertEquals(Integer.valueOf(6), byMode.getVariantKillCounts().get("normal"));
		assertEquals(3, byMode.dropped(ItemID.RUNE_PLATEBODY));
		// Without Chat Commands' counts, the record's own total stays
		assertEquals(7, AllTimeRecords.byMode(record, Collections.emptyMap()).getLootKills());
	}
}
