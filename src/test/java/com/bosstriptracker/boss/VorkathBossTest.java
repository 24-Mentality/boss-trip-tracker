package com.bosstriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

/**
 * Against the diagnostic log of 2026-10-23 (2 kills, KC 4,339 and 4,340) and the OSRS Wiki's rates.
 */
public class VorkathBossTest
{
	private final BossRegistry registry = BossRegistry.standard();
	private final BossDefinition boss = registry.byId(VorkathBoss.ID);

	@Test
	public void theIslandAndUngael()
	{
		assertSame(boss, registry.forRegion(9023));
		assertNull(registry.forRegion(10553));
		assertEquals(Collections.singleton(10553), boss.getWaitingRegions());
		assertSame(TripModel.INSTANCE_KILLS, boss.getTripModel());
		assertSame(boss, registry.forBossNpc(NpcID.VORKATH));
		// Poked while asleep, and his spawn dying mustn't end the kill clock
		assertNull(registry.forBossNpc(NpcID.VORKATH_SLEEPING));
		assertNull(registry.forBossNpc(NpcID.VORKATH_SPAWN));
		assertTrue(boss.getKillNames().isEmpty());
		assertTrue(boss.isLootEvent("Vorkath", LootRecordType.NPC, "Vorkath"));
		assertFalse(boss.isLootEvent("Vorkath", LootRecordType.EVENT, "Vorkath"));
	}

	@Test
	public void clockRatesAndTheDeathbank()
	{
		assertTrue(boss.isFightStartOnFirstHit());
		assertFalse(boss.isFightStartOnSpawn());
		assertEquals(1 / 1_000.0 + 2 / 5_000.0, boss.anyUniqueChance(KillContext.DEFAULT), 1e-12);
		assertTrue(boss.isUnique(ItemID.DRAGONBONE_NECKLACE));
		assertTrue(boss.isUnique(ItemID.SKELETAL_VISAGE));
		assertTrue(boss.isUnique(ItemID.DRAGONFIRE_VISAGE));
		assertFalse(boss.isUnique(ItemID.JAR_OF_DECAY));
		assertFalse(boss.isUnique(ItemID.VORKATH_HEAD));
		assertEquals(ItemID.VORKATHPET, boss.getPet().getItemId());
		assertEquals(1 / 3_000.0, boss.getPet().chance(KillContext.DEFAULT), 1e-12);
		// Torfinn keeps your items for 100,000 (wiki, unverified); a payment message would replace it
		assertEquals(100_000, boss.getDeathFee());
		assertFalse(boss.isGravestoneFeeEstimated());
		assertTrue(boss.getGraveHelperNpcs().contains(NpcID.TORFINN_COLLECT_UNGAEL));
		assertEquals(Long.valueOf(100_000), boss.reclaimFee("Payment has been taken from your bank: 100,000 x Coins"));
		assertEquals(LootCategories.RESOURCES, boss.lootCategory(ItemID.DRAGON_BONES_SUPERIOR, 0));
		assertEquals(LootCategories.OTHER, boss.lootCategory(ItemID.VORKATH_HEAD, 0));
		assertEquals("drops_NPC_Vorkath", boss.getAllTimeSources().get(0).getLootTrackerKey());
		assertEquals("vorkath", boss.getAllTimeSources().get(0).getKillCountKey());
	}
}
