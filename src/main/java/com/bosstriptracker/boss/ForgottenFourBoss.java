package com.bosstriptracker.boss;

import com.bosstriptracker.tracking.ChatPatterns;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * The Forgotten Four of Desert Treasure II: Vardorvis, Duke Sucellus, the Whisperer and the Leviathan, one boss
 * each. Regions, NPCs, messages and how the fight clock runs are from the diagnostic log of 2026-10-22 (3 Vardorvis,
 * 2 Duke, 1 Whisperer and 1 Leviathan kills, no deaths); rates are from the OSRS Wiki.
 *
 * <p>Each boss rolls its tradeable unique table once per kill (1 in 136, 90, 64 and 96). On it: the vestige 1/8, the
 * Soulreaper axe piece 1/8, chromium ingots 3/8, Virtus 1/8 (a third each) and 2/8 gold rings. The vestige only drops
 * on the third hit of its slot (the first two give gold rings), so per kill it averages 1 in 8 tables; luck treats
 * each kill as independent, which is why it's labelled approximate. The awakened variants roll the table three times
 * as often; their kill-count name ("Vardorvis (Awakened)") follows RuneLite's Chat Commands and is unverified.
 */
public final class ForgottenFourBoss extends TableBoss
{
	public static final String VARDORVIS_ID = "vardorvis";
	public static final String DUKE_ID = "duke_sucellus";
	public static final String WHISPERER_ID = "whisperer";
	public static final String LEVIATHAN_ID = "leviathan";
	public static final String AWAKENED = "awakened";

	/**
	 * Template regions. Vardorvis' ritual site is not instanced; the others are.
	 */
	public static final int VARDORVIS_REGION_ID = 4405;
	public static final int STRANGLEWOOD_REGION_ID = 4661;
	public static final int DUKE_REGION_ID = 12132;
	public static final int DUKE_CORRIDOR_REGION_ID = 11681;
	public static final int WHISPERER_REGION_ID = 10595;
	public static final int SHADOW_REALM_REGION_ID = 9571;
	public static final int WHISPERER_APPROACH_REGION_ID = 10596;
	public static final int LEVIATHAN_REGION_ID = 8291;
	public static final int LEVIATHAN_DOCK_REGION_ID = 8292;

	/**
	 * Awakened bosses roll the unique table this many times as often (OSRS Wiki).
	 */
	public static final int AWAKENED_TABLE_ROLLS = 3;

	/**
	 * "Duke Sucellus awakens..." (with a "@mes_hl_mag@" colour marker in front): the fight clock starts here, exactly,
	 * on both kills in the log.
	 */
	private static final Pattern DUKE_AWAKENS = Pattern.compile("(?:^|@)Duke Sucellus awakens\\.\\.\\.$");

	/**
	 * Mushrooms picked, ground and mixed in Duke Sucellus' chamber to wake him: never supplies.
	 */
	private static final Set<Integer> DUKE_INGREDIENTS = ImmutableSet.of(
		ItemID.DUKE_SUCELLUS_MUSHROOM_1, ItemID.DUKE_SUCELLUS_MUSHROOM_1_GROUND,
		ItemID.DUKE_SUCELLUS_MUSHROOM_2, ItemID.DUKE_SUCELLUS_MUSHROOM_2_GROUND,
		ItemID.DUKE_SUCELLUS_MUSHROOM_3, ItemID.DUKE_SUCELLUS_MUSHROOM_3_GROUND,
		ItemID.DUKE_SUCELLUS_MUSHROOM_4, ItemID.DUKE_SUCELLUS_MUSHROOM_4_GROUND,
		ItemID.DUKE_SUCELLUS_SALT, ItemID.DUKE_SUCELLUS_POTION_1_2, ItemID.DUKE_SUCELLUS_POTION_1_3,
		ItemID.DUKE_SUCELLUS_POTION_1_4, ItemID.DUKE_SUCELLUS_POTION_2_3, ItemID.DUKE_SUCELLUS_POTION_2_4,
		ItemID.DUKE_SUCELLUS_POTION_3_4);

	private static final List<BossVariant> VARIANTS = ImmutableList.of(
		// Unverified: no awakened kill in the logs, so there is no chip until one is recognised
		new BossVariant(AWAKENED, "Awakened", true, false));

