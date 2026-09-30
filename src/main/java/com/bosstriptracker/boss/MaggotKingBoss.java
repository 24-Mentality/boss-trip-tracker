package com.bosstriptracker.boss;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.bosstriptracker.model.TripMath;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * The Maggot King in Vampyrium: a solo instanced boss with several kills per trip. Ids and messages are from the
 * diagnostic logs (PROJECT_BRIEF.md, "Observed in-game"); rates are from the OSRS Wiki.
 */
public final class MaggotKingBoss extends BossDefinition
{
	public static final String ID = "maggot_king";

	/**
	 * Template region of the lair. There is no gameval for regions.
	 */
	public static final int LAIR_REGION_ID = 11645;
	/**
	 * Just outside the lair: where the lair exit leads.
	 */
	public static final int LAIR_ENTRANCE_REGION_ID = 10618;

	public static final int BOSS = NpcID.MAGGOT_KING;
	public static final int CORPSE = NpcID.MAGGOT_KING_CORPSE;
	public static final int PET_ITEM = ItemID.MAGGOTKINGPET;
	public static final int FANG = ItemID.ELDER_VENATOR_FANG;
	public static final int KISTEN = ItemID.CRIMSON_KISTEN;

	public static final double ANY_UNIQUE = 1 / 205.6;
	public static final double FANG_RATE = 1 / 340.0;
	public static final double KISTEN_RATE = 1 / 520.0;
	public static final double PET_PER_STOMACH = 1 / 3500.0;

	/**
	 * Open-stomach rolls the drop table (uniques and the kill pet); Take-eggs rolls the egg table.
	 */
	public static final LootChoice STOMACH = new LootChoice("Open-stomach", "STOMACH", "Stomach", true);
	public static final LootChoice EGGS = new LootChoice("Take-eggs", "EGGS", "Eggs", false);

	private static final Map<Integer, Double> EGG_PET = ImmutableMap.<Integer, Double>builder()
		.put(ItemID.MAGGOT_EGG, 1 / 3000.0)
		.put(ItemID.SICKLY_MAGGOT_EGG, 1 / 2500.0)
		.put(ItemID.WARM_MAGGOT_EGG, 1 / 1250.0)
		.put(ItemID.PULSATING_MAGGOT_EGG, 1 / 500.0)
		.put(ItemID.WRIGGLING_MAGGOT_EGG, 1 / 10.0)
		.put(ItemID.WRITHING_MAGGOT_EGG, 1 / 2.0)
		.build();

	private static final Set<Integer> TARNISHED = ImmutableSet.of(
		ItemID.TARNISHED_LONGSWORD,
		ItemID.TARNISHED_SPEAR,
		ItemID.TARNISHED_2H_SWORD,
		ItemID.TARNISHED_BATTLEAXE,
		ItemID.TARNISHED_HALBERD,
		ItemID.TARNISHED_RING,
		ItemID.TARNISHED_BRACELET,
		ItemID.TARNISHED_NECKLACE,
		ItemID.TARNISHED_AMULET
	);

	private static final List<ExpectedDrop> DROPS = ImmutableList.of(
		ExpectedDrop.fixed(FANG, DropKind.UNIQUE, FANG_RATE),
		ExpectedDrop.fixed(KISTEN, DropKind.UNIQUE, KISTEN_RATE),
		ExpectedDrop.fixed(PET_ITEM, DropKind.PET, PET_PER_STOMACH)
	);

	private static final TripStat SPLIT = new TripStat("Stom / Eggs",
		"Kills where you chose Open-stomach / Take-eggs on the corpse.",
		trip -> TripMath.countChoice(trip, STOMACH.getKey()) + " / " + TripMath.countChoice(trip, EGGS.getKey()));

