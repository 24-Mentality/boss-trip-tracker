package com.bosstriptracker.ui;

import com.bosstriptracker.view.DrynessView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.Value;

/**
 * The drop chances numbers: any unique, each unique, then the pet, from RuneLite's all-time record when there is
 * one, otherwise the kills this plugin tracked. Shared by the panel's card and the share card so they always agree.
 */
final class DropChances
{
	/**
	 * Item id of the "any unique" row, which has no item.
	 */
	static final int ANY = -1;

	private DropChances()
	{
	}

	@Value
	static class Row
	{
		int itemId;
		double expected;
		int received;

		/**
		 * Expected tab: how far toward the next statistical drop (0 to 1).
		 */
		double toNext()
		{
			return expected - Math.floor(expected);
		}

		/**
		 * Received tab: drops ahead of (or behind) expectation.
		 */
		double luck()
		{
			return received - expected;
		}
	}

	static List<Row> rows(DrynessView dryness)
	{
		List<Row> rows = new ArrayList<>();
		DrynessView.AllTime allTime = dryness.getAllTime();
		rows.add(new Row(ANY, allTime != null ? allTime.getExpectedUniques() : dryness.getExpectedUniques(),
			allTime != null ? allTime.getUniquesReceived() : dryness.getUniquesReceived()));
		for (DrynessView.Drop unique : allTime != null ? allTime.getUniques() : dryness.getUniques())
		{
			rows.add(new Row(unique.getItemId(), unique.getExpected(), unique.getReceived()));
		}
		DrynessView.Drop pet = dryness.getPet();
		if (pet != null)
		{
			// Eggs popped add to the pet (the Maggot King); the all-time figures already include them
			rows.add(allTime != null
				? new Row(pet.getItemId(), allTime.getPet().getExpected(), allTime.getPet().getReceived())
				: new Row(pet.getItemId(), pet.getExpected() + dryness.getEggPetExpected(), pet.getReceived() + dryness.getPetsFromEggs()));
		}
		return rows;
	}

	/**
	 * The largest difference from expectation among the rows (at least 1), which fills half a Received bar.
	 */
	static double luckScale(List<Row> rows)
	{
		double scale = 1;
		for (Row row : rows)
		{
			scale = Math.max(scale, Math.abs(row.luck()));
		}
		return scale;
	}

	/**
	 * E.g. "All-time · 2,630 kills · KC 2,752", or "Tracked · 172 kills".
	 */
	static String source(DrynessView dryness)
	{
		DrynessView.AllTime allTime = dryness.getAllTime();
		if (allTime != null)
		{
			return String.format(Locale.ROOT, "All-time · %,d kills", allTime.getLootKills())
				+ (allTime.getKillCount() != null ? String.format(Locale.ROOT, " · KC %,d", allTime.getKillCount()) : "");
		}
		return String.format(Locale.ROOT, "Tracked · %,d kills", dryness.getLuckKills());
	}
}