	private enum FightStart
	{
		/**
		 * Vardorvis: your first hitsplat on him, 0s included (exact on all 3 kills).
		 */
		FIRST_HIT,
		/**
		 * Duke Sucellus: "Duke Sucellus awakens..." (exact on both kills).
		 */
		AWAKENS_MESSAGE,
		/**
		 * The Whisperer and the Leviathan: the boss appearing. The game's clock began 6 ticks after "Disturb" on the
		 * Odd Figure, 1 tick before the first attack on the Whisperer, and 2 ticks before the first attack on the
		 * Leviathan; the NPCs weren't logged, so this is the best fit and is unverified.
		 */
		SPAWN
	}

	private final FightStart fightStart;
	private final Set<Integer> waitingRegions;
	private final Set<Integer> convertedItems;
	private final String luckNote;

	private ForgottenFourBoss(BossData data, FightStart fightStart, Set<Integer> waitingRegions, Set<Integer> convertedItems,
		String luckNote)
	{
		super(data);
		this.fightStart = fightStart;
		this.waitingRegions = waitingRegions;
		this.convertedItems = convertedItems;
		this.luckNote = luckNote;
	}

	/**
	 * Vardorvis, in the ritual site under the Stranglewood. He respawns in place 10 seconds after a kill; walking back
	 * out into the Stranglewood pauses the trip.
	 */
	public static ForgottenFourBoss vardorvis()
	{
		Table table = new Table(136, ItemID.ULTOR_VESTIGE, ItemID.SOULREAPER_AXE_HEAD, ItemID.VARDORVISPET, 1 / 3_000.0,
			1 / 80.6, ItemID.BLOOD_QUARTZ, 1 / 204.0);
		Map<Integer, String> categories = LootCategories.builder()
			.put(LootCategories.UNIQUES, ItemID.ULTOR_VESTIGE, ItemID.SOULREAPER_AXE_HEAD, ItemID.CHROMIUM_INGOT,
				ItemID.VIRTUS_MASK, ItemID.VIRTUS_TOP, ItemID.VIRTUS_LEGS, ItemID.VARDORVISPET)
			.put(LootCategories.SECONDARY_UNIQUES, ItemID.VARDORVIS_TABLET, ItemID.DT2_AWAKENERS_ORB, ItemID.BLOOD_QUARTZ)
			.put(LootCategories.CONSUMABLES, ItemID.POTATO_TUNA_SWEETCORN, ItemID._3DOSEPRAYERRESTORE, ItemID._2DOSE2COMBAT)
			.put(LootCategories.RESOURCES, ItemID.BLANKRUNE_HIGH, ItemID.IRON_ORE, ItemID.SILVER_ORE, ItemID.COAL,
				ItemID.MITHRIL_ORE, ItemID.ADAMANTITE_ORE, ItemID.RUNITE_ORE, ItemID.SAPPHIRE, ItemID.EMERALD, ItemID.RUBY,
				ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND, ItemID.RUNE_JAVELIN_HEAD, ItemID.DRAGON_JAVELIN_HEAD,
				ItemID.DRAGON_DART_TIP, ItemID.RAW_SHARK)
			.put(LootCategories.RUNES_AND_AMMO, ItemID.BRONZE_JAVELIN, ItemID.MITHRIL_JAVELIN, ItemID.ADAMANT_JAVELIN,
				ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_TIPPED_ONYX_ENCHANTED, ItemID.MINDRUNE, ItemID.FIRERUNE, ItemID.LAVARUNE,
				ItemID.BLOODRUNE, ItemID.SOULRUNE)
			.put(LootCategories.OTHER, ItemID.GOLD_RING, ItemID.KONAR_KEY, ItemID.DT2_SANGUINE_TORVA_KIT)
			.build();
		return new ForgottenFourBoss(BossData.builder()
			.id(VARDORVIS_ID)
			.displayName("Vardorvis")
			.iconItemId(ItemID.VARDORVISPET)
			.nameNpcId(NpcID.VARDORVIS)
			.bossNpcIds(ImmutableSet.of(NpcID.VARDORVIS))
			.variants(VARIANTS)
			.killNames(killNames("Vardorvis"))
			.regions(ImmutableSet.of(VARDORVIS_REGION_ID))
			.waitingRegions(ImmutableSet.of(STRANGLEWOOD_REGION_ID))
			.drops(table.drops())
			.allTimeSources(ImmutableList.of(new AllTimeSource("drops_NPC_Vardorvis", "vardorvis", null)))
			.lootCategories(categories)
			.areaNoun("ritual site")
			.emptyStateText("No trips yet. Enter Vardorvis' ritual site to start one.")
			.build(),
			FightStart.FIRST_HIT, ImmutableSet.of(STRANGLEWOOD_REGION_ID), Collections.emptySet(),
			table.luckNote("Ultor vestige"));
	}

