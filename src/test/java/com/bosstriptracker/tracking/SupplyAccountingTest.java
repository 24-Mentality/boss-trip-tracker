package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class SupplyAccountingTest
{
	private static final Integer[] TOB_REGIONS = {12869, 12613, 13125, 13122, 13123, 12612, 12611, 12867, 13379};

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
	}

	private static SupplyReplay maggotKing() throws Exception
	{
		return new SupplyReplay("supply-test-maggot-king.log", 11645).converted(ItemID.DULL_ZAROSIAN_MEDAL);
	}
}
