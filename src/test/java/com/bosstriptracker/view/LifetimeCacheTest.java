package com.bosstriptracker.view;

import static org.junit.Assert.assertEquals;
import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.KillGoal;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.persistence.Fixtures;
import com.bosstriptracker.persistence.HistoryCodec;
import com.bosstriptracker.pricing.PriceService;
import com.google.gson.Gson;
import net.runelite.api.gameval.ItemID;
import org.junit.Before;
import org.junit.Test;

/**
 * The cached Lifetime view (finished trips kept, the trip in progress added on top) always matches adding up every
 * trip again.
 */
public class LifetimeCacheTest
{
	private static final long NOW = 1_790_030_000_000L;

	private final BossDefinition boss = new MaggotKingBoss();
	private final ViewBuilder builder = new ViewBuilder(new PriceService(null)
	{
		@Override
		public long price(int itemId)
		{
			return itemId;
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
	private Trip open;
	private long version;

	@Before
	public void load() throws Exception
	{
		history = HistoryCodec.decode(new Gson(), Fixtures.historyV1()).getHistory().getBosses().get(MaggotKingBoss.ID);
		open = history.getTrips().get(history.getTrips().size() - 1);
	}

	@Test
	public void cachedTotalsMatchARecountAsTheOpenTripChanges()
	{
		assertSame();

		// Supplies and charges used: only the open trip changes, the history version doesn't
		open.getSupplies().add(new ItemEntry(ItemID.ANGLERFISH, 3, 1_600));
		open.getSupplies().add(ItemEntry.charges(ItemID.BLOOD_AMULET, 40, ItemID.BLOOD_SHARD, 2_000_000, 10_000));
		assertSame();

		// A kill with a unique changes the luck numbers
		Kill kill = new Kill();
		kill.setKillCount(2_606);
		kill.setEndedAt(NOW);
		kill.setChoice("STOMACH");
		kill.getLoot().add(new ItemEntry(ItemID.ELDER_VENATOR_FANG, 1, 50_000_000));
		open.getKills().add(kill);
		assertSame();

		// Loot added to that kill later
		kill.getLoot().add(new ItemEntry(ItemID.UNCUT_RUBY, 11, 900));
		assertSame();
	}

	@Test
	public void historyChangesAreCountedOnceTheVersionChanges()
	{
		assertSame();

		history.getTrips().remove(0);
		version++;
		assertSame();

		// The open trip ends
		open.setEndedAt(NOW);
		version++;
		assertEquals(builder.lifetime(boss, history, null, null, true, NOW),
			builder.lifetime(boss, history, null, null, true, NOW, null, version));
	}

	@Test
	public void goalCountMatchesARecount()
	{
		history.setGoal(KillGoal.startingAt(100, 0, history.getTrips()));
		assertEquals(builder.goal(history, open, NOW), builder.goal(history, open, NOW, version));
		Kill kill = new Kill();
		kill.setEndedAt(NOW);
		open.getKills().add(kill);
		assertEquals(builder.goal(history, open, NOW), builder.goal(history, open, NOW, version));
	}

	private void assertSame()
	{
		LifetimeView full = builder.lifetime(boss, history, null, null, true, NOW);
		LifetimeView cached = builder.lifetime(boss, history, null, null, true, NOW, open, version);
		assertEquals(full, cached);
	}
}
