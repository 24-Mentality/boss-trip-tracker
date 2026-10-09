package com.bosstriptracker.tracking;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.Trip;

/**
 * The state the tracker's parts share, all on the client thread: the boss shown, the loaded history, the trip in
 * progress and where the player is.
 */
class TripSession
{
	/**
	 * The boss the panel shows. Changing it never affects tracking.
	 */
	BossDefinition selectedBoss;

	/**
	 * Variant chip selected for the shown boss; null for All.
	 */
	String selectedVariant;

	AccountHistory history;

	boolean readOnly;

	/**
	 * Where an unreadable history file was moved when this account's history was loaded, or null.
	 */
	String corruptBackup;

	long accountHash = -1;

	boolean loading;

	Trip currentTrip;

	/**
	 * The boss of the current trip; null when there is none.
	 */
	BossDefinition tripBoss;

	/**
	 * When the player logged out mid-trip; the trip resumes if they are back within the grace period.
	 */
	Long suspendedAt;

	Trip lastEndedTrip;

	BossDefinition lastEndedBoss;

	boolean inArea;

	/**
	 * Leagues, Deadman, beta and tournament worlds keep their own records in RuneLite, so they aren't tracked.
	 */
	boolean untrackedWorld;

	/**
	 * The boss whose area you're in; null outside.
	 */
	BossDefinition areaBoss;

	boolean dead;

	int ignoreDeltasUntilTick = -1;

	boolean viewDirty = true;

	boolean historyDirty = true;

	/**
	 * Goes up whenever the history changes, so cached totals of finished trips are worked out again.
	 */
	long historyVersion;

	/**
	 * The open trip is waiting outside the lair (walked out), rather than logged out.
	 */
	boolean suspendedOutside;

	/**
	 * The saved history changed: the views and cached totals are worked out again.
	 */
	void historyChanged()
	{
		historyVersion++;
		historyDirty = true;
		viewDirty = true;
	}

	/**
	 * @return this boss's saved data, or null when no history is loaded or it is read-only
	 */
	BossHistory writableHistory(BossDefinition boss)
	{
		return history == null || readOnly ? null : history.boss(boss.getId());
	}

	/**
	 * Why the clock is stopped while in the lair on a trip; null while it runs. Kills, loot and supplies still count.
	 */
	InLairPause inLairPause;

	/**
	 * Why the clock is stopped while in the boss's area on a trip.
	 */
	enum InLairPause
	{
		MANUAL,
		IDLE,
	}
}
