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

	/**
	 * Shows the next page of older trips in History.
	 */
	void showMoreHistory();

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
	 * @param countFrom when the count starts (now, or a trip's start); null keeps a running goal's count, and starts
	 *                  a new one from the trip in progress, or else now
	 */
	void setGoal(int target, Long countFrom);

	/**
	 * Restarts the shown boss's goal count from {@code countFrom} (now, or a trip's start).
	 */
	void restartGoalFrom(long countFrom);

	/**
	 * Pause or resume the trip and goal clocks.
	 */
	void togglePause();
}
