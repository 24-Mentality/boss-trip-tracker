package com.bosstriptracker.ui;

import com.bosstriptracker.model.LuckOdds;
import com.bosstriptracker.model.LuckTier;
import com.bosstriptracker.view.DrynessView;
import com.bosstriptracker.view.Words;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * Uniques received vs expected and the luck tier, from RuneLite's all-time records when there are some, otherwise
 * from the kills this plugin tracked. Shared by the Luck card, the share card and the overlay so they always agree.
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
	/**
	 * Chance of this many uniques or fewer, and of this many or more, over the same kills.
	 */
	double atMost;
	double atLeast;
	/**
	 * Chance of at least one unique over the same kills.
	 */
	double chanceOfAny;
	/**
	 * Average chance of any unique per kill over the same kills: the one rate every row uses.
	 */
	double rate;
	/**
	 * Null without kills.
	 */
	LuckTier tier;
	boolean approximate;
	Words words;
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
		Map<Double, Integer> chances = allTime != null ? allTime.getUniqueChances() : dryness.getUniqueChances();
		if (chances == null || chances.isEmpty())
		{
			// Without each kill's chance, every kill at the average rate
			chances = basisKills == 0 ? Collections.emptyMap()
				: Collections.singletonMap(expected / basisKills, basisKills);
		}
		double atMost = LuckOdds.atMost(chances, received);
		double atLeast = LuckOdds.atLeast(chances, received);
		double rate = basisKills > 0 && expected > 0 ? expected / basisKills : dryness.getAnyUniqueRate();
		LuckTier tier = basisKills == 0 ? null : LuckTier.of(atMost, atLeast, expected, dryness.getMinExpectedForRuck());
		return new LuckSummary(received, expected, basisKills, allTime != null, atMost, atLeast,
			1 - LuckOdds.atMost(chances, 0), rate, tier,
			dryness.isLuckApproximate(), dryness.getWords(), allTime != null ? allTime.getUniques() : dryness.getUniques());
	}

	/**
	 * Hover text for the tier: where you stand and what each tier means.
	 */
	String tierHelp()
	{
		StringBuilder help = new StringBuilder();
		if (tier == LuckTier.TOO_EARLY)
		{
			help.append("Under one unique is expected so far, so it's too early to tell.");
		}
		else if (atMost <= LuckTier.TAIL)
		{
			help.append(String.format(Locale.ROOT, "1 in %s players with as many " + words.units() + " is this dry or drier.", oneIn(atMost)));
		}
		else if (atLeast <= LuckTier.TAIL)
		{
			help.append(String.format(Locale.ROOT, "Only 1 in %s players with as many " + words.units() + " is this lucky or luckier.",
				oneIn(atLeast)));
		}
		else
		{
			help.append("Most players with as many " + words.units() + " have about as many uniques as you.");
		}
		help.append("\n\nDRY AS RUCK: in the driest 10% of players\nDry: the driest 35%\nOn Rate: in between\n"
			+ "Lucky: the luckiest 35%\nLUCKY AS RUCK: the luckiest 10%");
		if (approximate)
		{
			help.append("\n\nApproximate: your chance is taken as an equal share of the team's, with no deaths.");
		}
		return UiFormat.tooltip(help.toString());
	}

	/**
	 * The dry streak as a multiple of the drop rate, e.g. 1.4 for 1.4 times the average kills between uniques.
	 */
	double dryVsRate(int killsSinceUnique)
	{
		return rate <= 0 ? 0 : killsSinceUnique * rate;
	}

	/**
	 * Share of players who go this many kills without a unique (1 when the rate is unknown).
	 */
	double chanceThisDry(int killsSinceUnique)
	{
		return rate <= 0 ? 1 : Math.pow(1 - rate, killsSinceUnique);
	}

	/**
	 * Share of players who would have had a unique within this many kills.
	 */
	double chanceByNow(int killsSinceUnique)
	{
		return rate <= 0 ? 0 : 1 - Math.pow(1 - rate, killsSinceUnique);
	}

	private static String oneIn(double chance)
	{
		return String.format(Locale.ROOT, "%,d", Math.max(1, Math.round(1 / Math.max(chance, 1e-9))));
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
