package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.BossRegistry;
import com.bosstriptracker.boss.DoomOfMokhaiotlBoss;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.boss.NightmareBoss;
import com.bosstriptracker.boss.TheatreOfBloodBoss;
import com.bosstriptracker.boss.VorkathBoss;
import com.bosstriptracker.model.AllTimeCounts;
import com.bosstriptracker.model.CollectionLogSnapshot;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * The user's pages of 2026-10-23 against RuneLite's records.
 */
public class CollectionLogMergeTest
{
	private final BossRegistry registry = BossRegistry.standard();

	/**
	 * Vorkath: the Loot Tracker had forgotten him (a year without a drop), then recorded 2 kills tonight.
	 */
	@Test
	public void theLogRestoresWhatTheLootTrackerForgot()
	{
		BossDefinition vorkath = registry.byId(VorkathBoss.ID);
		CollectionLogSnapshot log = snapshot(1_000, ImmutableMap.of("Vorkath kills", 4_340),
			ImmutableMap.<Integer, Integer>builder().put(ItemID.VORKATHPET, 1).put(ItemID.VORKATH_HEAD, 77)
				.put(ItemID.DRAGONFIRE_VISAGE, 2).put(ItemID.SKELETAL_VISAGE, 2).put(ItemID.JAR_OF_DECAY, 2)
				.put(ItemID.DRAGONBONE_NECKLACE, 5).build());

		// No record at all: the log is the record
		AllTimeCounts fromLog = CollectionLogMerge.merge(null, log, vorkath, null);
		assertEquals(4_340, fromLog.getLootKills());
		assertEquals(Integer.valueOf(4_340), fromLog.getKillCount());
		assertEquals(5, fromLog.dropped(ItemID.DRAGONBONE_NECKLACE));
		assertEquals(2, fromLog.dropped(ItemID.DRAGONFIRE_VISAGE));
		assertEquals(1, fromLog.dropped(ItemID.VORKATHPET));
		assertEquals(2, fromLog.dropped(ItemID.JAR_OF_DECAY));
		// Heads aren't a drop the plugin follows, so common loot stays the Loot Tracker's
		assertEquals(0, fromLog.dropped(ItemID.VORKATH_HEAD));
		assertEquals(1_000, fromLog.getCollectionLogReadAt());
		assertEquals(1_000, fromLog.getLastRecordedAt());
		assertEquals(0, fromLog.getFirstRecordedAt());

		// Two kills recorded since: the larger of each count
		AllTimeCounts record = new AllTimeCounts(2, 4_340, 500, 900, ImmutableMap.of(ItemID.DRAGONHIDE_BLUE, 4));
		AllTimeCounts merged = CollectionLogMerge.merge(record, log, vorkath, null);
		assertEquals(4_340, merged.getLootKills());
		assertEquals(5, merged.dropped(ItemID.DRAGONBONE_NECKLACE));
		assertEquals(4, merged.dropped(ItemID.DRAGONHIDE_BLUE));
		assertEquals(500, merged.getFirstRecordedAt());
		assertEquals(1_000, merged.getLastRecordedAt());
	}

	/**
	 * The Doom: the record only had the Eye of Ayak; the log also has the treads, and counts delves.
	 */
	@Test
	public void theLargerCountWinsPerItem()
	{
		BossDefinition doom = registry.byId(DoomOfMokhaiotlBoss.ID);
		CollectionLogSnapshot log = snapshot(2_000, ImmutableMap.of("Deepest delve", 7, "Deep delves", 0, "Total delves", 1_446),
			ImmutableMap.of(ItemID.AVERNIC_TREADS, 1, ItemID.EYE_OF_AYAK_UNCHARGED, 1, ItemID.DOM_TELEPORT_ITEM, 50));
		AllTimeCounts record = new AllTimeCounts(1, null, 100, 2_500, ImmutableMap.of(ItemID.EYE_OF_AYAK_UNCHARGED, 1));

		AllTimeCounts merged = CollectionLogMerge.merge(record, log, doom, null);
		assertEquals(1_446, merged.getLootKills());
		assertEquals(Integer.valueOf(1_446), merged.getKillCount());
		assertEquals(1, merged.dropped(ItemID.AVERNIC_TREADS));
		assertEquals(1, merged.dropped(ItemID.EYE_OF_AYAK_UNCHARGED));
		assertEquals(0, merged.dropped(ItemID.DOM_TELEPORT_ITEM));
		assertEquals(2_500, merged.getLastRecordedAt());
	}

