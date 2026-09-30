package com.bosstriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The overlay's profit row: the trip's.
 */
@Getter
@RequiredArgsConstructor
public enum OverlayLootStat
{
	NET_PROFIT("Net profit"),
	NET_GP_PER_HOUR("Net GP/hr");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
