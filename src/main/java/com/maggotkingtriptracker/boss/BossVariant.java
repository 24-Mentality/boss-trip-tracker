package com.maggotkingtriptracker.boss;

import lombok.Value;

/**
 * A mode of a boss that is tracked separately in the variant chips (e.g. Theatre of Blood Hard).
 */
@Value
public class BossVariant
{
	/**
	 * Stored on kills. Never change it: saved history uses it.
	 */
	String id;
	String label;
}
