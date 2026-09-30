package com.bosstriptracker.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.model.AccountHistory;

/**
 * Upgrades saved history JSON to the current schema, one version at a time. Works on the JSON tree so fields
 * that moved can be carried over exactly.
 */
public final class HistoryMigrator
{
	/**
	 * Schema 1 fields that belonged to the Maggot King, the only boss at the time.
	 */
	private static final String[] V1_BOSS_FIELDS = {"trips", "goal", "eggPops", "polishOutcomes"};

	private HistoryMigrator()
	{
	}

	/**
	 * @return the file's schema version; files from before versioning count as 1
	 */
	public static int version(JsonObject root)
	{
		JsonElement version = root.get("schemaVersion");
		if (version instanceof JsonPrimitive && ((JsonPrimitive) version).isNumber())
		{
			return Math.max(1, version.getAsInt());
		}
		return 1;
	}

	/**
	 * Upgrades {@code root} in place. Files from a newer version are left alone.
	 *
	 * @return the version the file had before
	 */
	public static int migrate(JsonObject root)
	{
		int from = version(root);
		if (from < 2)
		{
			v1ToV2(root);
		}
		// 2 to 3 changes no layout: the scythe prices it corrects need GE prices, so the tracker fixes them after
		// loading (SupplyCorrections), knowing the file's version from the load result.
		// 3 to 4 and 4 to 5 only add fields, which are empty in older files
		if (from < AccountHistory.CURRENT_SCHEMA_VERSION || !root.has("schemaVersion"))
		{
			root.addProperty("schemaVersion", Math.max(from, AccountHistory.CURRENT_SCHEMA_VERSION));
		}
		return from;
	}

	/**
	 * Moves the top-level Maggot King data under bosses.maggot_king.
	 */
	private static void v1ToV2(JsonObject root)
	{
		JsonObject boss = new JsonObject();
		for (String field : V1_BOSS_FIELDS)
		{
			JsonElement value = root.remove(field);
			if (value != null && !value.isJsonNull())
			{
				boss.add(field, value);
			}
		}
		JsonObject bosses = new JsonObject();
		bosses.add(MaggotKingBoss.ID, boss);
		root.add("bosses", bosses);
		root.addProperty("schemaVersion", 2);
	}
}
