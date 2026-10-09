package com.bosstriptracker.view;

import com.bosstriptracker.boss.BossDefinition;
import java.util.Locale;
import lombok.Value;

/**
 * How a boss's numbers are worded: "kill" or "raid", and where the fight is ("lair", "dream", "Theatre").
 */
@Value
public class Words
{
	public static final Words KILLS = new Words("kill", "area");

	String unit;
	String area;

	/**
	 * "kills" or "raids".
	 */
	public String units()
	{
		return unit + "s";
	}

	/**
	 * "Kills" or "Raids".
	 */
	public String unitsTitle()
	{
		return capitalize(units());
	}

	/**
	 * "Kill" or "Raid".
	 */
	public String unitTitle()
	{
		return capitalize(unit);
	}

	/**
	 * "1 kill", "1,234 raids".
	 */
	public String count(long n)
	{
		return String.format(Locale.ROOT, "%,d %s", n, n == 1 ? unit : units());
	}

	/**
	 * A short count for streaks: "1,234 kc", or "1,234 raids" for a raid.
	 */
	public String kc(long n)
	{
		return "kill".equals(unit) ? String.format(Locale.ROOT, "%,d kc", n) : count(n);
	}

	public static Words of(BossDefinition boss)
	{
		return new Words(boss.getUnitNoun(), boss.getAreaNoun());
	}

	private static String capitalize(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