	/**
	 * Duke Sucellus, in his chamber under Ghorrock. The mushrooms picked and ground there to wake him are never
	 * supplies.
	 */
	public static ForgottenFourBoss dukeSucellus()
	{
		Table table = new Table(90, ItemID.MAGUS_VESTIGE, ItemID.SOULREAPER_AXE_EYE, ItemID.DUKESUCELLUSPET, 1 / 2_500.0,
			1 / 48.5, ItemID.ICE_QUARTZ, 1 / 206.6);
		Map<Integer, String> categories = LootCategories.builder()
			.put(LootCategories.UNIQUES, ItemID.MAGUS_VESTIGE, ItemID.SOULREAPER_AXE_EYE, ItemID.CHROMIUM_INGOT,
				ItemID.VIRTUS_MASK, ItemID.VIRTUS_TOP, ItemID.VIRTUS_LEGS, ItemID.DUKESUCELLUSPET)
			.put(LootCategories.SECONDARY_UNIQUES, ItemID.DUKE_SUCELLUS_TABLET, ItemID.DT2_AWAKENERS_ORB, ItemID.ICE_QUARTZ)
			.put(LootCategories.CONSUMABLES, ItemID.PINEAPPLE_PIZZA, ItemID._3DOSEPRAYERRESTORE, ItemID._2DOSE2COMBAT)
			.put(LootCategories.GEAR, ItemID.BRONZE_CHAINBODY, ItemID.MITHRIL_CHAINBODY, ItemID.ADAMANT_CHAINBODY,
				ItemID.DRAGON_PLATELEGS)
			.put(LootCategories.RESOURCES, ItemID.BLANKRUNE_HIGH, ItemID.IRON_ORE, ItemID.COAL, ItemID.MITHRIL_ORE,
				ItemID.ADAMANTITE_ORE, ItemID.RUNITE_ORE, ItemID.SAPPHIRE, ItemID.EMERALD, ItemID.RUBY, ItemID.UNCUT_RUBY,
				ItemID.UNCUT_DIAMOND, ItemID.BRONZE_BAR, ItemID.DRAGON_ARROWHEADS, ItemID.RUNE_JAVELIN_HEAD,
				ItemID.DRAGON_JAVELIN_HEAD, ItemID.RAW_SEATURTLE)
			.put(LootCategories.RUNES_AND_AMMO, ItemID.AIRRUNE, ItemID.MISTRUNE, ItemID.CHAOSRUNE, ItemID.SOULRUNE)
			.put(LootCategories.OTHER, ItemID.GOLD_RING, ItemID.KONAR_KEY, ItemID.DT2_SANGUINE_TORVA_KIT)
			.build();
		return new ForgottenFourBoss(BossData.builder()
			.id(DUKE_ID)
			.displayName("Duke Sucellus")
			.iconItemId(ItemID.DUKESUCELLUSPET)
			.nameNpcId(NpcID.DUKE_SUCELLUS_AWAKE)
			.bossNpcIds(ImmutableSet.of(NpcID.DUKE_SUCELLUS_ASLEEP, NpcID.DUKE_SUCELLUS_AWAKE, NpcID.DUKE_SUCELLUS_DEAD))
			.variants(VARIANTS)
			.killNames(killNames("Duke Sucellus"))
			.regions(ImmutableSet.of(DUKE_REGION_ID))
			.waitingRegions(ImmutableSet.of(DUKE_CORRIDOR_REGION_ID))
			.drops(table.drops())
			.allTimeSources(ImmutableList.of(new AllTimeSource("drops_NPC_Duke Sucellus", "duke sucellus", null)))
			.lootCategories(categories)
			.areaNoun("chamber")
			.emptyStateText("No trips yet. Enter Duke Sucellus' chamber to start one.")
			.build(),
			FightStart.AWAKENS_MESSAGE, ImmutableSet.of(DUKE_CORRIDOR_REGION_ID), DUKE_INGREDIENTS,
			table.luckNote("Magus vestige"));
	}

