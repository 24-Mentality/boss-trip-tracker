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

	/**
	 * Share of players with the same number of kills who would have received fewer drops than {@code received},
	 * counting half of those who received exactly as many. 0.5 is exactly average; higher is luckier.
	 * Uses a Poisson approximation with the given expected count.
	 */
	public static double luckPercentile(int received, double expected)
	{
		double below = 0;
		double term = Math.exp(-expected);
		for (int k = 0; k < received; k++)
		{
			below += term;
			term *= expected / (k + 1);
		}
		return Math.min(1, below + term / 2);
	}
}
