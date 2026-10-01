package com.bosstriptracker.diagnostic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces other players' names in diagnostic log lines with PLAYER1, PLAYER2 and so on. Each name keeps its stand-in
 * until {@link #startNewFile()}, so stand-ins are consistent within one log file.
 * <p>
 * Only names it has been told about are replaced (players seen nearby, chat senders, friends, clan and Theatre of Blood
 * party members). A name the client never shows anywhere else, such as a stranger who only appears in a broadcast,
 * can't be recognised. A name that is also an ordinary word (e.g. a player called "Blood") is replaced wherever that
 * word stands on its own; the log keeps item and NPC ids, so nothing needed is lost.
 * <p>
 * Not thread-safe: the log writer uses it on its own thread.
 */
class PlayerNameScrubber
{
	/**
	 * Jagex treats a space, a non-breaking space, an underscore and a hyphen in a name as the same character.
	 */
	private static final String SPACE_LIKE = "[  _\\-]";
	private static final Pattern TAG = Pattern.compile("<[^>]*>");
	private static final int MAX_NAME_LENGTH = 12;

	private final Set<String> names = new LinkedHashSet<>();
	private final Map<String, String> standIns = new HashMap<>();
	private Pattern pattern;

	/**
	 * Remembers a name to replace from now on. Ignores names that can't be a player's, and names without a letter
	 * (they would match tick counts and item ids).
	 */
	void learn(String name)
	{
		String key = normalize(name);
		if (key.isEmpty() || key.length() > MAX_NAME_LENGTH || !key.matches(".*[a-z].*"))
		{
			return;
		}
		if (names.add(key))
		{
			pattern = null;
		}
	}

	/**
	 * Starts numbering again at PLAYER1, for a new log file. Names already learned are still replaced.
	 */
	void startNewFile()
	{
		standIns.clear();
	}

	String scrub(String text)
	{
		if (names.isEmpty() || text == null || text.isEmpty())
		{
			return text;
		}
		if (pattern == null)
		{
			pattern = buildPattern();
		}

		Matcher matcher = pattern.matcher(text);
		StringBuffer sb = null;
		while (matcher.find())
		{
			if (sb == null)
			{
				sb = new StringBuffer(text.length());
			}
			String standIn = standIns.computeIfAbsent(normalize(matcher.group()), k -> "PLAYER" + (standIns.size() + 1));
			matcher.appendReplacement(sb, standIn);
		}
		if (sb == null)
		{
			return text;
		}
		matcher.appendTail(sb);
		return sb.toString();
	}

	private Pattern buildPattern()
	{
		// Longest first, so "Bob Smith" wins over "Bob"
		List<String> sorted = new ArrayList<>(names);
		sorted.sort((a, b) -> b.length() - a.length());
		StringBuilder alternatives = new StringBuilder();
		for (String name : sorted)
		{
			if (alternatives.length() > 0)
			{
				alternatives.append('|');
			}
			for (char c : name.toCharArray())
			{
				alternatives.append(c == ' ' ? SPACE_LIKE : Pattern.quote(String.valueOf(c)));
			}
		}
		// Whole words only: "Tim" must not change "Time"
		return Pattern.compile("(?<![A-Za-z0-9])(?:" + alternatives + ")(?![A-Za-z0-9])",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	/**
	 * A name as one key: tags removed, every space-like character a single space, lower case.
	 */
	static String normalize(String name)
	{
		if (name == null)
		{
			return "";
		}
		return TAG.matcher(name).replaceAll("").replaceAll(SPACE_LIKE + "+", " ").trim().toLowerCase();
	}
}
