package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.boss.ForgottenFourBoss;
import com.bosstriptracker.model.ItemEntry;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SupplyAccountingTest
{
	private static final Integer[] TOB_REGIONS = {12869, 12613, 13125, 13122, 13123, 12612, 12611, 12867, 13379};
	/**
	 * The Nexus, Xarth, Bork, Dreadnoth the Jellied and the inner vault.
	 */
	private static final Integer[] TFA_REGIONS = {16471, 16726, 16727, 16728, 16472};

	/**
	 * A Maggot King trip (2026-09-28, 14:35 to 15:40): every Drink and Eat click in the lair is one dose or item.
	 * One of 41 prayer potion clicks and one of 8 antidote clicks did nothing (a double click).
	 */
	@Test
	public void maggotKingTripMatchesDrinkAndEatClicks() throws Exception
	{
		SupplyReplay replay = maggotKing().run();

		assertEquals(40, replay.quantity(ItemID._4DOSEPRAYERRESTORE));
		assertEquals(7, replay.quantity(ItemID.ANTIDOTE__4));
		assertEquals(3, replay.quantity(ItemID._4DOSEDIVINECOMBAT));
		assertEquals(31, replay.quantity(ItemID.STYMPHIKE_TARTARE));
		assertEquals(1, replay.quantity(ItemID.ANGLERFISH));
		assertEquals(5, replay.used.size());
		// Dropped to make room for loot; the medals are polished, not supplies
		assertEquals(Long.valueOf(3), replay.dropped.get(ItemID.STYMPHIKE_TARTARE));
		assertEquals(Long.valueOf(15), replay.dropped.get(ItemID.VIAL_EMPTY));
	}

	/**
	 * Phosani's Nightmare (2026-09-28, 20:36 to 20:56) with a death: items lost dying are never supplies.
	 */
	@Test
	public void phosanisTripLeavesOutTheDeath() throws Exception
	{
		SupplyReplay replay = new SupplyReplay("supply-test-phosani.log", 15515)
			.recoverable(ItemID.BLISTERWOOD_STAKE).run();

		assertEquals(17, replay.quantity(ItemID._4DOSEPRAYERRESTORE));
		assertEquals(6, replay.quantity(ItemID.SANFEW_SALVE_4_DOSE));
		assertEquals(3, replay.quantity(ItemID._4DOSEDIVINECOMBAT));
		assertEquals(2, replay.quantity(ItemID.ANGLERFISH));
		assertEquals(4, replay.used.size());
	}

	/**
	 * A Normal Theatre of Blood raid (2026-09-29) with a death at Bloat, which keeps every item.
	 */
	@Test
	public void theatreOfBloodRaidCountsDoses() throws Exception
	{
		SupplyReplay replay = new SupplyReplay("supply-test-tob.log", TOB_REGIONS).deathKeepsItems().run();

		assertEquals(11, replay.quantity(ItemID._4DOSE2RESTORE));
		assertEquals(9, replay.quantity(ItemID._4DOSEPOTIONOFSARADOMIN));
		assertEquals(3, replay.quantity(ItemID._4DOSEDIVINECOMBAT));
		assertEquals(4, replay.quantity(ItemID.DRAGON_ARROW));
		assertTrue(replay.line(ItemID._4DOSE2RESTORE).isPerDose());
		// Picked up at Verzik and gone when the room ends: gear is never a supply
		assertNull(replay.line(ItemID.VERZIK_SPECIAL_WEAPON));
	}

	/**
	 * A Fractured Archive raid (2026-10-21, 22:26 to 23:09): five challenges, deaths at Dreadnoth and Ocellus, four
	 * brews, a restore and a prayer potion bought from the Quartermaster, part-used potions picked up from and dropped
	 * on the Nexus floor for teammates, and a wipe at Ocellus that put every item in a gravestone in the same tick.
	 * Only what was used is a supply: the wipe isn't, and neither is gear.
	 */
	@Test
	public void fracturedArchiveRaidLeavesOutTheWipe() throws Exception
	{
		SupplyReplay replay = new SupplyReplay("supply-test-fractured-archive.log", TFA_REGIONS)
			.wipedOn("You failed to survive the Fractured Archive.").run();

		// Arrows shot and chinchompas thrown (switching a stack to the weapon slot nets out); the 754 arrows and 23
		// chinchompas lost to the gravestone aren't counted
		assertEquals(21, replay.quantity(ItemID.DRAGON_ARROW));
		assertEquals(7, replay.quantity(ItemID.CHINCHOMPA_BLACK));
		// Potions by the dose: 31 brew doses drunk, plus the 11 in the brews dropped for teammates, match the 42 Drink
		// clicks on brews. No whole potions from the wipe.
		assertTrue(replay.line(ItemID._4DOSEPOTIONOFSARADOMIN).isPerDose());
		assertEquals(31, replay.quantity(ItemID._4DOSEPOTIONOFSARADOMIN));
		assertEquals(13, replay.quantity(ItemID._4DOSE2RESTORE));
		assertEquals(6, replay.quantity(ItemID._4DOSEPRAYERRESTORE));
		assertEquals(4, replay.quantity(ItemID._4DOSERANGERSPOTION));
		assertEquals(2, replay.quantity(ItemID._4DOSE2COMBAT));
		assertEquals(2, replay.quantity(ItemID._4DOSESTATRENEWAL));
		assertEquals(1, replay.quantity(ItemID.SANFEW_SALVE_4_DOSE));
		assertEquals(Long.valueOf(2), replay.dropped.get(ItemID._4DOSEPOTIONOFSARADOMIN));
		assertEquals(Long.valueOf(1), replay.dropped.get(ItemID._3DOSEPOTIONOFSARADOMIN));
		assertEquals(Long.valueOf(2), replay.dropped.get(ItemID._1DOSE2RESTORE));
		// What the raid gave you, in doses: Quartermaster purchases (4 brews, a restore, a prayer potion) and the
		// part-used potions picked up in the Nexus (a brew(2), restore(2) and (1), prayer potion(2))
		assertEquals(18, acquired(replay, ItemID._4DOSEPOTIONOFSARADOMIN));
		assertEquals(7, acquired(replay, ItemID._4DOSE2RESTORE));
		assertEquals(6, acquired(replay, ItemID._4DOSEPRAYERRESTORE));
		// Gear switched, worn and lost is never a supply; nor is the chugging barrel
		assertNull(replay.line(ItemID.SCYTHE_OF_VITUR));
		assertNull(replay.line(ItemID.TWISTED_BOW));
		assertNull(replay.line(ItemID.TONALZTICS_OF_RALOS_CHARGED));
		assertNull(replay.line(ItemID.TOXIC_BLOWPIPE_LOADED));
		assertNull(replay.line(ItemID.MM_PREPOT_DEVICE));
		assertEquals(9, replay.used.size());
	}

	/**
	 * Three Vardorvis kills (2026-10-22, 22:47 to 22:56), walking out to the Stranglewood and back between the first
	 * two: Drink and Eat clicks in the ritual site, and the house teleport out; the floor loot is only acquired.
	 */
	@Test
	public void vardorvisTripCountsDosesAndTheTeleportOut() throws Exception
	{
		SupplyReplay replay = new SupplyReplay("supply-test-vardorvis.log", ForgottenFourBoss.VARDORVIS_REGION_ID).run();

		assertEquals(7, replay.quantity(ItemID._4DOSEPRAYERRESTORE));
		assertEquals(2, replay.quantity(ItemID._4DOSEDIVINECOMBAT));
		assertEquals(8, replay.quantity(ItemID.SHARK));
		assertEquals(1, replay.quantity(ItemID.POH_TABLET_TELEPORTTOHOUSE));
		assertNull(replay.line(ItemID.SCYTHE_OF_VITUR));
		assertNull(replay.line(ItemID.RING_OF_SHADOWS));
		assertEquals(4, replay.used.size());
		assertTrue(replay.dropped.isEmpty());
		assertEquals(36, acquired(replay, ItemID.RUNE_JAVELIN_HEAD));
		assertEquals(200, acquired(replay, ItemID.LAVARUNE));
	}

	/**
	 * Two Duke Sucellus kills (2026-10-22, 22:59 to 23:07): the Arder mushrooms picked in the chamber and ground into
	 * powder (16 mushrooms, 80 powder) are conversions, never supplies; food dropped to make room is picked back up.
	 */
	@Test
	public void dukeSucellusMushroomsAreNeverSupplies() throws Exception
	{
		SupplyReplay replay = new SupplyReplay("supply-test-duke.log", ForgottenFourBoss.DUKE_REGION_ID)
			.converted(ForgottenFourBoss.dukeSucellus().getConvertedItems().toArray(new Integer[0])).run();

		assertEquals(2, replay.quantity(ItemID._4DOSEPRAYERRESTORE));
		assertEquals(1, replay.quantity(ItemID._4DOSEDIVINECOMBAT));
		assertEquals(1, replay.quantity(ItemID.SHARK));
		assertEquals(1, replay.quantity(ItemID.POH_TABLET_TELEPORTTOHOUSE));
		assertNull(replay.line(ItemID.DUKE_SUCELLUS_MUSHROOM_3));
		assertNull(replay.line(ItemID.DUKE_SUCELLUS_MUSHROOM_3_GROUND));
		assertEquals(4, replay.used.size());
		assertEquals(16, acquired(replay, ItemID.DUKE_SUCELLUS_MUSHROOM_3));
		assertEquals(80, acquired(replay, ItemID.DUKE_SUCELLUS_MUSHROOM_3_GROUND));
		// Three anglerfish and a shark dropped for the mushrooms, the anglerfish picked back up before leaving
		assertEquals(Long.valueOf(3), replay.dropped.get(ItemID.ANGLERFISH));
		assertEquals(3, acquired(replay, ItemID.ANGLERFISH));
	}

	private static long acquired(SupplyReplay replay, int itemId)
	{
		for (ItemEntry entry : replay.acquired)
		{
			if (entry.getItemId() == itemId)
			{
				return entry.getQuantity();
			}
		}
		return 0;
	}

	private final FakeItems items = new FakeItems()
		.gear(ItemID.SCYTHE_OF_VITUR, "Scythe of vitur", 1_200_000_000L)
		.gear(ItemID.SCYTHE_OF_VITUR_UNCHARGED, "Scythe of vitur (uncharged)", 1_150_000_000L)
		.gear(ItemID.TOXIC_BLOWPIPE_LOADED, "Toxic blowpipe", 6_000_000)
		.gear(ItemID.TOXIC_BLOWPIPE, "Toxic blowpipe (empty)", 5_900_000)
		.gear(ItemID.BLOOD_AMULET, "Amulet of blood fury", 3_000_000)
		.gear(ItemID.ENCHANTED_ONYX_AMULET, "Amulet of fury", 2_500_000)
		.gear(ItemID.RING_OF_RECOIL, "Ring of recoil", 6_000)
		.gear(ItemID.BARROWS_AHRIM_HEAD_100, "Ahrim's hood 100", 900_000)
		.gear(ItemID.BARROWS_AHRIM_HEAD_75, "Ahrim's hood 75", 850_000)
		.ammo(ItemID.DRAGON_ARROW, "Dragon arrow", 1_500)
		.item(ItemID.DRAGON_DART, "Dragon dart", 2_000)
		.item(ItemID.SNAKEBOSS_SCALE, "Zulrah's scales", 150)
		.item(ItemID.BLOOD_SHARD, "Blood shard", 2_500_000)
		.item(ItemID.STYMPHIKE_FEATHER, "Stymphike feather", 400)
		.item(ItemID.PIEDISH, "Pie dish", 5)
		.food(ItemID.SUMMER_PIE, "Summer pie", 1_200)
		.food(ItemID.HALF_SUMMER_PIE, "Half a summer pie", 700)
		.food(ItemID.STYMPHIKE_TARTARE, "Stymphike tartare", 3_000)
		.food(ItemID.ANGLERFISH, "Anglerfish", 1_600);
	private final SupplyAccounting accounting = new SupplyAccounting(items);
	private final RecentClicks clicks = new RecentClicks();

	@Test
	public void weaponRunningDryIsNotASupply()
	{
		SupplyAccounting.Change change = change(ImmutableMap.of(ItemID.SCYTHE_OF_VITUR, -1L, ItemID.SCYTHE_OF_VITUR_UNCHARGED, 1L));
		List<ItemEntry> used = used(change);

		assertTrue(used.isEmpty());
		// The uncharged scythe isn't loot or something obtained inside either
		assertTrue(change.gained.isEmpty());
		assertTrue(accounting.acquisitions(change).isEmpty());
	}

	@Test
	public void barrowsDegradingAndBloodFuryRunningDryAreNotSupplies()
	{
		assertTrue(used(change(ImmutableMap.of(ItemID.BARROWS_AHRIM_HEAD_100, -1L, ItemID.BARROWS_AHRIM_HEAD_75, 1L))).isEmpty());
		assertTrue(used(change(ImmutableMap.of(ItemID.BLOOD_AMULET, -1L, ItemID.ENCHANTED_ONYX_AMULET, 1L))).isEmpty());
		// Gear that just disappears isn't either (left in a raid room)
		assertTrue(used(change(ImmutableMap.of(ItemID.TOXIC_BLOWPIPE_LOADED, -1L))).isEmpty());
	}

	@Test
	public void ammoAndBrokenJewelleryAreStillSupplies()
	{
		List<ItemEntry> used = used(change(ImmutableMap.of(ItemID.DRAGON_ARROW, -3L, ItemID.RING_OF_RECOIL, -1L)));

		assertEquals(3, quantity(used, ItemID.DRAGON_ARROW));
		assertEquals(1, quantity(used, ItemID.RING_OF_RECOIL));
	}

	@Test
	public void rechargingIsAConversion()
	{
		// Darts and scales into an empty blowpipe
		clicks.add(10, "Use", ItemID.DRAGON_DART);
		clicks.add(11, "Use", ItemID.TOXIC_BLOWPIPE);
		SupplyAccounting.Change change = change(ImmutableMap.of(ItemID.DRAGON_DART, -500L, ItemID.SNAKEBOSS_SCALE, -2_000L,
			ItemID.TOXIC_BLOWPIPE, -1L, ItemID.TOXIC_BLOWPIPE_LOADED, 1L));
		assertTrue(used(change, 11).isEmpty());
		assertTrue(change.gained.isEmpty());

		// A blood shard onto an amulet of fury
		clicks.clear();
		clicks.add(20, "Use", ItemID.ENCHANTED_ONYX_AMULET);
		assertTrue(used(change(ImmutableMap.of(ItemID.BLOOD_SHARD, -1L, ItemID.ENCHANTED_ONYX_AMULET, -1L,
			ItemID.BLOOD_AMULET, 1L)), 20).isEmpty());
	}

	@Test
	public void rechargeItemsUsedOtherwiseAreSupplies()
	{
		// Darts thrown from the inventory without a recharge click
		assertEquals(5, quantity(used(change(ImmutableMap.of(ItemID.DRAGON_DART, -5L))), ItemID.DRAGON_DART));
	}

	@Test
	public void splitFoodCostsOnlyThePartEaten()
	{
		clicks.add(5, "Eat", ItemID.SUMMER_PIE);
		SupplyAccounting.Change change = change(ImmutableMap.of(ItemID.SUMMER_PIE, -1L, ItemID.HALF_SUMMER_PIE, 1L));
		List<ItemEntry> used = used(change, 5);

		assertEquals(1, used.size());
		assertEquals(ItemID.SUMMER_PIE, used.get(0).getItemId());
		assertEquals(500, used.get(0).totalValue());
		assertTrue(change.gained.isEmpty());

		// The second half leaves a pie dish: the two halves add up to the whole pie, less the dish
		clicks.add(8, "Eat", ItemID.HALF_SUMMER_PIE);
		used = used(change(ImmutableMap.of(ItemID.HALF_SUMMER_PIE, -1L, ItemID.PIEDISH, 1L)), 8);
		assertEquals(695, used.get(0).totalValue());
	}

	@Test
	public void lootInTheSameTickIsNotAFoodRemainder()
	{
		clicks.add(5, "Eat", ItemID.STYMPHIKE_TARTARE);
		SupplyAccounting.Change change = change(ImmutableMap.of(ItemID.STYMPHIKE_TARTARE, -1L, ItemID.STYMPHIKE_FEATHER, 1L));
		List<ItemEntry> used = used(change, 5);

		assertEquals(3_000, used.get(0).totalValue());
		assertEquals(Long.valueOf(1), change.gained.get(ItemID.STYMPHIKE_FEATHER));
	}

	@Test
	public void droppedSuppliesArentJunkWhilePricesAreMissing()
	{
		items.item(ItemID.VIAL_EMPTY, "Vial", 2);
		assertTrue(accounting.droppedAsUsed(ImmutableMap.of(ItemID.VIAL_EMPTY, 3L)).isEmpty());

		items.pricesLoaded = false;
		List<ItemEntry> used = accounting.droppedAsUsed(ImmutableMap.of(ItemID.ANGLERFISH, 1L));
		assertEquals(1, used.size());
		assertEquals(0, used.get(0).getPriceEach());
	}

	@Test
	public void namesShareAWord()
	{
		assertTrue(SupplyAccounting.sharesWord("Summer pie", "Half a summer pie"));
		assertTrue(SupplyAccounting.sharesWord("Anchovy pizza", "1/2 anchovy pizza"));
		assertFalse(SupplyAccounting.sharesWord("Shark", "Half a summer pie"));
		// Short words don't count
		assertFalse(SupplyAccounting.sharesWord("Cup of tea", "Bag of salt"));
	}

	private SupplyAccounting.Change change(Map<Integer, Long> delta)
	{
		return SupplyAccounting.Change.of(delta);
	}

	private List<ItemEntry> used(SupplyAccounting.Change change)
	{
		return used(change, 100);
	}

	private List<ItemEntry> used(SupplyAccounting.Change change, int tick)
	{
		accounting.removeConversions(change, clicks, tick, Collections.emptySet());
		return accounting.consumption(change);
	}

	private static long quantity(List<ItemEntry> used, int itemId)
	{
		return used.stream().filter(e -> e.getItemId() == itemId).mapToLong(ItemEntry::getQuantity).sum();
	}

	private static SupplyReplay maggotKing() throws Exception
	{
		return new SupplyReplay("supply-test-maggot-king.log", 11645).converted(ItemID.DULL_ZAROSIAN_MEDAL);
	}
}
