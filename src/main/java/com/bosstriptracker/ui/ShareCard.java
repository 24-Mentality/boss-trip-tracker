package com.bosstriptracker.ui;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.BossVariant;
import com.bosstriptracker.model.LuckTier;
import com.bosstriptracker.model.TripMath;
import com.bosstriptracker.view.DrynessView;
import com.bosstriptracker.view.LifetimeView;
import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.view.TripView;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * What goes on a share card, taken from the panel's state for the shown boss and variant.
 */
@Value
@Builder
class ShareCard
{
	static final int RECENT_TRIPS = 5;

	@Value
	static class Drop
	{
		int itemId;
		String name;
		int count;
	}

	@Value
	static class TripLine
	{
		long startedAt;
		int kills;
		/**
		 * The boss's description of the trip (a raid's "Normal · team of 4"), shown instead of the kills; null if none.
		 */
		String detail;
		long net;
	}

	String bossName;
	/**
	 * Selected variant, e.g. "Hard"; null for All or bosses without variants.
	 */
	String variant;
	int iconItemId;
	/**
	 * Null when names are turned off or unknown.
	 */
	String playerName;
	/**
	 * The game's kill count; null if unknown.
	 */
	Integer killCount;
	int uniquesReceived;
	double uniquesExpected;
	/**
	 * Null without kills.
	 */
	LuckTier tier;
	/**
	 * The luck numbers come from RuneLite's all-time records rather than tracked kills.
	 */
	boolean allTime;
	int dryKills;
	int longestDryStreak;
	/**
	 * Raids since anyone in the team got a unique (Theatre of Blood); null for other bosses.
	 */
	Integer teamDryStreak;
	/**
	 * Kills until the average kills between uniques is reached; negative when overdue by that many.
	 */
	int dueInKills;
	/**
	 * Average chance of any unique per kill.
	 */
	double uniqueRate;
	/**
	 * Each unique, then the pet.
	 */
	List<Drop> drops;
	/**
	 * The drop chances rows (any unique, each unique, the pet), shown with both the Expected and Received bars.
	 */
	List<DropChances.Row> chances;
	/**
	 * Where the drop chances come from, e.g. "All-time · 2,630 kills · KC 2,752".
	 */
	String chancesSource;
	long loot;
	long costs;
	long net;
	long gpPerHour;
	int trips;
	/**
	 * Kills this plugin has tracked, which the loot, costs and profit are based on.
	 */
	int trackedKills;
	/**
	 * Kill count of the first tracked kill, where tracking began; null if unknown.
	 */
	Integer trackedFromKc;
	List<TripLine> recentTrips;
	long createdAt;

	/**
	 * @return the card, or null if there is nothing to show (not logged in)
	 */
	static ShareCard from(PanelState state, boolean showName, long now)
	{
		LifetimeView lifetime = state.getLifetime();
		if (lifetime == null)
		{
			return null;
		}
		BossDefinition boss = state.getBoss();
		DrynessView dryness = lifetime.getDryness();
		LuckSummary luck = LuckSummary.of(dryness);

		List<Drop> drops = new ArrayList<>();
		for (DrynessView.Drop unique : luck.getUniques())
		{
			drops.add(new Drop(unique.getItemId(), unique.getName(), unique.getReceived()));
		}
		DrynessView.Drop pet = LuckSummary.pet(dryness);
		if (pet != null)
		{
			drops.add(new Drop(pet.getItemId(), pet.getName(), pet.getReceived()));
		}

		List<TripLine> recent = new ArrayList<>();
		for (TripView trip : state.getHistory())
		{
			if (recent.size() == RECENT_TRIPS)
			{
				break;
			}
			recent.add(new TripLine(trip.getStartedAt(), trip.getKills(), trip.getDetail(), trip.getNetProfit()));
		}

		String variant = null;
		for (BossVariant v : boss.getVariants())
		{
			if (v.getId().equals(state.getVariant()))
			{
				variant = v.getLabel();
			}
		}

		Integer killCount = dryness.getAllTime() != null && dryness.getAllTime().getKillCount() != null
			? dryness.getAllTime().getKillCount() : dryness.getCurrentKc();
		long costs = lifetime.getSupplyCost() + lifetime.getDroppedCost() + lifetime.getDeathCost();
		return ShareCard.builder()
			.bossName(boss.getDisplayName())
			.variant(variant)
			.iconItemId(boss.getIconItemId())
			.playerName(showName ? state.getPlayerName() : null)
			.killCount(killCount)
			.uniquesReceived(luck.getReceived())
			.uniquesExpected(luck.getExpected())
			.tier(luck.getTier())
			.allTime(luck.isAllTime())
			.dryKills(dryness.getKillsSinceUnique())
			.longestDryStreak(dryness.getLongestDryStreak())
			.teamDryStreak(dryness.getTeamDryStreak())
			.dueInKills(LuckSummary.dueInKills(dryness))
			.uniqueRate(dryness.getAnyUniqueRate())
			.drops(drops)
			.chances(DropChances.rows(dryness))
			.chancesSource(DropChances.source(dryness))
			.loot(lifetime.getLootValue())
			.costs(costs)
			.net(lifetime.getNetProfit())
			.gpPerHour(TripMath.gpPerHour(lifetime.getNetProfit(), lifetime.getActiveMs()))
			.trips(lifetime.getTrips())
			.trackedKills(lifetime.getKills())
			.trackedFromKc(dryness.getFirstTrackedKc())
			.recentTrips(recent)
			.createdAt(now)
			.build();
	}
}
