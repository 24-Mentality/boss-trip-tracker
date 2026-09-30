package com.maggotkingtriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableMap;
import com.maggotkingtriptracker.model.DeathRecord;
import com.maggotkingtriptracker.model.ItemEntry;
import com.maggotkingtriptracker.model.Kill;
import com.maggotkingtriptracker.model.Trip;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

/**
 * Against the Normal Mode diagnostic raid of 2026-09-29 and the OSRS Wiki's rates.
 */
public class TheatreOfBloodBossTest
{
	private final BossRegistry registry = BossRegistry.standard();
	private final BossDefinition boss = registry.byId(TheatreOfBloodBoss.ID);

	@Test
	public void everyRoomOfTheRaidIsTheTripAreaButNotVerSinhaza()
	{
		// Entrance, Maiden, Bloat, Nylocas, Sotetseg, Xarpus, Verzik and the vault, as the log went
		for (int region : new int[]{12869, 12613, 13125, 13122, 13123, 12612, 12611, 12867})
		{
			assertSame(boss, registry.forRegion(region));
		}
		assertNull(registry.forRegion(TheatreOfBloodBoss.VER_SINHAZA_REGION_ID));
		assertTrue(boss.getWaitingRegions().isEmpty());
		assertSame(TripModel.ONE_RAID, boss.getTripModel());
		assertFalse(boss.isDeathEndsTrip());
		// The raid clock starts when the Maiden spawns
		assertSame(boss, registry.forBossNpc(NpcID.TOB_MAIDEN_100));
		assertEquals(5, boss.getTeamSlotVarbits().size());
	}

	@Test
	public void onlyTheRewardEventIsLoot()
	{
		assertTrue(boss.isRaidLootEvent("Theatre of Blood", LootRecordType.EVENT));
		// Each room's book
		assertFalse(boss.isLootEvent("The Maiden of Sugadinti", LootRecordType.NPC, "The Maiden of Sugadinti"));
		assertFalse(boss.isRaidLootEvent("Tarnished necklace", LootRecordType.EVENT));
	}

	@Test
	public void purpleChanceIsTheTeamChanceSplitByTeamSize()
	{
		// Normal, team of 4: 1/9.1 per raid for the team, a quarter of it each
		assertEquals(1 / 9.1 / 4, boss.anyUniqueChance(new KillContext(TheatreOfBloodBoss.NORMAL, 4)), 1e-12);
		assertEquals(1 / 7.7 / 2, boss.anyUniqueChance(new KillContext(TheatreOfBloodBoss.HARD, 2)), 1e-12);
		assertEquals(0, boss.anyUniqueChance(new KillContext(TheatreOfBloodBoss.ENTRY, 4)), 0);
		// Unknown team size: a team of 4
		assertEquals(1 / 9.1 / 4, boss.anyUniqueChance(KillContext.DEFAULT), 1e-12);

		// The purples add up to the any-purple chance in both modes
		for (String mode : new String[]{TheatreOfBloodBoss.NORMAL, TheatreOfBloodBoss.HARD})
		{
			KillContext context = new KillContext(mode, 3);
			double sum = boss.getDrops().stream().filter(d -> d.getKind() == DropKind.UNIQUE).mapToDouble(d -> d.chance(context)).sum();
			assertEquals(boss.anyUniqueChance(context), sum, 1e-12);
		}
		// Normal weights: hilt 8/19, scythe 1/19; Hard: hilt 7/18
		assertEquals(1 / 9.1 * 8 / 19 / 4, chance(ItemID.INFERNAL_DEFENDER_HILT, TheatreOfBloodBoss.NORMAL, 4), 1e-12);
		assertEquals(1 / 9.1 / 19 / 4, chance(ItemID.SCYTHE_OF_VITUR_UNCHARGED, TheatreOfBloodBoss.NORMAL, 4), 1e-12);
		assertEquals(1 / 7.7 * 7 / 18 / 4, chance(ItemID.INFERNAL_DEFENDER_HILT, TheatreOfBloodBoss.HARD, 4), 1e-12);
		// The pet isn't split; the kits are Hard Mode only
		assertEquals(1 / 650.0, chance(ItemID.VERZIKPET, TheatreOfBloodBoss.NORMAL, 4), 1e-12);
		assertEquals(1 / 500.0, chance(ItemID.VERZIKPET, TheatreOfBloodBoss.HARD, 4), 1e-12);
		assertEquals(0, chance(ItemID.TOB_HARDMODE_KIT, TheatreOfBloodBoss.NORMAL, 4), 0);
		assertEquals(1 / 100.0, chance(ItemID.TOB_HARDMODE_KIT, TheatreOfBloodBoss.HARD, 4), 1e-12);
		assertTrue(boss.isUnique(ItemID.SCYTHE_OF_VITUR_UNCHARGED));
		assertFalse(boss.isUnique(ItemID.TOB_HARDMODE_KIT));
	}

