package com.bosstriptracker.tracking;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.client.util.Text;

/**
 * Parsers for the game messages the tracker relies on. Input is the raw chat message, including color tags.
 */
public final class ChatPatterns
{
	private static final Pattern KILL_COUNT = Pattern.compile("^Your (.+) kill count is: ([\\d,]+)\\.?$");
	/**
	 * Anywhere in the message: Phosani's Nightmare puts the team size first ("Team size: Solo Fight duration: ...").
	 */
	private static final Pattern FIGHT_DURATION = Pattern.compile("Fight duration: (?:(\\d+):)?(\\d+):(\\d+(?:\\.\\d+)?)");

	/**
	 * Arceuus thralls. Only the greater ghost's messages are from a diagnostic log (2026-09-28); they start with a
	 * "@mes_hl_mag@" colour marker that isn't a tag, so they aren't anchored at the start. The other thralls are
	 * assumed to follow the same wording (unverified).
	 */
	private static final Pattern THRALL_RAISED = Pattern.compile("You resurrect an? [a-z ]+ thrall\\.$");
	private static final Pattern THRALL_GONE = Pattern.compile("Your [a-z ]+ thrall returns to the grave\\.$");

	public static final String DEATH = "Oh dear, you are dead!";
	public static final String PET_FOLLOWER = "You have a funny feeling like you're being followed.";
	public static final String PET_BACKPACK = "You feel something weird sneaking into your backpack.";
	public static final String PET_DUPLICATE = "You have a funny feeling like you would have been followed";

	private ChatPatterns()
	{
	}

	/**
	 * @return the kill count if this is the kill-count message for the given boss, otherwise null
	 */
	public static Integer killCount(String message, String bossName)
	{
		KillCount kc = killCount(message);
		return kc == null || !kc.getName().equalsIgnoreCase(bossName) ? null : kc.getCount();
	}

	/**
	 * @return the boss name and kill count of a kill-count message, or null if it isn't one
	 */
	public static KillCount killCount(String message)
	{
		Matcher m = KILL_COUNT.matcher(Text.removeTags(message));
		if (!m.matches())
		{
			return null;
		}
		return new KillCount(m.group(1), Integer.parseInt(m.group(2).replace(",", "")));
	}

	@Value
	public static class KillCount
	{
		String name;
		int count;
	}

	/**
	 * @return the fight duration in milliseconds from a "Fight duration: m:ss.cc" message, otherwise null
	 */
	public static Long fightDurationMs(String message)
	{
		Matcher m = FIGHT_DURATION.matcher(Text.removeTags(message));
		if (!m.find())
		{
			return null;
		}
		long hours = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
		long minutes = Long.parseLong(m.group(2));
		double seconds = Double.parseDouble(m.group(3));
		return hours * 3_600_000 + minutes * 60_000 + Math.round(seconds * 1000);
	}

	public static boolean isPetMessage(String message)
	{
		String text = Text.removeTags(message);
		return text.equals(PET_FOLLOWER) || text.equals(PET_BACKPACK) || text.startsWith(PET_DUPLICATE);
	}

	public static boolean isDeathMessage(String message)
	{
		return Text.removeTags(message).equals(DEATH);
	}

	/**
	 * @return true if this message says a thrall was raised, false if one returned to the grave, otherwise null
	 */
	public static Boolean thrall(String message)
	{
		String text = Text.removeTags(message);
		if (THRALL_RAISED.matcher(text).find())
		{
			return true;
		}
		if (THRALL_GONE.matcher(text).find())
		{
			return false;
		}
		return null;
	}
}
