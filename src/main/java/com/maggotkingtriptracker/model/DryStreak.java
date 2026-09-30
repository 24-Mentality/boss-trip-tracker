package com.maggotkingtriptracker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import lombok.Value;

/**
 * Kills since the last unique. Counts tracked luck kills since the last tracked unique, or, when you entered the
 * kill count of a unique from before tracking and nothing newer was tracked, since that kill count.
 */
public final class DryStreak
{
	private DryStreak()
	{
	}

	@Value
	public static class Result
	{
		int since;
		/**
		 * Kill count of the unique the streak counts from; null if unknown.
		 */
		Integer lastUniqueKc;
		boolean fromEnteredKc;
	}

	/**
	 * The longest gap between uniques, by kill count, among the uniques whose kill count is known (tracked ones and
	 * the one you entered), or the current streak if that is longer.
	 *
	 * @param uniqueKcs kill counts of tracked uniques, in any order
	 * @param enteredKc kill count of your last unique as you entered it; null if none
	 * @param current the current dry streak
	 */
	public static int longest(List<Integer> uniqueKcs, Integer enteredKc, int current)
	{
		List<Integer> kcs = new ArrayList<>(uniqueKcs);
		if (enteredKc != null)
		{
			kcs.add(enteredKc);
		}
		Collections.sort(kcs);
		int longest = current;
		for (int i = 1; i < kcs.size(); i++)
		{
			longest = Math.max(longest, kcs.get(i) - kcs.get(i - 1));
		}
		return longest;
	}

	/**
	 * @param kills tracked kills, oldest first
	 * @param countsForLuck kills that can roll uniques (e.g. Open-stomach)
	 * @param hasUnique kills that dropped a unique
	 * @param enteredKc kill count of your last unique as you entered it; null if none
	 * @param knownKc your current kill count from elsewhere (Chat Commands), used only when no tracked kill has one
	 */
	public static Result compute(List<Kill> kills, Predicate<Kill> countsForLuck, Predicate<Kill> hasUnique,
		Integer enteredKc, Integer knownKc)
	{
		int since = 0;
		Integer lastUniqueKc = null;
		boolean trackedUnique = false;
		boolean trackedUniqueKcUnknown = false;
		for (Kill kill : kills)
		{
			if (!countsForLuck.test(kill))
			{
				continue;
			}
			since++;
			if (hasUnique.test(kill))
			{
				since = 0;
				trackedUnique = true;
				trackedUniqueKcUnknown = kill.getKillCount() == null;
				if (kill.getKillCount() != null)
				{
					lastUniqueKc = kill.getKillCount();
				}
			}
		}

		// A tracked unique newer than the entered kill count wins (one with an unknown kill count too)
		if (enteredKc == null || (trackedUnique && (trackedUniqueKcUnknown || lastUniqueKc > enteredKc)))
		{
			return new Result(since, lastUniqueKc, false);
		}

		Integer firstKc = null;
		for (Kill kill : kills)
		{
			if (kill.getKillCount() != null)
			{
				firstKc = kill.getKillCount();
				break;
			}
		}
		if (firstKc == null)
		{
			// No tracked kill counts: go by the current kill count, if known
			int gap = knownKc == null ? 0 : Math.max(0, knownKc - enteredKc);
			return new Result(gap, enteredKc, true);
		}

		// Kills between the entered kill count and the start of tracking can't be told apart, so all of them count
		int count = Math.max(0, firstKc - enteredKc - 1);
		// A kill whose kill count was missed is taken to be one after the previous kill
		int effectiveKc = firstKc - 1;
		for (Kill kill : kills)
		{
			effectiveKc = kill.getKillCount() != null ? kill.getKillCount() : effectiveKc + 1;
			if (effectiveKc > enteredKc && countsForLuck.test(kill))
			{
				count++;
			}
		}
		return new Result(count, enteredKc, true);
	}
}
