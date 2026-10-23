package com.bosstriptracker.view;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.TripModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The boss dropdown's entries: bosses first, then raids, each group in alphabetical order.
 */
public final class BossOptions
{
	private BossOptions()
	{
	}

	/**
	 * @param live whether a boss has a trip in progress
	 */
	public static List<BossOption> of(List<BossDefinition> bosses, Predicate<BossDefinition> live)
	{
		List<BossDefinition> ordered = new ArrayList<>(bosses);
		ordered.sort(Comparator.comparing(BossOptions::isRaid).thenComparing(BossDefinition::getDisplayName, String.CASE_INSENSITIVE_ORDER));
		List<BossOption> options = new ArrayList<>();
		for (BossDefinition boss : ordered)
		{
			options.add(new BossOption(boss.getId(), boss.getDisplayName(), boss.getIconItemId(), live.test(boss), isRaid(boss)));
		}
		return options;
	}

	private static boolean isRaid(BossDefinition boss)
	{
		return boss.getTripModel() == TripModel.ONE_RAID;
	}
}
