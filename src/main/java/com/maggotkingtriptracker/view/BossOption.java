package com.maggotkingtriptracker.view;

import lombok.Value;

/**
 * One entry in the boss dropdown.
 */
@Value
public class BossOption
{
	String id;
	String name;
	int iconItemId;
	/**
	 * A trip for this boss is in progress.
	 */
	boolean live;
}
