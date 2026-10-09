package com.bosstriptracker.boss;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * The Theatre of Blood: one raid per trip, in Entry, Normal or Hard Mode. Regions, messages and the reward event are
 * from the Normal Mode diagnostic raid of 2026-09-29 (PROJECT_BRIEF.md, "Observed in-game"); Entry and Hard Mode
 * texts and keys follow the same pattern but are unverified. Rates are from the OSRS Wiki.
 */
public final class TheatreOfBloodBoss extends BossDefinition
{
	public static final String ID = "theatre_of_blood";
	public static final String ENTRY = "entry";
	public static final String NORMAL = "normal";
	public static final String HARD = "hard";

	/**
	 * Ver Sinhaza, where the Theatre is entered and the chest for unclaimed rewards is.
	 */
	public static final int VER_SINHAZA_REGION_ID = 14642;
	/**
	 * TOB_CLIENT_PARTYSTATUS values: 1 in a party, 2 in the raid, 3 leaving (the vault), 0 no party (diagnostic log
	 * 2026-09-29).
	 */
	private static final int PARTY_IN_RAID = 2;
	private static final int PARTY_LEAVING = 3;
	/**
	 * Inside the entrance, the rooms from the Maiden to Verzik, and the vault. 13379 is Sotetseg's maze, where one
	 * player is sent during the fight (unverified: not visited in the log).
	 */
	private static final Set<Integer> REGIONS = ImmutableSet.of(12869, 12613, 13125, 13122, 13123, 13379, 12612, 12611, 12867);

	/**
	 * The team's chance of a purple per raid without deaths, whatever the team size (OSRS Wiki).
	 */
	public static final double NORMAL_TEAM_CHANCE = 1 / 9.1;
	public static final double HARD_TEAM_CHANCE = 1 / 7.7;
	/**
	 * Used when a raid's team size isn't known.
	 */
	public static final int TYPICAL_TEAM_SIZE = 4;
	public static final double NORMAL_PET_RATE = 1 / 650.0;
	public static final double HARD_PET_RATE = 1 / 500.0;

	private static final List<BossVariant> VARIANTS = ImmutableList.of(
		new BossVariant(NORMAL, "Normal", false),
		// No Hard Mode raid has been logged yet
		new BossVariant(HARD, "Hard", true));
	private static final Map<String, String> MODE_LABELS = ImmutableMap.of(ENTRY, "Entry", NORMAL, "Normal", HARD, "Hard");
	/**
	 * The Maiden's first form in each mode: the raid clock starts when she spawns (19:51.60 in the log against 1,984
	 * ticks from her spawn).
	 */
	private static final Set<Integer> BOSS_NPCS = ImmutableSet.of(
		NpcID.TOB_MAIDEN_100, NpcID.TOB_MAIDEN_100_STORY, NpcID.TOB_MAIDEN_100_HARD);
	private static final List<Integer> TEAM_SLOTS = ImmutableList.of(
		VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4);
	/**
	 * The Loot Tracker's reward event, from the vault chest or the chest by the Ver Sinhaza bank.
	 */
	private static final String LOOT_EVENT = "Theatre of Blood";
	/**
	 * Items are reclaimed from the chest for 100,000 coins after a wipe (OSRS Wiki; no wipe logged yet).
	 */
	private static final long WIPE_FEE = 100_000;

	/**
	 * Each purple's share of the purple roll, out of 19 in Normal and 18 in Hard (OSRS Wiki). Order: hilt, rapier,
	 * staff, faceguard, chestguard, legguards, scythe.
	 */
	private static final int[] PURPLES = {ItemID.INFERNAL_DEFENDER_HILT, ItemID.GHRAZI_RAPIER, ItemID.SANGUINESTI_STAFF_UNCHARGED,
		ItemID.JUSTICIAR_FACEGUARD, ItemID.JUSTICIAR_CHESTGUARD, ItemID.JUSTICIAR_LEG_GUARDS, ItemID.SCYTHE_OF_VITUR_UNCHARGED};
	private static final int[] NORMAL_WEIGHTS = {8, 2, 2, 2, 2, 2, 1};
	private static final int[] HARD_WEIGHTS = {7, 2, 2, 2, 2, 2, 1};
	private static final double NORMAL_WEIGHT_TOTAL = 19;
	private static final double HARD_WEIGHT_TOTAL = 18;

