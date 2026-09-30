package com.bosstriptracker.ui;

import com.bosstriptracker.CanvasSection;

/**
 * What the panel's buttons do. Called on the Swing thread.
 */
public interface PanelActions
{
	/**
	 * @return whether this section is shown on the overlay
	 */
	boolean isOnCanvas(CanvasSection section);

	/**
	 * Show or hide this section on the overlay ("Add to canvas" / "Remove from canvas").
	 */
	void toggleCanvas(CanvasSection section);

	/**
	 * Show this boss in all three tabs.
	 */
	void selectBoss(String bossId);

	/**
	 * @param variant variant id, or null for All
	 */
	void selectVariant(String variant);

	/**
	 * @param killCount kill count of your last unique from before tracking; null clears it
	 */
	void setLastUniqueKc(Integer killCount);

	void deleteTrip(String tripId);

	void clearHistory();

	void exportCsv();

	/**
	 * Make a share card for the shown boss: copy it to the clipboard and save it as a screenshot.
	 */
	void shareCard();

	void exportJson();

	void importJson();

	/**
	 * @param target kills; 0 removes the goal
	 */
	void setGoal(int target);

	void resetGoal();

	/**
	 * Pause or resume the trip and goal clocks.
	 */
	void togglePause();
}
