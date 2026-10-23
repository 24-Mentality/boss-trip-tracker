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
 * Against the diagnostic log of 2026-10-23 (2 kills, KC 916 and 917) and the OSRS Wiki's rates.
 */
public class PhantomMuspahBossTest
{
	private final BossRegistry registry = BossRegistry.standard();
	private final BossDefinition boss = registry.byId(PhantomMuspahBoss.ID);

	@Test
	public void theCavernAndItsCorridor()
	{
		assertSame(boss, registry.forRegion(11330));
		// The corridor is also Duke Sucellus' waiting region: nobody's area
		assertNull(registry.forRegion(11681));
		assertEquals(Collections.singleton(11681), boss.getWaitingRegions());
		assertSame(boss, registry.forBossNpc(NpcID.MUSPAH));
		assertSame(boss, registry.forBossNpc(NpcID.MUSPAH_MELEE));
		assertSame(boss, registry.forBossNpc(NpcID.MUSPAH_SOULSPLIT));
		assertTrue(boss.isLootEvent("Phantom Muspah", LootRecordType.NPC, "Phantom Muspah"));
	}

	@Test
	public void clockRatesAndTheGrave()
	{
		// The clock began 5 ticks after the cavern loaded and 50 ticks (the respawn) after the first kill
		assertTrue(boss.isFightStartOnSpawn());
		assertFalse(boss.isFightStartOnFirstHit());
		assertEquals(1 / 100.0, boss.anyUniqueChance(KillContext.DEFAULT), 1e-12);
		assertTrue(boss.isUnique(ItemID.VENATOR_SHARD));
		assertFalse(boss.isUnique(ItemID.ANCIENT_ICON));
		assertFalse(boss.isUnique(ItemID.FROZEN_CACHE));
		assertEquals(ItemID.MUSPAHPET, boss.getPet().getItemId());
		assertEquals(1 / 2_500.0, boss.getPet().chance(KillContext.DEFAULT), 1e-12);
		assertTrue(boss.isGravestoneFeeEstimated());
		assertTrue(boss.getGraveHelperNpcs().contains(NpcID.GRAVESTONE_DEFAULT));
		assertEquals(Long.valueOf(115_000), boss.reclaimFee("Payment has been taken from your bank: 115,000 x Coins"));
		assertEquals(LootCategories.OTHER, boss.lootCategory(ItemID.ANCIENT_ESSENCE, 0));
		assertEquals(LootCategories.RESOURCES, boss.lootCategory(ItemID.SHARK_LURE, 0));
		assertEquals("drops_NPC_Phantom Muspah", boss.getAllTimeSources().get(0).getLootTrackerKey());
		assertEquals("phantom muspah", boss.getAllTimeSources().get(0).getKillCountKey());
	}
}
