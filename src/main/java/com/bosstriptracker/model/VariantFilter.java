package com.bosstriptracker.model;

/**
 * Which kills and trips the variant chips (e.g. Theatre of Blood [All | Normal | Hard]) select.
 */
public final class VariantFilter
{
	private VariantFilter()
	{
	}

	/**
	 * @param variant variant id, or null for All
	 */
	public static boolean matches(Kill kill, String variant)
	{
		return variant == null || variant.equals(kill.getVariant());
	}

	/**
	 * A trip matches if any of its kills do. With All, every trip matches, including ones without kills.
	 */
	public static boolean matches(Trip trip, String variant)
	{
		if (variant == null)
		{
			return true;
		}
		for (Kill kill : trip.getKills())
		{
			if (matches(kill, variant))
			{
				return true;
			}
		}
		return false;
	}
}
