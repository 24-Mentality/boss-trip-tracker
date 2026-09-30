package com.maggotkingtriptracker.tracking;

import com.maggotkingtriptracker.boss.BossDefinition;
import com.maggotkingtriptracker.model.BossHistory;

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

	void alertPet(String message);

	void alertForDrop(BossDefinition boss, int itemId, long quantity);
}
