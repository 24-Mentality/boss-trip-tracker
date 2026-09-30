package com.maggotkingtriptracker.ui;

import static org.junit.Assert.assertTrue;
import com.maggotkingtriptracker.CanvasSection;
import com.maggotkingtriptracker.LuckCardStyle;
import com.maggotkingtriptracker.boss.BossDefinition;
import com.maggotkingtriptracker.boss.DropKind;
import com.maggotkingtriptracker.boss.ExpectedDrop;
import com.maggotkingtriptracker.boss.KillContext;
import com.maggotkingtriptracker.boss.MaggotKingBoss;
import com.maggotkingtriptracker.boss.NightmareBoss;
import com.maggotkingtriptracker.model.TripEndReason;
import com.maggotkingtriptracker.view.BossOption;
import com.maggotkingtriptracker.view.DrynessView;
import com.maggotkingtriptracker.view.GoalView;
import com.maggotkingtriptracker.view.ItemView;
import com.maggotkingtriptracker.view.LifetimeView;
import com.maggotkingtriptracker.view.PanelState;
import com.maggotkingtriptracker.view.PolishView;
import com.maggotkingtriptracker.view.StatView;
import com.maggotkingtriptracker.view.SupplyCategory;
import com.maggotkingtriptracker.view.TripView;
import java.awt.Component;
import java.awt.Graphics2D;
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

	@Test
	public void everyLabelFitsOnAllTabs() throws Exception
	{
		List<String> problems = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			TrackerPanel panel = new TrackerPanel(null, new NoActions(), BOSS);
			panel.update(worstCaseState(PanelState.Status.IN_TRIP, false));
			for (int tab = 0; tab < 3; tab++)
			{
				panel.selectTab(tab);
				check(panel, "tab " + tab, problems);
				if (tab == 0)
				{
					preview(panel);
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
			panel.selectTab(0);
			panel.update(worstCaseState(PanelState.Status.IN_TRIP, false, true).toBuilder()
				.luckCardStyle(LuckCardStyle.CLASSIC)
				.build());
			check(panel, "raid classic luck card", problems);

			// The classic Luck card, hidden by default
			PanelState classic = worstCaseState(PanelState.Status.IN_TRIP, false).toBuilder()
				.luckCardStyle(LuckCardStyle.CLASSIC)
				.build();
			panel.update(classic);
			check(panel, "classic luck card", problems);
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
			.supplies(Collections.<ItemView>emptyList())
			.supplyCategories(Arrays.asList(
				new SupplyCategory("Charges", 99_999_999_000L),
				new SupplyCategory("Runes", 99_999_999_000L),
				new SupplyCategory("Potions", 99_999_999_000L),
				new SupplyCategory("Food", 99_999_999_000L),
				new SupplyCategory("Other", 99_999_999_000L)))
			.allTimeLoot(Collections.<ItemView>emptyList())
			.allTimeLootValue(123_456_789_000L)
			.allTimeSince(1_785_447_588_633L)
			.build();

		// 1 kill per hour for 12,000 hours: KPH and the time to goal are at their longest
		GoalView goal = new GoalView(99_999, 12_345, 12_345L * 3600 * 1000, System.currentTimeMillis(), !paused);
		List<BossOption> bosses = Collections.singletonList(
			new BossOption(BOSS.getId(), BOSS.getDisplayName(), BOSS.getIconItemId(), true));
		return PanelState.builder()
			.boss(BOSS)
			.bosses(bosses)
			.status(status)
			.currentTrip(trip)
			.history(Collections.singletonList(trip))
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
		public void setGoal(int target)
		{
		}

		@Override
		public void resetGoal()
		{
		}

		@Override
		public void togglePause()
		{
		}
	}
}
