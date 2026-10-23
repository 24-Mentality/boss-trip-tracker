package com.bosstriptracker.view;

import lombok.Value;

/**
 * One entry in the boss dropdown.
 */
@Value
public class BossOption
{
	/**
	 * The boss's id; null for a section header ("Bosses", "Raids"), which can't be chosen.
	 */
	String id;
	String name;
	int iconItemId;
	/**
	 * A trip for this boss is in progress.
	 */
	boolean live;
	/**
	 * Listed under the Raids header.
	 */
	boolean raid;

	public static BossOption header(String name, boolean raid)
	{
		return new BossOption(null, name, 0, false, raid);
	}

	public boolean isHeader()
	{
		return id == null;
	}
}
