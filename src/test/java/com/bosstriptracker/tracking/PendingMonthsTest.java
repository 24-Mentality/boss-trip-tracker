package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

/**
 * Months of finished trips are only forgotten once the save that holds them is written.
 */
public class PendingMonthsTest
{
	private final PendingMonths pending = new PendingMonths();

	@Test
	public void aWrittenSaveLeavesNothingWaiting()
	{
		pending.add("maggot_king", "2026-09");
		PendingMonths.Batch batch = pending.take();
		assertEquals(ImmutableSet.of("maggot_king/2026-09"), batch.getMonths());

		pending.written(batch);

		assertFalse(pending.isUnsaved());
		assertTrue(pending.take().getMonths().isEmpty());
	}

	@Test
	public void aFailedSavesMonthsGoIntoTheNextOne()
	{
		pending.add("maggot_king", "2026-09");
		PendingMonths.Batch failed = pending.take();

		pending.failed(failed);

		assertTrue(pending.isUnsaved());
		PendingMonths.Batch retry = pending.take();
		assertEquals(ImmutableSet.of("maggot_king/2026-09"), retry.getMonths());
		pending.written(retry);
		assertFalse(pending.isUnsaved());
	}

	@Test
	public void monthsChangedWhileASaveFailsAreWrittenWithIt()
	{
		pending.add("maggot_king", "2026-09");
		PendingMonths.Batch failed = pending.take();
		pending.add("nightmare", "2026-10");

		pending.failed(failed);

		assertEquals(ImmutableSet.of("maggot_king/2026-09", "nightmare/2026-10"), pending.take().getMonths());
	}

	@Test
	public void aSaveStartedBeforeTheFailureDoesntCountAsCatchingUp()
	{
		pending.add("maggot_king", "2026-09");
		PendingMonths.Batch first = pending.take();
		pending.add("maggot_king", "2026-10");
		PendingMonths.Batch second = pending.take();

		// The first fails after the second was already on its way: the second doesn't hold September
		pending.failed(first);
		pending.written(second);
		assertTrue(pending.isUnsaved());

		PendingMonths.Batch third = pending.take();
		assertEquals(ImmutableSet.of("maggot_king/2026-09"), third.getMonths());
		pending.written(third);
		assertFalse(pending.isUnsaved());
	}

	@Test
	public void failingAgainKeepsTheMonthsWaiting()
	{
		pending.add("maggot_king", "2026-09");
		pending.failed(pending.take());
		pending.failed(pending.take());
		pending.failed(pending.take());

		assertTrue(pending.isUnsaved());
		assertEquals(ImmutableSet.of("maggot_king/2026-09"), pending.take().getMonths());
	}

	@Test
	public void clearingForgetsWhatWasWaiting()
	{
		pending.add("maggot_king", "2026-09");
		pending.failed(pending.take());

		pending.clear();

		assertFalse(pending.isUnsaved());
		assertTrue(pending.take().getMonths().isEmpty());
	}
}
