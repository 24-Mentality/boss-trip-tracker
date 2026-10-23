package com.bosstriptracker.tracking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Value;

/**
 * What a gravestone charges to give items back (OSRS Wiki, Gravestone): per stack by its Grand Exchange value, under
 * 100,000 free, 1,000 from 100,000, 10,000 from 1 million, 100,000 from 10 million, 500,000 at most, half for
 * ironmen (not ultimate). The items kept on death are left out first: the 3 highest-valued units (4 with Protect
 * Item; 0 when skulled, 1 skulled with Protect Item). Untradeable items are taken as free (unverified: Death gives some
 * of them a value of their own).
 */
public final class GravestoneFee
{
	public static final long CAP = 500_000;
	static final long FREE_BELOW = 100_000;

	private GravestoneFee()
	{
	}

	/**
	 * One stack carried at death.
	 */
	@Value
	public static class Stack
	{
		int itemId;
		long quantity;
		/**
		 * Grand Exchange price of one; 0 for an untradeable item.
		 */
		long unitPrice;
	}

	/**
	 * @param accountType the IRONMAN varbit: 0 a normal account, 2 an ultimate ironman, any other kind an ironman
	 */
	public static boolean hasIronmanDiscount(int accountType)
	{
		return accountType != 0 && accountType != 2;
	}

	/**
	 * How many units are kept on death.
	 */
	public static int keptUnits(boolean skulled, boolean protectItem)
	{
		return (skulled ? 0 : 3) + (protectItem ? 1 : 0);
	}

	/**
	 * The fee for one stack.
	 */
	static long stackFee(long value)
	{
		if (value < FREE_BELOW)
		{
			return 0;
		}
		if (value < 1_000_000)
		{
			return 1_000;
		}
		if (value < 10_000_000)
		{
			return 10_000;
		}
		return 100_000;
	}

	/**
	 * @param carried  everything in the inventory and worn at death
	 * @param keptUnits see {@link #keptUnits}
	 * @param ironman  see {@link #hasIronmanDiscount}
	 * @return the fee to get everything else back from the grave
	 */
	public static long estimate(List<Stack> carried, int keptUnits, boolean ironman)
	{
		List<Stack> lost = new ArrayList<>();
		for (Stack stack : carried)
		{
			if (stack.getQuantity() > 0)
			{
				lost.add(stack);
			}
		}
		// The most valuable units are kept, one at a time: a stack can lose several
		lost.sort(Comparator.comparingLong(Stack::getUnitPrice).reversed());
		int toKeep = keptUnits;
		for (int i = 0; i < lost.size() && toKeep > 0; i++)
		{
			Stack stack = lost.get(i);
			if (stack.getUnitPrice() <= 0)
			{
				break;
			}
			long kept = Math.min(toKeep, stack.getQuantity());
			toKeep -= kept;
			lost.set(i, new Stack(stack.getItemId(), stack.getQuantity() - kept, stack.getUnitPrice()));
		}

		long fee = 0;
		for (Stack stack : lost)
		{
			fee += stackFee(stack.getQuantity() * stack.getUnitPrice());
		}
		fee = Math.min(fee, CAP);
		return ironman ? fee / 2 : fee;
	}
}
