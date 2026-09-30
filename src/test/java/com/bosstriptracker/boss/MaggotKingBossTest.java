package com.bosstriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableSet;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

public class MaggotKingBossTest
{
	private final BossRegistry registry = BossRegistry.standard();
	private final BossDefinition boss = registry.byId(MaggotKingBoss.ID);

	@Test
	public void registryFindsTheMaggotKingByRegionAndItem()
	{
		assertSame(boss, registry.first());
		assertSame(boss, registry.forRegion(11645));
		// Just outside the lair isn't the trip area
		assertNull(registry.forRegion(10618));
		assertTrue(boss.getWaitingRegions().contains(10618));
		assertSame(boss, registry.forEgg(ItemID.WRITHING_MAGGOT_EGG));
		assertSame(boss, registry.forTarnished(ItemID.TARNISHED_AMULET));
		assertNull(registry.forEgg(ItemID.COINS));
		assertNull(registry.byId("nope"));
	}

	@Test
	public void ratesMatchTheWiki()
	{
		assertEquals(1 / 205.6, boss.anyUniqueChance(KillContext.DEFAULT), 1e-12);
		assertEquals(3, boss.getDrops().size());
		assertEquals(ItemID.ELDER_VENATOR_FANG, boss.getDrops().get(0).getItemId());
		assertEquals(1 / 340.0, boss.getDrops().get(0).chance(KillContext.DEFAULT), 1e-12);
		assertEquals(1 / 520.0, boss.getDrops().get(1).chance(KillContext.DEFAULT), 1e-12);
		assertEquals(ItemID.MAGGOTKINGPET, boss.getPet().getItemId());
		assertEquals(1 / 3500.0, boss.getPet().chance(KillContext.DEFAULT), 1e-12);
		assertEquals(6, boss.getEggPetRates().size());
		assertEquals(1 / 2.0, boss.getEggPetRates().get(ItemID.WRITHING_MAGGOT_EGG), 1e-12);
		assertEquals(ImmutableSet.of(ItemID.ELDER_VENATOR_FANG, ItemID.CRIMSON_KISTEN, ItemID.MAGGOTKINGPET),
			boss.getHighlightedItems());
		assertTrue(boss.isUnique(ItemID.CRIMSON_KISTEN));
		assertFalse(boss.isUnique(ItemID.MAGGOTKINGPET));
	}

	@Test
	public void onlyOpenStomachCountsForLuck()
	{
		assertEquals("STOMACH", boss.choiceForOption("open-stomach").getKey());
		assertEquals("EGGS", boss.choiceForOption("Take-eggs").getKey());
		assertNull(boss.choiceForOption("Examine"));

		Kill kill = new Kill();
		kill.setChoice("STOMACH");
		assertTrue(boss.countsForLuck(kill));
		kill.setChoice("EGGS");
		assertFalse(boss.countsForLuck(kill));
		kill.setChoice(null);
		assertFalse(boss.countsForLuck(kill));
	}

	@Test
	public void encounterIds()
	{
		assertEquals(ImmutableSet.of(NpcID.MAGGOT_KING), boss.getBossNpcIds());
		assertEquals(ImmutableSet.of(NpcID.MAGGOT_KING_CORPSE), boss.getLootTriggerNpcs());
		assertTrue(boss.getGravePaymentItems().contains(ItemID.VIAL_BLOOD));
		assertTrue(boss.getConvertedItems().contains(ItemID.MAGGOT_EGG));
		assertTrue(boss.getConvertedItems().contains(ItemID.TARNISHED_SPEAR));
		assertTrue(boss.isGroundOverflowLoot());
	}

	@Test
	public void allTimeKeysAreTheOnesSeenInTheProfileConfig()
	{
		assertEquals(1, boss.getAllTimeSources().size());
		assertEquals("drops_NPC_Maggot King", boss.getAllTimeSources().get(0).getLootTrackerKey());
		assertEquals("maggot king", boss.getAllTimeSources().get(0).getKillCountKey());
	}

	@Test
	public void profitCellAndCsvColumnsSplitStomachAndEggs()
	{
		Trip trip = new Trip();
		for (String choice : new String[]{"STOMACH", "STOMACH", "EGGS"})
		{
			Kill kill = new Kill();
			kill.setChoice(choice);
			trip.getKills().add(kill);
		}
		assertEquals("Stom / Eggs", boss.getProfitCell().getLabel());
		assertEquals("2 / 1", boss.getProfitCell().valueOf(trip));
		assertEquals("stomach", boss.getCsvColumns().get(0).getLabel());
		assertEquals("2", boss.getCsvColumns().get(0).valueOf(trip));
		assertEquals("1", boss.getCsvColumns().get(1).valueOf(trip));
		assertEquals("maggot-king", boss.getFileSlug());
	}
}
