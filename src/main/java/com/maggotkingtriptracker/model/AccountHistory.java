package com.maggotkingtriptracker.model;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;

/**
 * Everything stored for one account. Serialized with Gson to one JSON file per account.
 * Schema 1 kept the Maggot King's data at the top level; schema 2 keeps each boss's data under its id
 * (see HistoryMigrator); schema 3 is the same layout with Scythe of Vitur charges repriced (see
 * SupplyCorrections); schema 4 adds fields for raids (Kill.teamUniques, BossHistory.gameDryStreak and two trip end
 * reasons) with no change to existing data; schema 5 adds the game's team dry streak (BossHistory.gameTeamDryStreak).
 */
@Data
public class AccountHistory
{
	public static final int CURRENT_SCHEMA_VERSION = 5;

	private int schemaVersion = CURRENT_SCHEMA_VERSION;
	private long accountHash;
	private String lastDisplayName;
	/**
	 * Boss id (BossDefinition#getId) to that boss's data.
	 */
	private Map<String, BossHistory> bosses = new LinkedHashMap<>();

	/**
	 * @return this boss's data, created empty if there is none yet
	 */
	public BossHistory boss(String bossId)
	{
		return bosses.computeIfAbsent(bossId, id -> new BossHistory());
	}
}
