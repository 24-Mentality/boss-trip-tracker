package com.bosstriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A panel card whose right-click menu puts its numbers on the overlay ("Add to canvas") or takes them off.
 */
@Getter
@RequiredArgsConstructor
public enum CanvasSection
{
	GOAL,
	TRIP,
	LOOT;

	/**
	 * The stat "Add to canvas" puts on the overlay for this card.
	 */
	public OverlayStat defaultStat()
	{
		switch (this)
		{
			case GOAL:
				return OverlayStat.KILLS_PER_HOUR;
			case TRIP:
				return OverlayStat.CURRENT_KILL;
			default:
				return OverlayStat.NET_PROFIT;
		}
	}
}
