package com.bosstriptracker.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.model.AllTimeCounts;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.persistence.Fixtures;
import com.bosstriptracker.persistence.HistoryCodec;
import com.bosstriptracker.pricing.PriceService;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.Test;

/**
 * Lifetime, luck and trip views for the Maggot King from a migrated schema 1 file.
 */
public class ViewBuilderTest
{
	private static final double DELTA = 1e-9;

	private final BossDefinition boss = new MaggotKingBoss();
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
			return itemId == ItemID.ANGLERFISH;
		}

		@Override
		public int canonicalize(int itemId)
		{
			return itemId;
		}

		@Override
		public boolean isDrinkable(int itemId)
		{
			return false;
		}

		@Override
		public boolean isEquipable(int itemId)
		{
			return false;
		}
	});
	private BossHistory history;

	@Before
	public void load() throws Exception
	{
		history = HistoryCodec.decode(new Gson(), Fixtures.historyV1()).getHistory().getBosses().get(MaggotKingBoss.ID);
	}

	@Test
	public void lifetimeTotals()
	{
		LifetimeView view = builder.lifetime(boss, history, null, null, false, 1_790_030_000_000L);

		assertEquals(3, view.getTrips());
		assertEquals(5, view.getKills());
		assertEquals("Stomach 4 · Eggs 1", view.getChoiceSummary());
		assertEquals(1, view.getDeaths());
		assertEquals(20_051_500, view.getLootValue());
		assertEquals(2, view.getNetPerTrip().size());
		assertEquals(2, view.getPolish().size());
	}

	@Test
	public void luckFromTrackedOpenStomachKills()
	{
		DrynessView dryness = builder.lifetime(boss, history, null, null, false, 0).getDryness();

		assertEquals(4, dryness.getLuckKills());
		assertEquals(1, dryness.getUniquesReceived());
		assertEquals(4 / 205.6, dryness.getExpectedUniques(), DELTA);
		assertEquals(1 / 205.6, dryness.getAnyUniqueRate(), 0);
		// Fang at 2601, then Open-stomach kills 2603, 2604 and 2605 (2602 was Take-eggs)
		assertEquals(3, dryness.getKillsSinceUnique());
		assertFalse(dryness.isSinceFromEnteredKc());
		assertEquals(Integer.valueOf(2601), dryness.getLastUniqueKc());
		assertEquals(Integer.valueOf(2601), dryness.getFirstTrackedKc());
		assertEquals(Integer.valueOf(2605), dryness.getCurrentKc());
		assertEquals(Math.pow(1 - 1 / 205.6, 3), dryness.getChanceThisDry(), DELTA);
		// Only one known unique, so the current streak
		assertEquals(3, dryness.getLongestDryStreak());

		assertEquals(2, dryness.getUniques().size());
		assertEquals(1, dryness.getUniques().get(0).getReceived());
		assertEquals(4 / 340.0, dryness.getUniques().get(0).getExpected(), DELTA);
		assertEquals(0, dryness.getUniques().get(1).getReceived());
		assertEquals(4 / 3500.0, dryness.getPet().getExpected(), DELTA);
		assertEquals(0, dryness.getPet().getReceived());

		assertEquals(6, dryness.getEggTiers().size());
		assertEquals(1, dryness.getPetsFromEggs());
		assertEquals(1 / 3000.0 + 1 / 1250.0, dryness.getEggPetExpected(), DELTA);
		assertNull(dryness.getAllTime());
	}

	@Test
	public void enteredKcOnlyCountsWhenNewerThanTheTrackedUnique()
	{
		history.setLastUniqueKc(2500);
		DrynessView older = builder.lifetime(boss, history, null, null, false, 0).getDryness();
		assertEquals(3, older.getKillsSinceUnique());
		assertFalse(older.isSinceFromEnteredKc());
		assertEquals(Integer.valueOf(2500), older.getEnteredLastUniqueKc());

		history.setLastUniqueKc(2603);
		DrynessView newer = builder.lifetime(boss, history, null, null, false, 0).getDryness();
		// 2604 and 2605
		assertEquals(2, newer.getKillsSinceUnique());
		// Tracked fang at 2601, entered unique at 2603
		assertEquals(2, newer.getLongestDryStreak());
		history.setLastUniqueKc(2500);
		assertEquals(101, builder.lifetime(boss, history, null, null, false, 0).getDryness().getLongestDryStreak());
		assertTrue(newer.isSinceFromEnteredKc());
		assertEquals(Integer.valueOf(2603), newer.getLastUniqueKc());
	}

	@Test
	public void allTimeUsesTheLootTrackerRecord()
	{
		AllTimeCounts counts = new AllTimeCounts(2630, 2636, 1_785_447_588_633L, 0,
			ImmutableMap.of(ItemID.ELDER_VENATOR_FANG, 6, ItemID.CRIMSON_KISTEN, 5));
		DrynessView.AllTime allTime = builder.lifetime(boss, history, null, counts, false, 0).getDryness().getAllTime();

		assertEquals(2630, allTime.getLootKills());
		assertEquals(Integer.valueOf(2636), allTime.getKillCount());
		assertEquals(11, allTime.getUniquesReceived());
		assertEquals(2630 / 205.6, allTime.getExpectedUniques(), DELTA);
		assertEquals(6, allTime.getUniques().get(0).getReceived());
		assertEquals(2630 / 340.0, allTime.getUniques().get(0).getExpected(), DELTA);
		assertEquals(5, allTime.getUniques().get(1).getReceived());
		// No pet in the Loot Tracker record, one tracked from an egg
		assertEquals(1, allTime.getPet().getReceived());
		assertEquals(2630 / 3500.0 + 1 / 3000.0 + 1 / 1250.0, allTime.getPet().getExpected(), DELTA);
	}

	@Test
	public void neverHavingAUniqueMakesTheWholeKillCountTheDryStreak()
	{
		// Take the tracked fang away: no unique tracked, none in the Loot Tracker, no KC entered
		history.getTrips().get(0).getKills().get(0).getLoot().clear();
		AllTimeCounts none = new AllTimeCounts(2630, 2700, 1_785_447_588_633L, 0, ImmutableMap.<Integer, Integer>of());
		DrynessView dryness = builder.lifetime(boss, history, null, none, false, 0).getDryness();
		assertTrue(dryness.isSinceWholeKillCount());
		assertEquals(2700, dryness.getKillsSinceUnique());
		assertEquals(2700, dryness.getLongestDryStreak());
		assertEquals(Math.pow(1 - 1 / 205.6, 2700), dryness.getChanceThisDry(), DELTA);

		// A unique in the Loot Tracker's record: back to counting from tracking
		AllTimeCounts one = new AllTimeCounts(2630, 2700, 1_785_447_588_633L, 0, ImmutableMap.of(ItemID.CRIMSON_KISTEN, 1));
		dryness = builder.lifetime(boss, history, null, one, false, 0).getDryness();
		assertFalse(dryness.isSinceWholeKillCount());
		assertEquals(4, dryness.getKillsSinceUnique());

		// A last-unique KC entered by hand takes over too
		history.setLastUniqueKc(2600);
		dryness = builder.lifetime(boss, history, null, none, false, 0).getDryness();
		assertFalse(dryness.isSinceWholeKillCount());

		// Without all-time records there's no kill count to use
		history.setLastUniqueKc(null);
		dryness = builder.lifetime(boss, history, null, null, false, 0).getDryness();
		assertFalse(dryness.isSinceWholeKillCount());
		assertEquals(4, dryness.getKillsSinceUnique());
	}

	@Test
	public void tracksDropsTheLootTrackerHasNotSavedYet()
	{
		// Saved before the fixture's first kill: the fang at 2601 and all four Open-stomach kills are newer
		AllTimeCounts before = new AllTimeCounts(2630, 2600, 1_785_447_588_633L, 1_790_000_000_000L,
			ImmutableMap.of(ItemID.ELDER_VENATOR_FANG, 6, ItemID.CRIMSON_KISTEN, 5));
		DrynessView.AllTime allTime = builder.lifetime(boss, history, null, before, false, 0).getDryness().getAllTime();
		assertEquals(2630 + 4, allTime.getLootKills());
		assertEquals(7, allTime.getUniques().get(0).getReceived());
		assertEquals(12, allTime.getUniquesReceived());
		assertEquals((2630 + 4) / 205.6, allTime.getExpectedUniques(), DELTA);
		// Chat Commands was behind too: the tracked kill count is newer
		assertEquals(Integer.valueOf(2605), allTime.getKillCount());

		// Saved after the fang: only the Open-stomach kills at 2604 and 2605 are newer, no uniques among them
		AllTimeCounts after = new AllTimeCounts(2634, 2605, 1_785_447_588_633L, 1_790_002_000_000L,
			ImmutableMap.of(ItemID.ELDER_VENATOR_FANG, 7, ItemID.CRIMSON_KISTEN, 5));
		allTime = builder.lifetime(boss, history, null, after, false, 0).getDryness().getAllTime();
		assertEquals(2634 + 2, allTime.getLootKills());
		assertEquals(7, allTime.getUniques().get(0).getReceived());
	}

	@Test
	public void tripViewHasTheStomachEggsCellAndGoldUniques()
	{
		TripView trip = builder.trip(boss, history.getTrips().get(0));

		assertEquals("Stom / Eggs", trip.getBossStat().getLabel());
		assertEquals("3 / 1", trip.getBossStat().getValue());
		assertEquals(19_934_000, trip.getNetProfit());
		ItemView fang = trip.getLoot().get(0);
		assertEquals(ItemID.ELDER_VENATOR_FANG, fang.getItemId());
		assertTrue(fang.isUnique());
		assertTrue(trip.getLoot().stream().anyMatch(ItemView::isPending));
	}

	@Test
	public void goalCountsKillsSinceItWasSet()
	{
		GoalView goal = builder.goal(history, null, 0);
		assertEquals(150, goal.getTarget());
		assertEquals(5, goal.getDone());
		assertFalse(goal.isRunning());
	}
}
