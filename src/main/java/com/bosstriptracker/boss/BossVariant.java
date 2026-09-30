package com.bosstriptracker.boss;

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
	/**
	 * Built from the wiki and other modes' logs, not yet confirmed in game. Marked "(beta)" on its chip.
	 */
	boolean beta;
}
