package com.bosstriptracker.tracking;

import com.bosstriptracker.model.TripEndReason;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * The raid in progress (Theatre of Blood, Fractured Archive): its mode, team size, your deaths and how it ended. No
 * client calls, so it can be tested.
 */
class RaidState
{
	/**
	 * A death is reported twice (the actor death and the game message, some ticks apart).
	 */
	private static final int SAME_DEATH_TICKS = 20;

	/**
	 * Variant id of the mode, or null until a message names it.
	 */
	@Getter
	private String mode;
	/**
	 * Players in the team at the start: the most seen before the first room was cleared. Null if never seen.
	 */
	@Getter
	private Integer teamSize;
	@Getter
	private boolean completed;
	/**
	 * The game said the whole team died (the Fractured Archive's "You failed to survive").
	 */
	@Getter
	private boolean wiped;
	/**
	 * You gave the raid up (the Fractured Archive's abandon option).
	 */
	@Getter
	private boolean abandoned;
	/**
	 * The game's total time for the raid, from the message just before the completion count.
	 */
	@Getter
	private Long raidTimeMs;
	/**
	 * Purples broadcast before the completion was recorded.
	 */
	@Getter
	private final List<Integer> teamUniques = new ArrayList<>();
	private boolean firstRoomCleared;
	private boolean diedInCurrentRoom;
	private int lastDeathTick = -SAME_DEATH_TICKS - 1;

	/**
	 * A new raid.
	 *
	 * @param mode the mode from the entry message, if seen; null otherwise
	 */
	void start(String mode)
	{
		this.mode = mode;
		teamSize = null;
		completed = false;
		wiped = false;
		abandoned = false;
		raidTimeMs = null;
		teamUniques.clear();
		firstRoomCleared = false;
		diedInCurrentRoom = false;
		lastDeathTick = -SAME_DEATH_TICKS - 1;
	}

	void modeSeen(String mode)
	{
		this.mode = mode;
	}

	/**
	 * @param players team members whose slot is set this tick
	 */
	void teamSeen(int players)
	{
		if (!firstRoomCleared && players > 0 && (teamSize == null || players > teamSize))
		{
			teamSize = players;
		}
	}

	void roomCleared()
	{
		firstRoomCleared = true;
		diedInCurrentRoom = false;
	}

	/**
	 * @return whether this is a new death, not the same one reported again
	 */
	boolean died(int tick)
	{
		if (tick - lastDeathTick <= SAME_DEATH_TICKS)
		{
			return false;
		}
		lastDeathTick = tick;
		diedInCurrentRoom = true;
		return true;
	}

	void raidTime(long ms)
	{
		raidTimeMs = ms;
	}

	void completed()
	{
		completed = true;
	}

	void wiped()
	{
		wiped = true;
	}

	void abandoned()
	{
		abandoned = true;
	}

	/**
	 * How the raid ended when you left it: completed; wiped when the game said so, or if you had died in the room you
	 * left from (the team is sent out once everyone has died); null for leaving any other way, abandoning included.
	 */
	TripEndReason endReason()
	{
		if (completed)
		{
			return TripEndReason.COMPLETED;
		}
		if (wiped)
		{
			return TripEndReason.WIPED;
		}
		return diedInCurrentRoom && !abandoned ? TripEndReason.WIPED : null;
	}
}
