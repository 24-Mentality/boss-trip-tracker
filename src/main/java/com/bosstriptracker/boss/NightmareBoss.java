package com.bosstriptracker.boss;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.bosstriptracker.model.ChargeType;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * The Nightmare, with Phosani's Nightmare and the regular Nightmare as variants. Only Phosani's is tracked so far:
 * its regions, messages and NPCs come from the diagnostic log of 2026-09-28 (PROJECT_BRIEF.md, "Observed in-game").
 * The regular Nightmare is not verified yet, so its kills aren't recognised. Rates are from the OSRS Wiki.
 */
public final class NightmareBoss extends TableBoss
{
	public static final String ID = "nightmare";
	public static final String PHOSANI = "phosani";
	public static final String REGULAR = "regular";

	/**
	 * Template region of Phosani's dream (instanced). Unverified whether the regular Nightmare's arena shares it.
	 */
	public static final int DREAM_REGION_ID = 15515;
	/**
	 * The Sisterhood Sanctuary, where the Pool of Nightmares is and where you land on leaving or dying.
	 */
	public static final int SANCTUARY_REGION_ID = 15256;

	/**
	 * Phosani's per-kill rates (OSRS Wiki).
	 */
	public static final double INQUISITOR_PIECE_RATE = 1 / 700.0;
	public static final double MACE_RATE = 31 / 35_000.0;
	public static final double STAFF_RATE = 69 / 35_000.0;
	public static final double ORB_RATE = 1 / 1_600.0;
	public static final double PET_RATE = 1 / 1_400.0;
	public static final double JAR_OF_DREAMS_RATE = 1 / 4_000.0;

	/**
	 * The fight clock starts 8 ticks after "The Nightmare has awoken!" (or "…has reawoken!" for later kills in the
	 * same dream): the phase durations add up to the reported fight duration from there, on every kill in the log.
	 */
	private static final long FIGHT_START_DELAY_MS = 8 * 600;
	private static final Set<String> FIGHT_START_MESSAGES = ImmutableSet.of(
		"The Nightmare has awoken!", "The Nightmare has reawoken!");
	/**
	 * After "Collect" on Sister Senga: "Payment has been taken from your bank: 60,000 x Coins".
	 */
	private static final Pattern BANK_PAYMENT = Pattern.compile("^Payment has been taken from your bank: ([\\d,]+) x Coins");

	private static final List<BossVariant> VARIANTS = ImmutableList.of(
		new BossVariant(PHOSANI, "Phosani's", false),
		// Not recognised yet (no diagnostic log of a regular kill), so it has no chip
		new BossVariant(REGULAR, "Regular", false, false));
	private static final Map<String, String> KILL_NAMES = ImmutableMap.of("Phosani's Nightmare", PHOSANI);
	private static final Set<Integer> REGIONS = ImmutableSet.of(DREAM_REGION_ID);
	private static final Set<Integer> WAITING_REGIONS = ImmutableSet.of(SANCTUARY_REGION_ID);
	/**
	 * Phosani's changes NPC id with each phase.
	 */
	private static final Set<Integer> BOSS_NPCS = ImmutableSet.of(
		NpcID.NIGHTMARE_CHALLENGE_INITIAL,
		NpcID.NIGHTMARE_CHALLENGE_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_PHASE_02, NpcID.NIGHTMARE_CHALLENGE_PHASE_03,
		NpcID.NIGHTMARE_CHALLENGE_PHASE_04, NpcID.NIGHTMARE_CHALLENGE_PHASE_05,
		NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_02,
		NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_03, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_04,
		NpcID.NIGHTMARE_CHALLENGE_BLAST, NpcID.NIGHTMARE_CHALLENGE_DYING);
	/**
	 * Sister Senga returns items after a death in Phosani's dream. Shura does it for the regular Nightmare
	 * (unverified).
	 */
	private static final Set<Integer> RECLAIM_NPCS = ImmutableSet.of(NpcID.NIGHTMARE_CHALLENGE_SISTER_2OP, NpcID.SHURA_2OP);

	private static final Set<Integer> STAKES = ImmutableSet.of(ItemID.BLISTERWOOD_STAKE);

	private static final List<ExpectedDrop> DROPS = ImmutableList.of(
		ExpectedDrop.fixed(ItemID.INQUISITORS_HELM, DropKind.UNIQUE, INQUISITOR_PIECE_RATE),
		ExpectedDrop.fixed(ItemID.INQUISITORS_BODY, DropKind.UNIQUE, INQUISITOR_PIECE_RATE),
		ExpectedDrop.fixed(ItemID.INQUISITORS_SKIRT, DropKind.UNIQUE, INQUISITOR_PIECE_RATE),
		ExpectedDrop.fixed(ItemID.INQUISITORS_MACE, DropKind.UNIQUE, MACE_RATE),
		ExpectedDrop.fixed(ItemID.NIGHTMARE_STAFF, DropKind.UNIQUE, STAFF_RATE),
		ExpectedDrop.fixed(ItemID.ELDRITCH_ORB, DropKind.UNIQUE, ORB_RATE),
		ExpectedDrop.fixed(ItemID.HARMONISED_ORB, DropKind.UNIQUE, ORB_RATE),
		ExpectedDrop.fixed(ItemID.VOLATILE_ORB, DropKind.UNIQUE, ORB_RATE),
		ExpectedDrop.fixed(ItemID.NIGHTMAREPET, DropKind.PET, PET_RATE),
		ExpectedDrop.fixed(ItemID.JAR_OF_DREAMS, DropKind.TERTIARY, JAR_OF_DREAMS_RATE));
	/**
	 * Any unique: the sum of the unique rates, about 1/111 as the wiki gives.
	 */
	private static final double ANY_UNIQUE = 3 * INQUISITOR_PIECE_RATE + MACE_RATE + STAFF_RATE + 3 * ORB_RATE;

