package com.bosstriptracker.boss;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * The Fractured Archive (released 2026-10-20): one raid per trip, for teams of 1 to 8. Regions, NPCs and messages
 * are from 16 diagnostic raids of 2026-10-21 (PROJECT_BRIEF.md, "Observed in-game"), none of which was completed, so
 * the completion count, the reward chest and the team-size varbits are unverified. Jagex hasn't published the drop
 * rates yet: every chance here is 0 and the luck cards say so ({@link #hasPublishedRates()}).
 */
public final class FracturedArchiveBoss extends TableBoss
{
	public static final String ID = "fractured_archive";

	/**
	 * The lobby outside the Archive Door (bank, grouping board, your gravestone after a wipe). Not instanced.
	 */
	public static final int LOBBY_REGION_ID = 16470;
	/**
	 * The Nexus, the raid's hub: the Archive Guardian is fought here, and the Quartermaster opens shop here after
	 * the third challenge.
	 */
	public static final int NEXUS_REGION_ID = 16471;
	/**
	 * Instanced template regions seen: the Nexus (Archive Guardian), Xarth, Bork, Dreadnoth the Jellied and the inner
	 * vault (Ocellus). Ezekiel, Moia and the Dagon'hai elder weren't drawn, so their rooms are unknown; being in an
	 * instance that isn't the lobby keeps the raid going there ({@link #isStillInside}).
	 */
	private static final Set<Integer> REGIONS = ImmutableSet.of(NEXUS_REGION_ID, 16726, 16727, 16728, 16472);
	/**
	 * Each challenge boss's first form (hitsplat targets in the logs; the three unseen bosses are their gameval ids).
	 */
	private static final Set<Integer> BOSS_NPCS = ImmutableSet.of(NpcID.TFA_GUARDIAN, NpcID.TFA_BORK, NpcID.TFA_JELLY_BOSS,
		NpcID.TFA_XARTH, NpcID.TFA_EZEKIEL, NpcID.TFA_MOIA, NpcID.TFA_DAGONHAI_LEADER, NpcID.TFA_OCELLUS_P1);
	/**
	 * After a wipe you're put in the lobby with your items in a gravestone: "Check" on it, then Unlock, takes the fee
	 * from the bank (125,000 coins on every wipe logged, under 5 completions). Any gravestone design.
	 */
	private static final Set<Integer> GRAVESTONES = ImmutableSet.of(NpcID.GRAVESTONE_DEFAULT, NpcID.GRAVESTONE_ANGEL,
		NpcID.GRAVESTONE_DEFAULT_001, NpcID.GRAVESTONE_DEFAULT_002, NpcID.GRAVESTONE_DEFAULT_003,
		NpcID.GRAVESTONE_DEFAULT_004, NpcID.GRAVESTONE_DEFAULT_005, NpcID.GRAVESTONE_DEFAULT_006,
		NpcID.GRAVESTONE_DEFAULT_007, NpcID.GRAVESTONE_DEFAULT_008, NpcID.GRAVESTONE_DEFAULT_009,
		NpcID.GRAVESTONE_DEFAULT_010, NpcID.GRAVESTONE_DEFAULT_011, NpcID.GRAVESTONE_DEFAULT_012,
		NpcID.GRAVESTONE_DEFAULT_013, NpcID.GRAVESTONE_DEFAULT_014, NpcID.GRAVESTONE_DEFAULT_015,
		NpcID.GRAVESTONE_DEFAULT_016, NpcID.GRAVESTONE_DEFAULT_017, NpcID.GRAVESTONE_DEFAULT_018,
		NpcID.GRAVESTONE_DEFAULT_019, NpcID.GRAVESTONE_DEFAULT_020, NpcID.GRAVESTONE_DEFAULT_021,
		NpcID.GRAVESTONE_DEFAULT_022, NpcID.GRAVESTONE_DEFAULT_023, NpcID.GRAVESTONE_DEFAULT_024);
	/**
	 * Health bar slots for up to 8 players (unverified: the logs didn't record them).
	 */
	private static final List<Integer> TEAM_SLOTS = ImmutableList.of(VarbitID.TFA_CLIENT_P0, VarbitID.TFA_CLIENT_P1,
		VarbitID.TFA_CLIENT_P2, VarbitID.TFA_CLIENT_P3, VarbitID.TFA_CLIENT_P4, VarbitID.TFA_CLIENT_P5,
		VarbitID.TFA_CLIENT_P6, VarbitID.TFA_CLIENT_P7);
	/**
	 * RuneLite's Loot Tracker names the reward chest's event "Fractured Archive" (its 1.13.3 build); the type is
	 * taken to be EVENT like the Theatre of Blood's (unverified: no raid was completed).
	 */
	private static final String LOOT_EVENT = "Fractured Archive";

	/**
	 * The uniques, most to least common by the OSRS Wiki's ordering: Rondache, Zorya's tome and the Horn of Bran,
	 * Ascension crossbows, Obligator. Rates unpublished (0 until Jagex releases them).
	 */
	private static final int[] UNIQUES = {ItemID.RONDACHE, ItemID.ZORYAS_TOME_UNCHARGED, ItemID.TFA_ZEAL_REWARD,
		ItemID.ASCENSION_CROSSBOWS, ItemID.OBLIGATOR_UNCHARGED};

	private static final List<ExpectedDrop> DROPS = drops();

	/**
	 * The OSRS Wiki's chest table groups. The folio and the elemental fragments are tertiary drops, kept apart from
	 * the uniques.
	 */
	private static final Map<Integer, String> LOOT_CATEGORIES = LootCategories.builder()
		.put(LootCategories.UNIQUES, UNIQUES)
		.put(LootCategories.UNIQUES, ItemID.OCELLUSPET)
		.put(LootCategories.RUNES, ItemID.NATURERUNE, ItemID.ASTRALRUNE, ItemID.BLOODRUNE, ItemID.SOULRUNE, ItemID.EARTHRUNE)
		.put(LootCategories.RESOURCES, ItemID.SAPPHIRE, ItemID.EMERALD, ItemID.RUBY, ItemID.DIAMOND,
			ItemID.UNCUT_SAPPHIRE, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND, ItemID.IRON_ORE,
			ItemID.ADAMANTITE_ORE, ItemID.RUNITE_ORE, ItemID.ASCENSION_SHARDS, ItemID.EARTH_ORB, ItemID.DESICCATED_PAGE,
			ItemID.BLANKRUNE_HIGH, ItemID.MAHOGANY_LOGS, ItemID.RAW_SHARK, ItemID.WINE_OF_ZAMORAK)
		.put(LootCategories.HERBS_AND_SEEDS, ItemID.UNIDENTIFIED_HARRALANDER, ItemID.UNIDENTIFIED_RANARR,
			ItemID.UNIDENTIFIED_TOADFLAX, ItemID.SNAPDRAGON_SEED, ItemID.KWUARM_SEED, ItemID.SNAPE_GRASS_SEED, ItemID.YEW_SEED)
		.put(LootCategories.GEAR, ItemID.BATTLESTAFF, ItemID.DRAGON_PLATELEGS, ItemID.DRAGON_PLATESKIRT, ItemID.RUNE_KITESHIELD)
		.put(LootCategories.OTHER, ItemID.ARCHIVISTS_FOLIO_EMPTY, ItemID.TFA_MOTE_AIR, ItemID.TFA_MOTE_WATER,
			ItemID.TFA_MOTE_EARTH, ItemID.TFA_MOTE_FIRE, ItemID.CABBAGE)
		.build();

	private static final TripStat TEAM_STAT = new TripStat("Team", "Team size at the start.",
		trip -> teamSize(trip) == null ? "" : String.valueOf(teamSize(trip)));
	/**
	 * RuneLite's record keys for the raid, by its Loot Tracker event name and Chat Commands' lower-cased boss name
	 * (unverified until a raid is completed with them on).
	 */
	private static final List<AllTimeSource> ALL_TIME = ImmutableList.of(
		new AllTimeSource("drops_EVENT_Fractured Archive", "fractured archive", null));

	static final BossData DATA = BossData.builder()
		.id(ID)
		.displayName("Fractured Archive")
		.iconItemId(ItemID.OCELLUSPET)
		.nameNpcId(NpcID.TFA_OCELLUS_P1)
		.bossNpcIds(BOSS_NPCS)
		.regions(REGIONS)
		.tripModel(TripModel.ONE_RAID)
		.drops(DROPS)
		// A team rolls for one unique; the rate is unknown, so every player's chance is 0 for now
		.dropModel(DropModel.anyUniqueRate(0))
		.allTimeSources(ALL_TIME)
		.lootCategories(LOOT_CATEGORIES)
		.unitNoun("raid")
		.areaNoun("Archive")
		.emptyStateText("No raids yet. Enter the Fractured Archive to start one.")
		.build();

	public FracturedArchiveBoss()
	{
		super(DATA);
	}

	@Override
	public boolean isLootEvent(String name, LootRecordType type, String bossName)
	{
		// The reward comes from your chest in the vault (isRaidLootEvent), not from the challenge bosses
		return false;
	}

	@Override
	public boolean isRaidLootEvent(String name, LootRecordType type)
	{
		return LOOT_EVENT.equals(name);
	}

	@Override
	public String getLuckKillsName()
	{
		return "raids";
	}

	@Override
	public String getLuckKillsLabel()
	{
		return "Raids";
	}

	@Override
	public String getLuckNote()
	{
		return "Jagex hasn't published the Fractured Archive's drop rates yet, so nothing is expected and there is no"
			+ " luck tier. Uniques are still counted.";
	}

	@Override
	public boolean hasPublishedRates()
	{
		return false;
	}

	@Override
	public boolean isLuckApproximate()
	{
		return true;
	}

	@Override
	public double getMinExpectedForRuckTiers()
	{
		return 3;
	}

	@Override
	public boolean isAcquiredInsideFree()
	{
		// Quartermaster purchases (Supply Credits) arrive in the inventory the same tick and cost nothing
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
		// "You will respawn when your party completes or fails the challenge."
		return false;
	}

	@Override
	public boolean isFightStartOnSpawn()
	{
		return false;
	}

	/**
	 * Each challenge's clock starts at "Challenge started: ...".
	 */
	@Override
	public Long fightStartDelayMs(String text)
	{
		return TfaMessages.challengeStarted(text) != null ? 0L : null;
	}

	@Override
	public boolean isRaidRoomComplete(String text)
	{
		return TfaMessages.challengeComplete(text) != null;
	}

	@Override
	public RaidCompletion raidCompletion(String text)
	{
		return TfaMessages.completion(text);
	}

	@Override
	public Long raidTimeMs(String text)
	{
		return TfaMessages.totalTimeMs(text);
	}

	@Override
	public boolean isOwnRaidDeath(String text)
	{
		return TfaMessages.isOwnDeath(text);
	}

	@Override
	public boolean isRaidFailed(String text)
	{
		return TfaMessages.isWipe(text);
	}

	@Override
	public boolean isRaidAbandoned(String text)
	{
		return TfaMessages.isAbandon(text);
	}

	@Override
	public Set<Integer> getGraveHelperNpcs()
	{
		return GRAVESTONES;
	}

	@Override
	public Long reclaimFee(String text)
	{
		return TfaMessages.bankPayment(text);
	}

	/**
	 * Every room is an instance and the only ways out lead to the lobby, so an instance that isn't the lobby is still
	 * the raid (the three unseen bosses' rooms).
	 */
	@Override
	public boolean isStillInside(IntUnaryOperator varbits, int region, boolean instanced)
	{
		return instanced && region != LOBBY_REGION_ID;
	}

	@Override
	public List<Integer> getTeamSlotVarbits()
	{
		return TEAM_SLOTS;
	}

	@Override
	public List<TripStat> getCsvColumns()
	{
		return ImmutableList.of(TEAM_STAT, getProfitCell());
	}

	@Override
	public String tripDetail(Trip trip)
	{
		Integer team = teamSize(trip);
		if (team == null)
		{
			return null;
		}
		return "Raid · " + (team == 1 ? "solo" : "team of " + team);
	}

	private static List<ExpectedDrop> drops()
	{
		ImmutableList.Builder<ExpectedDrop> drops = ImmutableList.builder();
		for (int unique : UNIQUES)
		{
			drops.add(ExpectedDrop.fixed(unique, DropKind.UNIQUE, 0));
		}
		// Orion, Ocellus's pet
		drops.add(ExpectedDrop.fixed(ItemID.OCELLUSPET, DropKind.PET, 0));
		// The Archivist's folio and the four elemental fragments: the wiki lists them as tertiary (about 1/20 and
		// 1/80), but nothing is shown as expected until Jagex publishes the table
		drops.add(ExpectedDrop.fixed(ItemID.ARCHIVISTS_FOLIO_EMPTY, DropKind.TERTIARY, 0));
		drops.add(ExpectedDrop.fixed(ItemID.TFA_MOTE_AIR, DropKind.TERTIARY, 0));
		drops.add(ExpectedDrop.fixed(ItemID.TFA_MOTE_WATER, DropKind.TERTIARY, 0));
		drops.add(ExpectedDrop.fixed(ItemID.TFA_MOTE_EARTH, DropKind.TERTIARY, 0));
		drops.add(ExpectedDrop.fixed(ItemID.TFA_MOTE_FIRE, DropKind.TERTIARY, 0));
		return drops.build();
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
}
