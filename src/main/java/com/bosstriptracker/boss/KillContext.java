package com.bosstriptracker.boss;

import com.bosstriptracker.model.Kill;
import lombok.Value;

/**
 * What a drop chance can depend on: the boss variant (e.g. ToB Hard) and the party size.
 */
@Value
public class KillContext
{
	public static final KillContext DEFAULT = new KillContext(null, null);

	/**
	 * Variant id, or null for bosses without variants.
	 */
	String variant;
	/**
	 * Party size at the start of the kill, or null if unknown or solo-only.
	 */
	Integer partySize;

	public static KillContext of(Kill kill)
	{
		return kill.getVariant() == null && kill.getPartySize() == null ? DEFAULT
			: new KillContext(kill.getVariant(), kill.getPartySize());
	}
}
