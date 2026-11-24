package com.bosstriptracker.tracking;

/**
 * Something seen that shows which boss an area belongs to, for bosses whose region alone doesn't say
 * ({@link com.bosstriptracker.boss.BossDefinition#isEntryEvidenceNeeded()}): the message on the way in, or the boss
 * itself. It only holds for a short while, so it can't carry over to a later visit. No client calls, so it can be
 * tested.
 */
class EntryEvidence
{
	/**
	 * How long a sighting holds. The Pool of Nightmares takes you into the dream within a few ticks of its message.
	 */
	static final int TICKS = 30;

	private String bossId;
	private int tick;

	void seen(String bossId, int tick)
	{
		this.bossId = bossId;
		this.tick = tick;
	}

	/**
	 * @return whether this boss was shown to be here recently enough
	 */
	boolean has(String bossId, int tick)
	{
		return bossId != null && bossId.equals(this.bossId) && tick - this.tick <= TICKS;
	}

	/**
	 * Leaving the area, or logging out: the next visit needs its own.
	 */
	void clear()
	{
		bossId = null;
	}
}