	private static final List<ExpectedDrop> DROPS = drops();

	/**
	 * The OSRS Wiki's Monumental chest groups. The Hard Mode kits and dust go with the uniques (the user's choice).
	 */
	private static final Map<Integer, String> LOOT_CATEGORIES = LootCategories.builder()
		.put(LootCategories.UNIQUES, PURPLES)
		.put(LootCategories.UNIQUES, ItemID.VERZIKPET, ItemID.TOB_HARDMODE_KIT, ItemID.TOB_HARDMODE_KIT_BLOOD,
			ItemID.TOB_HARDMODE_DUST)
		.put(LootCategories.RUNES, ItemID.DEATHRUNE, ItemID.BLOODRUNE)
		.put(LootCategories.RESOURCES, ItemID.VIAL_BLOOD, ItemID.SWAMP_TAR, ItemID.COAL, ItemID.GOLD_ORE,
			ItemID.MOLTEN_GLASS, ItemID.ADAMANTITE_ORE, ItemID.RUNITE_ORE, ItemID.WINE_OF_ZAMORAK, ItemID.CACTUS_POTATO)
		.put(LootCategories.HERBS_AND_SEEDS, ItemID.UNIDENTIFIED_CADANTINE, ItemID.UNIDENTIFIED_AVANTOE,
			ItemID.UNIDENTIFIED_TOADFLAX, ItemID.UNIDENTIFIED_KWUARM, ItemID.UNIDENTIFIED_IRIT,
			ItemID.UNIDENTIFIED_RANARR, ItemID.UNIDENTIFIED_SNAPDRAGON, ItemID.UNIDENTIFIED_LANTADYME,
			ItemID.UNIDENTIFIED_DWARF_WEED, ItemID.UNIDENTIFIED_TORSTOL, ItemID.PALM_TREE_SEED, ItemID.YEW_SEED,
			ItemID.MAGIC_TREE_SEED, ItemID.MAHOGANY_SEED)
		.put(LootCategories.GEAR, ItemID.BATTLESTAFF, ItemID.RUNE_BATTLEAXE, ItemID.RUNE_PLATEBODY, ItemID.RUNE_CHAINBODY)
		.put(LootCategories.OTHER, ItemID.CABBAGE)
		.build();

	private static final TripStat PURPLES_STAT = new TripStat("Purples",
		"Purples this raid: yours / the team's (yours included). The team's come from the game's broadcast.",
		trip -> countMine(trip) + " / " + countTeam(trip));
	private static final List<AllTimeSource> ALL_TIME = ImmutableList.of(
		new AllTimeSource("drops_EVENT_Theatre of Blood", null, null,
			// Hard Mode's key follows Chat Commands' naming (unverified)
			ImmutableMap.of(NORMAL, "theatre of blood", HARD, "theatre of blood hard mode")));

	@Override
	public String getId()
	{
		return ID;
	}

	@Override
	public String getDisplayName()
	{
		return "Theatre of Blood";
	}

	@Override
	public int getIconItemId()
	{
		return ItemID.VERZIKPET;
	}

	@Override
	public List<BossVariant> getVariants()
	{
		// Entry Mode raids have no purples: they only show under All
		return VARIANTS;
	}

	@Override
	public Set<Integer> getRegions()
	{
		return REGIONS;
	}

	@Override
	public TripModel getTripModel()
	{
		return TripModel.ONE_RAID;
	}

	@Override
	public Set<Integer> getBossNpcIds()
	{
		return BOSS_NPCS;
	}

	@Override
	public int getNameNpcId()
	{
		return NpcID.TOB_MAIDEN_100;
	}

	@Override
	public boolean isLootEvent(String name, LootRecordType type, String bossName)
	{
		// Each room's boss drops only its book; the reward comes from the chest (isRaidLootEvent)
		return false;
	}

	@Override
	public boolean isRaidLootEvent(String name, LootRecordType type)
	{
		return type == LootRecordType.EVENT && LOOT_EVENT.equals(name);
	}

	@Override
	protected Map<Integer, String> getLootCategoryMap()
	{
		return LOOT_CATEGORIES;
	}

	@Override
	public List<ExpectedDrop> getDrops()
	{
		return DROPS;
	}

	@Override
	public double anyUniqueChance(KillContext context)
	{
		return teamChance(context.getVariant()) / teamSize(context);
	}

