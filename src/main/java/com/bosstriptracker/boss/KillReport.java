package com.bosstriptracker.boss;

import lombok.Value;

/**
 * A kill a boss reports in its own words rather than with "Your &lt;boss&gt; kill count is" (the Doom of Mokhaiotl's
 * "Delve level: 3 duration: 1:30.00").
 */
@Value
public class KillReport
{
	/**
	 * The kill count on the boss's scale, or null if the message doesn't carry one.
	 */
	Integer killCount;
	/**
	 * The variant the kill belongs to, or null.
	 */
	String variant;
	/**
	 * The game's fight duration, or null if the message doesn't carry one.
	 */
	Long durationMs;
}