	private static final Set<Integer> REGIONS = ImmutableSet.of(LAIR_REGION_ID);
	private static final Set<Integer> WAITING_REGIONS = ImmutableSet.of(LAIR_ENTRANCE_REGION_ID);
	private static final Set<Integer> BOSS_NPCS = ImmutableSet.of(BOSS);
	private static final Set<Integer> CORPSE_NPCS = ImmutableSet.of(CORPSE);
	private static final List<LootChoice> CHOICES = ImmutableList.of(STOMACH, EGGS);
	/**
	 * Popping eggs and polishing tarnished items convert them rather than use them up.
	 */
	private static final Set<Integer> CONVERTED = ImmutableSet.<Integer>builder()
		.addAll(EGG_PET.keySet()).addAll(TARNISHED).build();
	/**
	 * The aranei scout variants that handle death recovery, and what they accept for moving a gravestone.
	 */
	private static final Set<Integer> ARANEI_SCOUTS = ImmutableSet.of(
		NpcID.VAMPYRIUM_ARANEI_DEATH_HELPER,
		NpcID.VAMPYRIUM_ARANEI_DEATH_HELPER_1OP,
		NpcID.VAMPYRIUM_ARANEI_DEATH_HELPER_3OP);
	private static final Set<Integer> GRAVE_PAYMENTS = ImmutableSet.of(ItemID.COINS, ItemID.VIAL_BLOOD, ItemID.STYMPHIKE_FEATHER);
	private static final List<TripStat> CSV_COLUMNS = ImmutableList.of(
		new TripStat("stomach", null, trip -> String.valueOf(TripMath.countChoice(trip, STOMACH.getKey()))),
		new TripStat("eggs", null, trip -> String.valueOf(TripMath.countChoice(trip, EGGS.getKey()))));
	/**
	 * Both keys seen in the RS profile config (PROJECT_BRIEF.md, 2026-09-27).
	 */
	private static final List<AllTimeSource> ALL_TIME = ImmutableList.of(
		new AllTimeSource("drops_NPC_Maggot King", "maggot king", null));

	@Override
	public String getId()
	{
		return ID;
	}

	@Override
	public String getDisplayName()
	{
		return "Maggot King";
	}

	@Override
	public int getIconItemId()
	{
		return PET_ITEM;
	}

	@Override
	public Set<Integer> getRegions()
	{
		return REGIONS;
	}

	@Override
	public Set<Integer> getWaitingRegions()
	{
		return WAITING_REGIONS;
	}

	@Override
	public Set<Integer> getBossNpcIds()
	{
		return BOSS_NPCS;
	}

	@Override
	public int getNameNpcId()
	{
		return BOSS;
	}

	@Override
	public Set<Integer> getLootTriggerNpcs()
	{
		return CORPSE_NPCS;
	}

	@Override
	public List<LootChoice> getLootChoices()
	{
		return CHOICES;
	}

	@Override
	public boolean isGroundOverflowLoot()
	{
		return true;
	}

	@Override
	public List<ExpectedDrop> getDrops()
	{
		return DROPS;
	}

	@Override
	public double anyUniqueChance(KillContext context)
	{
		return ANY_UNIQUE;
	}

	@Override
	public String getLuckKillsName()
	{
		return "Open-stomach kills";
	}

	@Override
	public Map<Integer, Double> getEggPetRates()
	{
		return EGG_PET;
	}

	@Override
	public Set<Integer> getTarnishedItems()
	{
		return TARNISHED;
	}

	@Override
	public Set<Integer> getConvertedItems()
	{
		return CONVERTED;
	}

	@Override
	public Set<Integer> getGraveHelperNpcs()
	{
		return ARANEI_SCOUTS;
	}

	@Override
	public Set<Integer> getGravePaymentItems()
	{
		return GRAVE_PAYMENTS;
	}

	@Override
	public TripStat getProfitCell()
	{
		return SPLIT;
	}

	@Override
	public List<TripStat> getCsvColumns()
	{
		return CSV_COLUMNS;
	}

	@Override
	public String getEmptyStateText()
	{
		return "No trips yet. Enter the Maggot King's lair to start one.";
	}

	@Override
	public String getAreaNoun()
	{
		return "lair";
	}

	@Override
	public String getLuckNote()
	{
		return "Uniques and the kill pet only come from Open-stomach.";
	}

	@Override
	public String getLuckKillsLabel()
	{
		return "Stomach kills";
	}

	@Override
	public List<AllTimeSource> getAllTimeSources()
	{
		return ALL_TIME;
	}
}
