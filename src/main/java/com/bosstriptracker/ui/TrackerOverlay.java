package com.bosstriptracker.ui;

import com.bosstriptracker.BossTripTrackerConfig;
import com.bosstriptracker.OverlayGoalStat;
import com.bosstriptracker.OverlayLootStat;
import com.bosstriptracker.OverlayTripStat;
import com.bosstriptracker.model.TripMath;
import com.bosstriptracker.view.GoalView;
import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.view.TripView;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.SplitComponent;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * An optional box on the game screen built exactly like RuneLite's XP tracker box (XpInfoBoxOverlay): the same small
 * font, borders, gaps and standard width, the boss icon scaled to the skill icon's size, the rows beside it (goal,
 * trip and profit, each shown or hidden and picked in the config) and the goal progress bar underneath. It shows
 * nothing about the boss or its mechanics, and drawing only formats a few numbers from the latest panel state.
 * <p>
 * The layout and spacing constants follow RuneLite's XpInfoBoxOverlay, Copyright (c) 2018 Jasper Ketelaar and
 * (c) 2020 Anthony (https://github.com/while-loop), BSD-2-Clause; see the RuneLite repository for its licence.
 */
public class TrackerOverlay extends OverlayPanel
{
	/**
	 * RuneLite's skill icons are 25 x 23; the 36 x 32 item icon is scaled to the same height.
	 */
	static final int ICON_WIDTH = 26;
	static final int ICON_HEIGHT = 23;
	// The XP tracker box's spacing
	private static final int BORDER_SIZE = 2;
	private static final int ROWS_AND_BAR_GAP = 2;
	private static final int ROWS_AND_ICON_GAP = 4;
	private static final Rectangle ROWS_AND_ICON_BORDER = new Rectangle(2, 1, 4, 0);
	/**
	 * Room for a row beside the icon: the standard width minus the borders, the icon and the gap.
	 */
	static final int ROW_WIDTH = ComponentConstants.STANDARD_WIDTH - 2 * BORDER_SIZE
		- (ROWS_AND_ICON_BORDER.x + ROWS_AND_ICON_BORDER.width) - ICON_WIDTH - ROWS_AND_ICON_GAP;
	private static final Color BAR_BACKGROUND = new Color(61, 56, 49);
	// Row labels
	static final String KILLS_PER_HOUR = "KC/Hr:";
	static final String TIME_TO_GOAL = "TTG:";
	static final String KC_DONE = "KC Done:";
	static final String KC_LEFT = "KC Left:";
	static final String CURRENT_KILL = "Kill:";
	static final String LAST_KILL = "Last KC:";
	static final String TRIP_TIME = "Trip Time:";
	static final String TRIP_KC = "Trip KC:";
	static final String AVERAGE_KILL = "Avg KC:";
	static final String PB = "PB:";
	static final String NET_PROFIT = "Net Profit:";
	static final String NET_GP_PER_HOUR = "GP/hr:";
	private static final String NOT_AVAILABLE = "N/A";
	private static final long TEN_HOURS_MS = 10 * 3_600_000L;
	private static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR.darker();

	private final BossTripTrackerConfig config;
	private final Supplier<PanelState> state;
	private final IntFunction<BufferedImage> icons;
	private final PanelComponent iconRowsPanel = new PanelComponent();
	/**
	 * Scaled icons by item id. Only used on the client thread, where both drawing and icon loading callbacks run.
	 */
	private final Map<Integer, BufferedImage> scaledIcons = new HashMap<>();

	/**
	 * @param state the latest panel state; read on the client thread, where it is also produced
	 * @param icons item icon by id (the boss icon); may return null
	 */
	public TrackerOverlay(Plugin plugin, BossTripTrackerConfig config, Supplier<PanelState> state,
		IntFunction<BufferedImage> icons)
	{
		super(plugin);
		this.config = config;
		this.state = state;
		this.icons = icons;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setBorder(new Rectangle(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE));
		panelComponent.setGap(new Point(0, ROWS_AND_BAR_GAP));
		iconRowsPanel.setBorder(ROWS_AND_ICON_BORDER);
		iconRowsPanel.setBackgroundColor(null);
	}

	@Override
	public String getName()
	{
		return "BossTripTrackerOverlay";
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		PanelState s = state.get();
		if (s == null || s.getLifetime() == null || s.getBoss() == null)
		{
			return null;
		}
		boolean onTrip = s.getStatus() == PanelState.Status.IN_TRIP || s.getStatus() == PanelState.Status.AFK_PAUSED;
		if (config.overlayOnlyOnTrip() && !onTrip)
		{
			return null;
		}

		long now = System.currentTimeMillis();
		GoalView goal = config.overlayShowGoal() ? s.getGoal() : null;
		TripView trip = s.getCurrentTrip();
		List<LineComponent> rows = new ArrayList<>(3);
		if (goal != null)
		{
			addGoalRow(rows, goal, config.overlayGoalRow(), now);
		}
		if (trip != null && config.overlayShowTrip())
		{
			addTripRow(rows, s, trip, config.overlayTripRow(), now);
		}
		if (trip != null && config.overlayShowLoot())
		{
			addLootRow(rows, trip, config.overlayLootRow(), now);
		}
		boolean bar = goal != null && config.overlayProgressBar();
		if (rows.isEmpty() && !bar)
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		iconRowsPanel.getChildren().clear();
		LayoutableRenderableEntity lines = stack(rows);
		if (lines != null)
		{
			BufferedImage icon = icon(s.getBoss().getIconItemId());
			iconRowsPanel.getChildren().add(icon == null ? lines : SplitComponent.builder()
				.first(new ImageComponent(icon))
				.second(lines)
				.orientation(ComponentOrientation.HORIZONTAL)
				.gap(new Point(ROWS_AND_ICON_GAP, 0))
				.build());
			panelComponent.getChildren().add(iconRowsPanel);
		}
		if (bar)
		{
			panelComponent.getChildren().add(progressBar(goal));
		}
		return super.render(graphics);
	}

	/**
	 * The item icon scaled to the skill icon's size, redone once the icon has loaded.
	 */
	private BufferedImage icon(int itemId)
	{
		BufferedImage scaled = scaledIcons.get(itemId);
		if (scaled == null)
		{
			BufferedImage raw = icons.apply(itemId);
			if (raw == null)
			{
				return null;
			}
			scaled = ImageUtil.resizeImage(raw, ICON_WIDTH, ICON_HEIGHT);
			scaledIcons.put(itemId, scaled);
			if (raw instanceof AsyncBufferedImage)
			{
				((AsyncBufferedImage) raw).onLoaded(() -> scaledIcons.put(itemId, ImageUtil.resizeImage(raw, ICON_WIDTH, ICON_HEIGHT)));
			}
		}
		return scaled;
	}

	/**
	 * Trip time as h:mm:ss; from 10 hours of fighting time on, "10h+" so the row still fits the box.
	 */
	static String tripTime(long ms)
	{
		return ms >= TEN_HOURS_MS ? "10h+" : UiFormat.duration(ms);
	}

	/**
	 * The rows one above the other, beside the icon.
	 */
	private static LayoutableRenderableEntity stack(List<LineComponent> rows)
	{
		LayoutableRenderableEntity stacked = null;
		for (int i = rows.size() - 1; i >= 0; i--)
		{
			stacked = stacked == null ? rows.get(i) : SplitComponent.builder()
				.first(rows.get(i))
				.second(stacked)
				.orientation(ComponentOrientation.VERTICAL)
				.build();
		}
		return stacked;
	}

	private static void addGoalRow(List<LineComponent> rows, GoalView goal, OverlayGoalStat stat, long now)
	{
		// Time-based numbers are greyed while the goal clock is stopped, as in the panel
		Color clock = goal.isRunning() ? Color.WHITE : MUTED;
		switch (stat)
		{
			case KILLS_PER_HOUR:
				double killsPerHour = goal.killsPerHourAt(now);
				rows.add(line(KILLS_PER_HOUR, killsPerHour > 0 ? String.format(Locale.ROOT, "%.1f", killsPerHour) : NOT_AVAILABLE, clock));
				break;
			case TIME_TO_GOAL:
				Long toGoal = goal.msToGoalAt(now);
				rows.add(line(TIME_TO_GOAL, goal.getRemaining() == 0 ? "Done" : toGoal != null ? GoalCard.timeToGoal(toGoal)
					: NOT_AVAILABLE, clock));
				break;
			case KILLS_DONE:
				rows.add(line(KC_DONE, GoalCard.count(goal.getDone()), Color.WHITE));
				break;
			case KILLS_LEFT:
				rows.add(line(KC_LEFT, GoalCard.count(goal.getRemaining()), Color.WHITE));
				break;
			default:
				break;
		}
	}

	private static void addTripRow(List<LineComponent> rows, PanelState s, TripView trip, OverlayTripStat stat, long now)
	{
		switch (stat)
		{
			case CURRENT_KILL:
				Long start = s.getKillStartedAt();
				// Counts from the boss spawning, like the game's Fight duration; the last kill's time between kills
				rows.add(start != null ? line(CURRENT_KILL, UiFormat.duration(now - start), Color.WHITE)
					: line(LAST_KILL, UiFormat.killTime(trip.getLastKillMs()), MUTED));
				break;
			case TRIP_TIME:
				rows.add(line(TRIP_TIME, tripTime(trip.activeMsAt(now)), s.getPauseText() != null ? MUTED : Color.WHITE));
				break;
			case KILLS:
				rows.add(line(TRIP_KC, String.valueOf(trip.getKills()), Color.WHITE));
				break;
			case AVERAGE_KILL:
				rows.add(line(AVERAGE_KILL, UiFormat.killTime(trip.getAverageKillMs()), Color.WHITE));
				break;
			case PB:
				rows.add(line(PB, UiFormat.killTime(trip.getFastestKillMs()), Color.WHITE));
				break;
			default:
				break;
		}
	}

	private static void addLootRow(List<LineComponent> rows, TripView trip, OverlayLootStat stat, long now)
	{
		switch (stat)
		{
			case NET_PROFIT:
				rows.add(line(NET_PROFIT, UiFormat.gp(trip.getNetProfit()), UiFormat.profitColor(trip.getNetProfit())));
				break;
			case NET_GP_PER_HOUR:
				long rate = TripMath.gpPerHour(trip.getNetProfit(), trip.activeMsAt(now));
				rows.add(line(NET_GP_PER_HOUR, UiFormat.gp(rate), UiFormat.profitColor(rate)));
				break;
			default:
				break;
		}
	}

	/**
	 * Like the XP tracker's bar: kills done on the left, the goal on the right and the percentage in the middle.
	 */
	private static ProgressBarComponent progressBar(GoalView goal)
	{
		ProgressBarComponent bar = new ProgressBarComponent();
		bar.setBackgroundColor(BAR_BACKGROUND);
		bar.setForegroundColor(ColorScheme.PROGRESS_COMPLETE_COLOR);
		bar.setLeftLabel(GoalCard.count(goal.getDone()));
		bar.setRightLabel(GoalCard.count(goal.getTarget()));
		bar.setValue(Math.min(100, goal.getDone() * 100.0 / Math.max(1, goal.getTarget())));
		return bar;
	}

	private static LineComponent line(String left, String right, Color rightColor)
	{
		return LineComponent.builder()
			.left(left)
			.right(right)
			.rightColor(rightColor)
			.build();
	}
}
