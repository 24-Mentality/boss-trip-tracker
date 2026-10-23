package com.bosstriptracker.boss;

import com.bosstriptracker.tracking.ChatPatterns;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * The Doom of Mokhaiotl. A trip is one delve run: it starts when you enter the burrow's lobby (or at "Delve level: 1"
 * if you're already there), each level completed is a "delve" (the trip's kill unit), and it ends when you claim your
 * rewards ("Claim and leave", which brings RuneLite's "Doom of Mokhaiotl" loot event), when you die (back to the lobby
 * with the run's loot forfeited) or when you leave. From the diagnostic log of 2026-10-23: two runs (levels 1 to 3
 * ending in a death, then 1 to 7 claimed); rates are from the OSRS Wiki.
 */
public final class DoomOfMokhaiotlBoss extends TableBoss
{
	public static final String ID = "doom_of_mokhaiotl";
	/**
	 * The lobby, where level 1 is also fought (not instanced); levels 2 to 5 and 6 to 7 were in these instanced
	 * regions. Deeper levels are kept in the trip by being instanced.
	 */
	public static final int LOBBY_REGION_ID = 5269;
	public static final int UPPER_LEVELS_REGION_ID = 13668;
	public static final int LOWER_LEVELS_REGION_ID = 14180;
	/**
	 * Level 9 and deeper ("deep delves") share one variant; past claims are counted as this many levels.
	 */
	public static final int DEEP_LEVEL = 9;
	public static final int PAST_RUN_LEVELS = 7;
	public static final String PAST_RUN = "run";
	/**
	 * The game's clock started 4 to 6 ticks after "Delve level: N" on all 9 levels in the log (5 on most).
	 */
	private static final long FIGHT_START_DELAY_MS = 5 * 600;

	/**
	 * "Delve level: 3" (with a "@mes_hl_red@" colour marker in front).
	 */
	private static final Pattern LEVEL_STARTED = Pattern.compile("^(?:@\\w+@)?Delve level: (\\d+)$");
	/**
	 * "Delve level: 3 duration: @mes_hl_red@1:30.00. Personal best: @mes_hl_red@0:58.20"; the first-ever completion's
	 * wording ("(new personal best)") is assumed to start the same way.
	 */
	private static final Pattern LEVEL_COMPLETE = Pattern.compile("^Delve level: (\\d+) duration: (?:@\\w+@)?(\\d[\\d:.]*\\d)");
	private static final Pattern TIME = Pattern.compile("^(?:(\\d+):)?(\\d+):(\\d+(?:\\.\\d+)?)$");
	public static final String LOOT_EVENT = "Doom of Mokhaiotl";

	/**
	 * Unique rates by level (OSRS Wiki): the cloth from level 2, the Eye from 3, the treads from 4, each its own roll.
	 */
	private static final double[] UNIQUE_RATES = {0, 0, 1 / 2_500.0, 1 / 2_000.0, 1 / 1_350.0, 1 / 810.0, 1 / 810.0,
		1 / 810.0, 1 / 630.0, 1 / 540.0};
	private static final double[] PET_RATES = {0, 0, 0, 0, 0, 0, 1 / 1_000.0, 1 / 750.0, 1 / 500.0, 1 / 250.0};
	private static final int CLOTH_FROM = 2;
	private static final int EYE_FROM = 3;
	private static final int TREADS_FROM = 4;

	private static final List<BossVariant> VARIANTS = levels();

	private static final List<ExpectedDrop> DROPS = ImmutableList.of(
		new ExpectedDrop(ItemID.MOKHAIOTL_CLOTH, DropKind.UNIQUE, c -> uniqueRate(c, CLOTH_FROM)),
		new ExpectedDrop(ItemID.EYE_OF_AYAK_UNCHARGED, DropKind.UNIQUE, c -> uniqueRate(c, EYE_FROM)),
		new ExpectedDrop(ItemID.AVERNIC_TREADS, DropKind.UNIQUE, c -> uniqueRate(c, TREADS_FROM)),
		new ExpectedDrop(ItemID.DOMPET, DropKind.PET, DoomOfMokhaiotlBoss::petRate));

