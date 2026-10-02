package com.bosstriptracker.tracking;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.Trip;

/**
 * What the boss feature trackers (eggs, polishing) need from the trip tracker. Client thread only.
 */
interface TrackerHost
{
	/**
	 * @return this boss's saved data, or null when no history is loaded or it is read-only
	 */
	BossHistory writableHistory(BossDefinition boss);

	/**
	 * Saved data changed: refresh the panel and save soon.
	 */
	void historyChanged();

	/**
	 * The trip to change: a finished trip is replaced in the history by a copy, which is returned (finished trips are
	 * never changed in place); null if it's no longer in the history.
	 */
	Trip editable(Trip trip);

	void alertPet(String message);

	void alertForDrop(BossDefinition boss, int itemId, long quantity);
}
