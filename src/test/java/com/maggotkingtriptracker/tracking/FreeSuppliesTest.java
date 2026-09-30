package com.maggotkingtriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.maggotkingtriptracker.model.ItemEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class FreeSuppliesTest
{
	@Test
	public void chestPotionIsFreeWhetherDrunkOrDropped()
	{
		FreeSupplies free = new FreeSupplies();
		// Bought a Stamina potion(4) from the supply chest
		free.acquired(doses(ItemID._4DOSESTAMINA, 4));

		// Two doses shared, then the rest dropped for room: all four are the chest's
		assertTrue(free.paidFor(Collections.singletonList(doses(ItemID._4DOSESTAMINA, 2))).isEmpty());
		assertTrue(free.paidFor(Collections.singletonList(doses(ItemID._4DOSESTAMINA, 2))).isEmpty());
		// A dose beyond what was bought was brought in
		List<ItemEntry> paid = free.paidFor(Collections.singletonList(doses(ItemID._4DOSESTAMINA, 1)));
		assertEquals(1, paid.size());
		assertEquals(1, paid.get(0).getQuantity());
	}

	@Test
	public void onlyUseBeyondWhatWasObtainedIsPaidFor()
	{
		FreeSupplies free = new FreeSupplies();
		free.acquired(doses(ItemID._4DOSEPOTIONOFSARADOMIN, 4));

		// Six brew doses and two anglerfish used: 2 doses and both fish were brought in
		List<ItemEntry> paid = free.paidFor(Arrays.asList(doses(ItemID._4DOSEPOTIONOFSARADOMIN, 6),
			new ItemEntry(ItemID.ANGLERFISH, 2, 1_500)));
		assertEquals(2, paid.size());
		assertEquals(2, paid.get(0).getQuantity());
		assertTrue(paid.get(0).isPerDose());
		assertEquals(2, paid.get(1).getQuantity());
		assertEquals(0, free.remaining(ItemID._4DOSEPOTIONOFSARADOMIN, true));
	}

	@Test
	public void wholeItemsAndDosesAreKeptApartAndChargesAreNeverFree()
	{
		FreeSupplies free = new FreeSupplies();
		// Three dragon arrows picked back up
		free.acquired(new ItemEntry(ItemID.DRAGON_ARROW, 3, 1_000));
		free.acquired(ItemEntry.charges(ItemID.SCYTHE_OF_VITUR, 10, ItemID.VIAL_BLOOD, 70_000, 100));

		List<ItemEntry> paid = free.paidFor(Arrays.asList(new ItemEntry(ItemID.DRAGON_ARROW, 5, 1_000),
			ItemEntry.charges(ItemID.SCYTHE_OF_VITUR, 10, ItemID.VIAL_BLOOD, 70_000, 100)));
		assertEquals(2, paid.get(0).getQuantity());
		assertEquals(10, paid.get(1).getQuantity());
		assertTrue(paid.get(1).isCharges());
		// Whole brews don't cover doses
		free.acquired(new ItemEntry(ItemID._4DOSEPOTIONOFSARADOMIN, 1, 8_000));
		assertEquals(1, free.paidFor(Collections.singletonList(doses(ItemID._4DOSEPOTIONOFSARADOMIN, 1))).size());
	}

	private static ItemEntry doses(int fullDoseItemId, long doses)
	{
		ItemEntry entry = new ItemEntry(fullDoseItemId, doses, 2_000);
		entry.setPerDose(true);
		return entry;
	}

	@Test
	public void whatIsObtainedLaterMakesUpForUseAlreadyPaidFor()
	{
		FreeSupplies free = new FreeSupplies();
		// Four dragon arrows fired, then three picked back up (the Nylocas room of the diagnostic raid)
		assertEquals(4, free.paidFor(Collections.singletonList(new ItemEntry(ItemID.DRAGON_ARROW, 4, 1_000))).get(0).getQuantity());
		assertEquals(3, free.acquired(new ItemEntry(ItemID.DRAGON_ARROW, 3, 1_000)));
		assertEquals(0, free.remaining(ItemID.DRAGON_ARROW, false));

		// Six restore doses drunk, then two bought from the chest (8 doses): 6 make up for what was drunk, 2 stay free
		assertEquals(6, free.paidFor(Collections.singletonList(doses(ItemID._4DOSE2RESTORE, 6))).get(0).getQuantity());
		assertEquals(6, free.acquired(doses(ItemID._4DOSE2RESTORE, 8)));
		assertEquals(2, free.remaining(ItemID._4DOSE2RESTORE, true));
		assertTrue(free.paidFor(Collections.singletonList(doses(ItemID._4DOSE2RESTORE, 2))).isEmpty());
	}
}