	/**
	 * The Whisperer, in the Lassar Undercity. The shadow realm is part of the fight.
	 */
	public static ForgottenFourBoss whisperer()
	{
		Table table = new Table(64, ItemID.BELLATOR_VESTIGE, ItemID.SOULREAPER_AXE_STAFF, ItemID.WHISPERERPET, 1 / 2_000.0,
			1 / 34.5, ItemID.SHADOW_QUARTZ, 1 / 209.3);
		Map<Integer, String> categories = LootCategories.builder()
			.put(LootCategories.UNIQUES, ItemID.BELLATOR_VESTIGE, ItemID.SOULREAPER_AXE_STAFF, ItemID.CHROMIUM_INGOT,
				ItemID.VIRTUS_MASK, ItemID.VIRTUS_TOP, ItemID.VIRTUS_LEGS, ItemID.WHISPERERPET)
			.put(LootCategories.SECONDARY_UNIQUES, ItemID.WHISPERER_TABLET, ItemID.DT2_AWAKENERS_ORB, ItemID.SHADOW_QUARTZ)
			.put(LootCategories.CONSUMABLES, ItemID._2DOSEANCIENTBREW, ItemID._3DOSEPRAYERRESTORE, ItemID.MANTARAY)
			.put(LootCategories.GEAR, ItemID.BRONZE_LONGSWORD, ItemID.MITHRIL_LONGSWORD, ItemID.ADAMANT_LONGSWORD,
				ItemID.BATTLESTAFF, ItemID.DRAGON_PLATESKIRT)
			.put(LootCategories.RESOURCES, ItemID.BLANKRUNE_HIGH, ItemID.IRON_ORE, ItemID.COAL, ItemID.GOLD_ORE,
				ItemID.MITHRIL_ORE, ItemID.ADAMANTITE_ORE, ItemID.RUNITE_ORE, ItemID.SAPPHIRE, ItemID.EMERALD, ItemID.RUBY,
				ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND, ItemID.DRAGON_JAVELIN_HEAD,
				ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_UNFEATHERED, ItemID.RAW_MONKFISH)
			.put(LootCategories.RUNES_AND_AMMO, ItemID.WATERRUNE, ItemID.STEAMRUNE, ItemID.CHAOSRUNE, ItemID.DEATHRUNE,
				ItemID.SOULRUNE)
			.put(LootCategories.OTHER, ItemID.GOLD_RING, ItemID.KONAR_KEY, ItemID.DT2_SANGUINE_TORVA_KIT)
			.build();
		return new ForgottenFourBoss(BossData.builder()
			.id(WHISPERER_ID)
			.displayName("Whisperer")
			.iconItemId(ItemID.WHISPERERPET)
			.nameNpcId(NpcID.WHISPERER)
			// The Odd Figure (WHISPERER_SPAWN) is left out: it turning into the Whisperer is the start of the fight
			.bossNpcIds(ImmutableSet.of(NpcID.WHISPERER, NpcID.WHISPERER_MELEE))
			.variants(VARIANTS)
			.killNames(killNames("Whisperer"))
			.regions(ImmutableSet.of(WHISPERER_REGION_ID, SHADOW_REALM_REGION_ID))
			.waitingRegions(ImmutableSet.of(WHISPERER_APPROACH_REGION_ID))
			.drops(table.drops())
			.allTimeSources(ImmutableList.of(new AllTimeSource("drops_NPC_The Whisperer", "whisperer", null)))
			.lootCategories(categories)
			.areaNoun("Undercity")
			.emptyStateText("No trips yet. Go to the Whisperer in the Lassar Undercity to start one.")
			.build(),
			FightStart.SPAWN, ImmutableSet.of(WHISPERER_APPROACH_REGION_ID), Collections.emptySet(),
			table.luckNote("Bellator vestige"));
	}

