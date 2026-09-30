package com.bosstriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Which Luck card the Trip tab shows.
 */
@Getter
@RequiredArgsConstructor
public enum LuckCardStyle
{
	/**
	 * Laid out like the share card's luck section: uniques, since last unique, overdue, rate and unique counts.
	 */
	OVERVIEW("Overview"),
	/**
	 * The original card: uniques, dry streak, chance by now, next unique and a progress bar.
	 */
	CLASSIC("Classic");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
