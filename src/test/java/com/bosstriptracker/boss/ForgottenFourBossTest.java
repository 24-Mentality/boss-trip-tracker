package com.bosstriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.model.Kill;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

/**
 * Against the diagnostic log of 2026-10-22 (3 Vardorvis, 2 Duke Sucellus, 1 Whisperer and 1 Leviathan kills) and the
 * OSRS Wiki's rates.
 */
public class ForgottenFourBossTest
{
	private final BossRegistry registry = BossRegistry.standard();
	private final BossDefinition vardorvis = registry.byId(ForgottenFourBoss.VARDORVIS_ID);
	private final BossDefinition duke = registry.byId(ForgottenFourBoss.DUKE_ID);
	private final BossDefinition whisperer = registry.byId(ForgottenFourBoss.WHISPERER_ID);
	private final BossDefinition leviathan = registry.byId(ForgottenFourBoss.LEVIATHAN_ID);
	private static final KillContext AWAKENED = new KillContext(ForgottenFourBoss.AWAKENED, null);

	@Test
	public void eachArenaIsItsOwnBossAndTheWayInWaits()
	{
		assertSame(vardorvis, registry.forRegion(4405));
		assertEquals(Collections.singleton(4661), vardorvis.getWaitingRegions());
		assertSame(duke, registry.forRegion(12132));
		assertEquals(Collections.singleton(11681), duke.getWaitingRegions());
		assertSame(whisperer, registry.forRegion(10595));
		// The shadow realm is part of the Whisperer's fight
		assertSame(whisperer, registry.forRegion(9571));
		assertEquals(Collections.singleton(10596), whisperer.getWaitingRegions());
		assertSame(leviathan, registry.forRegion(8291));
		assertEquals(Collections.singleton(8292), leviathan.getWaitingRegions());
		// The Ghorrock bank and the house are nobody's
		assertNull(registry.forRegion(9776));
		assertNull(registry.forRegion(7790));
		for (BossDefinition boss : new BossDefinition[]{vardorvis, duke, whisperer, leviathan})
		{
			assertSame(TripModel.INSTANCE_KILLS, boss.getTripModel());
			assertTrue(boss.isDeathEndsTrip());
			assertEquals("kill", boss.getUnitNoun());
			assertTrue(boss.getShownVariants().isEmpty());
			assertTrue(boss.isLuckApproximate());
		}
	}

	@Test
	public void killMessagesAndLootEventsUseTheGamesNames()
	{
		// "Your Whisperer kill count is: 140." but the Loot Tracker's "The Whisperer"
		assertTrue(whisperer.getKillNames().containsKey("Whisperer"));
		assertNull(whisperer.getKillNames().get("Whisperer"));
		assertEquals(ForgottenFourBoss.AWAKENED, whisperer.getKillNames().get("Whisperer (Awakened)"));
		assertTrue(whisperer.isLootEvent("The Whisperer", LootRecordType.NPC, "The Whisperer"));
		assertFalse(whisperer.isLootEvent("The Leviathan", LootRecordType.NPC, "The Whisperer"));
		assertTrue(leviathan.getKillNames().containsKey("Leviathan"));
		assertTrue(vardorvis.getKillNames().containsKey("Vardorvis"));
		assertTrue(duke.getKillNames().containsKey("Duke Sucellus"));
		assertEquals(NpcID.WHISPERER, whisperer.getNameNpcId());
		assertEquals(NpcID.LEVIATHAN, leviathan.getNameNpcId());
		assertSame(duke, registry.forBossNpc(NpcID.DUKE_SUCELLUS_AWAKE));
		assertSame(whisperer, registry.forBossNpc(NpcID.WHISPERER_MELEE));
		// The Odd Figure isn't the boss yet: its change into the Whisperer starts the fight
		assertNull(registry.forBossNpc(NpcID.WHISPERER_SPAWN));
	}

