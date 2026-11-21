package com.bosstriptracker.tracking;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The months of finished trips that still have to be written, and whether a write has failed since everything was
 * last on disk. A save takes the waiting months as a batch; they are only forgotten once that save is written, and
 * go back to waiting if it fails. Client thread.
 */
final class PendingMonths
{
	/**
	 * What one save writes.
	 */
	static final class Batch
	{
		private final long number;
		private final Set<String> months;

		private Batch(long number, Set<String> months)
		{
			this.number = number;
			this.months = months;
		}

		/**
		 * The months to write, as "boss/2026-09".
		 */
		Set<String> getMonths()
		{
			return months;
		}
	}

	private final Set<String> waiting = new HashSet<>();
	private long saves;
	/**
	 * The first save that, once written, means nothing is left unsaved: saves started before the last failure don't
	 * hold the months that failure put back.
	 */
	private long cleanFrom;
	private boolean unsaved;

	void add(String bossId, String month)
	{
		waiting.add(bossId + "/" + month);
	}

	/**
	 * A save starts: it writes every month waiting.
	 */
	Batch take()
	{
		Batch batch = new Batch(++saves, Collections.unmodifiableSet(new HashSet<>(waiting)));
		waiting.clear();
		return batch;
	}

	/**
	 * The save was written.
	 */
	void written(Batch batch)
	{
		if (batch.number >= cleanFrom)
		{
			unsaved = false;
		}
	}

	/**
	 * The save wasn't written: its months wait for the next one.
	 */
	void failed(Batch batch)
	{
		waiting.addAll(batch.months);
		unsaved = true;
		cleanFrom = saves + 1;
	}

	/**
	 * @return whether a save has failed and its months haven't been written since
	 */
	boolean isUnsaved()
	{
		return unsaved;
	}

	/**
	 * Forgets everything, e.g. when another account's history loads.
	 */
	void clear()
	{
		waiting.clear();
		unsaved = false;
		cleanFrom = saves + 1;
	}
}
