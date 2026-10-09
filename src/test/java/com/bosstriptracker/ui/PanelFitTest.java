package com.bosstriptracker.ui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.CanvasSection;
import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.DropKind;
import com.bosstriptracker.boss.ExpectedDrop;
import com.bosstriptracker.boss.KillContext;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.boss.NightmareBoss;
import com.bosstriptracker.model.TripEndReason;
import com.bosstriptracker.view.BossOption;
import com.bosstriptracker.view.DrynessView;
import com.bosstriptracker.view.GoalView;
import com.bosstriptracker.view.ItemView;
import com.bosstriptracker.view.LifetimeView;
import com.bosstriptracker.view.LootCategory;
import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.view.PolishView;
import com.bosstriptracker.view.StatView;
import com.bosstriptracker.view.SupplyCategory;
import com.bosstriptracker.view.TripView;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Lays out the whole panel at the narrowest sidebar width with worst-case values and checks that no label is
 * cut off or wraps.
 */
public class PanelFitTest
{
	/**
	 * The sidebar gives a non-scrolling PluginPanel 242px, minus room for our own vertical scrollbar.
	 */
	private static final int WIDTH = 242 - 17;
	private static final BossDefinition BOSS = new MaggotKingBoss();
	/**
	 * Every box open, so their contents are laid out too (the headers show either way).
	 */
	private static final String OPEN_BOXES = "trip.loot=open,trip.supplies=open,trip.dropped=open,history.loot=open,"
		+ "history.supplies=open,history.dropped=open";
	/**
	 * Five categories with the longest names, Other last, each at a very large value.
	 */
	private static final List<LootCategory> LOOT_CATEGORIES = Arrays.asList(
		new LootCategory("Herbs & seeds", 99_999_999_000L, Collections.<ItemView>emptyList()),
		new LootCategory("Runes & ammo", 99_999_999_000L, Collections.<ItemView>emptyList()),
		new LootCategory("Consumables", 99_999_999_000L, Collections.<ItemView>emptyList()),
		new LootCategory("Jewellery", 99_999_999_000L, Collections.<ItemView>emptyList()),
		new LootCategory("Other", 99_999_999_000L, Collections.<ItemView>emptyList()));

