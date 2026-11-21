package com.bosstriptracker.persistence;

import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.Trip;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Adds one history's trips to another without losing any of either. No IO, so it can be tested.
 */
final class HistoryMerge
{
	private HistoryMerge()
	{
	}

	/**
	 * Adds to {@code target} the trips of {@code source} it doesn't have (by id); the target's own trips are never
	 * changed or removed. A boss's other data (goal, last-unique KC, egg pops, polish tallies, Collection Log page)
	 * stays the target's, unless the target has none saved for that boss: then the source's is used.
	 *
	 * @param targetBosses the bosses the target has that other data for
	 * @return the ids of the trips added
	 */
	static Set<String> addMissing(AccountHistory target, AccountHistory source, Set<String> targetBosses)
	{
		Set<String> added = new LinkedHashSet<>();
		source.getBosses().forEach((bossId, theirs) ->
		{
			BossHistory mine = target.getBosses().get(bossId);
			if (mine == null || !targetBosses.contains(bossId))
			{
				BossHistory copy = theirs.withoutTrips();
				copy.setTrips(mine == null ? new ArrayList<>() : mine.getTrips());
				target.getBosses().put(bossId, copy);
				mine = copy;
			}

			Set<String> ids = new HashSet<>();
			for (Trip trip : mine.getTrips())
			{
				ids.add(trip.getId());
			}
			for (Trip trip : theirs.getTrips())
			{
				// A trip without an id can't be kept in the folder layout
				if (trip != null && trip.getId() != null && ids.add(trip.getId()))
				{
					mine.getTrips().add(trip);
					added.add(trip.getId());
				}
			}
			mine.getTrips().sort(Comparator.comparingLong(Trip::getStartedAt));
		});
		if (target.getLastDisplayName() == null)
		{
			target.setLastDisplayName(source.getLastDisplayName());
		}
		return added;
	}
}
