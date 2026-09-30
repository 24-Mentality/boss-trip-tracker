package com.maggotkingtriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

/**
 * Which page is used to price Tome of fire charges.
 */
@Getter
@RequiredArgsConstructor
public enum TomePage
{
	SEARING("Searing pages", ItemID.WINT_SEARING_PAGE),
	BURNT("Burnt pages", ItemID.WINT_BURNT_PAGE);

	private final String label;
	private final int itemId;

	@Override
	public String toString()
	{
		return label;
	}
}