	@Test
	public void theFightClockStartsAsTheLogsShow()
	{
		assertTrue(vardorvis.isFightStartOnFirstHit());
		assertFalse(vardorvis.isFightStartOnSpawn());
		assertNull(vardorvis.fightStartDelayMs("Vardorvis' head gazes upon you..."));

		assertFalse(duke.isFightStartOnFirstHit());
		assertFalse(duke.isFightStartOnSpawn());
		assertEquals(Long.valueOf(0), duke.fightStartDelayMs("@mes_hl_mag@Duke Sucellus awakens..."));
		assertEquals(Long.valueOf(0), duke.fightStartDelayMs("Duke Sucellus awakens..."));
		assertNull(duke.fightStartDelayMs("Duke Sucellus turns his gaze upon you..."));

		assertTrue(whisperer.isFightStartOnSpawn());
		assertTrue(leviathan.isFightStartOnSpawn());
		assertFalse(leviathan.isFightStartOnFirstHit());
	}

	@Test
	public void ratesFollowTheUniqueTable()
	{
		assertRate(vardorvis, ItemID.ULTOR_VESTIGE, 1 / 1_088.0);
		assertRate(vardorvis, ItemID.SOULREAPER_AXE_HEAD, 1 / 1_088.0);
		assertRate(vardorvis, ItemID.VIRTUS_MASK, 1 / 3_264.0);
		assertRate(vardorvis, ItemID.CHROMIUM_INGOT, 3 / 1_088.0);
		assertRate(vardorvis, ItemID.VARDORVISPET, 1 / 3_000.0);
		assertRate(vardorvis, ItemID.DT2_AWAKENERS_ORB, 1 / 80.6);
		assertRate(vardorvis, ItemID.BLOOD_QUARTZ, 1 / 204.0);
		assertRate(duke, ItemID.MAGUS_VESTIGE, 1 / 720.0);
		assertRate(duke, ItemID.VIRTUS_TOP, 1 / 2_160.0);
		assertRate(duke, ItemID.DUKESUCELLUSPET, 1 / 2_500.0);
		assertRate(whisperer, ItemID.BELLATOR_VESTIGE, 1 / 512.0);
		assertRate(whisperer, ItemID.SOULREAPER_AXE_STAFF, 1 / 512.0);
		assertRate(whisperer, ItemID.VIRTUS_LEGS, 1 / 1_536.0);
		assertRate(whisperer, ItemID.WHISPERERPET, 1 / 2_000.0);
		assertRate(leviathan, ItemID.VENATOR_VESTIGE, 1 / 768.0);
		assertRate(leviathan, ItemID.SOULREAPER_AXE_LURE, 1 / 768.0);
		assertRate(leviathan, ItemID.VIRTUS_MASK, 1 / 2_304.0);
		assertRate(leviathan, ItemID.LEVIATHANPET, 1 / 2_500.0);

		// Any unique: 6 of the table's 8 slots (the other 2 are gold rings)
		assertEquals(6 / 1_088.0, vardorvis.anyUniqueChance(KillContext.DEFAULT), 1e-12);
		assertEquals(6 / 720.0, duke.anyUniqueChance(KillContext.DEFAULT), 1e-12);
		assertEquals(6 / 512.0, whisperer.anyUniqueChance(KillContext.DEFAULT), 1e-12);
		assertEquals(6 / 768.0, leviathan.anyUniqueChance(KillContext.DEFAULT), 1e-12);

		// Awakened: the table three times as often, the pet unchanged
		assertEquals(3 / 1_088.0, chance(vardorvis, ItemID.ULTOR_VESTIGE, AWAKENED), 1e-12);
		assertEquals(18 / 512.0, whisperer.anyUniqueChance(AWAKENED), 1e-12);
		assertEquals(1 / 3_000.0, chance(vardorvis, ItemID.VARDORVISPET, AWAKENED), 1e-12);
		assertEquals(1 / 2_500.0, chance(leviathan, ItemID.LEVIATHANPET, AWAKENED), 1e-12);
		assertEquals(KillContext.DEFAULT, vardorvis.pastKillContext(null, 4));

		assertTrue(vardorvis.isUnique(ItemID.CHROMIUM_INGOT));
		assertFalse(vardorvis.isUnique(ItemID.GOLD_RING));
		assertFalse(vardorvis.isUnique(ItemID.BLOOD_QUARTZ));
		assertTrue(vardorvis.getHighlightedItems().contains(ItemID.VARDORVISPET));
		assertTrue(vardorvis.countsForLuck(new Kill()));
		assertTrue(vardorvis.getLuckNote().contains("gold rings"));
	}

