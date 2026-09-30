package com.maggotkingtriptracker.boss;

import lombok.Value;

/**
 * A raid's completion-count message: the mode completed and the game's count for that mode.
 */
@Value
public class RaidCompletion
{
	/**
	 * Variant id of the mode.
	 */
	String variant;
	int count;
}
