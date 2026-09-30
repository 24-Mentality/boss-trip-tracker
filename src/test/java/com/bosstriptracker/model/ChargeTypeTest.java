package com.bosstriptracker.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class ChargeTypeTest
{
	@Test
	public void rechargesFromTheWiki()
	{
		ChargeType scythe = ChargeType.forSourceItem(ItemID.SCYTHE_OF_VITUR);
		assertSame(ChargeType.SCYTHE_OF_VITUR, scythe);
		assertEquals(100, scythe.getChargesPerRecharge());
		assertEquals(ItemID.VIAL_BLOOD, scythe.getChargeItemId());
		// "Each vial and 200 blood runes adds 100 charges"
		assertEquals(200, scythe.getComponents().get(1).getQuantity());

		ChargeType shadow = ChargeType.TUMEKENS_SHADOW;
		assertEquals(1, shadow.getChargesPerRecharge());
		assertEquals(ItemID.SOULRUNE, shadow.getComponents().get(0).getItemId());
		assertEquals(2, shadow.getComponents().get(0).getQuantity());
		assertEquals(ItemID.CHAOSRUNE, shadow.getComponents().get(1).getItemId());
		assertEquals(5, shadow.getComponents().get(1).getQuantity());

		assertNull(ChargeType.forSourceItem(ItemID.CRIMSON_KISTEN));

		assertEquals(2, ChargeType.SANGUINESTI_STAFF.getComponents().get(0).getQuantity());
		assertEquals(4, ChargeType.TRIDENT_OF_THE_SWAMP.getComponents().size());
		assertEquals(ItemID.SNAKEBOSS_SCALE, ChargeType.TRIDENT_OF_THE_SWAMP.getComponents().get(3).getItemId());
		assertEquals(ItemID.COINS, ChargeType.TRIDENT_OF_THE_SEAS.getComponents().get(3).getItemId());
		assertEquals(ItemID.DEMON_TEAR, ChargeType.EYE_OF_AYAK.getChargeItemId());
		assertEquals(2, ChargeType.EYE_OF_AYAK_RUNES.getComponents().get(0).getQuantity());
		// Two scales every three shots; darts lost per 25 shots by cape
		assertEquals(3, ChargeType.TOXIC_BLOWPIPE_SCALES.getChargesPerRecharge());
		assertEquals(2, ChargeType.TOXIC_BLOWPIPE_SCALES.getComponents().get(0).getQuantity());
		assertEquals(5, ChargeType.TOXIC_BLOWPIPE_DARTS_80.getComponents().get(0).getQuantity());
		assertEquals(7, ChargeType.TOXIC_BLOWPIPE_DARTS_72.getComponents().get(0).getQuantity());
		assertEquals(10, ChargeType.TOXIC_BLOWPIPE_DARTS_60.getComponents().get(0).getQuantity());
		assertEquals(25, ChargeType.TOXIC_BLOWPIPE_DARTS_0.getComponents().get(0).getQuantity());
	}

	@Test
	public void savedLinesFindTheirType()
	{
		assertSame(ChargeType.TOXIC_BLOWPIPE_SCALES, ChargeType.forLine(ItemID.TOXIC_BLOWPIPE_LOADED, ItemID.SNAKEBOSS_SCALE));
		// Darts are saved as the dart chosen in config
		assertSame(ChargeType.TOXIC_BLOWPIPE_DARTS_80, ChargeType.forLine(ItemID.TOXIC_BLOWPIPE_LOADED, ItemID.DRAGON_DART));
		assertTrue(ChargeType.forLine(ItemID.TOXIC_BLOWPIPE_LOADED, ItemID.AMETHYST_DART).isBlowpipeDarts());
		assertSame(ChargeType.EYE_OF_AYAK_RUNES, ChargeType.forLine(ItemID.EYE_OF_AYAK, ItemID.DEATHRUNE));
		assertSame(ChargeType.TOME_OF_FIRE, ChargeType.forLine(ItemID.TOME_OF_FIRE, ItemID.WINT_BURNT_PAGE));
	}

	@Test
	public void chargeLinesArePricedFromTheWholeRecharge()
	{
		// The wiki's figure: 765.32 gp an attack with a vial of blood at 8,332 and blood runes at 341
		ItemEntry scythe = ItemEntry.charges(ItemID.SCYTHE_OF_VITUR, 100, ItemID.VIAL_BLOOD, 8_332 + 200 * 341, 100);
		assertEquals(76_532, scythe.totalValue());

		// 135 shadow casts with soul runes at 150 and chaos runes at 80: 700 gp a charge
		ItemEntry shadow = ItemEntry.charges(ItemID.TUMEKENS_SHADOW, 135, ItemID.SOULRUNE, 2 * 150 + 5 * 80, 1);
		assertEquals(94_500, shadow.totalValue());
	}
}
