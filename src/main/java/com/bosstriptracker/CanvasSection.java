package com.bosstriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A part of the overlay that a panel card can add to or remove from the game screen ("Add to canvas").
 */
@Getter
@RequiredArgsConstructor
public enum CanvasSection
{
	GOAL("overlayShowGoal"),
	TRIP("overlayShowTrip"),
	LOOT("overlayShowLoot");

	/**
	 * The config key of the section's Show toggle.
	 */
	private final String configKey;

	public boolean isShown(BossTripTrackerConfig config)
	{
		switch (this)
		{
			case GOAL:
				return config.overlayShowGoal();
			case TRIP:
				return config.overlayShowTrip();
			default:
				return config.overlayShowLoot();
		}
	}
}
