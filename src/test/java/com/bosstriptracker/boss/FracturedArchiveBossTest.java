package com.bosstriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

/**
 * Against the diagnostic raids of 2026-10-21: the Nexus, Bork, Dreadnoth, Xarth and Ocellus, 15 wipes and one
 * abandon, each wipe followed by a 125,000 coin gravestone payment outside.
 */
public class FracturedArchiveBossTest
{
	private final BossRegistry registry = BossRegistry.standard();
	private final BossDefinition boss = registry.byId(FracturedArchiveBoss.ID);

	@Test
	public void theRoomsSeenAreTheRaidButNotTheLobby()
	{
		for (int region : new int[]{16471, 16727, 16728, 16726, 16472, 16474})
		{
			assertSame(boss, registry.forRegion(region));
		}
		assertNull(registry.forRegion(FracturedArchiveBoss.LOBBY_REGION_ID));
		// Spectating from the Strategy board puts you in the rooms with party status 0: not a trip
		assertFalse(boss.isParticipant(v -> 0));
		assertTrue(boss.isParticipant(v -> 2));
		assertTrue(boss.isParticipant(v -> 1));
		assertTrue(boss.getWaitingRegions().isEmpty());
		assertSame(TripModel.ONE_RAID, boss.getTripModel());
		assertFalse(boss.isDeathEndsTrip());
		assertEquals("raid", boss.getUnitNoun());
		assertEquals(8, boss.getTeamSlotVarbits().size());
		assertTrue(boss.getVariants().isEmpty());
		// Every challenge boss's first form
		assertSame(boss, registry.forBossNpc(NpcID.TFA_BORK));
		assertSame(boss, registry.forBossNpc(NpcID.TFA_OCELLUS_P1));
	}

	@Test
	public void unseenRoomsKeepTheRaidGoingWhileInstanced()
	{
		int unknownRoom = 16729;
		assertTrue(boss.isStillInside(v -> 0, unknownRoom, true));
		assertFalse(boss.isStillInside(v -> 0, unknownRoom, false));
		assertFalse(boss.isStillInside(v -> 0, FracturedArchiveBoss.LOBBY_REGION_ID, true));
	}

	@Test
	public void onlyTheChestEventIsLoot()
	{
		assertTrue(boss.isRaidLootEvent("Fractured Archive", LootRecordType.EVENT));
		assertFalse(boss.isRaidLootEvent("Theatre of Blood", LootRecordType.EVENT));
		assertFalse(boss.isLootEvent("Bork", LootRecordType.NPC, "Ocellus"));
	}

	@Test
	public void ratesAreUnpublishedSoNothingIsExpected()
	{
		assertFalse(boss.hasPublishedRates());
		assertEquals(0, boss.anyUniqueChance(KillContext.DEFAULT), 0);
		assertEquals(0, boss.anyUniqueChance(new KillContext(null, 5)), 0);
		for (ExpectedDrop drop : boss.getDrops())
		{
			assertEquals(0, drop.chance(KillContext.DEFAULT), 0);
		}
		assertEquals(5, boss.getDrops().stream().filter(d -> d.getKind() == DropKind.UNIQUE).count());
		assertTrue(boss.isUnique(ItemID.RONDACHE));
		assertTrue(boss.isUnique(ItemID.TFA_ZEAL_REWARD));
		assertTrue(boss.isUnique(ItemID.OBLIGATOR_UNCHARGED));
		assertFalse(boss.isUnique(ItemID.ARCHIVISTS_FOLIO_EMPTY));
		assertEquals(ItemID.OCELLUSPET, boss.getPet().getItemId());
		assertTrue(boss.getHighlightedItems().contains(ItemID.ASCENSION_CROSSBOWS));
		assertTrue(boss.countsForLuck(new Kill()));
	}

	@Test
	public void challengesDeathsWipesAndTheGravestoneFee()
	{
		assertFalse(boss.isFightStartOnSpawn());
		assertEquals(Long.valueOf(0), boss.fightStartDelayMs("Challenge started: Bork."));
		assertNull(boss.fightStartDelayMs("Challenge complete: Bork. Duration: 3:50.40. Total: 7:33.00 (new personal best)"));
		assertTrue(boss.isRaidRoomComplete("Challenge complete: Bork. Duration: 3:50.40. Total: 7:33.00 (new personal best)"));
		assertEquals(Long.valueOf(7 * 60_000 + 33_000),
			boss.raidTimeMs("Challenge complete: Bork. Duration: 3:50.40. Total: 7:33.00 (new personal best)"));
		assertTrue(boss.isOwnRaidDeath("You have died. Total deaths: 6."));
		assertTrue(boss.isRaidFailed("You failed to survive the Fractured Archive."));
		assertTrue(boss.isRaidAbandoned("You abandon the raid and leave the Fractured Archive."));
		assertNull(boss.raidMode("You enter the Archive..."));
		// The fee is paid at the gravestone outside, from the bank, not a fixed amount on the wipe
		assertEquals(0, boss.getWipeFee());
		assertTrue(boss.getGraveHelperNpcs().contains(NpcID.GRAVESTONE_DEFAULT));
		assertEquals(Long.valueOf(125_000), boss.reclaimFee("Payment has been taken from your bank: 125,000 x Coins"));
		assertNull(boss.reclaimFee("You successfully retrieved everything from your gravestone."));
		// Quartermaster purchases are free; dropped potions count as used
		assertTrue(boss.isAcquiredInsideFree());
		assertTrue(boss.isDroppedSupplyUsed());
	}

	@Test
	public void historyCardNamesTheTeam()
	{
		Trip trip = new Trip();
		assertNull(boss.tripDetail(trip));
		Kill raid = new Kill();
		raid.setPartySize(5);
		trip.getKills().add(raid);
		assertEquals("Raid · team of 5", boss.tripDetail(trip));
		raid.setPartySize(1);
		assertEquals("Raid · solo", boss.tripDetail(trip));
		assertEquals("Uniques", boss.getProfitCell().getLabel());
		assertEquals("0", boss.getProfitCell().valueOf(trip));
		assertEquals(2, boss.getCsvColumns().size());
		assertEquals("1", boss.getCsvColumns().get(0).valueOf(trip));
	}

	@Test
	public void recordKeysFollowRuneLitesNaming()
	{
		AllTimeSource source = boss.getAllTimeSources().get(0);
		assertEquals("drops_EVENT_Fractured Archive", source.getLootTrackerKey());
		assertEquals("fractured archive", source.getKillCountKey());
		assertNull(source.getVariant());
	}
}