	@Override
	public boolean countsForLuck(Kill kill)
	{
		return !ENTRY.equals(kill.getVariant());
	}

	@Override
	public boolean countsTowardKillCount(Kill kill)
	{
		return !ENTRY.equals(kill.getVariant());
	}

	@Override
	public String getLuckKillsName()
	{
		return "Normal and Hard raids";
	}

	@Override
	public String getLuckKillsLabel()
	{
		return "Raids";
	}

	@Override
	public String getLuckNote()
	{
		return "Your chance per raid is the team's purple chance divided by the team size, which assumes equal"
			+ " contribution and no deaths. Entry Mode raids have no purples.";
	}

	@Override
	public KillContext pastKillContext(String variant, int pastTeamSize)
	{
		return new KillContext(variant == null ? NORMAL : variant, pastTeamSize);
	}

	@Override
	public boolean isAcquiredInsideFree()
	{
		return true;
	}

	@Override
	public boolean isDroppedSupplyUsed()
	{
		return true;
	}

	@Override
	public boolean isDeathEndsTrip()
	{
		return false;
	}

	@Override
	public String raidMode(String text)
	{
		return TobMessages.mode(text);
	}

	@Override
	public boolean isRaidRoomComplete(String text)
	{
		return TobMessages.isRoomComplete(text);
	}

	@Override
	public RaidCompletion raidCompletion(String text)
	{
		return TobMessages.completion(text);
	}

	@Override
	public Integer raidKillCount(RaidCompletion completion, Function<String, Integer> killCounts)
	{
		// Normal and Hard raids on one scale, like the all-time kill count; Entry Mode has its own
		if (ENTRY.equals(completion.getVariant()))
		{
			return null;
		}
		String other = HARD.equals(completion.getVariant()) ? NORMAL : HARD;
		Integer otherCount = killCounts.apply(ALL_TIME.get(0).getVariantKillCountKeys().get(other));
		return completion.getCount() + (otherCount == null ? 0 : otherCount);
	}

	@Override
	public Long raidTimeMs(String text)
	{
		return TobMessages.totalTimeMs(text);
	}

	@Override
	public boolean isOwnRaidDeath(String text)
	{
		return TobMessages.isOwnDeath(text);
	}

	@Override
	public String teamUniqueName(String text)
	{
		return TobMessages.specialLootItem(text);
	}

	@Override
	public Integer gameDryStreak(String text)
	{
		return TobMessages.dryStreak(text);
	}

	@Override
	public Integer gameTeamDryStreak(String text)
	{
		return TobMessages.teamDryStreak(text);
	}

	@Override
	public boolean hasTeamDryStreak()
	{
		return true;
	}

	@Override
	public long getWipeFee()
	{
		return WIPE_FEE;
	}

	/**
	 * In a raid while the party status is 2 (entered: seen from entry to the vault on one raid, 2026-09-29) or 3
	 * (leaving: seen in the vault), in an instance outside Ver Sinhaza. Unverified while dead or after a wipe.
	 */
	@Override
	public boolean isStillInside(IntUnaryOperator varbits, int region, boolean instanced)
	{
		int status = varbits.applyAsInt(VarbitID.TOB_CLIENT_PARTYSTATUS);
		return instanced && region != VER_SINHAZA_REGION_ID && (status == PARTY_IN_RAID || status == PARTY_LEAVING);
	}

	/**
	 * Your share of the team's purple chance is an estimate (equal contribution, no deaths), so the AS RUCK tiers wait
	 * for enough raids to mean something.
	 */
	@Override
	public double getMinExpectedForRuckTiers()
	{
		return 3;
	}

	@Override
	public boolean isLuckApproximate()
	{
		return true;
	}

	/**
	 * Lil' Zik: 1/650 in Normal and 1/500 in Hard (OSRS Wiki), lower with weaker personal performance.
	 */
	@Override
	public String getPetRateNote()
	{
		return "1/650 (Normal) to 1/500 (Hard), lower with weaker personal performance";
	}

	@Override
	public String getUnitNoun()
	{
		return "raid";
	}

	@Override
	public List<Integer> getTeamSlotVarbits()
	{
		return TEAM_SLOTS;
	}

	@Override
	public TripStat getProfitCell()
	{
		return PURPLES_STAT;
	}

