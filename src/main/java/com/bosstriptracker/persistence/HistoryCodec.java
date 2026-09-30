package com.bosstriptracker.persistence;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.model.BossHistory;
import java.util.LinkedHashMap;
import lombok.Value;

/**
 * Turns saved history JSON (any schema version) into an {@link AccountHistory}. No IO, so it can be tested.
 */
public final class HistoryCodec
{
	private HistoryCodec()
	{
	}

	@Value
	public static class Decoded
	{
		AccountHistory history;
		/**
		 * Schema version of the JSON as read, before migration.
		 */
		int sourceVersion;

		/**
		 * From a newer plugin version: it may hold data this version doesn't understand.
		 */
		public boolean isNewer()
		{
			return sourceVersion > AccountHistory.CURRENT_SCHEMA_VERSION;
		}

		/**
		 * From an older version: the file on disk should be backed up before it is rewritten.
		 */
		public boolean isMigrated()
		{
			return sourceVersion < AccountHistory.CURRENT_SCHEMA_VERSION;
		}
	}

	/**
	 * @return the decoded history, or null if the JSON is empty
	 * @throws JsonParseException if it isn't a history file
	 */
	public static Decoded decode(Gson gson, String json)
	{
		JsonObject root = gson.fromJson(json, JsonObject.class);
		if (root == null)
		{
			return null;
		}
		int from = HistoryMigrator.migrate(root);
		AccountHistory history = gson.fromJson(root, AccountHistory.class);
		if (history.getBosses() == null)
		{
			// Only possible for a newer format, which is read-only anyway
			history.setBosses(new LinkedHashMap<>());
		}
		for (BossHistory boss : history.getBosses().values())
		{
			if (boss != null)
			{
				boss.fillMissing();
			}
		}
		history.getBosses().values().removeIf(b -> b == null);
		return new Decoded(history, from);
	}
}