	@Test
	public void everyLabelFitsOnAllTabs() throws Exception
	{
		List<String> problems = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			TrackerPanel panel = new TrackerPanel(null, new SectionStates(OPEN_BOXES, saved -> { }), new NoActions(), BOSS);
			// As when the panel is opened in the sidebar
			panel.onActivate();
			panel.update(worstCaseState(PanelState.Status.IN_TRIP, false));
			for (int tab = 0; tab < 3; tab++)
			{
				panel.selectTab(tab);
				check(panel, "tab " + tab, problems);
				if (tab == 0)
				{
					preview(panel);
				}
				if (tab == 1)
				{
					// An expanded History card: its boxes are a little narrower than the Trip tab's
					expand(find(panel, TripCard.class));
					check(panel, "history card", problems);
				}
			}

			// Paused states change button and status text
			panel.update(worstCaseState(PanelState.Status.AFK_PAUSED, true));
			panel.selectTab(0);
			check(panel, "paused", problems);

			// The profit card collapsed to net profit and net GP/hr
			TripSummaryCard summary = find(panel, TripSummaryCard.class);
			summary.setProfitCollapsed(true);
			check(panel, "profit collapsed", problems);
			summary.setProfitCollapsed(false);

			// The Nightmare: eight uniques and the pet on the luck card, variant chips under the dropdown
			panel.update(nightmareState(worstCaseState(PanelState.Status.IN_TRIP, false)));
			for (int tab = 0; tab < 3; tab++)
			{
				panel.selectTab(tab);
				check(panel, "nightmare tab " + tab, problems);
			}
			panel.selectTab(0);

			// The Theatre of Blood: a raid's History card and the team dry streak row, on both luck cards
			panel.update(worstCaseState(PanelState.Status.IN_TRIP, false, true));
			for (int tab = 0; tab < 3; tab++)
			{
				panel.selectTab(tab);
				check(panel, "raid tab " + tab, problems);
			}
			panel.shutDown();
		});
		assertTrue("Labels that don't fit:\n" + String.join("\n", problems), problems.isEmpty());
	}

	private static void check(TrackerPanel panel, String where, List<String> problems)
	{
		panel.setSize(WIDTH, 4000);
		// Twice: the first pass settles nested preferred sizes
		layout(panel);
		layout(panel);
		findLabels(panel, where, problems);
	}

	/**
	 * For looking at the layout: build/panel-preview.png (top of the Trip tab).
	 */
	private static void preview(TrackerPanel panel)
	{
		BufferedImage image = new BufferedImage(WIDTH, 420, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		panel.printAll(g);
		g.dispose();
		try
		{
			File out = new File("build/panel-preview.png");
			out.getParentFile().mkdirs();
			ImageIO.write(image, "PNG", out);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	private static void expand(TripCard card)
	{
		Component header = card.getComponent(0);
		header.dispatchEvent(new MouseEvent(header, MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false, MouseEvent.BUTTON1));
		assertTrue(card.isExpanded());
	}

	@Test
	public void onlyTheTabOnScreenIsUpdated() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			TrackerPanel panel = new TrackerPanel(null, new SectionStates(OPEN_BOXES, saved -> { }), new NoActions(), BOSS);
			// Closed in the sidebar: nothing is built
			panel.update(worstCaseState(PanelState.Status.IN_TRIP, false));
			assertNull(find(panel, TripDetails.class));

			// Opened on the Trip tab: the History tab's cards wait until it's shown
			panel.onActivate();
			assertNotNull(find(panel, TripDetails.class));
			assertNull(find(panel, TripCard.class));
			panel.selectTab(1);
			assertNotNull(find(panel, TripCard.class));

			// "Show 50 more (12,344 older)" fits at sidebar width (with room for the button's own margins)
			panel.setSize(WIDTH, 4000);
			layout(panel);
			layout(panel);
			JButton showMore = find(panel, JButton.class, b -> b.getText().startsWith("Show "));
			assertNotNull(showMore);
			assertTrue(showMore.getText(), showMore.getFontMetrics(showMore.getFont()).stringWidth(showMore.getText()) + 30
				<= showMore.getParent().getWidth());
			panel.onDeactivate();
		});
	}

	@Test
	public void killChartsFitAtSidebarWidth() throws Exception
	{
		List<String> problems = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 8);
			java.util.Map<java.time.LocalDate, Integer> kills = new java.util.TreeMap<>();
			for (int i = 0; i < 300; i += 1 + i % 3)
			{
				kills.put(today.minusDays(i), 1 + (i * 7) % 40);
			}
			KillChartsPanel charts = new KillChartsPanel(() -> today);
			charts.update(kills, com.bosstriptracker.view.Words.KILLS);
			javax.swing.JPanel holder = new javax.swing.JPanel(new java.awt.BorderLayout());
			holder.setBackground(net.runelite.client.ui.ColorScheme.DARK_GRAY_COLOR);
			holder.add(charts, java.awt.BorderLayout.NORTH);
			holder.setSize(WIDTH - 10, 400);
			layout(holder);
			layout(holder);
			findLabels(holder, "kill charts", problems);
			BufferedImage image = new BufferedImage(WIDTH - 10, 200, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			holder.printAll(g);
			g.dispose();
			try
			{
				ImageIO.write(image, "PNG", new File("build/kill-charts-preview.png"));
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		});
		assertTrue("Labels that don't fit:\n" + String.join("\n", problems), problems.isEmpty());
	}

	private static <T> T find(Component component, Class<T> type, java.util.function.Predicate<T> matches)
	{
		if (type.isInstance(component) && matches.test(type.cast(component)))
		{
			return type.cast(component);
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				T found = find(child, type, matches);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static <T> T find(Component component, Class<T> type)
	{
		if (type.isInstance(component))
		{
			return type.cast(component);
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				T found = find(child, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static void layout(Component component)
	{
		component.invalidate();
		component.doLayout();
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				layout(child);
			}
		}
	}

	private static void findLabels(Component component, String where, List<String> problems)
	{
		if (!component.isVisible())
		{
			return;
		}
		if (component instanceof JLabel)
		{
			JLabel label = (JLabel) component;
			String text = label.getText();
			if (text != null && !text.isEmpty() && label.getWidth() > 0
				&& label.getPreferredSize().width > label.getWidth())
			{
				problems.add(where + ": \"" + text.replaceAll("<[^>]+>", "") + "\" needs "
					+ label.getPreferredSize().width + "px, has " + label.getWidth() + "px");
			}
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				findLabels(child, where, problems);
			}
		}
	}

	private static PanelState nightmareState(PanelState state)
	{
		BossDefinition nightmare = new NightmareBoss();
		List<DrynessView.Drop> uniques = new ArrayList<>();
		for (ExpectedDrop drop : nightmare.getDrops())
		{
			if (drop.getKind() == DropKind.UNIQUE)
			{
				uniques.add(new DrynessView.Drop(drop.getItemId(), "Inquisitor's great helm", drop.chance(KillContext.DEFAULT),
					12.34, 99, Collections.<Integer>emptyList()));
			}
		}
		DrynessView dryness = state.getLifetime().getDryness().toBuilder()
			.uniques(uniques)
			.eggTiers(Collections.<DrynessView.EggTier>emptyList())
			.allTime(state.getLifetime().getDryness().getAllTime().toBuilder().uniques(uniques).build())
			.build();
		List<BossOption> bosses = Arrays.asList(
			new BossOption(BOSS.getId(), BOSS.getDisplayName(), BOSS.getIconItemId(), false),
			new BossOption(nightmare.getId(), nightmare.getDisplayName(), nightmare.getIconItemId(), true));
		return state.toBuilder()
			.boss(nightmare)
			.bosses(bosses)
			.variant(NightmareBoss.PHOSANI)
			.lifetime(state.getLifetime().toBuilder().dryness(dryness).choiceSummary("").polish(Collections.<PolishView>emptyList()).build())
			.build();
	}

	private static PanelState worstCaseState(PanelState.Status status, boolean paused)
	{
		return worstCaseState(status, paused, false);
	}

	/**
	 * @param raid a Theatre of Blood raid: the History card's raid detail and the team dry streak row
	 */
	private static PanelState worstCaseState(PanelState.Status status, boolean paused, boolean raid)
	{
		TripView trip = TripView.builder()
			.id("trip")
			.startedAt(1_790_000_000_000L)
			.endedAt(1_790_045_000_000L)
			// A long wiped raid: every part of the History card's line shows
			.endReason(raid ? TripEndReason.WIPED : TripEndReason.TELEPORT)
			.activeMs(raid ? (59 * 60 + 59) * 1000L : (12 * 3600 + 34 * 60 + 56) * 1000L)
			.kills(999)
			.detail(raid ? "Normal · team of 5" : null)
			.bossStat(new StatView(BOSS.getProfitCell().getLabel(), "999 / 999", BOSS.getProfitCell().getHelp()))
			.deaths(99)
			.pet(true)
			.lootValue(123_456_789_000L)
			.supplyCost(99_999_999_000L)
			.droppedCost(9_999_999_000L)
			.deathCost(9_999_999_000L)
			.netProfit(-12_400_000_000L)
			.averageKillMs(599_900L)
			.fastestKillMs(599_900L)
			.lastKillMs(599_900L)
			.loot(Collections.<ItemView>emptyList())
			.lootCategories(LOOT_CATEGORIES)
			.supplies(Collections.<ItemView>emptyList())
			.dropped(Collections.<ItemView>emptyList())
			.supplyCategories(Arrays.asList(
				new SupplyCategory("Charges", 99_999_999_000L),
				new SupplyCategory("Runes", 99_999_999_000L),
				new SupplyCategory("Potions", 99_999_999_000L),
				new SupplyCategory("Food", 99_999_999_000L),
				new SupplyCategory("Other", 99_999_999_000L)))
			.build();

		List<DrynessView.Drop> uniques = Arrays.asList(
			new DrynessView.Drop(ItemID.ELDER_VENATOR_FANG, "Elder venator fang", 1 / 340.0, 36.31, 2, Arrays.asList(12_345, 12_346)),
			new DrynessView.Drop(ItemID.CRIMSON_KISTEN, "Crimson kisten", 1 / 520.0, 23.74, 1, Collections.singletonList(12_345)));
		DrynessView.Drop pet = new DrynessView.Drop(ItemID.MAGGOTKINGPET, "Maggot marquess", 1 / 3500.0, 3.53, 9,
			Collections.<Integer>emptyList());
		List<DrynessView.EggTier> eggs = new ArrayList<>();
		for (int id : new int[]{ItemID.MAGGOT_EGG, ItemID.SICKLY_MAGGOT_EGG, ItemID.WARM_MAGGOT_EGG,
			ItemID.PULSATING_MAGGOT_EGG, ItemID.WRIGGLING_MAGGOT_EGG, ItemID.WRITHING_MAGGOT_EGG})
		{
			eggs.add(new DrynessView.EggTier(id, "Pulsating maggot egg", 999, 99, 1 / 3000.0));
		}
		// Realistic but large: KC 12,345 with 99 uniques
		DrynessView dryness = DrynessView.builder()
			.luckKills(12_345)
			.killsSinceUnique(1_234)
			.longestDryStreak(12_345)
			.chanceThisDry(0.0024)
			.anyUniqueRate(1 / 205.6)
			.uniquesReceived(99)
			.expectedUniques(60.04)
			.uniques(uniques)
			.pet(pet)
			.eggTiers(eggs)
			.eggPetChance(0.9999)
			.eggPetExpected(1.5)
			.petsFromEggs(9)
			.currentKc(12_345)
			.lastUniqueKc(11_111)
			.firstTrackedKc(12_345)
			.teamDryStreak(raid ? 12_345 : null)
			.allTime(DrynessView.AllTime.builder()
				.lootKills(12_345)
				.killCount(12_345)
				.firstRecordedAt(1_785_447_588_633L)
				.uniquesReceived(99)
				.expectedUniques(60.04)
				.uniques(uniques)
				.pet(pet)
				.build())
			.build();

		List<PolishView> polish = Collections.singletonList(new PolishView(ItemID.TARNISHED_NECKLACE, "Tarnished necklace",
			999, Collections.singletonList(new ItemView(ItemID.DIAMOND_NECKLACE, "Diamond necklace", 999, 0, false, false,
			false, null, 0, null, null))));

		LifetimeView lifetime = LifetimeView.builder()
			.trips(9_999)
			// "Tracked since 30 Sep 2026 (KC 12,345)" at its longest
			.trackedSince(1_790_750_000_000L)
			.kills(99_999)
			.choiceSummary("Stomach 99999 · Eggs 99999")
			.deaths(9_999)
			.pets(99)
			.activeMs(999L * 3600 * 1000)
			.lootValue(123_456_789_000L)
			.supplyCost(99_999_999_000L)
			.droppedCost(9_999_999_000L)
			.deathCost(9_999_999_000L)
			.netProfit(-12_400_000_000L)
			.averageKillMs(599_900L)
			.lootValueToday(123_456_789_000L)
			.netPerTrip(Arrays.asList(-12_400_000_000L, 5_000_000L))
			.dryness(dryness)
			.polish(polish)
			// The Lifetime tab's loot and supplies cards, with every stat at its longest (item grids need the client's
			// item icons, so they're left empty)
			.loot(Collections.<ItemView>emptyList())
			.lootCategories(LOOT_CATEGORIES)
			.supplies(Collections.<ItemView>emptyList())
			.supplyCategories(Arrays.asList(
				new SupplyCategory("Charges", 99_999_999_000L),
				new SupplyCategory("Runes", 99_999_999_000L),
				new SupplyCategory("Potions", 99_999_999_000L),
				new SupplyCategory("Food", 99_999_999_000L),
				new SupplyCategory("Other", 99_999_999_000L)))
			.allTimeLoot(Collections.<ItemView>emptyList())
			.allTimeLootValue(123_456_789_000L)
			.allTimeLootCategories(LOOT_CATEGORIES)
			.allTimeSince(1_785_447_588_633L)
			.build();

		// 1 kill per hour for 12,000 hours: KPH and the time to goal are at their longest
		GoalView goal = new GoalView(99_999, 12_345, 12_345L * 3600 * 1000, System.currentTimeMillis(), !paused, 0);
		List<BossOption> bosses = Collections.singletonList(
			new BossOption(BOSS.getId(), BOSS.getDisplayName(), BOSS.getIconItemId(), true));
		return PanelState.builder()
			.boss(BOSS)
			.bosses(bosses)
			.status(status)
			.currentTrip(trip)
			.history(Collections.singletonList(trip))
			// "Show 50 more (12,344 older)"
			.historyTotal(12_345)
			.lifetime(lifetime)
			.goal(goal)
			.pauseText(paused ? "Trip paused (outside the lair)" : null)
			.pausedInLair(paused)
			.canPause(true)
			// A 9:59 kill in progress while not paused; the last kill's time while paused
			.killStartedAt(paused ? null : System.currentTimeMillis() - 599_000L)
			.playerName("Twelve Chars")
			.build();
	}

	private static class NoActions implements PanelActions
	{
		@Override
		public void showMoreHistory()
		{
		}

		@Override
		public boolean isOnCanvas(CanvasSection section)
		{
			return false;
		}

		@Override
		public void toggleCanvas(CanvasSection section)
		{
		}

		@Override
		public void selectBoss(String bossId)
		{
		}

		@Override
		public void selectVariant(String variant)
		{
		}

		@Override
		public void setLastUniqueKc(Integer killCount)
		{
		}

		@Override
		public void deleteTrip(String tripId)
		{
		}

		@Override
		public void clearHistory()
		{
		}

		@Override
		public void exportCsv()
		{
		}

		@Override
		public void shareCard()
		{
		}

		@Override
		public void exportJson()
		{
		}

		@Override
		public void importJson()
		{
		}

		@Override
		public void setGoal(int target, Long countFrom)
		{
		}

		@Override
		public void restartGoalFrom(long countFrom)
		{
		}

		@Override
		public void togglePause()
		{
		}
	}
}