	/**
	 * The OSRS Wiki's drop table groups.
	 */
	private static final Map<Integer, String> LOOT_CATEGORIES = LootCategories.builder()
		.put(LootCategories.UNIQUES, ItemID.MOKHAIOTL_CLOTH, ItemID.EYE_OF_AYAK_UNCHARGED, ItemID.AVERNIC_TREADS, ItemID.DOMPET)
		.put(LootCategories.GEAR, ItemID.DRAGON_MED_HELM, ItemID.DRAGON_PLATELEGS, ItemID.MYSTIC_EARTH_STAFF, ItemID.RUNE_PICKAXE)
		.put(LootCategories.RUNES_AND_AMMO, ItemID.DEATHRUNE, ItemID.CHAOSRUNE, ItemID.EARTHRUNE, ItemID.FIRERUNE,
			ItemID.MCANNONBALL, ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX)
		.put(LootCategories.RESOURCES, ItemID.COAL, ItemID.GOLD_ORE, ItemID.RUNITE_ORE, ItemID.DRAGON_DART_TIP,
			ItemID.RAW_SHARK, ItemID.SHARK_LURE, ItemID.SUN_KISSED_BONE)
		.put(LootCategories.HERBS_AND_SEEDS, ItemID.RANARR_SEED, ItemID.SPIRIT_TREE_SEED)
		.put(LootCategories.OTHER, ItemID.DEMON_TEAR, ItemID.DOM_TELEPORT_ITEM)
		.build();

	/**
	 * RuneLite's Loot Tracker records each claim as one kill of the "Doom of Mokhaiotl" event. No kill-count message
	 * was seen over 9 levels and a claim, so there is no Chat Commands key.
	 */
	private static final List<AllTimeSource> ALL_TIME = ImmutableList.of(
		new AllTimeSource("drops_EVENT_" + LOOT_EVENT, null, null));

	static final BossData DATA = BossData.builder()
		.id(ID)
		.displayName("Doom of Mokhaiotl")
		.iconItemId(ItemID.DOMPET)
		.nameNpcId(NpcID.DOM_BOSS)
		.bossNpcIds(ImmutableSet.of(NpcID.DOM_BOSS, NpcID.DOM_BOSS_SHIELDED, NpcID.DOM_BOSS_BURROWED))
		.variants(VARIANTS)
		.regions(ImmutableSet.of(LOBBY_REGION_ID, UPPER_LEVELS_REGION_ID, LOWER_LEVELS_REGION_ID))
		.drops(DROPS)
		.allTimeSources(ALL_TIME)
		.lootCategories(LOOT_CATEGORIES)
		.unitNoun("delve")
		.areaNoun("burrow")
		.emptyStateText("No trips yet. Descend into the Doom of Mokhaiotl's burrow to start one.")
		.build();

	public DoomOfMokhaiotlBoss()
	{
		super(DATA);
	}

	private static List<BossVariant> levels()
	{
		ImmutableList.Builder<BossVariant> levels = ImmutableList.builder();
		for (int level = 1; level < DEEP_LEVEL; level++)
		{
			// Each level has its own rates but no chip: a kill's variant only carries its level
			levels.add(new BossVariant(variantFor(level), "Level " + level, false, false));
		}
		levels.add(new BossVariant(variantFor(DEEP_LEVEL), "Deep", false, false));
		return levels.build();
	}

	/**
	 * The variant stored on a kill for a delve level: "level1" to "level8", "deep" from 9.
	 */
	public static String variantFor(int level)
	{
		return level >= DEEP_LEVEL ? "deep" : "level" + level;
	}

	/**
	 * @return the level a kill's variant stands for, 0 for none
	 */
	static int levelOf(String variant)
	{
		if (variant == null)
		{
			return 0;
		}
		if ("deep".equals(variant))
		{
			return DEEP_LEVEL;
		}
		return variant.startsWith("level") ? Integer.parseInt(variant.substring(5)) : 0;
	}

