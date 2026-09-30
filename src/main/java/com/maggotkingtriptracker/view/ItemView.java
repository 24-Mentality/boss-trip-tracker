package com.maggotkingtriptracker.view;

import lombok.Value;

@Value
public class ItemView
{
	int itemId;
	String name;
	long quantity;
	long totalValue;
	boolean perDose;
	boolean unique;
	boolean pending;
	/**
	 * For charge lines, the name of what recharges the item (e.g. "Blood shard"); otherwise null.
	 */
	String chargeItemName;
	int chargesPerItem;
	/**
	 * For a polished tarnished drop, the tarnished item's name; otherwise null.
	 */
	String polishedFromName;
	/**
	 * A caveat shown in the tooltip, e.g. that a charge count is approximate at this boss; otherwise null.
	 */
	String note;

	public boolean isCharges()
	{
		return chargeItemName != null;
	}
}
