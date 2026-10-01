package com.bosstriptracker.view;

import static org.junit.Assert.assertEquals;
import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.DropKind;
import com.bosstriptracker.boss.ExpectedDrop;
import com.bosstriptracker.boss.LootCategories;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.boss.NightmareBoss;
import com.bosstriptracker.boss.TheatreOfBloodBoss;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.pricing.PriceService;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.Test;

/**
 * Loot box categories: each boss's drop table groups, the generic fallback and folding into Other.
 */
public class LootCategoryTest
{
	private final BossDefinition maggotKing = new MaggotKingBoss();
	private final BossDefinition nightmare = new NightmareBoss();
	private final BossDefinition tob = new TheatreOfBloodBoss();
	private final ViewBuilder builder = new ViewBuilder(new PriceService(null)
	{
		@Override
		public long price(int itemId)
		{
			return 0;
		}

		@Override
		public String name(int itemId)
		{
			return "Item " + itemId;
		}

		@Override
		public int canonicalize(int itemId)
		{
			return itemId == ItemID.Cert.GOLD_ORE ? ItemID.GOLD_ORE : itemId;
		}

		@Override
		public boolean isFood(int itemId)
		{
			return itemId == ItemID.ANGLERFISH;
		}

		@Override
		public boolean isDrinkable(int itemId)
		{
			return itemId == ItemID._4DOSEPRAYERRESTORE;
		}

		@Override
		public boolean isEquipable(int itemId)
		{
			return itemId == ItemID.RUNE_SPEAR || itemId == ItemID.DRAGON_DAGGER;
		}
	});

	@Before
	public void runes()
	{
		builder.setRuneIds(ImmutableSet.of(ItemID.FIRERUNE));
	}

	@Test
	public void everyUniqueAndPetIsAUnique()
	{
		for (BossDefinition boss : Arrays.asList(maggotKing, nightmare, tob))
		{
			for (ExpectedDrop drop : boss.getDrops())
			{
				if (drop.getKind() == DropKind.UNIQUE || drop.getKind() == DropKind.PET)
				{
					assertEquals(boss.getId() + " " + drop.getItemId(), LootCategories.UNIQUES,
						builder.lootCategory(boss, drop.getItemId(), 0));
				}
			}
		}
	}

	@Test
	public void tobHardModeKitsAndDustAreUniques()
	{
		for (int id : new int[]{ItemID.TOB_HARDMODE_KIT, ItemID.TOB_HARDMODE_KIT_BLOOD, ItemID.TOB_HARDMODE_DUST})
		{
			assertEquals(LootCategories.UNIQUES, builder.lootCategory(tob, id, 0));
		}
	}

	@Test
	public void bossGroups()
	{
		assertEquals(LootCategories.EGGS, builder.lootCategory(maggotKing, ItemID.WRITHING_MAGGOT_EGG, 0));
		assertEquals(LootCategories.RESOURCES, builder.lootCategory(maggotKing, ItemID.VENATOR_TOOTH, 0));
		assertEquals(LootCategories.OTHER, builder.lootCategory(maggotKing, ItemID.DULL_ZAROSIAN_MEDAL, 0));
		assertEquals(LootCategories.RUNES_AND_AMMO, builder.lootCategory(nightmare, ItemID.MCANNONBALL, 0));
		assertEquals(LootCategories.CONSUMABLES, builder.lootCategory(nightmare, ItemID.SHARK, 0));
		assertEquals(LootCategories.HERBS_AND_SEEDS, builder.lootCategory(tob, ItemID.UNIDENTIFIED_TORSTOL, 0));
		assertEquals(LootCategories.GEAR, builder.lootCategory(tob, ItemID.RUNE_PLATEBODY, 0));
	}

	@Test
	public void notedDropsUseTheUnnotedItem()
	{
		assertEquals(LootCategories.RESOURCES, builder.lootCategory(nightmare, ItemID.Cert.GOLD_ORE, 0));
	}

	@Test
	public void polishedItemsGoWithTheirTarnishedType()
	{
		// The same result files by what it was polished from, not by the item itself
		assertEquals(LootCategories.WEAPONS, builder.lootCategory(maggotKing, ItemID.RUNE_SPEAR, ItemID.TARNISHED_SPEAR));
		assertEquals(LootCategories.JEWELLERY, builder.lootCategory(maggotKing, ItemID.RUNE_SPEAR, ItemID.TARNISHED_RING));
		assertEquals(LootCategories.JEWELLERY, builder.lootCategory(maggotKing, ItemID.TARNISHED_AMULET, 0));
		assertEquals(LootCategories.WEAPONS, builder.lootCategory(maggotKing, ItemID.TARNISHED_2H_SWORD, 0));
		// Not polished: the generic rules
		assertEquals(LootCategories.GEAR, builder.lootCategory(maggotKing, ItemID.RUNE_SPEAR, 0));
	}

