package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import com.bosstriptracker.tracking.CollectionLogPage.Slot;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Pages as the game drew them on 2026-10-23, header texts with their colour tags.
 */
public class CollectionLogPageTest
{
	@Test
	public void aBossPage()
	{
		CollectionLogPage page = CollectionLogPage.parse(Arrays.asList("Vorkath", "Obtained: <col=0dc10d>6/6</col>",
				"Personal Best: <col=ffffff>1:08.40</col>", "Vorkath kills: <col=ffffff>4,340</col>"),
			Arrays.asList(new Slot(ItemID.VORKATHPET, 1, true), new Slot(ItemID.VORKATH_HEAD, 77, true),
				new Slot(ItemID.DRAGONFIRE_VISAGE, 2, true), new Slot(ItemID.SKELETAL_VISAGE, 2, true),
				new Slot(ItemID.JAR_OF_DECAY, 2, true), new Slot(ItemID.DRAGONBONE_NECKLACE, 5, true)));

		assertEquals("Vorkath", page.getName());
		assertEquals(Collections.singletonMap("Vorkath kills", 4_340), page.getCounts());
		assertEquals(Integer.valueOf(5), page.getItems().get(ItemID.DRAGONBONE_NECKLACE));
		assertEquals(Integer.valueOf(77), page.getItems().get(ItemID.VORKATH_HEAD));
		assertEquals(6, page.getItems().size());
	}

	@Test
	public void missingItemsAreLeftOutAndEmptySlotsIgnored()
	{
		CollectionLogPage page = CollectionLogPage.parse(Arrays.asList("Maggot King", "Obtained: <col=ffff00>1/3</col>",
				"Personal Best: <col=ffffff>1:36.00</col>", "Maggot King kills: <col=ffffff>1,007</col>", ""),
			Arrays.asList(new Slot(ItemID.MAGGOTKINGPET, 1, false), new Slot(ItemID.CRIMSON_KISTEN, 1, false),
				new Slot(ItemID.ELDER_VENATOR_FANG, 6, true), new Slot(-1, 0, true)));

		assertEquals(Collections.singletonMap(ItemID.ELDER_VENATOR_FANG, 6), page.getItems());
		assertEquals(Integer.valueOf(1_007), page.getCounts().get("Maggot King kills"));
		assertFalse(page.getCounts().containsKey("Obtained"));
		assertFalse(page.getCounts().containsKey("Personal Best"));
	}

	@Test
	public void sharedAndRaidPagesKeepEveryCount()
	{
		CollectionLogPage nightmare = CollectionLogPage.parse(Arrays.asList("The Nightmare", "Obtained: <col=ffff00>2/12</col>",
			"Personal Best: <col=ffffff>3:27.00</col>", "Phosani's Nightmare kills: <col=ffffff>194</col>",
			"Nightmare kills: <col=ffffff>2</col>"), Collections.emptyList());
		assertEquals("The Nightmare", nightmare.getName());
		assertEquals(Integer.valueOf(194), nightmare.getCounts().get("Phosani's Nightmare kills"));
		assertEquals(Integer.valueOf(2), nightmare.getCounts().get("Nightmare kills"));

		CollectionLogPage tob = CollectionLogPage.parse(Arrays.asList("Theatre of Blood", "Obtained: <col=ffff00>6/17</col>",
			"Theatre of Blood completions: <col=ffffff>235</col>", "Theatre of Blood (Entry) completions: <col=ffffff>1</col>",
			"Theatre of Blood (Hard) completions: <col=ffffff>0</col>"), Collections.emptyList());
		assertEquals(Integer.valueOf(235), tob.getCounts().get("Theatre of Blood completions"));
		assertEquals(Integer.valueOf(0), tob.getCounts().get("Theatre of Blood (Hard) completions"));

		CollectionLogPage doom = CollectionLogPage.parse(Arrays.asList("Doom of Mokhaiotl", "Obtained: <col=ffff00>4/6</col>",
			"Personal Best: <col=ffffff>N/A</col>", "Deepest delve: <col=ffffff>7</col>", "Deep delves: <col=ffffff>0</col>",
			"Total delves: <col=ffffff>1,446</col>"), Collections.emptyList());
		assertEquals(Integer.valueOf(1_446), doom.getCounts().get("Total delves"));
		assertEquals(Integer.valueOf(7), doom.getCounts().get("Deepest delve"));
		assertFalse(doom.getCounts().containsKey("Personal Best"));

		assertNull(CollectionLogPage.parse(Arrays.asList("", null), Collections.emptyList()));
	}
}
