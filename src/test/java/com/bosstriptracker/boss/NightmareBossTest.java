package com.bosstriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.model.ChargeType;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

/**
 * Against the Phosani's diagnostic log of 2026-09-28.
 */
public class NightmareBossTest
{
	private final BossRegistry registry = BossRegistry.standard();
	private final BossDefinition boss = registry.byId(NightmareBoss.ID);

	@Test
	public void theDreamIsTheTripAreaAndTheSanctuaryWhereItWaits()
	{
		assertSame(boss, registry.forRegion(15515));
		assertNull(registry.forRegion(15256));
		assertTrue(boss.getWaitingRegions().contains(15256));
		// Every form Phosani's took in the log
		for (int id : new int[]{9416, 9418, 9420, 9423, 11153, 11154})
		{
			assertSame(boss, registry.forBossNpc(id));
		}
		assertTrue(boss.getGraveHelperNpcs().contains(NpcID.NIGHTMARE_CHALLENGE_SISTER_2OP));
		// Thrown at sleepwalkers and picked back up
		assertTrue(boss.getRecoverableItems().contains(ItemID.BLISTERWOOD_STAKE));
	}

	@Test
	public void onlyPhosanisKillsAndLootAreRecognisedSoFar()
	{
		assertEquals(NightmareBoss.PHOSANI, boss.getKillNames().get("Phosani's Nightmare"));
		assertEquals(1, boss.getKillNames().size());
		assertTrue(boss.isLootEvent("Phosani's Nightmare", LootRecordType.NPC, "ignored"));
		assertFalse(boss.isLootEvent("The Nightmare", LootRecordType.NPC, "ignored"));
		assertFalse(boss.isLootEvent("Phosani's Nightmare", LootRecordType.EVENT, "ignored"));
		assertEquals(2, boss.getVariants().size());
	}

	@Test
	public void regularHasNoChipUntilItsKillsAreTracked()
	{
		assertFalse(boss.getVariants().get(1).isTracked());
		// Phosani's alone would show the same as All, so there's no chip row at all
		assertTrue(boss.getShownVariants().isEmpty());
	}

	@Test
	public void fightClockStartsEightTicksAfterTheAwokenMessage()
	{
		assertFalse(boss.isFightStartOnSpawn());
		assertEquals(Long.valueOf(4800), boss.fightStartDelayMs("The Nightmare has awoken!"));
		assertEquals(Long.valueOf(4800), boss.fightStartDelayMs("The Nightmare has reawoken!"));
		assertNull(boss.fightStartDelayMs("The Nightmare will awaken in 10 seconds!"));
	}

	@Test
	public void sisterSengasFeeComesFromTheBankPaymentMessage()
	{
		assertEquals(Long.valueOf(60_000), boss.reclaimFee("Payment has been taken from your bank: 60,000 x Coins"));
		assertNull(boss.reclaimFee("Sister Senga has retrieved some of your items. You can collect them from her in the"
			+ " Sisterhood Sanctuary."));
	}

	@Test
	public void phosanisRates()
	{
		// About 1/111 for any unique, as the wiki says
		assertEquals(110.9, 1 / boss.anyUniqueChance(KillContext.DEFAULT), 0.1);
		assertEquals(8, boss.getDrops().stream().filter(d -> d.getKind() == DropKind.UNIQUE).count());
		assertEquals(ItemID.NIGHTMAREPET, boss.getPet().getItemId());
		assertEquals(1 / 1400.0, boss.getPet().chance(KillContext.DEFAULT), 1e-12);
		assertTrue(boss.isUnique(ItemID.VOLATILE_ORB));
		assertFalse(boss.getHighlightedItems().contains(ItemID.JAR_OF_DREAMS));
	}

	@Test
	public void uniquesThisTripCell()
	{
		Trip trip = new Trip();
		Kill kill = new Kill();
		kill.getLoot().add(new ItemEntry(ItemID.INQUISITORS_MACE, 1, 1));
		kill.getLoot().add(new ItemEntry(ItemID.SOULRUNE, 184, 1));
		trip.getKills().add(kill);
		assertEquals("Uniques", boss.getProfitCell().getLabel());
		assertEquals("1", boss.getProfitCell().valueOf(trip));
	}

	@Test
	public void bloodFuryIsMarkedApproximateHereOnly()
	{
		assertTrue(boss.getChargeNote(ChargeType.BLOOD_FURY).startsWith("Approximate at the Nightmare"));
		assertNull(boss.getChargeNote(ChargeType.SCYTHE_OF_VITUR));
		assertNull(registry.byId(MaggotKingBoss.ID).getChargeNote(ChargeType.BLOOD_FURY));
	}

	@Test
	public void allTimeReadsPhosanisRecords()
	{
		assertEquals(1, boss.getAllTimeSources().size());
		assertEquals("drops_NPC_Phosani's Nightmare", boss.getAllTimeSources().get(0).getLootTrackerKey());
		assertEquals("phosani's nightmare", boss.getAllTimeSources().get(0).getKillCountKey());
	}
}