	@Test
	public void entryRaidsCountForProfitButNotLuck()
	{
		assertFalse(boss.countsForLuck(kill(TheatreOfBloodBoss.ENTRY)));
		assertFalse(boss.countsTowardKillCount(kill(TheatreOfBloodBoss.ENTRY)));
		assertTrue(boss.countsForLuck(kill(TheatreOfBloodBoss.NORMAL)));
		assertTrue(boss.countsForLuck(kill(TheatreOfBloodBoss.HARD)));
		assertEquals(new KillContext(TheatreOfBloodBoss.NORMAL, 4), boss.pastKillContext(null, 4));
	}

	@Test
	public void killCountPutsNormalAndHardOnOneScale()
	{
		Map<String, Integer> keys = ImmutableMap.of("theatre of blood", 6, "theatre of blood hard mode", 3);
		// Normal completion 7 plus the 3 Hard raids so far
		assertEquals(Integer.valueOf(10), boss.raidKillCount(new RaidCompletion(TheatreOfBloodBoss.NORMAL, 7), keys::get));
		assertEquals(Integer.valueOf(10), boss.raidKillCount(new RaidCompletion(TheatreOfBloodBoss.HARD, 4), keys::get));
		// No Hard count known yet
		assertEquals(Integer.valueOf(7), boss.raidKillCount(new RaidCompletion(TheatreOfBloodBoss.NORMAL, 7), k -> null));
		assertNull(boss.raidKillCount(new RaidCompletion(TheatreOfBloodBoss.ENTRY, 2), keys::get));
	}

	@Test
	public void profitCellIsYourPurplesOverTheTeams()
	{
		Trip trip = new Trip();
		Kill kill = kill(TheatreOfBloodBoss.NORMAL);
		kill.setPartySize(4);
		kill.getLoot().add(new ItemEntry(ItemID.GHRAZI_RAPIER, 1, 1));
		kill.getTeamUniques().add(ItemID.GHRAZI_RAPIER);
		kill.getTeamUniques().add(ItemID.INFERNAL_DEFENDER_HILT);
		trip.getKills().add(kill);
		trip.getDeaths().add(new DeathRecord());

		assertEquals("1 / 2", boss.getProfitCell().valueOf(trip));
		assertEquals("Normal · team of 4", boss.tripDetail(trip));
		// Your own purple counts for the team even if the broadcast was missed
		kill.getTeamUniques().clear();
		assertEquals("1 / 1", boss.getProfitCell().valueOf(trip));
	}

	private double chance(int itemId, String mode, int team)
	{
		return boss.getDrops().stream().filter(d -> d.getItemId() == itemId).findFirst().get()
			.chance(new KillContext(mode, team));
	}

	private static Kill kill(String variant)
	{
		Kill kill = new Kill();
		kill.setVariant(variant);
		return kill;
	}
}