	/**
	 * The Leviathan, on its island in the Scar. The rowboat's dock is where a trip waits.
	 */
	public static ForgottenFourBoss leviathan()
	{
		Table table = new Table(96, ItemID.VENATOR_VESTIGE, ItemID.SOULREAPER_AXE_LURE, ItemID.LEVIATHANPET, 1 / 2_500.0,
			1 / 53.6, ItemID.SMOKE_QUARTZ, 1 / 206.0);
		Map<Integer, String> categories = LootCategories.builder()
			.put(LootCategories.UNIQUES, ItemID.VENATOR_VESTIGE, ItemID.SOULREAPER_AXE_LURE, ItemID.CHROMIUM_INGOT,
				ItemID.VIRTUS_MASK, ItemID.VIRTUS_TOP, ItemID.VIRTUS_LEGS, ItemID.LEVIATHANPET)
			.put(LootCategories.SECONDARY_UNIQUES, ItemID.LEVIATHAN_TABLET, ItemID.DT2_AWAKENERS_ORB, ItemID.SMOKE_QUARTZ)
			.put(LootCategories.CONSUMABLES, ItemID._3DOSEPRAYERRESTORE, ItemID._2DOSERANGERSPOTION, ItemID.SEATURTLE)
			.put(LootCategories.RESOURCES, ItemID.IRON_ORE, ItemID.SILVER_ORE, ItemID.COAL, ItemID.GOLD_ORE,
				ItemID.ADAMANTITE_ORE, ItemID.RUNITE_ORE, ItemID.SAPPHIRE, ItemID.EMERALD, ItemID.RUBY, ItemID.UNCUT_RUBY,
				ItemID.UNCUT_DIAMOND, ItemID.DRAGON_JAVELIN_HEAD, ItemID.DRAGON_BOLTS_UNFEATHERED,
				ItemID.XBOWS_BOLT_TIPS_ONYX, ItemID.RAW_MANTARAY, ItemID.ANGLERFISH)
			.put(LootCategories.RUNES_AND_AMMO, ItemID.BRONZE_ARROW, ItemID.MITHRIL_ARROW, ItemID.ADAMANT_ARROW,
				ItemID.RUNE_ARROW, ItemID.BODYRUNE, ItemID.EARTHRUNE, ItemID.SMOKERUNE, ItemID.SOULRUNE)
			.put(LootCategories.OTHER, ItemID.GOLD_RING, ItemID.KONAR_KEY, ItemID.DT2_SANGUINE_TORVA_KIT)
			.build();
		return new ForgottenFourBoss(BossData.builder()
			.id(LEVIATHAN_ID)
			.displayName("Leviathan")
			.iconItemId(ItemID.LEVIATHANPET)
			.nameNpcId(NpcID.LEVIATHAN)
			.bossNpcIds(ImmutableSet.of(NpcID.LEVIATHAN))
			.variants(VARIANTS)
			.killNames(killNames("Leviathan"))
			.regions(ImmutableSet.of(LEVIATHAN_REGION_ID))
			.waitingRegions(ImmutableSet.of(LEVIATHAN_DOCK_REGION_ID))
			.drops(table.drops())
			.allTimeSources(ImmutableList.of(new AllTimeSource("drops_NPC_The Leviathan", "leviathan", null)))
			.lootCategories(categories)
			.areaNoun("island")
			.emptyStateText("No trips yet. Row out to the Leviathan's island to start one.")
			.build(),
			FightStart.SPAWN, ImmutableSet.of(LEVIATHAN_DOCK_REGION_ID), Collections.emptySet(),
			table.luckNote("Venator vestige"));
	}

	/**
	 * The four together, for the registry.
	 */
	public static List<BossDefinition> all()
	{
		return ImmutableList.of(vardorvis(), dukeSucellus(), whisperer(), leviathan());
	}

	/**
	 * The kill-count message's names: the boss's for a normal kill (no variant) and, unverified, "&lt;name&gt;
	 * (Awakened)" for the awakened one, as RuneLite's Chat Commands keys them.
	 */
	private static Map<String, String> killNames(String name)
	{
		Map<String, String> names = new LinkedHashMap<>();
		names.put(name, null);
		names.put(name + " (Awakened)", AWAKENED);
		return Collections.unmodifiableMap(names);
	}

