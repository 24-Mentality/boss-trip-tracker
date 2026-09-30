package com.bosstriptracker.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Everything stored for one boss on one account.
 */
@Data
public class BossHistory
{
	private List<Trip> trips = new ArrayList<>();
	/**
	 * Kill goal shown on the Trip tab; null if none is set.
	 */
	private KillGoal goal;
	/**
	 * Kill count of your last unique from before tracking began, as you entered it; null if none.
	 */
	private Integer lastUniqueKc;
	/**
	 * The game's own count of raids since your last unique ("You are on a personal dry streak of 7.", Theatre of
	 * Blood), and your kill count when it was seen; null if never seen. Added in schema 4.
	 */
	private Integer gameDryStreak;
	private Integer gameDryStreakKc;
	/**
	 * The game's count of raids since you last saw a unique for anyone in the team ("You have completed 7 raids
	 * since you've seen any purple."), and when the raid it was seen in was completed; null if never seen. Added in
	 * schema 5.
	 */
	private Integer gameTeamDryStreak;
	private Long gameTeamDryStreakAt;
	/**
	 * Every egg popped, anywhere (Maggot King only).
	 */
	private List<EggPop> eggPops = new ArrayList<>();
	/**
	 * Tarnished item id to (polished result id to count), for every polish seen (Maggot King only).
	 */
	private Map<Integer, Map<Integer, Integer>> polishOutcomes = new HashMap<>();

	/**
	 * Fills in lists and maps missing from a file (older versions, hand edits), so callers never see null.
	 */
	public void fillMissing()
	{
		if (trips == null)
		{
			trips = new ArrayList<>();
		}
		if (eggPops == null)
		{
			eggPops = new ArrayList<>();
		}
		if (polishOutcomes == null)
		{
			polishOutcomes = new HashMap<>();
		}
	}
}
