package com.bosstriptracker.boss;

import com.bosstriptracker.tracking.ChatPatterns;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * Vorkath, on his island off Ungael. Regions, messages and the fight clock are from the diagnostic log of 2026-10-23
 * (2 kills, KC 4,339 and 4,340); rates are from the OSRS Wiki.
 */
public final class VorkathBoss extends TableBoss
{
	public static final String ID = "vorkath";
	/**
	 * Vorkath's island (the log reports it as not instanced) and Ungael, where Torfinn's boat lands.
	 */
	public static final int ISLAND_REGION_ID = 9023;
	public static final int UNGAEL_REGION_ID = 10553;

	public static final double DRAGONBONE_NECKLACE_RATE = 1 / 1_000.0;
	public static final double VISAGE_RATE = 1 / 5_000.0;
	public static final double PET_RATE = 1 / 3_000.0;
	public static final double JAR_RATE = 1 / 3_000.0;
	/**
	 * Items lost dying are kept by Torfinn for 100,000 coins (OSRS Wiki). Unverified: no death in the logs, so the
	 * fee is recorded at death and replaced by a bank payment message if one is seen.
	 */
	public static final long DEATH_FEE = 100_000;

	private static final List<ExpectedDrop> DROPS = ImmutableList.of(
		ExpectedDrop.fixed(ItemID.DRAGONBONE_NECKLACE, DropKind.UNIQUE, DRAGONBONE_NECKLACE_RATE),
		ExpectedDrop.fixed(ItemID.SKELETAL_VISAGE, DropKind.UNIQUE, VISAGE_RATE),
		ExpectedDrop.fixed(ItemID.DRAGONFIRE_VISAGE, DropKind.UNIQUE, VISAGE_RATE),
		ExpectedDrop.fixed(ItemID.VORKATHPET, DropKind.PET, PET_RATE),
		ExpectedDrop.fixed(ItemID.JAR_OF_DECAY, DropKind.TERTIARY, JAR_RATE));

	private static final Set<Integer> TORFINN = ImmutableSet.of(NpcID.TORFINN_COLLECT_RELLEKKA, NpcID.TORFINN_COLLECT_UNGAEL,
		NpcID.TORFINN_TRAVEL_RELLEKKA, NpcID.TORFINN_TRAVEL_UNGAEL);

	/**
	 * The OSRS Wiki's drop table groups.
	 */
	private static final Map<Integer, String> LOOT_CATEGORIES = LootCategories.builder()
		.put(LootCategories.UNIQUES, ItemID.DRAGONBONE_NECKLACE, ItemID.SKELETAL_VISAGE, ItemID.DRAGONFIRE_VISAGE,
			ItemID.VORKATHPET)
		.put(LootCategories.GEAR, ItemID.RUNE_LONGSWORD, ItemID.RUNE_KITESHIELD, ItemID.BATTLESTAFF, ItemID.DRAGON_BATTLEAXE,
			ItemID.DRAGON_LONGSWORD, ItemID.DRAGON_PLATELEGS, ItemID.DRAGON_PLATESKIRT)
		.put(LootCategories.RUNES_AND_AMMO, ItemID.CHAOSRUNE, ItemID.DEATHRUNE, ItemID.WRATHRUNE)
		.put(LootCategories.RESOURCES, ItemID.DRAGON_BONES_SUPERIOR, ItemID.DRAGON_BONES, ItemID.DRAGONHIDE_BLUE,
			ItemID.DRAGONHIDE_GREEN, ItemID.DRAGONHIDE_RED, ItemID.DRAGONHIDE_BLACK, ItemID.SCALY_BLUEHIDE,
			ItemID.DRAGON_BOLTS_UNFEATHERED, ItemID.XBOWS_BOLT_TIPS_DRAGONSTONE, ItemID.XBOWS_BOLT_TIPS_ONYX,
			ItemID.XBOWS_BOLT_TIPS_DIAMOND, ItemID.XBOWS_BOLT_TIPS_EMERALD, ItemID.XBOWS_BOLT_TIPS_RUBY,
			ItemID.XBOWS_BOLT_TIPS_SAPPHIRE, ItemID.RUNE_DART_TIP, ItemID.DRAGON_DART_TIP, ItemID.DRAGON_ARROWHEADS,
			ItemID.ADAMANTITE_ORE, ItemID.MAGIC_LOGS, ItemID.DIAMOND, ItemID.DRAGONSTONE)
		.put(LootCategories.HERBS_AND_SEEDS, ItemID.RANARR_SEED, ItemID.SNAPDRAGON_SEED, ItemID.TORSTOL_SEED,
			ItemID.WATERMELON_SEED, ItemID.WILLOW_SEED, ItemID.MAHOGANY_SEED, ItemID.MAPLE_SEED, ItemID.TEAK_SEED,
			ItemID.YEW_SEED, ItemID.PAPAYA_TREE_SEED, ItemID.MAGIC_TREE_SEED, ItemID.PALM_TREE_SEED, ItemID.SPIRIT_TREE_SEED,
			ItemID.DRAGONFRUIT_TREE_SEED, ItemID.REDWOOD_TREE_SEED)
		.put(LootCategories.COINS, ItemID.COINS)
		.put(LootCategories.OTHER, ItemID.GRAPES, ItemID.WRATH_TALISMAN, ItemID.VORKATH_HEAD, ItemID.JAR_OF_DECAY,
			ItemID.KONAR_KEY)
		.build();

	/**
	 * Keys predicted from the NPC's name; RuneLite's Chat Commands lists "Vorkath".
	 */
	private static final List<AllTimeSource> ALL_TIME = ImmutableList.of(new AllTimeSource("drops_NPC_Vorkath", "vorkath", null));

	static final BossData DATA = BossData.builder()
		.id(ID)
		.displayName("Vorkath")
		.iconItemId(ItemID.VORKATHPET)
		.nameNpcId(NpcID.VORKATH)
		// The sleeping Vorkath is only poked; his spawn's death mustn't end the kill clock
		.bossNpcIds(ImmutableSet.of(NpcID.VORKATH))
		.regions(ImmutableSet.of(ISLAND_REGION_ID))
		.waitingRegions(ImmutableSet.of(UNGAEL_REGION_ID))
		.drops(DROPS)
		.allTimeSources(ALL_TIME)
		.lootCategories(LOOT_CATEGORIES)
		.areaNoun("island")
		.emptyStateText("No trips yet. Sail to Vorkath's island to start one.")
		.build();

	public VorkathBoss()
	{
		super(DATA);
	}

	/**
	 * The game's clock began 1 and 2 ticks after the first hitsplat on both kills, 13 and 15 ticks after "Poke" on the
	 * sleeping Vorkath: the first hit is the closest signal (within 1.2 s).
	 */
	@Override
	public boolean isFightStartOnSpawn()
	{
		return false;
	}

	@Override
	public boolean isFightStartOnFirstHit()
	{
		return true;
	}

	@Override
	public long getDeathFee()
	{
		return DEATH_FEE;
	}

	@Override
	public Set<Integer> getGraveHelperNpcs()
	{
		return TORFINN;
	}

	@Override
	public Long reclaimFee(String text)
	{
		return ChatPatterns.bankPayment(text);
	}
}
