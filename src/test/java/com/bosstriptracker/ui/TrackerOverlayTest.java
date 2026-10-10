package com.bosstriptracker.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.BossTripTrackerConfig;
import com.bosstriptracker.OverlayOptionalStat;
import com.bosstriptracker.OverlayStat;
import com.bosstriptracker.boss.MaggotKingBoss;
import com.bosstriptracker.view.GoalView;
import com.bosstriptracker.view.LifetimeView;
import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.view.StatView;
import com.bosstriptracker.view.TripView;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import javax.imageio.ImageIO;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import com.bosstriptracker.boss.TheatreOfBloodBoss;
import com.bosstriptracker.view.Words;
import org.junit.Test;

public class TrackerOverlayTest
{
	private boolean enabled;
	private boolean onlyOnTrip = true;
	private boolean bar = true;
	private OverlayStat row1 = OverlayStat.KILLS_PER_HOUR;
	private OverlayOptionalStat row2 = OverlayOptionalStat.CURRENT_KILL;
	private OverlayOptionalStat row3 = OverlayOptionalStat.NET_PROFIT;

	private final BossTripTrackerConfig config = new BossTripTrackerConfig()
	{
		@Override
		public boolean overlayEnabled()
		{
			return enabled;
		}

		@Override
		public boolean overlayOnlyOnTrip()
		{
			return onlyOnTrip;
		}

		@Override
		public boolean overlayProgressBar()
		{
			return bar;
		}

		@Override
		public OverlayStat overlayRow1()
		{
			return row1;
		}

		@Override
		public OverlayOptionalStat overlayRow2()
		{
			return row2;
		}

		@Override
		public OverlayOptionalStat overlayRow3()
		{
			return row3;
		}

		@Override
		public void setSelectedBoss(String bossId)
		{
		}

		@Override
		public void setSectionStates(String states)
		{
		}
	};

	@Test
	public void offByDefaultAndOnlyDuringATrip()
	{
		assertNull(render(state(PanelState.Status.IN_TRIP, true)));
		enabled = true;
		assertNotNull(render(state(PanelState.Status.IN_TRIP, true)));
		assertNull(render(state(PanelState.Status.IDLE, true)));
		onlyOnTrip = false;
		assertNotNull(render(state(PanelState.Status.IDLE, true)));
	}

	@Test
	public void boxOnlyAppearsWithSomethingToShow()
	{
		enabled = true;
		bar = false;
		row2 = OverlayOptionalStat.NOTHING;
		row3 = OverlayOptionalStat.NOTHING;
		// Just the goal row
		assertNotNull(render(state(PanelState.Status.IN_TRIP, true)));
		// Without a goal the goal row and bar are hidden, and nothing is left
		assertNull(render(state(PanelState.Status.IN_TRIP, false)));
		// The progress bar alone is enough
		bar = true;
		assertNotNull(render(state(PanelState.Status.IN_TRIP, true)));
		// A trip row shows without a goal
		row2 = OverlayOptionalStat.TRIP_TIME;
		assertNotNull(render(state(PanelState.Status.IN_TRIP, false)));
	}

