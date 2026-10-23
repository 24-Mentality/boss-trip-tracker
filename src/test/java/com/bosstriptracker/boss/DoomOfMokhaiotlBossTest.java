package com.bosstriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

/**
 * Against the diagnostic log of 2026-10-23: a run to level 3 ending in a death, then a run to level 7 claimed with
 * "Claim and leave"; rates from the OSRS Wiki.
 */
public class DoomOfMokhaiotlBossTest
{
	private final BossRegistry registry = BossRegistry.standard();
	private final BossDefinition boss = registry.byId(DoomOfMokhaiotlBoss.ID);

	@Test
	public void theLobbyAndTheLevelsBelow()
	{
		assertSame(boss, registry.forRegion(5269));
		assertSame(boss, registry.forRegion(13668));
		assertSame(boss, registry.forRegion(14180));
		// Deeper than the log went: kept by the instance
		assertTrue(boss.isStillInside(v -> 0, 12345, true));
		assertFalse(boss.isStillInside(v -> 0, 12345, false));
		assertTrue(boss.getWaitingRegions().isEmpty());
		assertSame(TripModel.INSTANCE_KILLS, boss.getTripModel());
		assertEquals("delve", boss.getUnitNoun());
		assertSame(boss, registry.forBossNpc(NpcID.DOM_BOSS_SHIELDED));
		assertTrue(boss.getShownVariants().isEmpty());
	}

	@Test
	public void levelsStartAndCompleteTheirDelves()
	{
		assertEquals(1, DoomOfMokhaiotlBoss.levelStarted("@mes_hl_red@Delve level: 1"));
		assertEquals(7, DoomOfMokhaiotlBoss.levelStarted("Delve level: 7"));
		assertEquals(0, DoomOfMokhaiotlBoss.levelStarted("Delve level: 3 duration: @mes_hl_red@1:30.00. Personal best: @mes_hl_red@0:58.20"));
		assertTrue(boss.isTripStartMessage("@mes_hl_red@Delve level: 1"));
		assertFalse(boss.isTripStartMessage("@mes_hl_red@Delve level: 2"));
		// The game's clock started 4 to 6 ticks after the level message on all 9 levels
		assertEquals(Long.valueOf(3_000), boss.fightStartDelayMs("@mes_hl_red@Delve level: 4"));
		assertNull(boss.fightStartDelayMs("Total duration: @mes_hl_red@9:46.80"));

		KillReport report = boss.killReport("Delve level: 3 duration: @mes_hl_red@1:30.00. Personal best: @mes_hl_red@0:58.20");
		assertNull(report.getKillCount());
		assertEquals("level3", report.getVariant());
		assertEquals(Long.valueOf(90_000), report.getDurationMs());
		assertEquals("deep", boss.killReport("Delve level: 12 duration: @mes_hl_red@2:00.00 (new personal best)").getVariant());
		assertNull(boss.killReport("@mes_hl_red@Delve level: 3"));
		assertNull(boss.killReport("Your Vorkath kill count is: 4,339."));
	}

	@Test
	public void claimingEndsTheRunAndDyingEndsItInTheLobby()
	{
		assertTrue(boss.isLootEvent("Doom of Mokhaiotl", LootRecordType.EVENT, "Doom of Mokhaiotl"));
		assertFalse(boss.isLootEvent("Doom of Mokhaiotl", LootRecordType.NPC, "Doom of Mokhaiotl"));
		assertTrue(boss.isLootEventEndsTrip());
		assertTrue(boss.isDeathRespawnInside());
		assertTrue(boss.isDeathEndsTrip());
		assertTrue(boss.isGravestoneFeeEstimated());
		assertTrue(boss.getGraveHelperNpcs().contains(NpcID.GRAVESTONE_DEFAULT));
		assertEquals(Long.valueOf(115_000), boss.reclaimFee("Payment has been taken from your bank: 115,000 x Coins"));
		assertEquals("drops_EVENT_Doom of Mokhaiotl", boss.getAllTimeSources().get(0).getLootTrackerKey());
		assertNull(boss.getAllTimeSources().get(0).getKillCountKey());
	}

	@Test
	public void ratesDeepenWithTheLevel()
	{
		assertEquals(0, boss.anyUniqueChance(context(1)), 0);
		assertEquals(1 / 2_500.0, boss.anyUniqueChance(context(2)), 1e-12);
		assertEquals(2 / 2_000.0, boss.anyUniqueChance(context(3)), 1e-12);
		assertEquals(3 / 1_350.0, boss.anyUniqueChance(context(4)), 1e-12);
		assertEquals(3 / 810.0, boss.anyUniqueChance(context(7)), 1e-12);
		assertEquals(3 / 630.0, boss.anyUniqueChance(context(8)), 1e-12);
		assertEquals(3 / 540.0, boss.anyUniqueChance(new KillContext("deep", null)), 1e-12);
		assertEquals(0, chance(ItemID.AVERNIC_TREADS, context(3)), 0);
		assertEquals(1 / 1_350.0, chance(ItemID.AVERNIC_TREADS, context(4)), 1e-12);
		assertEquals(0, boss.getPet().chance(context(5)), 0);
		assertEquals(1 / 1_000.0, boss.getPet().chance(context(6)), 1e-12);
		assertEquals(1 / 250.0, boss.getPet().chance(new KillContext("deep", null)), 1e-12);
		// A past claim from RuneLite's record is taken as a 7-level run
		KillContext past = boss.pastKillContext(null, 4);
		assertEquals(1 / 2_500.0 + 2 / 2_000.0 + 3 / 1_350.0 + 3 * 3 / 810.0, boss.anyUniqueChance(past), 1e-12);
		assertEquals(1 / 1_000.0 + 1 / 750.0, boss.getPet().chance(past), 1e-12);
		// Nothing is expected of a kill without a level
		assertEquals(0, boss.anyUniqueChance(KillContext.DEFAULT), 0);
		assertTrue(boss.isLuckApproximate());
		assertEquals(LootCategories.OTHER, boss.lootCategory(ItemID.DEMON_TEAR, 0));
		assertEquals(LootCategories.UNIQUES, boss.lootCategory(ItemID.EYE_OF_AYAK_UNCHARGED, 0));
	}

	private static KillContext context(int level)
	{
		return new KillContext(DoomOfMokhaiotlBoss.variantFor(level), null);
	}

	private double chance(int itemId, KillContext context)
	{
		for (ExpectedDrop drop : boss.getDrops())
		{
			if (drop.getItemId() == itemId)
			{
				return drop.chance(context);
			}
		}
		throw new AssertionError(itemId);
	}
}