	@Test
	public void lootBoxCategories()
	{
		assertEquals(LootCategories.UNIQUES, vardorvis.lootCategory(ItemID.ULTOR_VESTIGE, 0));
		assertEquals(LootCategories.SECONDARY_UNIQUES, duke.lootCategory(ItemID.ICE_QUARTZ, 0));
		assertEquals(LootCategories.SECONDARY_UNIQUES, leviathan.lootCategory(ItemID.DT2_AWAKENERS_ORB, 0));
		assertEquals(LootCategories.RESOURCES, vardorvis.lootCategory(ItemID.RUNE_JAVELIN_HEAD, 0));
		assertEquals(LootCategories.RUNES_AND_AMMO, vardorvis.lootCategory(ItemID.LAVARUNE, 0));
		assertEquals(LootCategories.RUNES_AND_AMMO, whisperer.lootCategory(ItemID.DEATHRUNE, 0));
		assertEquals(LootCategories.RESOURCES, leviathan.lootCategory(ItemID.RUNITE_ORE, 0));
		assertEquals(LootCategories.OTHER, duke.lootCategory(ItemID.GOLD_RING, 0));
	}

	@Test
	public void deathsAndSupplies()
	{
		// A death puts your items in a grave outside; the fee comes from the bank when you check it (unverified)
		assertTrue(vardorvis.getGraveHelperNpcs().contains(NpcID.GRAVESTONE_DEFAULT));
		assertEquals(Long.valueOf(250_000), vardorvis.reclaimFee("Payment has been taken from your bank: 250,000 x Coins"));
		assertNull(vardorvis.reclaimFee("Vardorvis entangles you in some tendrils!"));
		assertEquals(0, vardorvis.getWipeFee());
		// Duke Sucellus' mushrooms, powders, salt and poisons are made and used in his chamber, never supplies
		assertTrue(duke.getConvertedItems().contains(ItemID.DUKE_SUCELLUS_MUSHROOM_3));
		assertTrue(duke.getConvertedItems().contains(ItemID.DUKE_SUCELLUS_MUSHROOM_3_GROUND));
		assertTrue(duke.getConvertedItems().contains(ItemID.DUKE_SUCELLUS_SALT));
		assertTrue(vardorvis.getConvertedItems().isEmpty());
		assertFalse(duke.isAcquiredInsideFree());
		assertFalse(duke.isDroppedSupplyUsed());
	}

	@Test
	public void recordKeysFollowRuneLitesNaming()
	{
		assertEquals("drops_NPC_Vardorvis", vardorvis.getAllTimeSources().get(0).getLootTrackerKey());
		assertEquals("vardorvis", vardorvis.getAllTimeSources().get(0).getKillCountKey());
		assertEquals("drops_NPC_Duke Sucellus", duke.getAllTimeSources().get(0).getLootTrackerKey());
		assertEquals("duke sucellus", duke.getAllTimeSources().get(0).getKillCountKey());
		assertEquals("drops_NPC_The Whisperer", whisperer.getAllTimeSources().get(0).getLootTrackerKey());
		assertEquals("whisperer", whisperer.getAllTimeSources().get(0).getKillCountKey());
		assertEquals("drops_NPC_The Leviathan", leviathan.getAllTimeSources().get(0).getLootTrackerKey());
		assertEquals("leviathan", leviathan.getAllTimeSources().get(0).getKillCountKey());
		assertEquals("Uniques", leviathan.getProfitCell().getLabel());
		assertEquals("duke-sucellus", duke.getFileSlug());
	}

	private static void assertRate(BossDefinition boss, int itemId, double rate)
	{
		assertEquals(boss.getDisplayName() + " " + itemId, rate, chance(boss, itemId, KillContext.DEFAULT), 1e-12);
	}

	private static double chance(BossDefinition boss, int itemId, KillContext context)
	{
		for (ExpectedDrop drop : boss.getDrops())
		{
			if (drop.getItemId() == itemId)
			{
				return drop.chance(context);
			}
		}
		throw new AssertionError(boss.getDisplayName() + " doesn't expect " + itemId);
	}
}
