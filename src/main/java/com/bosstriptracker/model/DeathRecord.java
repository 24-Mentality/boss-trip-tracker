package com.bosstriptracker.model;

import lombok.Data;

@Data
public class DeathRecord
{
	private long at;
	private long reclaimFee;
	/**
	 * Value of what was paid to the aranei scout to move the gravestone.
	 */
	private long graveMoveCost;

	public long totalCost()
	{
		return reclaimFee + graveMoveCost;
	}

	public DeathRecord copy()
	{
		DeathRecord copy = new DeathRecord();
		copy.at = at;
		copy.reclaimFee = reclaimFee;
		copy.graveMoveCost = graveMoveCost;
		return copy;
	}
}
