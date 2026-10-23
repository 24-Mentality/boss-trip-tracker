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
 * The Phantom Muspah, in its instanced cavern under Ghorrock. Regions, messages and the fight clock are from the
 * diagnostic log of 2026-10-23 (2 kills, KC 916 and 917); rates are from the OSRS Wiki.
 */
public final class PhantomMuspahBoss extends TableBoss
{
	public static final String ID = "phantom_muspah";
	/**
	 * The icy cavern ("You enter the icy cavern.", instanced) and the Ghorrock corridor it's entered from.
	 */
	public static final int CAVERN_REGION_ID = 11330;
	public static final int CORRIDOR_REGION_ID = 11681;

	public static final double VENATOR_SHARD_RATE = 1 / 100.0;
	public static final double ANCIENT_ICON_RATE = 2 / 100.0;
	public static final double FROZEN_CACHE_RATE = 4 / 100.0;
	public static final double PET_RATE = 1 / 2_500.0;

	private static final List<ExpectedDrop> DROPS = ImmutableList.of(
		ExpectedDrop.fixed(ItemID.VENATOR_SHARD, DropKind.UNIQUE, VENATOR_SHARD_RATE),
		ExpectedDrop.fixed(ItemID.MUSPAHPET, DropKind.PET, PET_RATE),
		ExpectedDrop.fixed(ItemID.ANCIENT_ICON, DropKind.TERTIARY, ANCIENT_ICON_RATE),
		ExpectedDrop.fixed(ItemID.FROZEN_CACHE, DropKind.TERTIARY, FROZEN_CACHE_RATE));

	/**
	 * The OSRS Wiki's drop table groups.
	 */
	private static final Map<Integer, String> LOOT_CATEGORIES = LootCategories.builder()
		.put(LootCategories.UNIQUES, ItemID.VENATOR_SHARD, ItemID.MUSPAHPET)
		.put(LootCategories.CONSUMABLES, ItemID.SHARK, ItemID.SUMMER_PIE, ItemID._3DOSEANCIENTBREW,
			ItemID._3DOSERANGERSPOTION, ItemID._3DOSE2RESTORE, ItemID._3DOSEPRAYERRESTORE)
		.put(LootCategories.GEAR, ItemID.RUNE_KITESHIELD, ItemID.DRAGON_PLATESKIRT, ItemID.RUNE_PLATELEGS,
			ItemID.BLACK_DRAGONHIDE_BODY, ItemID.DRAGON_PLATELEGS, ItemID.RUNE_SWORD)
		.put(LootCategories.RUNES_AND_AMMO, ItemID.LAWRUNE, ItemID.SOULRUNE, ItemID.DEATHRUNE, ItemID.SMOKERUNE,
			ItemID.CHAOSRUNE, ItemID.FIRERUNE, ItemID.MCANNONBALL)
		.put(LootCategories.HERBS_AND_SEEDS, ItemID.UNIDENTIFIED_TOADFLAX, ItemID.UNIDENTIFIED_KWUARM,
			ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.UNIDENTIFIED_CADANTINE, ItemID.UNIDENTIFIED_LANTADYME, ItemID.YEW_SEED,
			ItemID.TORSTOL_SEED, ItemID.PALM_TREE_SEED, ItemID.RANARR_SEED, ItemID.SNAPDRAGON_SEED, ItemID.SPIRIT_TREE_SEED,
			ItemID.WATERMELON_SEED, ItemID.WILLOW_SEED, ItemID.MAHOGANY_SEED, ItemID.MAPLE_SEED, ItemID.TEAK_SEED,
			ItemID.PAPAYA_TREE_SEED, ItemID.MAGIC_TREE_SEED, ItemID.DRAGONFRUIT_TREE_SEED, ItemID.REDWOOD_TREE_SEED)
		.put(LootCategories.RESOURCES, ItemID.ADAMANTITE_ORE, ItemID.GOLD_ORE, ItemID.PLANK_TEAK, ItemID.MOLTEN_GLASS,
			ItemID.BLANKRUNE_HIGH, ItemID.COAL, ItemID.RUNITE_ORE, ItemID.SILVER_ORE, ItemID.WATER_ORB,
			ItemID.DRAGON_BOLTS_UNFEATHERED, ItemID.LIMPWURT_ROOT, ItemID.RAW_SHARK, ItemID.SHARK_LURE, ItemID.MANTARAY)
		.put(LootCategories.OTHER, ItemID.ANCIENT_ESSENCE, ItemID.ANCIENT_ICON, ItemID.FROZEN_CACHE, ItemID.KONAR_KEY)
		.build();

	/**
	 * Keys predicted from the NPC's name; RuneLite's Chat Commands lists "Phantom Muspah".
	 */
	private static final List<AllTimeSource> ALL_TIME = ImmutableList.of(
		new AllTimeSource("drops_NPC_Phantom Muspah", "phantom muspah", null));

	static final BossData DATA = BossData.builder()
		.id(ID)
		.displayName("Phantom Muspah")
		.iconItemId(ItemID.MUSPAHPET)
		.nameNpcId(NpcID.MUSPAH)
		// Its forms through the fight; a change between them doesn't restart the clock
		.bossNpcIds(ImmutableSet.of(NpcID.MUSPAH, NpcID.MUSPAH_MELEE, NpcID.MUSPAH_SOULSPLIT, NpcID.MUSPAH_FINAL,
			NpcID.MUSPAH_TELEPORT))
		.regions(ImmutableSet.of(CAVERN_REGION_ID))
		.waitingRegions(ImmutableSet.of(CORRIDOR_REGION_ID))
		.drops(DROPS)
		.allTimeSources(ALL_TIME)
		.lootCategories(LOOT_CATEGORIES)
		.areaNoun("cavern")
		.emptyStateText("No trips yet. Enter the Phantom Muspah's cavern to start one.")
		.build();

	public PhantomMuspahBoss()
	{
		super(DATA);
	}

	/**
	 * The game's clock started 5 ticks after the cavern loaded and, for the second kill, exactly 50 ticks after the
	 * first (the wiki's respawn time): when the Muspah spawns. The spawn itself wasn't logged, so this is the best fit.
	 */
	@Override
	public boolean isFightStartOnSpawn()
	{
		return true;
	}

	/**
	 * Dying leaves your items in a gravestone outside the instance (OSRS Wiki); the fee is estimated at death and
	 * replaced by the bank payment message when the grave is checked (the message is the one seen at the Doom of
	 * Mokhaiotl's grave).
	 */
	@Override
	public boolean isGravestoneFeeEstimated()
	{
		return true;
	}

	@Override
	public Set<Integer> getGraveHelperNpcs()
	{
		return FracturedArchiveBoss.GRAVESTONES;
	}

	@Override
	public Long reclaimFee(String text)
	{
		return ChatPatterns.bankPayment(text);
	}
}
