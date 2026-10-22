package com.bosstriptracker.diagnostic;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which of RuneLite's saved Loot Tracker and Chat Commands records belong to the bosses planned next or whose keys
 * are unverified (the Nightmare, Theatre of Blood, Nex, the Fractured Archive), so diagnostic mode can list them.
 */
final class RecordKeys
{
	/**
	 * Whole words, so "nex" matches "drops_NPC_Nex" and "nex" but not "annex".
	 */
	private static final Pattern PLANNED_BOSSES = Pattern.compile("\\b(nightmare|nex|theatre of blood|verzik|fractured archive|tfa)\\b");
	private static final Pattern KILLS = Pattern.compile("\"kills\"\\s*:\\s*(\\d+)");

	private RecordKeys()
	{
	}

	static boolean isPlannedBoss(String key)
	{
		// Underscores count as word characters, so "drops_NPC_Nex" needs them turned into spaces first
		return PLANNED_BOSSES.matcher(key.replace('_', ' ').toLowerCase(Locale.ROOT)).find();
	}

	/**
	 * @return the kill count in a Loot Tracker record's JSON, or null if there is none
	 */
	static Integer lootTrackerKills(String json)
	{
		if (json == null)
		{
			return null;
		}
		Matcher m = KILLS.matcher(json);
		return m.find() ? Integer.valueOf(m.group(1)) : null;
	}
}