	@Test
	public void sharedPagesCountByVariant()
	{
		BossDefinition nightmare = registry.byId(NightmareBoss.ID);
		CollectionLogSnapshot log = snapshot(1, ImmutableMap.of("Phosani's Nightmare kills", 194, "Nightmare kills", 2),
			ImmutableMap.of(ItemID.INQUISITORS_HELM, 1));
		assertEquals(194, CollectionLogMerge.merge(null, log, nightmare, NightmareBoss.PHOSANI).getLootKills());
		assertEquals(2, CollectionLogMerge.merge(null, log, nightmare, NightmareBoss.REGULAR).getLootKills());
		assertEquals(196, CollectionLogMerge.merge(null, log, nightmare, null).getLootKills());
		assertEquals(1, CollectionLogMerge.merge(null, log, nightmare, NightmareBoss.PHOSANI).dropped(ItemID.INQUISITORS_HELM));

		BossDefinition tob = registry.byId(TheatreOfBloodBoss.ID);
		CollectionLogSnapshot raids = snapshot(1, ImmutableMap.of("Theatre of Blood completions", 235,
			"Theatre of Blood (Entry) completions", 1, "Theatre of Blood (Hard) completions", 0), Collections.emptyMap());
		assertEquals(Integer.valueOf(235), tob.collectionLogKills(raids.getCounts(), TheatreOfBloodBoss.NORMAL));
		assertEquals(Integer.valueOf(0), tob.collectionLogKills(raids.getCounts(), TheatreOfBloodBoss.HARD));
		assertEquals(Integer.valueOf(235), tob.collectionLogKills(raids.getCounts(), null));
		assertNull(tob.collectionLogKills(raids.getCounts(), TheatreOfBloodBoss.ENTRY));
	}

	/**
	 * The Maggot King's page counts Take-eggs kills too, which luck leaves out: its items are taken, its kills not.
	 */
	@Test
	public void theMaggotKingsKillsStayTheLootTrackers()
	{
		BossDefinition maggotKing = registry.byId(MaggotKingBoss.ID);
		CollectionLogSnapshot log = snapshot(1, ImmutableMap.of("Maggot King kills", 1_007),
			ImmutableMap.of(ItemID.ELDER_VENATOR_FANG, 6));
		AllTimeCounts record = new AllTimeCounts(600, 1_007, 1, 2, ImmutableMap.of(ItemID.ELDER_VENATOR_FANG, 4));

		AllTimeCounts merged = CollectionLogMerge.merge(record, log, maggotKing, null);
		assertEquals(600, merged.getLootKills());
		assertEquals(6, merged.dropped(ItemID.ELDER_VENATOR_FANG));
		// Without a record, a page whose kills can't be used still gives the items, with no kills
		assertEquals(0, CollectionLogMerge.merge(null, log, maggotKing, null).getLootKills());
		assertNull(CollectionLogMerge.merge(null, log, maggotKing, null).getKillCount());
	}

	@Test
	public void nothingToMergeIsNothing()
	{
		BossDefinition vorkath = registry.byId(VorkathBoss.ID);
		AllTimeCounts record = new AllTimeCounts(2, 2, 1, 2, Collections.emptyMap());
		assertSame(record, CollectionLogMerge.merge(record, null, vorkath, null));
		assertNull(CollectionLogMerge.merge(null, null, vorkath, null));
		assertNull(CollectionLogMerge.merge(null, snapshot(1, Collections.emptyMap(), Collections.emptyMap()), vorkath, null));
	}

	private static CollectionLogSnapshot snapshot(long readAt, java.util.Map<String, Integer> counts, java.util.Map<Integer, Integer> items)
	{
		CollectionLogSnapshot snapshot = new CollectionLogSnapshot();
		snapshot.setReadAt(readAt);
		snapshot.getCounts().putAll(counts);
		snapshot.getItems().putAll(items);
		return snapshot;
	}
}
