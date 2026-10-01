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
	/**
	 * Whether its kills are recognised yet. Until then it has no chip, since there would be nothing behind it.
	 */
	boolean tracked;

	public BossVariant(String id, String label, boolean beta, boolean tracked)
	{
		this.id = id;
		this.label = label;
		this.beta = beta;
		this.tracked = tracked;
	}

	public BossVariant(String id, String label, boolean beta)
	{
		this(id, label, beta, true);
	}
}
