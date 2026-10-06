package com.bosstriptracker.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import lombok.Value;

/**
 * Kills since the last unique, by kill count: from the last tracked unique, or the kill count you entered for a unique
 * from before tracking when nothing newer was tracked, to your current kill count (RuneLite's all-time count or the
 * latest tracked kill, whichever is higher). Kills done without the plugin count too. Without a known unique kill
 * count, it's the tracked kills since tracking began.
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
	 * The worst dry streak: the longest gap between uniques, by kill count, among the uniques whose kill count is known
	 * (tracked ones and the one you entered), or the current streak if that is longer.
	 *
	 * @param uniqueKcs  kill counts of tracked uniques, in any order
	 * @param enteredKc  kill count of your last unique as you entered it; null if none
	 * @param current    the current dry streak
	 * @param allKnown   every unique you've had is among these (RuneLite's record has no more): the kills before the
	 *                   first of them, from your first kill, were a dry streak too
	 */
	public static int longest(List<Integer> uniqueKcs, Integer enteredKc, int current, boolean allKnown)
	{
		List<Integer> kcs = new ArrayList<>(uniqueKcs);
		if (enteredKc != null && !kcs.contains(enteredKc))
		{
			kcs.add(enteredKc);
		}
		Collections.sort(kcs);
		if (allKnown && !kcs.isEmpty())
		{
			kcs.add(0, 0);
		}
		int longest = current;
		for (int i = 1; i < kcs.size(); i++)
		{
			longest = Math.max(longest, kcs.get(i) - kcs.get(i - 1));
		}
		return longest;
	}

	/**
	 * How many uniques have a known kill count: the tracked ones and the one you entered, counted once.
	 */
	public static int knownUniques(List<Integer> uniqueKcs, Integer enteredKc)
	{
		return uniqueKcs.size() + (enteredKc != null && !uniqueKcs.contains(enteredKc) ? 1 : 0);
	}

	/**
	 * @param kills tracked kills, oldest first
	 * @param countsForLuck kills that can roll uniques (e.g. Open-stomach)
	 * @param hasUnique kills that dropped a unique
	 * @param enteredKc kill count of your last unique as you entered it; null if none
	 * @param knownKc your current kill count from RuneLite's Chat Commands (all kills, with or without the plugin);
	 *                null if unknown
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
		boolean fromEntered = enteredKc != null
			&& !(trackedUnique && (trackedUniqueKcUnknown || lastUniqueKc > enteredKc));
		Integer fromKc = fromEntered ? enteredKc : lastUniqueKc;
		if (fromKc == null || (!fromEntered && trackedUniqueKcUnknown))
		{
			// Nothing to count from by kill count: the tracked kills since the last tracked unique (or since tracking began)
			return new Result(since, lastUniqueKc, false);
		}

		// By kill count, so kills done without the plugin count too: before tracking began, between tracked trips and
		// since the last tracked one. They can't be told apart, so all of them count; only tracked kills that can't roll
		// a unique (Take-eggs) are left out
		int currentKc = knownKc == null ? fromKc : Math.max(fromKc, knownKc);
		int notForLuck = 0;
		Integer effectiveKc = null;
		for (Kill kill : kills)
		{
			// A kill whose kill count was missed is taken to be one after the previous kill
			effectiveKc = kill.getKillCount() != null ? kill.getKillCount() : effectiveKc == null ? null : effectiveKc + 1;
			if (effectiveKc == null)
			{
				continue;
			}
			currentKc = Math.max(currentKc, effectiveKc);
			if (effectiveKc > fromKc && !countsForLuck.test(kill))
			{
				notForLuck++;
			}
		}
		return new Result(Math.max(0, currentKc - fromKc - notForLuck), fromKc, fromEntered);
	}
}
