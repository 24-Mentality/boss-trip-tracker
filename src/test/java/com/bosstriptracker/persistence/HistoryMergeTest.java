package com.bosstriptracker.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.KillGoal;
import com.bosstriptracker.model.Trip;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * Adding an older history file's trips to a history folder's.
 */
public class HistoryMergeTest
{
	private static final String BOSS = "maggot_king";

	@Test
	public void onlyTripsTheTargetLacksAreAdded()
	{
		AccountHistory target = new AccountHistory();
		Trip mine = trip("shared", 100);
		target.boss(BOSS).getTrips().add(mine);
		target.boss(BOSS).getTrips().add(trip("only-mine", 300));
		AccountHistory source = new AccountHistory();
		source.boss(BOSS).getTrips().add(trip("shared", 100));
		source.boss(BOSS).getTrips().add(trip("only-theirs", 200));

		Set<String> added = HistoryMerge.addMissing(target, source, ImmutableSet.of(BOSS));

		assertEquals(Collections.singleton("only-theirs"), added);
		List<Trip> trips = target.boss(BOSS).getTrips();
		assertEquals(Arrays.asList("shared", "only-theirs", "only-mine"), ids(trips));
		// The target's own copy of a shared trip is the one kept
		assertSame(mine, trips.get(0));
	}

	@Test
	public void theTargetsOtherDataIsKept()
	{
		AccountHistory target = new AccountHistory();
		target.boss(BOSS).setLastUniqueKc(1920);
		AccountHistory source = new AccountHistory();
		source.boss(BOSS).setLastUniqueKc(5);
		source.boss(BOSS).setGoal(new KillGoal());

		HistoryMerge.addMissing(target, source, ImmutableSet.of(BOSS));

		assertEquals(Integer.valueOf(1920), target.boss(BOSS).getLastUniqueKc());
		assertNull(target.boss(BOSS).getGoal());
	}

	@Test
	public void aBossTheTargetHasNoDataForTakesTheSources()
	{
		// The target's trips were read from its month files, but its account file is missing
		AccountHistory target = new AccountHistory();
		target.boss(BOSS).getTrips().add(trip("only-mine", 300));
		AccountHistory source = new AccountHistory();
		source.setLastDisplayName("Name");
		source.boss(BOSS).setLastUniqueKc(1920);
		source.boss(BOSS).getTrips().add(trip("only-theirs", 200));
		source.boss("nightmare").setGoal(new KillGoal());

		Set<String> added = HistoryMerge.addMissing(target, source, Collections.emptySet());

		assertEquals(Collections.singleton("only-theirs"), added);
		assertEquals(Integer.valueOf(1920), target.boss(BOSS).getLastUniqueKc());
		assertEquals(Arrays.asList("only-theirs", "only-mine"), ids(target.boss(BOSS).getTrips()));
		assertNotNull(target.getBosses().get("nightmare").getGoal());
		assertEquals("Name", target.getLastDisplayName());
		// The source isn't changed
		assertEquals(1, source.boss(BOSS).getTrips().size());
	}

	@Test
	public void aTripWithoutAnIdIsLeftOut()
	{
		AccountHistory target = new AccountHistory();
		AccountHistory source = new AccountHistory();
		source.boss(BOSS).getTrips().add(new Trip());

		assertTrue(HistoryMerge.addMissing(target, source, Collections.emptySet()).isEmpty());
		assertTrue(target.boss(BOSS).getTrips().isEmpty());
	}

	private static Trip trip(String id, long startedAt)
	{
		Trip trip = new Trip();
		trip.setId(id);
		trip.setStartedAt(startedAt);
		trip.setEndedAt(startedAt + 1);
		return trip;
	}

	private static List<String> ids(List<Trip> trips)
	{
		return trips.stream().map(Trip::getId).collect(Collectors.toList());
	}
}