	private static final TripStat UNIQUES = new TripStat("Uniques", "Uniques received this trip.",
		trip -> String.valueOf(countUniques(trip)));

	/**
	 * The OSRS Wiki's Phosani's Nightmare drop table groups (the regular Nightmare drops the same items).
	 */
	private static final Map<Integer, String> LOOT_CATEGORIES = LootCategories.builder()
		.put(LootCategories.UNIQUES, ItemID.NIGHTMARE_STAFF, ItemID.INQUISITORS_HELM, ItemID.INQUISITORS_BODY,
			ItemID.INQUISITORS_SKIRT, ItemID.INQUISITORS_MACE, ItemID.ELDRITCH_ORB, ItemID.HARMONISED_ORB,
			ItemID.VOLATILE_ORB, ItemID.NIGHTMAREPET)
		.put(LootCategories.RUNES_AND_AMMO, ItemID.COSMICRUNE, ItemID.NATURERUNE, ItemID.DEATHRUNE, ItemID.BLOODRUNE,
			ItemID.SOULRUNE, ItemID.MCANNONBALL, ItemID.RUNE_ARROW)
		.put(LootCategories.RESOURCES, ItemID.MITHRIL_ORE, ItemID.COAL, ItemID.GOLD_ORE, ItemID.ADAMANTITE_ORE,
			ItemID.RUNITE_ORE, ItemID.MAGIC_LOGS, ItemID.UNIDENTIFIED_CADANTINE, ItemID.UNIDENTIFIED_TORSTOL,
			ItemID.SNAPDRAGON_SEED, ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY)
		.put(LootCategories.CONSUMABLES, ItemID.BASS, ItemID.SHARK, ItemID._3DOSEPRAYERRESTORE,
			ItemID.SANFEW_SALVE_3_DOSE, ItemID._3DOSEPOTIONOFSARADOMIN, ItemID._3DOSEPOTIONOFZAMORAK)
		.put(LootCategories.COINS, ItemID.COINS)
		.put(LootCategories.OTHER, ItemID.BIG_BONES, ItemID.JAR_OF_DREAMS)
		.build();

	/**
	 * Keys seen in the RS profile config. The regular Nightmare's ("drops_NPC_The Nightmare", "nightmare") is left
	 * out until its kills are tracked: its odds depend on the team size.
	 */
	private static final List<AllTimeSource> ALL_TIME = ImmutableList.of(
		new AllTimeSource("drops_NPC_Phosani's Nightmare", "phosani's nightmare", PHOSANI));

	/**
	 * The Nightmare's table entry; its own mechanics (the fight clock, Sister Senga's fee, blisterwood stakes) are the
	 * overrides below.
	 */
	static final BossData DATA = BossData.builder()
		.id(ID)
		.displayName("Nightmare")
		.iconItemId(ItemID.NIGHTMAREPET)
		.nameNpcId(NpcID.NIGHTMARE_CHALLENGE_INITIAL)
		.bossNpcIds(BOSS_NPCS)
		.variants(VARIANTS)
		.killNames(KILL_NAMES)
		.regions(REGIONS)
		.waitingRegions(WAITING_REGIONS)
		.drops(DROPS)
		.dropModel(DropModel.anyUniqueRate(ANY_UNIQUE))
		.allTimeSources(ALL_TIME)
		.lootCategories(LOOT_CATEGORIES)
		.areaNoun("dream")
		.emptyStateText("No trips yet. Drink from the Pool of Nightmares to start one.")
		.profitCell(UNIQUES)
		.build();

	public NightmareBoss()
	{
		super(DATA);
	}

	@Override
	public boolean isLootEvent(String name, LootRecordType type, String bossName)
	{
		// Loot lands on the floor, and the Loot Tracker's event already lists all of it
		return type == LootRecordType.NPC && name != null && KILL_NAMES.containsKey(name);
	}

	@Override
	public boolean isFightStartOnSpawn()
	{
		return false;
	}

	@Override
	public Long fightStartDelayMs(String text)
	{
		return FIGHT_START_MESSAGES.contains(text) ? FIGHT_START_DELAY_MS : null;
	}

	@Override
	public Long reclaimFee(String text)
	{
		Matcher m = BANK_PAYMENT.matcher(text);
		return m.find() ? Long.valueOf(m.group(1).replace(",", "")) : null;
	}

	@Override
	public String getChargeNote(ChargeType type)
	{
		// Leaving out the thrall's hits, four Check readings (2026-09-28/29) that used 90, 52, 130 and 15 charges
		// count 95, 51, 137 and 14
		return type == ChargeType.BLOOD_FURY
			? "Approximate at the Nightmare: within about 6% of the amulet's Check in testing;"
				+ " at the Maggot King it's exact."
			: null;
	}

	@Override
	public Set<Integer> getRecoverableItems()
	{
		// Thrown at sleepwalkers: they leave the equipment, land on the floor (yours) and are picked back up
		return STAKES;
	}

	@Override
	public Set<Integer> getGraveHelperNpcs()
	{
		return RECLAIM_NPCS;
	}

	@Override
	public List<TripStat> getCsvColumns()
	{
		return ImmutableList.of(UNIQUES);
	}

	private static int countUniques(Trip trip)
	{
		int count = 0;
		for (Kill kill : trip.getKills())
		{
			for (ItemEntry entry : kill.getLoot())
			{
				for (ExpectedDrop drop : DROPS)
				{
					if (drop.getKind() == DropKind.UNIQUE && drop.getItemId() == entry.getItemId())
					{
						count += entry.getQuantity();
					}
				}
			}
		}
		return count;
	}
}
