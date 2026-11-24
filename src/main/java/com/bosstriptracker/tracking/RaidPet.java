package com.bosstriptracker.tracking;

/**
 * When a pet message belongs to a raid. A raid's pet comes with its reward, not when the last boss dies, and the
 * message is the same for every pet in the game. Inside the raid nothing else can give one, so it always counts;
 * outside, it only counts around the reward being claimed from the chest. No client calls, so it can be tested.
 * Unverified: no raid pet has been logged, so when the message really comes isn't known.
 */
class RaidPet
{
	/**
	 * After the reward is claimed outside, a pet message this soon is the raid's.
	 */
	static final int AFTER_CLAIM_TICKS = LootRecorder.CORPSE_WINDOW_TICKS;
	/**
	 * The message may also come just ahead of the Loot Tracker's event for the claim.
	 */
	static final int BEFORE_CLAIM_TICKS = 1;
	private static final int NEVER = -1_000;

	private boolean waiting;
	private int messageTick = NEVER;
	private int claimTick = NEVER;

	/**
	 * A new raid, or logging out: nothing is left over.
	 */
	void reset()
	{
		waiting = false;
		messageTick = NEVER;
		claimTick = NEVER;
	}

	/**
	 * A pet message inside the raid.
	 *
	 * @param completed whether the raid's completion has been recorded
	 * @return whether the completion takes the pet now; otherwise it waits for the completion ({@link #takeWaiting})
	 */
	boolean inRaid(boolean completed)
	{
		if (completed)
		{
			return true;
		}
		waiting = true;
		return false;
	}

	/**
	 * The raid's completion was recorded.
	 *
	 * @return whether a pet message came before it
	 */
	boolean takeWaiting()
	{
		boolean was = waiting;
		waiting = false;
		return was;
	}

	/**
	 * A pet message outside, after a raid.
	 *
	 * @return whether it's the raid's: its reward was claimed just before
	 */
	boolean outside(int tick)
	{
		if (tick - claimTick <= AFTER_CLAIM_TICKS)
		{
			claimTick = NEVER;
			return true;
		}
		messageTick = tick;
		return false;
	}

	/**
	 * The raid's reward was claimed from the chest outside.
	 *
	 * @return whether a pet message came just before it
	 */
	boolean claimedOutside(int tick)
	{
		if (tick - messageTick <= BEFORE_CLAIM_TICKS)
		{
			messageTick = NEVER;
			return true;
		}
		claimTick = tick;
		return false;
	}
}
