package com.bosstriptracker.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A target number of kills, counted from when the goal was set or last reset.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KillGoal
{
	private int target;
	private long startedAt;
	/**
	 * Time logged in since startedAt, for kills per hour.
	 */
	private long activeMs;
}
