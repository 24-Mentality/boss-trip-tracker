package com.maggotkingtriptracker.boss;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsers for the Theatre of Blood's game messages. Input is a message without colour tags. Normal Mode texts are
 * from the diagnostic log of 2026-09-29 (PROJECT_BRIEF.md, "Observed in-game"); Entry and Hard Mode are assumed to
 * follow the same wording with their mode name (unverified), and the purple broadcast hasn't been seen yet.
 */
public final class TobMessages
{
	/**
	 * "You enter the Theatre of Blood (Normal Mode)..."
	 */
	private static final Pattern ENTER = Pattern.compile("^You enter the Theatre of Blood \\((Entry|Normal|Hard) Mode\\)");
	/**
	 * "Wave 'The Maiden of Sugadinti' (Normal Mode) complete!Duration: 1:51.60 Total: 1:51.60" (the tags, a
	 * line break included, are gone).
	 */
	private static final Pattern WAVE = Pattern.compile("^Wave '.+' \\((Entry|Normal|Hard) Mode\\) complete!");
	/**
	 * "Your completed Theatre of Blood count is: 6." The other modes put the mode after a colon, as RuneLite's Chat
	 * Commands keys ("theatre of blood entry mode") suggest (unverified).
	 */
	private static final Pattern COMPLETION = Pattern.compile(
		"^Your completed Theatre of Blood(?:: (Entry|Hard) Mode)? count is: ([\\d,]+)\\.?$");
	/**
	 * "Theatre of Blood total completion time: 19:51.60 (new personal best)"
	 */
	private static final Pattern TOTAL_TIME = Pattern.compile(
		"^Theatre of Blood total completion time: (?:(\\d+):)?(\\d+):(\\d+(?:\\.\\d+)?)");
	/**
	 * "You have died. Death count: 2." The death count is the team's; a teammate's is "&lt;name&gt; has died. ...".
	 */
	private static final Pattern OWN_DEATH = Pattern.compile("^You have died\\. Death count: \\d+\\.?$");
	/**
	 * A purple for anyone in the raid, e.g. "&lt;name&gt; found something special: Scythe of vitur (uncharged)"
	 * (unverified: no purple has been logged yet). Only the item is kept.
	 */
	private static final Pattern SPECIAL_LOOT = Pattern.compile("^.+ found something special: (.+?)\\.?$");
	/**
	 * On entering the vault: "You are on a personal dry streak of 7."
	 */
	private static final Pattern DRY_STREAK = Pattern.compile("^You are on a personal dry streak of ([\\d,]+)\\.?");
	/**
	 * Right after it: "You have completed 7 raids since you've seen any purple." Anyone's purple counts.
	 */
	private static final Pattern TEAM_DRY_STREAK = Pattern.compile("^You have completed ([\\d,]+) raids? since you've seen any purple");

	private TobMessages()
	{
	}

	/**
	 * @return the variant id of the mode named when entering the Theatre or completing a room, or null
	 */
	public static String mode(String text)
	{
		Matcher m = ENTER.matcher(text);
		if (m.find())
		{
			return variant(m.group(1));
		}
		m = WAVE.matcher(text);
		return m.find() ? variant(m.group(1)) : null;
	}

	/**
	 * A room completed: "Wave 'Xarpus' (Normal Mode) complete!..."
	 */
	public static boolean isRoomComplete(String text)
	{
		return WAVE.matcher(text).find();
	}

	/**
	 * @return the mode and completion count from the completion-count message, or null
	 */
	public static RaidCompletion completion(String text)
	{
		Matcher m = COMPLETION.matcher(text);
		if (!m.matches())
		{
			return null;
		}
		String mode = m.group(1) == null ? "Normal" : m.group(1);
		return new RaidCompletion(variant(mode), Integer.parseInt(m.group(2).replace(",", "")));
	}

	/**
	 * @return the raid's total completion time in milliseconds, or null
	 */
	public static Long totalTimeMs(String text)
	{
		Matcher m = TOTAL_TIME.matcher(text);
		if (!m.find())
		{
			return null;
		}
		long hours = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
		long minutes = Long.parseLong(m.group(2));
		double seconds = Double.parseDouble(m.group(3));
		return hours * 3_600_000 + minutes * 60_000 + Math.round(seconds * 1000);
	}

	public static boolean isOwnDeath(String text)
	{
		return OWN_DEATH.matcher(text).matches();
	}

	/**
	 * @return the item name of a purple broadcast (anyone's), or null. The player's name is never returned.
	 */
	public static String specialLootItem(String text)
	{
		Matcher m = SPECIAL_LOOT.matcher(text);
		return m.matches() ? m.group(1) : null;
	}

	/**
	 * @return the game's count of raids since the player's last purple, or null
	 */
	public static Integer dryStreak(String text)
	{
		Matcher m = DRY_STREAK.matcher(text);
		return m.find() ? Integer.valueOf(m.group(1).replace(",", "")) : null;
	}

	/**
	 * @return the game's count of raids since you last saw a purple (anyone's), or null
	 */
	public static Integer teamDryStreak(String text)
	{
		Matcher m = TEAM_DRY_STREAK.matcher(text);
		return m.find() ? Integer.valueOf(m.group(1).replace(",", "")) : null;
	}

	private static String variant(String mode)
	{
		return mode.toLowerCase(Locale.ROOT);
	}
}