	/**
	 * One boss's drop rates: the unique table's rolls per kill and what's on it, the pet and the secondary uniques
	 * that aren't one-offs.
	 */
	private static final class Table
	{
		private final int tableDenominator;
		private final int vestige;
		private final int axePiece;
		private final int pet;
		private final double petRate;
		private final double orbRate;
		private final int quartz;
		private final double quartzRate;

		Table(int tableDenominator, int vestige, int axePiece, int pet, double petRate, double orbRate, int quartz,
			double quartzRate)
		{
			this.tableDenominator = tableDenominator;
			this.vestige = vestige;
			this.axePiece = axePiece;
			this.pet = pet;
			this.petRate = petRate;
			this.orbRate = orbRate;
			this.quartz = quartz;
			this.quartzRate = quartzRate;
		}

		/**
		 * @param slots the item's slots of the table's 8
		 */
		private ExpectedDrop onTable(int itemId, DropKind kind, double slots)
		{
			return new ExpectedDrop(itemId, kind, context -> slots / 8 * tableRolls(context) / tableDenominator);
		}

		private static int tableRolls(KillContext context)
		{
			return AWAKENED.equals(context.getVariant()) ? AWAKENED_TABLE_ROLLS : 1;
		}

		List<ExpectedDrop> drops()
		{
			return ImmutableList.of(
				onTable(vestige, DropKind.UNIQUE, 1),
				onTable(axePiece, DropKind.UNIQUE, 1),
				onTable(ItemID.VIRTUS_MASK, DropKind.UNIQUE, 1 / 3.0),
				onTable(ItemID.VIRTUS_TOP, DropKind.UNIQUE, 1 / 3.0),
				onTable(ItemID.VIRTUS_LEGS, DropKind.UNIQUE, 1 / 3.0),
				onTable(ItemID.CHROMIUM_INGOT, DropKind.UNIQUE, 3),
				ExpectedDrop.fixed(pet, DropKind.PET, petRate),
				ExpectedDrop.fixed(ItemID.DT2_AWAKENERS_ORB, DropKind.TERTIARY, orbRate),
				ExpectedDrop.fixed(quartz, DropKind.TERTIARY, quartzRate));
		}

		String luckNote(String vestigeName)
		{
			return "Uniques are the tradeable table (1 in " + tableDenominator + " per kill, 3 times as often awakened): "
				+ "the " + vestigeName + ", the axe piece, Virtus and chromium ingots. The vestige drops on the third hit "
				+ "of its slot (the first two give gold rings), so its luck is approximate. The quartz rate is the "
				+ "wiki's average: it climbs from 1/200 to 1/50 until your first.";
		}
	}

	@Override
	public Set<Integer> getWaitingRegions()
	{
		return waitingRegions;
	}

	@Override
	public boolean isFightStartOnSpawn()
	{
		return fightStart == FightStart.SPAWN;
	}

	@Override
	public boolean isFightStartOnFirstHit()
	{
		return fightStart == FightStart.FIRST_HIT;
	}

	@Override
	public Long fightStartDelayMs(String text)
	{
		return fightStart == FightStart.AWAKENS_MESSAGE && DUKE_AWAKENS.matcher(text).find() ? 0L : null;
	}

	@Override
	public Set<Integer> getConvertedItems()
	{
		return convertedItems;
	}

	/**
	 * Dying puts your items in a gravestone outside the instance (OSRS Wiki). The fee is estimated from what you
	 * carried, by the gravestone's brackets, the moment you die. Unverified: no death in the logs, so the grave's
	 * options and payment message are assumed to match the Fractured Archive's ("Check", then "Payment has been taken
	 * from your bank: ... x Coins"), which replaces the estimate.
	 */
	@Override
	public Set<Integer> getGraveHelperNpcs()
	{
		return FracturedArchiveBoss.GRAVESTONES;
	}

	@Override
	public boolean isGravestoneFeeEstimated()
	{
		return true;
	}

	@Override
	public Long reclaimFee(String text)
	{
		return ChatPatterns.bankPayment(text);
	}

	@Override
	public boolean isLuckApproximate()
	{
		return true;
	}

	@Override
	public String getLuckNote()
	{
		return luckNote;
	}
}
