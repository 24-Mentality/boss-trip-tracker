package com.bosstriptracker.model;

/**
 * Probability helpers for drop rates.
 */
public final class DropOdds
{
	private DropOdds()
	{
	}

	/**
	 * @return chance of at least one drop in {@code kills} kills at {@code rate} per kill
	 */
	public static double chanceByNow(double rate, int kills)
	{
		return 1 - Math.pow(1 - rate, kills);
	}

	/**
	 * @return kills needed for a {@code chance} (0-1) probability of at least one drop
	 */
	public static int killsForChance(double rate, double chance)
	{
		return (int) Math.ceil(Math.log(1 - chance) / Math.log(1 - rate));
	}
}
