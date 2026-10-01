package com.bosstriptracker.boss;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

/**
 * Names of the loot box categories, and a builder for a boss's item-to-category map. Categories are worked out
 * when the view is built and never saved, so they can be renamed freely.
 */
public final class LootCategories
{
	public static final String UNIQUES = "Uniques";
	public static final String JEWELLERY = "Jewellery";
	public static final String WEAPONS = "Weapons";
	public static final String RESOURCES = "Resources";
	public static final String EGGS = "Eggs";
	public static final String RUNES = "Runes";
	public static final String RUNES_AND_AMMO = "Runes & ammo";
	public static final String CONSUMABLES = "Consumables";
	public static final String COINS = "Coins";
	public static final String HERBS_AND_SEEDS = "Herbs & seeds";
	public static final String GEAR = "Gear";
	public static final String OTHER = "Other";

	private LootCategories()
	{
	}

	static Builder builder()
	{
		return new Builder();
	}

	static final class Builder
	{
		private final ImmutableMap.Builder<Integer, String> map = ImmutableMap.builder();

		/**
		 * @param itemIds unnoted item ids; noted drops are canonicalised before the lookup
		 */
		Builder put(String category, int... itemIds)
		{
			for (int itemId : itemIds)
			{
				map.put(itemId, category);
			}
			return this;
		}

		/**
		 * @throws IllegalArgumentException if an item was put in two categories
		 */
		Map<Integer, String> build()
		{
			return map.build();
		}
	}
}
