package com.bosstriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

/**
 * Checks every boss in the table: each item and NPC id is a gameval constant, ids and regions don't clash between
 * bosses, and the drop rates add up. (Reflection is only used here, to list the gameval constants.)
 */
public class BossTableTest
{
	private static final Set<Integer> ITEM_IDS = constants(ItemID.class);
	private static final Set<Integer> NPC_IDS = constants(NpcID.class);
	/**
	 * Variants the rates are checked under, with team sizes for the team-share bosses.
	 */
	private static final Integer[] TEAM_SIZES = {null, 1, 2, 3, 4, 5};

	@Test
	public void everyIdIsAGamevalConstant()
	{
		for (BossDefinition boss : BossRegistry.standard().all())
		{
			String name = boss.getDisplayName();
			assertItem(name, "icon", boss.getIconItemId());
			assertNpc(name, "name NPC", boss.getNameNpcId());
			boss.getBossNpcIds().forEach(id -> assertNpc(name, "boss NPC", id));
			boss.getLootTriggerNpcs().forEach(id -> assertNpc(name, "loot NPC", id));
			boss.getGraveHelperNpcs().forEach(id -> assertNpc(name, "grave NPC", id));
			boss.getDrops().forEach(drop -> assertItem(name, "drop", drop.getItemId()));
			boss.getEggPetRates().keySet().forEach(id -> assertItem(name, "egg", id));
			boss.getTarnishedItems().forEach(id -> assertItem(name, "tarnished item", id));
			boss.getRecoverableItems().forEach(id -> assertItem(name, "recoverable item", id));
			boss.getConvertedItems().forEach(id -> assertItem(name, "converted item", id));
			boss.getGravePaymentItems().forEach(id -> assertItem(name, "grave payment", id));
			boss.getLootCategoryMap().keySet().forEach(id -> assertItem(name, "loot category item", id));
		}
	}

	@Test
	public void bossesDontShareIdsOrRegions()
	{
		Set<String> ids = new HashSet<>();
		Set<Integer> regions = new HashSet<>();
		Set<Integer> npcs = new HashSet<>();
		for (BossDefinition boss : BossRegistry.standard().all())
		{
			assertTrue("Duplicate boss id " + boss.getId(), ids.add(boss.getId()));
			assertFalse(boss.getDisplayName() + " has no regions", boss.getRegions().isEmpty());
			assertFalse(boss.getDisplayName() + " has no boss NPCs", boss.getBossNpcIds().isEmpty());
			for (int region : boss.getRegions())
			{
				assertTrue("Region " + region + " is in two bosses", regions.add(region));
			}
			for (int npc : boss.getBossNpcIds())
			{
				assertTrue("NPC " + npc + " is in two bosses", npcs.add(npc));
			}
			for (int region : boss.getWaitingRegions())
			{
				assertFalse(boss.getDisplayName() + " waits in its own region " + region, boss.getRegions().contains(region));
			}
		}
	}

	@Test
	public void ratesAreProbabilitiesAndUniquesAddUp()
	{
		for (BossDefinition boss : BossRegistry.standard().all())
		{
			for (String variant : variants(boss))
			{
				for (Integer team : TEAM_SIZES)
				{
					KillContext context = new KillContext(variant, team);
					String where = boss.getDisplayName() + " (" + variant + ", team " + team + ")";
					double uniques = 0;
					for (ExpectedDrop drop : boss.getDrops())
					{
						double chance = drop.chance(context);
						assertTrue(where + ": chance of " + drop.getItemId() + " is " + chance, chance >= 0 && chance < 1);
						if (drop.getKind() == DropKind.UNIQUE)
						{
							uniques += chance;
						}
					}
					double any = boss.anyUniqueChance(context);
					assertTrue(where + ": any unique " + any, any >= 0 && any < 1);
					// The published "any unique" rate is rounded (Maggot King 1/205.6): within 0.5%
					assertEquals(where + ": uniques add up to the any-unique chance", any, uniques, any * 0.005);
				}
			}
		}
	}

	@Test
	public void everyBossHasItsRecordsAndUniques()
	{
		for (BossDefinition boss : BossRegistry.standard().all())
		{
			assertFalse(boss.getDisplayName() + " has no all-time records", boss.getAllTimeSources().isEmpty());
			assertTrue(boss.getDisplayName() + " has no uniques",
				boss.getDrops().stream().anyMatch(drop -> drop.getKind() == DropKind.UNIQUE));
			assertNotNull(boss.getProfitCell());
			assertTrue(boss.getDisplayName() + " is not a table entry", boss instanceof TableBoss);
		}
	}

	/**
	 * A boss made from data alone: the default uniques profit cell, and any unique as the sum of its uniques.
	 */
	@Test
	public void aDataOnlyBossWorks()
	{
		TableBoss boss = new TableBoss(BossData.builder()
			.id("example")
			.displayName("Example")
			.iconItemId(ItemID.COINS)
			.nameNpcId(NpcID.MAGGOT_KING)
			.bossNpcIds(Set.of(NpcID.MAGGOT_KING))
			.regions(Set.of(1))
			.drops(List.of(
				ExpectedDrop.fixed(ItemID.ELDER_VENATOR_FANG, DropKind.UNIQUE, 1 / 100.0),
				ExpectedDrop.fixed(ItemID.CRIMSON_KISTEN, DropKind.UNIQUE, 1 / 300.0),
				ExpectedDrop.fixed(ItemID.MAGGOTKINGPET, DropKind.PET, 1 / 1000.0)))
			.build());
		assertEquals(1 / 75.0, boss.anyUniqueChance(KillContext.DEFAULT), 1e-12);
		assertEquals("Uniques", boss.getProfitCell().getLabel());
		assertEquals("kill", boss.getUnitNoun());
		assertEquals("No trips yet. Go to Example to start one.", boss.getEmptyStateText());
		assertTrue(boss.getVariants().isEmpty());
	}

	private static List<String> variants(BossDefinition boss)
	{
		List<String> variants = new java.util.ArrayList<>();
		variants.add(null);
		boss.getVariants().forEach(v -> variants.add(v.getId()));
		return variants;
	}

	private static void assertItem(String boss, String what, int id)
	{
		assertTrue(boss + ": " + what + " " + id + " is not an ItemID constant", ITEM_IDS.contains(id));
	}

	private static void assertNpc(String boss, String what, int id)
	{
		assertTrue(boss + ": " + what + " " + id + " is not an NpcID constant", NPC_IDS.contains(id));
	}

	private static Set<Integer> constants(Class<?> type)
	{
		Set<Integer> ids = new HashSet<>();
		for (Field field : type.getFields())
		{
			if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class)
			{
				try
				{
					ids.add(field.getInt(null));
				}
				catch (IllegalAccessException e)
				{
					throw new AssertionError(e);
				}
			}
		}
		return ids;
	}
}
