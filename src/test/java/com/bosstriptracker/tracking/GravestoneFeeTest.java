package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.tracking.GravestoneFee.Stack;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The gravestone's brackets (OSRS Wiki): free under 100k, 1,000 to 1m, 10,000 to 10m, 100,000 above, 500,000 at most.
 */
public class GravestoneFeeTest
{
	@Test
	public void bracketsAndTheCap()
	{
		assertEquals(0, GravestoneFee.stackFee(99_999));
		assertEquals(1_000, GravestoneFee.stackFee(100_000));
		assertEquals(1_000, GravestoneFee.stackFee(999_999));
		assertEquals(10_000, GravestoneFee.stackFee(1_000_000));
		assertEquals(10_000, GravestoneFee.stackFee(9_999_999));
		assertEquals(100_000, GravestoneFee.stackFee(10_000_000));
		assertEquals(100_000, GravestoneFee.stackFee(1_500_000_000));
	}

	/**
	 * A near-max melee setup at Vardorvis: the three most valuable pieces are kept, the rest still reaches the cap.
	 */
	@Test
	public void nearMaxGearPaysTheCap()
	{
		List<Stack> gear = Arrays.asList(
			new Stack(1, 1, 1_250_000_000L), // scythe, kept
			new Stack(2, 1, 400_000_000L), // torva helm, kept
			new Stack(3, 1, 200_000_000L), // oathplate chest, kept
			new Stack(4, 1, 150_000_000L), // oathplate legs
			new Stack(5, 1, 90_000_000L), // dragon claws
			new Stack(6, 1, 30_000_000L), // amulet of rancour
			new Stack(7, 1, 20_000_000L), // bellator ring
			new Stack(8, 1, 15_000_000L), // ferocious gloves
			new Stack(9, 1, 12_000_000L), // avernic treads
			new Stack(10, 1, 0), // infernal cape, untradeable
			new Stack(11, 4, 2_000)); // prayer potions, free
		assertEquals(GravestoneFee.CAP, GravestoneFee.estimate(gear, 3, false));
		// Protect Item keeps the legs too: 5 items at 100,000 = the cap still
		assertEquals(500_000, GravestoneFee.estimate(gear, 4, false));
		// Skulled, nothing kept: capped all the same; an ironman pays half
		assertEquals(500_000, GravestoneFee.estimate(gear, 0, false));
		assertEquals(250_000, GravestoneFee.estimate(gear, 3, true));
	}

	@Test
	public void cheaperSetupsAddUpByBracket()
	{
		List<Stack> gear = Arrays.asList(
			new Stack(1, 1, 5_000_000), // kept
			new Stack(2, 1, 3_000_000), // kept
			new Stack(3, 1, 2_000_000), // kept
			new Stack(4, 1, 1_200_000), // 10,000
			new Stack(5, 1, 400_000), // 1,000
			new Stack(6, 1, 150_000), // 1,000
			new Stack(7, 20, 1_500), // sharks, 30,000 in all: free
			new Stack(8, 2_000, 900)); // runes, 1,800,000 as a stack: 10,000
		assertEquals(22_000, GravestoneFee.estimate(gear, 3, false));
		assertEquals(11_000, GravestoneFee.estimate(gear, 3, true));
	}

	@Test
	public void keptUnitsComeOutOfStacks()
	{
		// 3 of 100 blood runes are kept; the other 97 (291,000) cost 1,000
		List<Stack> runes = Collections.singletonList(new Stack(1, 100, 3_000));
		assertEquals(1_000, GravestoneFee.estimate(runes, 3, false));
		// With nothing worth keeping, nothing is kept and nothing is owed
		assertEquals(0, GravestoneFee.estimate(Collections.singletonList(new Stack(1, 1, 0)), 3, false));
		assertEquals(0, GravestoneFee.estimate(Collections.emptyList(), 3, false));
	}

	@Test
	public void whoKeepsWhatAndWhoPaysHalf()
	{
		assertEquals(3, GravestoneFee.keptUnits(false, false));
		assertEquals(4, GravestoneFee.keptUnits(false, true));
		assertEquals(0, GravestoneFee.keptUnits(true, false));
		assertEquals(1, GravestoneFee.keptUnits(true, true));
		assertFalse(GravestoneFee.hasIronmanDiscount(0));
		assertTrue(GravestoneFee.hasIronmanDiscount(1));
		assertFalse(GravestoneFee.hasIronmanDiscount(2));
		assertTrue(GravestoneFee.hasIronmanDiscount(3));
		assertTrue(GravestoneFee.hasIronmanDiscount(4));
	}
}
