package com.bosstriptracker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An item and quantity with the GE price recorded when it was gained or used.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemEntry
{
	private int itemId;
	private long quantity;
	/**
	 * GE price per unit at record time. For per-dose entries this is the price of one dose.
	 */
	private long priceEach;
	/**
	 * Quantity counts potion doses (itemId is the highest-dose variant), not whole items.
	 */
	private boolean perDose;
	/**
	 * Tarnished drop whose real value is only known once it is polished.
	 */
	private boolean pending;
	private String pendingId;
	/**
	 * For charge lines: the item that recharges it (blood shard, page, ether), whose price is priceEach.
	 * Quantity is then a number of charges and itemId is the charged item. 0 for normal lines.
	 */
	private int chargeItemId;
	private int chargesPerItem;
	/**
	 * For a tarnished drop that has been polished: the tarnished item it came from. itemId is the result.
	 */
	private int polishedFrom;

	public ItemEntry(int itemId, long quantity, long priceEach)
	{
		this(itemId, quantity, priceEach, false, false, null, 0, 0, 0);
	}

	public static ItemEntry charges(int chargedItemId, long charges, int chargeItemId, long chargeItemPrice, int chargesPerItem)
	{
		return new ItemEntry(chargedItemId, charges, chargeItemPrice, false, false, null, chargeItemId, chargesPerItem, 0);
	}

	public boolean isCharges()
	{
		return chargeItemId > 0 && chargesPerItem > 0;
	}

	public long totalValue()
	{
		if (pending)
		{
			return 0;
		}
		if (isCharges())
		{
			return Math.round((double) quantity * priceEach / chargesPerItem);
		}
		return quantity * priceEach;
	}

	public ItemEntry copy()
	{
		return new ItemEntry(itemId, quantity, priceEach, perDose, pending, pendingId, chargeItemId, chargesPerItem,
			polishedFrom);
	}
}
