package com.bosstriptracker.boss;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * Parsers for the Fractured Archive's game messages. Input is a message without colour tags. The challenge, death,
 * wipe, abandon and bank payment texts are from the diagnostic raids of 2026-10-21 (16 raids, none completed). The
 * completion-count and total-time messages haven't been seen: they follow RuneLite's Chat Commands patterns
 * ("The Fractured Archive: Total completion time:" is in its personal-best regex) and are unverified.
 */
public final class TfaMessages
{
	/**
	 * "Challenge started: Archive Guardian."
	 */
	private static final Pattern CHALLENGE_STARTED = Pattern.compile("^Challenge started: (.+?)\\.?$");
	/**
	 * "Challenge complete: Bork. Duration: 3:50.40. Total: 7:33.00 (new personal best)" or
	 * "Challenge complete: Bork. Duration: 4:27.00. Total: 6:59.40. Personal best: 3:50.40"
	 */
	private static final Pattern CHALLENGE_COMPLETE = Pattern.compile(
		"^Challenge complete: (.+?)\\. Duration: (\\d[\\d:.]*\\d)\\. Total: (\\d[\\d:.]*\\d)");
	/**
	 * "You have died. Total deaths: 4." The count is the team's. A teammate's is "&lt;name&gt; has died. Total deaths: 3."
	 */
	private static final Pattern OWN_DEATH = Pattern.compile("^You have died\\. Total deaths: [\\d,]+\\.?$");
	public static final String WIPE = "You failed to survive the Fractured Archive.";
	public static final String ABANDON = "You abandon the raid and leave the Fractured Archive.";
	/**
	 * "You enter the Archive..." (3 ticks before the Nexus loads).
	 */
	public static final String ENTER = "You enter the Archive...";
	/**
	 * The gravestone outside the Archive after a wipe: "Payment has been taken from your bank: 125,000 x Coins"
	 * (after "Check" on the grave and Unlock).
	 */
	private static final Pattern BANK_PAYMENT = Pattern.compile("^Payment has been taken from your bank: ([\\d,]+) x Coins");
	/**
	 * Unverified: by RuneLite's Chat Commands pattern, "Your completed Fractured Archive count is: 1." (it keys the
	 * count as "fractured archive"); "The" in front and "completion count" are accepted in case the game words it so.
	 */
	private static final Pattern COMPLETION = Pattern.compile(
		"^Your (?:completed )?(?:[Tt]he )?Fractured Archive (?:completion )?count is: ([\\d,]+)\\.?$");
	/**
	 * Unverified, from RuneLite's personal-best regex: "The Fractured Archive: Total completion time: 31:16.20 (new
	 * personal best)".
	 */
	private static final Pattern TOTAL_TIME = Pattern.compile("^The Fractured Archive: Total completion time: (\\d[\\d:.]*\\d)");
	private static final Pattern TIME = Pattern.compile("^(?:(\\d+):)?(\\d+):(\\d+(?:\\.\\d+)?)$");

	private TfaMessages()
	{
	}

	/**
	 * A challenge (room) cleared, with the challenge's time and the raid's running total.
	 */
	@Value
	public static class ChallengeComplete
	{
		String challenge;
		long durationMs;
		long totalMs;
	}

	/**
	 * @return the challenge named by "Challenge started: ...", or null
	 */
	public static String challengeStarted(String text)
	{
		Matcher m = CHALLENGE_STARTED.matcher(text);
		return m.matches() ? m.group(1) : null;
	}

	/**
	 * @return the completed challenge and its times, or null
	 */
	public static ChallengeComplete challengeComplete(String text)
	{
		Matcher m = CHALLENGE_COMPLETE.matcher(text);
		if (!m.find())
		{
			return null;
		}
		Long duration = timeMs(m.group(2));
		Long total = timeMs(m.group(3));
		if (duration == null || total == null)
		{
			return null;
		}
		return new ChallengeComplete(m.group(1), duration, total);
	}

	public static boolean isOwnDeath(String text)
	{
		return OWN_DEATH.matcher(text).matches();
	}

	public static boolean isWipe(String text)
	{
		return WIPE.equals(text);
	}

	public static boolean isAbandon(String text)
	{
		return ABANDON.equals(text);
	}

	/**
	 * @return the coins taken from the bank for the gravestone, or null if the message isn't that payment
	 */
	public static Long bankPayment(String text)
	{
		Matcher m = BANK_PAYMENT.matcher(text);
		return m.find() ? Long.valueOf(m.group(1).replace(",", "")) : null;
	}

	/**
	 * @return the completion count from the (unverified) completion-count message, or null
	 */
	public static RaidCompletion completion(String text)
	{
		Matcher m = COMPLETION.matcher(text);
		return m.matches() ? new RaidCompletion(null, Integer.parseInt(m.group(1).replace(",", ""))) : null;
	}

	/**
	 * The raid's total time: from the (unverified) total completion time message, or the running total of the last
	 * challenge completed, so a completed raid has a time even if that message never comes.
	 *
	 * @return the time in milliseconds, or null
	 */
	public static Long totalTimeMs(String text)
	{
		Matcher m = TOTAL_TIME.matcher(text);
		if (m.find())
		{
			return timeMs(m.group(1));
		}
		ChallengeComplete challenge = challengeComplete(text);
		return challenge == null ? null : challenge.getTotalMs();
	}

	/**
	 * @return "m:ss.cc" or "h:mm:ss.cc" in milliseconds, or null
	 */
	static Long timeMs(String time)
	{
		Matcher m = TIME.matcher(time);
		if (!m.matches())
		{
			return null;
		}
		long hours = m.group(1) != null ? Long.parseLong(m.group(1)) : 0;
		long minutes = Long.parseLong(m.group(2));
		double seconds = Double.parseDouble(m.group(3));
		return hours * 3_600_000 + minutes * 60_000 + Math.round(seconds * 1000);
	}
}
