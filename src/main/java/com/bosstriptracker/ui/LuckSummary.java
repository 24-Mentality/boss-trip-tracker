package com.bosstriptracker.ui;

import com.bosstriptracker.model.DropOdds;
import com.bosstriptracker.model.LuckTier;
import com.bosstriptracker.view.DrynessView;
import java.util.List;
import lombok.Value;

/**
 * Uniques received vs expected and the luck tier, from RuneLite's all-time records when there are some, otherwise
 * from the kills this plugin tracked. Shared by the Luck card and the share card so they always agree.
 */
@Value
class LuckSummary
{
	int received;
	double expected;
	/**
	 * Kills the numbers are based on.
	 */
	int basisKills;
	boolean allTime;
	double percentile;
	/**
	 * Null without kills.
	 */
	LuckTier tier;
	/**
	 * Each unique with its received and expected count, from the same source.
	 */
	List<DrynessView.Drop> uniques;

	static LuckSummary of(DrynessView dryness)
	{
		DrynessView.AllTime allTime = dryness.getAllTime();
		int received = allTime != null ? allTime.getUniquesReceived() : dryness.getUniquesReceived();
		double expected = allTime != null ? allTime.getExpectedUniques() : dryness.getExpectedUniques();
		int basisKills = allTime != null ? allTime.getLootKills() : dryness.getLuckKills();
		double percentile = DropOdds.luckPercentile(received, expected);
		return new LuckSummary(received, expected, basisKills, allTime != null, percentile,
			basisKills == 0 ? null : LuckTier.of(percentile),
			allTime != null ? allTime.getUniques() : dryness.getUniques());
	}

	/**
	 * Hover text for the tier: where you stand and what each tier means.
	 */
	String tierHelp()
	{
		return UiFormat.tooltip("You've had more uniques than about " + Math.round(percentile * 100)
			+ "% of players with the same kills (50% is exactly average).\n\n"
			+ "LUCKY AS RUCK: 90% and up\nLucky: 65% to 90%\nOn Rate: 35% to 65%\nDry: 10% to 35%\n"
			+ "DRY AS RUCK: 10% and down");
	}

	/**
	 * Kills until the average number of kills between uniques; negative when past it by that many.
	 */
	static int dueInKills(DrynessView dryness)
	{
		return (int) Math.ceil(1 / dryness.getAnyUniqueRate()) - dryness.getKillsSinceUnique();
	}

	/**
	 * Pets from kills (and eggs, for the Maggot King), from the same source; null if the boss has no pet.
	 */
	static DrynessView.Drop pet(DrynessView dryness)
	{
		if (dryness.getAllTime() != null)
		{
			return dryness.getAllTime().getPet();
		}
		DrynessView.Drop pet = dryness.getPet();
		if (pet == null)
		{
			return null;
		}
		return new DrynessView.Drop(pet.getItemId(), pet.getName(), pet.getRate(),
			pet.getExpected() + dryness.getEggPetExpected(), pet.getReceived() + dryness.getPetsFromEggs(),
			pet.getKillCounts());
	}
}