	private static double uniqueRate(KillContext context, int fromLevel)
	{
		if (PAST_RUN.equals(context.getVariant()))
		{
			double sum = 0;
			for (int level = 1; level <= PAST_RUN_LEVELS; level++)
			{
				sum += level >= fromLevel ? UNIQUE_RATES[level] : 0;
			}
			return sum;
		}
		int level = levelOf(context.getVariant());
		return level >= fromLevel ? UNIQUE_RATES[level] : 0;
	}

	private static double petRate(KillContext context)
	{
		if (PAST_RUN.equals(context.getVariant()))
		{
			double sum = 0;
			for (int level = 1; level <= PAST_RUN_LEVELS; level++)
			{
				sum += PET_RATES[level];
			}
			return sum;
		}
		return PET_RATES[levelOf(context.getVariant())];
	}

	/**
	 * @return the level "Delve level: N" starts, or 0
	 */
	public static int levelStarted(String text)
	{
		Matcher m = LEVEL_STARTED.matcher(text);
		return m.matches() ? Integer.parseInt(m.group(1)) : 0;
	}

	@Override
	public KillReport killReport(String text)
	{
		Matcher m = LEVEL_COMPLETE.matcher(text);
		if (!m.find())
		{
			return null;
		}
		int level = Integer.parseInt(m.group(1));
		return new KillReport(null, variantFor(level), timeMs(m.group(2)));
	}

	static Long timeMs(String time)
	{
		Matcher m = TIME.matcher(time);
		if (!m.matches())
		{
			return null;
		}
		long hours = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
		long minutes = Long.parseLong(m.group(2));
		double seconds = Double.parseDouble(m.group(3));
		return hours * 3_600_000 + minutes * 60_000 + Math.round(seconds * 1000);
	}

	@Override
	public boolean isFightStartOnSpawn()
	{
		return false;
	}

	@Override
	public Long fightStartDelayMs(String text)
	{
		return levelStarted(text) > 0 ? FIGHT_START_DELAY_MS : null;
	}

	@Override
	public boolean isTripStartMessage(String text)
	{
		return levelStarted(text) == 1;
	}

	@Override
	public boolean isLootEvent(String name, LootRecordType type, String bossName)
	{
		return type == LootRecordType.EVENT && LOOT_EVENT.equals(name);
	}

	@Override
	public boolean isLootEventEndsTrip()
	{
		return true;
	}

	@Override
	public boolean isStillInside(java.util.function.IntUnaryOperator varbits, int region, boolean instanced)
	{
		// Deeper levels than the log reached are instanced too
		return instanced;
	}

	/**
	 * Dying sends you back to the lobby (OSRS Wiki, and the death at level 3 in the log), still in the boss's area, so
	 * the trip ends there and the next "Delve level: 1" starts another.
	 */
	@Override
	public boolean isDeathRespawnInside()
	{
		return true;
	}

	/**
	 * The gravestone in the lobby: "Check", "Unlock", then "Payment has been taken from your bank: 115,000 x Coins" in
	 * the log. The estimate made at death can't know the 75% reduction the game gives until 20 delves; the payment
	 * message replaces it.
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

	@Override
	public KillContext pastKillContext(String variant, int pastTeamSize)
	{
		return new KillContext(PAST_RUN, null);
	}

	/**
	 * The page counts "Total delves: 1,446" (every level completed, this boss's kill unit), "Deep delves" and
	 * "Deepest delve".
	 */
	@Override
	public Integer collectionLogKills(Map<String, Integer> counts, String variant)
	{
		return counts.get("Total delves");
	}

	@Override
	public boolean isLuckApproximate()
	{
		return true;
	}

	@Override
	public String getLuckNote()
	{
		return "Each level completed is a delve with its own rates: the Mokhaiotl cloth from level 2, the Eye of Ayak "
			+ "from 3, the Avernic treads from 4, and Dom from 6, all rarer the shallower the level. RuneLite's record "
			+ "counts claims, not levels, so each past claim is taken as a " + PAST_RUN_LEVELS + "-level run.";
	}

	@Override
	public String getLuckKillsName()
	{
		return "delves";
	}

	@Override
	public String getLuckKillsLabel()
	{
		return "Delves";
	}
}