	@Test
	public void boxWidensOnlyForTheLuckRow()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
		java.awt.FontMetrics metrics = g.getFontMetrics(FontManager.getRunescapeSmallFont());
		TrackerOverlay.Row kph = new TrackerOverlay.Row(TrackerOverlay.killsPerHour(Words.KILLS), "99.9", Color.WHITE);
		assertEquals(ComponentConstants.STANDARD_WIDTH, TrackerOverlay.boxWidth(metrics, Collections.singletonList(kph)));
		TrackerOverlay.Row luck = new TrackerOverlay.Row(TrackerOverlay.LUCK, "LUCKY AS RUCK", Color.WHITE);
		int wider = TrackerOverlay.boxWidth(metrics, java.util.Arrays.asList(kph, luck));
		assertTrue(wider > ComponentConstants.STANDARD_WIDTH);
		// Everything in the wider box still fits beside the icon
		int room = wider - (ComponentConstants.STANDARD_WIDTH - TrackerOverlay.ROW_WIDTH);
		assertTrue(metrics.stringWidth(luck.getLeft()) + 4 + metrics.stringWidth(luck.getRight()) <= room);
		g.dispose();
	}

	@Test
	public void longestRowsFitBesideTheIcon()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
		java.awt.FontMetrics metrics = g.getFontMetrics(FontManager.getRunescapeSmallFont());
		// Worst cases: big goals, long trips, 10-minute kills, billions of gp
		for (Words words : new Words[]{Words.of(new MaggotKingBoss()), Words.of(new TheatreOfBloodBoss())})
		{
			assertRowsFit(metrics, new String[][]{
				{TrackerOverlay.killsPerHour(words), "99.9"},
				{TrackerOverlay.TIME_TO_GOAL, "100h+"},
				{TrackerOverlay.KC_DONE, "12.3K"},
				{TrackerOverlay.KC_LEFT, "87.7K"},
				{TrackerOverlay.CURRENT_KC, "9:59"},
				{TrackerOverlay.LAST_KC, "9:59.9"},
				{TrackerOverlay.TRIP_TIME, TrackerOverlay.tripTime(9 * 3_600_000L + 59 * 60_000L + 59_000L)},
				{TrackerOverlay.TRIP_TIME, TrackerOverlay.tripTime(12 * 3_600_000L)},
				{TrackerOverlay.TRIP_KC, "999"},
				{TrackerOverlay.AVERAGE_KC, "9:59.9"},
				{TrackerOverlay.PB, "9:59.9"},
				{TrackerOverlay.NET_PROFIT, "-12.4B"},
				{TrackerOverlay.NET_GP_PER_HOUR, "-985M"},
				{TrackerOverlay.TODAY_KC, "999"},
				{TrackerOverlay.WEEK_KC, "9,999"},
				{TrackerOverlay.MONTH_KC, "9,999"},
			});
		}
		g.dispose();
	}

	private static void assertRowsFit(java.awt.FontMetrics metrics, String[][] rows)
	{
		for (String[] row : rows)
		{
			// LineComponent needs a few pixels between the two sides
			int needed = metrics.stringWidth(row[0]) + 4 + metrics.stringWidth(row[1]);
			assertTrue(row[0] + " " + row[1] + " needs " + needed + "px, has " + TrackerOverlay.ROW_WIDTH,
				needed <= TrackerOverlay.ROW_WIDTH);
		}
	}

	@Test
	public void preview() throws Exception
	{
		enabled = true;
		BufferedImage image = new BufferedImage(340, 110, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(70, 90, 60));
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.setFont(FontManager.getRunescapeFont());
		// All three rows on the left (KPH, current kill, net profit, bar); goal and trip only (TTG, trip time) in the
		// middle, the size of RuneLite's XP tracker box
		draw(g, 10, 10);
		row1 = OverlayStat.TIME_TO_GOAL;
		row2 = OverlayOptionalStat.TRIP_TIME;
		row3 = OverlayOptionalStat.NOTHING;
		draw(g, 160, 10);
		g.dispose();
		ImageIO.write(image, "PNG", new File("build/overlay-preview.png"));
	}

	private void draw(Graphics2D g, int x, int y)
	{
		TrackerOverlay overlay = overlay(state(PanelState.Status.IN_TRIP, true));
		// RuneLite's overlay renderer normally supplies the font and the translucent background
		overlay.getPanelComponent().setBackgroundColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
		Graphics2D at = (Graphics2D) g.create();
		at.translate(x, y);
		// PanelComponent sizes itself from the previous frame's layout, so draw a first frame off-screen
		Graphics2D scratch = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
		overlay.render(scratch);
		scratch.dispose();
		overlay.render(at);
		at.dispose();
	}

	private Dimension render(PanelState state)
	{
		Graphics2D g = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB).createGraphics();
		g.setFont(FontManager.getRunescapeFont());
		Dimension size = overlay(state).render(g);
		g.dispose();
		return size;
	}

	private TrackerOverlay overlay(PanelState state)
	{
		return new TrackerOverlay(null, config, () -> state, TrackerOverlayTest::placeholderIcon);
	}

	private static BufferedImage placeholderIcon(int itemId)
	{
		BufferedImage icon = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setColor(new Color(150, 100, 70));
		g.fillOval(6, 1, 24, 30);
		g.dispose();
		return icon;
	}

	private static PanelState state(PanelState.Status status, boolean withGoal)
	{
		long now = System.currentTimeMillis();
		TripView trip = TripView.builder()
			.id("t")
			.startedAt(now - 3_600_000)
			.activeMs(2_985_000)
			.kills(21)
			.bossStat(new StatView("Stom / Eggs", "21 / 0", null))
			.netProfit(-1_240_000)
			.averageKillMs(131_200L)
			.fastestKillMs(104_400L)
			.lastKillMs(122_000L)
			.loot(Collections.emptyList())
			.supplies(Collections.emptyList())
			.dropped(Collections.emptyList())
			.supplyCategories(Collections.emptyList())
			.build();
		LifetimeView lifetime = LifetimeView.builder().netPerTrip(Collections.<Long>emptyList()).build();
		return PanelState.builder()
			.boss(new MaggotKingBoss())
			.status(status)
			.currentTrip(trip)
			.lifetime(lifetime)
			.goal(withGoal ? new GoalView(392, 163, 24_247_000, now, true, now - 86_400_000L) : null)
			.killStartedAt(now - 73_000)
			.build();
	}
}
