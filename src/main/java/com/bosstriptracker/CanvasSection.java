package com.bosstriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A panel card whose pin puts its numbers on the overlay or takes them off.
 */
@Getter
@RequiredArgsConstructor
public enum CanvasSection
{
	GOAL,
	TRIP,
	LOOT;

	/**
	 * The stat the pin puts on the overlay for this card.
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
