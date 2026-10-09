package com.bosstriptracker.model;

import java.util.Map;

/**
 * Exact odds for the number of uniques over kills that each have their own chance (a Poisson-binomial distribution):
 * every kill rolls once, at its own rate (the mode and team size of that kill). Kills with the same chance are
 * grouped, so thousands of kills cost a few small sums.
 */
public final class LuckOdds
{
	private LuckOdds()
	{
	}

	/**
	 * @param chances chance per kill to the number of kills with it
	 * @return the chance of {@code k} or fewer uniques
	 */
	public static double atMost(Map<Double, Integer> chances, int k)
	{
		if (k < 0)
		{
			return 0;
		}
		// Only counts up to k matter: dist[j] = chance of exactly j so far
		double[] dist = new double[k + 1];
		dist[0] = 1;
		for (Map.Entry<Double, Integer> e : chances.entrySet())
		{
			double p = e.getKey();
			int n = e.getValue();
			if (n <= 0 || p <= 0)
			{
				continue;
			}
			double[] binomial = binomial(p, n, k);
			double[] next = new double[k + 1];
			for (int i = 0; i <= k; i++)
			{
				if (dist[i] == 0)
				{
					continue;
				}
				for (int j = 0; i + j <= k; j++)
				{
					next[i + j] += dist[i] * binomial[j];
				}
			}
			dist = next;
		}
		double total = 0;
		for (double d : dist)
		{
			total += d;
		}
		return Math.min(1, total);
	}

	/**
	 * @return the chance of {@code k} or more uniques
	 */
	public static double atLeast(Map<Double, Integer> chances, int k)
	{
		return k <= 0 ? 1 : Math.max(0, 1 - atMost(chances, k - 1));
	}

	/**
	 * @return the expected number of uniques
	 */
	public static double expected(Map<Double, Integer> chances)
	{
		double expected = 0;
		for (Map.Entry<Double, Integer> e : chances.entrySet())
		{
			expected += e.getKey() * e.getValue();
		}
		return expected;
	}

	/**
	 * Chances of exactly 0..k successes in n tries at p, worked out in logs so long streaks don't underflow.
	 */
	private static double[] binomial(double p, int n, int k)
	{
		double[] pmf = new double[k + 1];
		if (p >= 1)
		{
			if (n <= k)
			{
				pmf[n] = 1;
			}
			return pmf;
		}
		double logP = Math.log(p);
		double logQ = Math.log1p(-p);
		double log = n * logQ;
		for (int j = 0; j <= k && j <= n; j++)
		{
			pmf[j] = Math.exp(log);
			// From j to j + 1: times (n - j) / (j + 1) * p / q
			log += Math.log(n - j) - Math.log(j + 1) + logP - logQ;
		}
		return pmf;
	}
}