	@Override
	public List<TripStat> getCsvColumns()
	{
		return ImmutableList.of(
			new TripStat("Mode", "Raid mode.", trip -> mode(trip) == null ? "" : MODE_LABELS.get(mode(trip))),
			new TripStat("Team", "Team size at the start.", trip -> teamSize(trip) == null ? "" : String.valueOf(teamSize(trip))),
			PURPLES_STAT);
	}

	@Override
	public String tripDetail(Trip trip)
	{
		List<String> parts = new ArrayList<>();
		String mode = mode(trip);
		parts.add(mode == null ? "Raid" : MODE_LABELS.getOrDefault(mode, mode));
		Integer team = teamSize(trip);
		if (team != null)
		{
			parts.add(team == 1 ? "solo" : "team of " + team);
		}
		// Deaths are in the card's Deaths cell: with them this line wouldn't fit the sidebar
		return String.join(" · ", parts);
	}

	@Override
	public String getEmptyStateText()
	{
		return "No raids yet. Enter the Theatre of Blood to start one.";
	}

	@Override
	public String getAreaNoun()
	{
		return "Theatre";
	}

	@Override
	public List<AllTimeSource> getAllTimeSources()
	{
		return ALL_TIME;
	}

	static double teamChance(String variant)
	{
		if (ENTRY.equals(variant))
		{
			return 0;
		}
		return HARD.equals(variant) ? HARD_TEAM_CHANCE : NORMAL_TEAM_CHANCE;
	}

	static int teamSize(KillContext context)
	{
		return context.getPartySize() == null ? TYPICAL_TEAM_SIZE : Math.max(1, context.getPartySize());
	}

	private static List<ExpectedDrop> drops()
	{
		ImmutableList.Builder<ExpectedDrop> drops = ImmutableList.builder();
		for (int i = 0; i < PURPLES.length; i++)
		{
			int normalWeight = NORMAL_WEIGHTS[i];
			int hardWeight = HARD_WEIGHTS[i];
			drops.add(new ExpectedDrop(PURPLES[i], DropKind.UNIQUE, context ->
			{
				double share = HARD.equals(context.getVariant()) ? hardWeight / HARD_WEIGHT_TOTAL : normalWeight / NORMAL_WEIGHT_TOTAL;
				return teamChance(context.getVariant()) * share / teamSize(context);
			}));
		}
		drops.add(new ExpectedDrop(ItemID.VERZIKPET, DropKind.PET, context ->
			ENTRY.equals(context.getVariant()) ? 0 : HARD.equals(context.getVariant()) ? HARD_PET_RATE : NORMAL_PET_RATE));
		// Hard Mode only
		drops.add(hardOnly(ItemID.TOB_HARDMODE_KIT, 1 / 100.0));
		drops.add(hardOnly(ItemID.TOB_HARDMODE_KIT_BLOOD, 1 / 150.0));
		drops.add(hardOnly(ItemID.TOB_HARDMODE_DUST, 1 / 275.0));
		return drops.build();
	}

	private static ExpectedDrop hardOnly(int itemId, double rate)
	{
		return new ExpectedDrop(itemId, DropKind.TERTIARY, context -> HARD.equals(context.getVariant()) ? rate : 0);
	}

	private static String mode(Trip trip)
	{
		for (Kill kill : trip.getKills())
		{
			if (kill.getVariant() != null)
			{
				return kill.getVariant();
			}
		}
		return null;
	}

	private static Integer teamSize(Trip trip)
	{
		for (Kill kill : trip.getKills())
		{
			if (kill.getPartySize() != null)
			{
				return kill.getPartySize();
			}
		}
		return null;
	}

	private static int countMine(Trip trip)
	{
		int count = 0;
		for (Kill kill : trip.getKills())
		{
			for (ItemEntry entry : kill.getLoot())
			{
				for (int purple : PURPLES)
				{
					if (entry.getItemId() == purple)
					{
						count += entry.getQuantity();
					}
				}
			}
		}
		return count;
	}

	private static int countTeam(Trip trip)
	{
		int team = 0;
		for (Kill kill : trip.getKills())
		{
			team += kill.getTeamUniques() == null ? 0 : kill.getTeamUniques().size();
		}
		// A missed broadcast still leaves your own
		return Math.max(team, countMine(trip));
	}
}