	@Test
	public void fallbackOrder()
	{
		assertEquals(LootCategories.COINS, builder.lootCategory(maggotKing, ItemID.COINS, 0));
		assertEquals(LootCategories.RUNES, builder.lootCategory(maggotKing, ItemID.FIRERUNE, 0));
		assertEquals(LootCategories.GEAR, builder.lootCategory(maggotKing, ItemID.DRAGON_DAGGER, 0));
		assertEquals(LootCategories.CONSUMABLES, builder.lootCategory(maggotKing, ItemID.ANGLERFISH, 0));
		assertEquals(LootCategories.CONSUMABLES, builder.lootCategory(maggotKing, ItemID._4DOSEPRAYERRESTORE, 0));
		assertEquals(LootCategories.OTHER, builder.lootCategory(maggotKing, ItemID.BONES, 0));
		// A boss's own group wins over the fallback (the Nightmare files its loot runes as Runes & ammo)
		assertEquals(LootCategories.RUNES_AND_AMMO, builder.lootCategory(nightmare, ItemID.BLOODRUNE, 0));
	}

	@Test
	public void groupsByCategoryHighestValueFirst()
	{
		List<ItemEntry> loot = Arrays.asList(
			new ItemEntry(ItemID.VENATOR_TOOTH, 10, 1_000),
			new ItemEntry(ItemID.UNCUT_RUBY, 5, 1_000),
			new ItemEntry(ItemID.ELDER_VENATOR_FANG, 1, 40_000),
			new ItemEntry(ItemID.DULL_ZAROSIAN_MEDAL, 1, 100_000));

		List<LootCategory> categories = builder.lootCategories(maggotKing, loot);

		assertEquals(3, categories.size());
		assertEquals(LootCategories.UNIQUES, categories.get(0).getName());
		assertEquals(40_000, categories.get(0).getValue());
		assertEquals(LootCategories.RESOURCES, categories.get(1).getName());
		assertEquals(15_000, categories.get(1).getValue());
		assertEquals(2, categories.get(1).getItems().size());
		// Other comes last even when it's worth the most
		assertEquals(LootCategories.OTHER, categories.get(2).getName());
		assertEquals(100_000, categories.get(2).getValue());
	}

	@Test
	public void fiveOrFewerAreAllShown()
	{
		List<LootCategory> categories = ViewBuilder.foldCategories(Arrays.asList(
			category("A", 5), category("B", 4), category("C", 3), category("D", 2), category(LootCategories.OTHER, 9)));

		assertEquals(Arrays.asList("A", "B", "C", "D", LootCategories.OTHER), names(categories));
	}

	@Test
	public void moreThanFiveFoldIntoOther()
	{
		List<LootCategory> categories = ViewBuilder.foldCategories(Arrays.asList(
			category("E", 1), category("A", 5), category("B", 4), category(LootCategories.OTHER, 7), category("C", 3),
			category("D", 2)));

		assertEquals(Arrays.asList("A", "B", "C", "D", LootCategories.OTHER), names(categories));
		// The real Other (7) and E (1)
		assertEquals(8, categories.get(4).getValue());
		assertEquals(2, categories.get(4).getItems().size());
	}

	@Test
	public void sixNamedCategoriesFoldTheSmallestTwo()
	{
		List<LootCategory> categories = ViewBuilder.foldCategories(Arrays.asList(
			category("A", 6), category("B", 5), category("C", 4), category("D", 3), category("E", 2), category("F", 1)));

		assertEquals(Arrays.asList("A", "B", "C", "D", LootCategories.OTHER), names(categories));
		assertEquals(3, categories.get(4).getValue());
	}

	@Test
	public void noLootNoCategories()
	{
		assertEquals(Collections.emptyList(), builder.lootCategories(maggotKing, Collections.<ItemEntry>emptyList()));
	}

	private static LootCategory category(String name, long value)
	{
		return new LootCategory(name, value, Collections.singletonList(
			new ItemView(1, name, 1, value, false, false, false, null, 0, null, null)));
	}

	private static List<String> names(List<LootCategory> categories)
	{
		List<String> names = new ArrayList<>();
		for (LootCategory category : categories)
		{
			names.add(category.getName());
		}
		return names;
	}
}
