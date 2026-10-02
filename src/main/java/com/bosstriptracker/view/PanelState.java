package com.bosstriptracker.view;

import com.bosstriptracker.LuckCardStyle;
import com.bosstriptracker.boss.BossDefinition;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class PanelState
{
	public enum Status
	{
		LOGGED_OUT,
		LOADING,
		IN_TRIP,
		/**
		 * Logged out mid-trip, within the grace period.
		 */
		PAUSED,
		/**
		 * Paused in the boss's area (Pause button or idle), or waiting just outside it.
		 */
		AFK_PAUSED,
		IDLE,
		/**
		 * On a Leagues, Deadman, beta or tournament world, where nothing is tracked.
		 */
		UNTRACKED_WORLD,
	}

	/**
	 * History cards shown at first, and added by each "Show more".
	 */
	public static final int HISTORY_PAGE = 50;

	/**
	 * The boss shown in all three tabs.
	 */
	BossDefinition boss;
	/**
	 * Every boss in the dropdown, with which one has a trip in progress.
	 */
	List<BossOption> bosses;
	/**
	 * Selected variant chip; null for All.
	 */
	String variant;
	/**
	 * Status of the shown boss: a trip for another boss keeps tracking but shows here as IDLE.
	 */
	Status status;
	/**
	 * The shown boss's trip in progress, or its most recent completed trip when idle; null if there are none.
	 */
	TripView currentTrip;
	/**
	 * The newest completed trips (a page at a time), newest first.
	 */
	List<TripView> history;
	/**
	 * Completed trips for the selected chip, including those not in {@link #history} yet.
	 */
	int historyTotal;
	LifetimeView lifetime;
	/**
	 * Null when no goal is set.
	 */
	GoalView goal;
	/**
	 * Why the open trip's clock is stopped, e.g. "Trip paused (idle)"; null while it runs.
	 */
	String pauseText;
	/**
	 * The clock is paused in the boss's area, so the Pause button reads Resume.
	 */
	boolean pausedInLair;
	/**
	 * Pausing is possible: in the shown boss's area on an open trip.
	 */
	boolean canPause;
	/**
	 * The file an unreadable history was moved to when a new one was started, or null.
	 */
	String corruptBackup;
	boolean readOnly;
	/**
	 * When the boss you're fighting spawned, for the live kill timer (the game's Fight duration counts from the
	 * spawn); null between kills or when the shown boss has no trip running.
	 */
	Long killStartedAt;
	/**
	 * Your display name, for share cards; null if unknown.
	 */
	String playerName;
	/**
	 * Which Luck card the Trip tab shows; null means the default (Overview).
	 */
	LuckCardStyle luckCardStyle;
}
