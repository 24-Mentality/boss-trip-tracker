package com.bosstriptracker.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableMap;
import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.TheatreOfBloodBoss;
import com.bosstriptracker.model.AllTimeCounts;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.model.TripEndReason;
import com.bosstriptracker.pricing.PriceService;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.Test;

/**
 * Luck and history views for raids: Entry Mode left out of luck, per-mode all-time odds and the game's dry streak.
 */
public class TheatreOfBloodViewTest
{
	private static final double DELTA = 1e-9;

	private final BossDefinition boss = new TheatreOfBloodBoss();
	private final ViewBuilder builder = new ViewBuilder(new PriceService(null)
	{
		@Override
		public long price(int itemId)
		{
			return 0;
		}

		@Override
		public String name(int itemId)
		{
			return "Item " + itemId;
		}

		@Override
		public boolean isFood(int itemId)
		{
			return false;
		}
	});
	private final BossHistory history = new BossHistory();

	@Before
	public void raids()
	{
		// Normal raids 5 and 6 with a team of 4, and an Entry raid in between
		history.getTrips().add(raid(TheatreOfBloodBoss.NORMAL, 5, 1_000));
		history.getTrips().add(raid(TheatreOfBloodBoss.ENTRY, null, 2_000));
		history.getTrips().add(raid(TheatreOfBloodBoss.NORMAL, 6, 3_000));
	}

	@Test
	public void entryRaidsAreLeftOutOfLuck()
	{
		DrynessView dryness = builder.lifetime(boss, history, null, null, false, 0).getDryness();

		assertEquals(2, dryness.getLuckKills());
		assertEquals(2 / 9.1 / 4, dryness.getExpectedUniques(), DELTA);
		assertEquals(2, dryness.getKillsSinceUnique());
		assertEquals(Integer.valueOf(6), dryness.getCurrentKc());
		// The Normal chip shows Normal raids only; profit-wise every raid is listed under All
		assertEquals(3, builder.lifetime(boss, history, null, null, false, 0).getTrips());
		assertEquals(2, builder.lifetime(boss, history, TheatreOfBloodBoss.NORMAL, null, false, 0).getTrips());
	}

	@Test
	public void allTimeOddsGoByEachModesKillCountAndTheTypicalTeam()
	{
		// The Loot Tracker's shared record, counted by Chat Commands: 6 Normal and 2 Hard raids
		AllTimeCounts counts = new AllTimeCounts(8, 8, 1, 2, ImmutableMap.of(ItemID.GHRAZI_RAPIER, 1),
			ImmutableMap.of(TheatreOfBloodBoss.NORMAL, 6, TheatreOfBloodBoss.HARD, 2));
		DrynessView.AllTime allTime = builder.lifetime(boss, history, null, counts, false, 0).getDryness().getAllTime();
		assertEquals(8, allTime.getLootKills());
		assertEquals(6 / 9.1 / 4 + 2 / 7.7 / 4, allTime.getExpectedUniques(), DELTA);
		assertEquals(1, allTime.getUniquesReceived());

		// "Typical team size for past raids" set to 2
		builder.setPastTeamSize(2);
		allTime = builder.lifetime(boss, history, null, counts, false, 0).getDryness().getAllTime();
		assertEquals(6 / 9.1 / 2 + 2 / 7.7 / 2, allTime.getExpectedUniques(), DELTA);
	}

	@Test
	public void theGamesDryStreakPlacesTheLastPurple()
	{
		// A purple before tracking (the record has one); in the vault of raid 6 the game said "dry streak of 2"
		AllTimeCounts counts = new AllTimeCounts(6, 6, 1, 2, ImmutableMap.of(ItemID.GHRAZI_RAPIER, 1),
			ImmutableMap.of(TheatreOfBloodBoss.NORMAL, 6));
		history.setGameDryStreak(2);
		history.setGameDryStreakKc(6);
		DrynessView dryness = builder.lifetime(boss, history, null, counts, false, 0).getDryness();
		assertTrue(dryness.isSinceFromGameCount());
		assertFalse(dryness.isSinceFromEnteredKc());
		assertEquals(2, dryness.getKillsSinceUnique());

		// A kill count you entered wins over the game's count
		history.setLastUniqueKc(5);
		dryness = builder.lifetime(boss, history, null, counts, false, 0).getDryness();
		assertFalse(dryness.isSinceFromGameCount());
		assertEquals(1, dryness.getKillsSinceUnique());
	}

	@Test
	public void teamDryStreakCountsEveryRaidSinceAnyonesPurple()
	{
		// Without the game's count: the 3 raids tracked, Entry included
		DrynessView dryness = builder.lifetime(boss, history, TheatreOfBloodBoss.NORMAL, null, false, 0).getDryness();
		assertEquals(Integer.valueOf(3), dryness.getTeamDryStreak());
		assertFalse(dryness.isTeamDryStreakFromGame());

		// "You have completed 7 raids since you've seen any purple." in the vault of the second raid: 7, plus one since
		history.setGameTeamDryStreak(7);
		history.setGameTeamDryStreakAt(2_000L);
		dryness = builder.lifetime(boss, history, null, null, false, 0).getDryness();
		assertEquals(Integer.valueOf(8), dryness.getTeamDryStreak());
		assertTrue(dryness.isTeamDryStreakFromGame());

		// A teammate's purple in the last raid resets it, though your own dry streak goes on
		history.getTrips().get(2).getKills().get(0).getTeamUniques().add(ItemID.SCYTHE_OF_VITUR_UNCHARGED);
		dryness = builder.lifetime(boss, history, null, null, false, 0).getDryness();
		assertEquals(Integer.valueOf(0), dryness.getTeamDryStreak());
		assertEquals(2, dryness.getKillsSinceUnique());
	}

	@Test
	public void historyCardDescribesTheRaid()
	{
		TripView view = builder.trip(boss, history.getTrips().get(0));
		assertEquals("Normal · team of 4", view.getDetail());
		assertEquals("Purples", view.getBossStat().getLabel());
		assertEquals("0 / 0", view.getBossStat().getValue());
	}

	private static Trip raid(String mode, Integer killCount, long endedAt)
	{
		Trip trip = new Trip();
		trip.setId(mode + endedAt);
		trip.setStartedAt(endedAt - 500);
		trip.setEndedAt(endedAt);
		trip.setEndReason(TripEndReason.COMPLETED);
		Kill kill = new Kill();
		kill.setVariant(mode);
		kill.setKillCount(killCount);
		kill.setPartySize(4);
		kill.setEndedAt(endedAt);
		kill.getLoot().add(new ItemEntry(ItemID.RUNE_PLATEBODY, 3, 38_000));
		trip.getKills().add(kill);
		return trip;
	}
}
