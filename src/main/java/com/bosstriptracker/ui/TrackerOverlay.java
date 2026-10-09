package com.bosstriptracker.ui;

import com.bosstriptracker.BossTripTrackerConfig;
import com.bosstriptracker.OverlayRows;
import com.bosstriptracker.OverlayStat;
import com.bosstriptracker.model.LuckTier;
import com.bosstriptracker.model.TripMath;
import com.bosstriptracker.view.DrynessView;
import com.bosstriptracker.view.GoalView;
import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.view.TripView;
import com.bosstriptracker.view.Words;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
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
import lombok.Value;
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
 * font, borders, gaps and standard width, the boss icon scaled to the skill icon's size, up to three rows beside it
 * (each picked in the config) and the goal progress bar underneath. The box only grows wider when a row needs it
 * (the luck status). It shows nothing about the boss or its mechanics, and drawing only formats a few numbers from
 * the latest panel state.
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
	// Row labels, worded like the panel; "kill" becomes "raid" for a raid
	static final String TIME_TO_GOAL = "TTG:";
	static final String TRIP_TIME = "Trip time:";
	static final String PB = "PB:";
	static final String NET_PROFIT = "Net profit:";
	static final String NET_GP_PER_HOUR = "Net GP/hr:";
	static final String LUCK = "Luck:";

	static String killsPerHour(Words words)
	{
		return "kill".equals(words.getUnit()) ? "KPH:" : words.unitsTitle() + "/hr:";
	}

	static String done(Words words)
	{
		return words.unitsTitle() + " done:";
	}

	static String left(Words words)
	{
		return words.unitsTitle() + " left:";
	}

	static String current(Words words)
	{
		return words.unitTitle() + ":";
	}

	static String last(Words words)
	{
		return "Last " + words.getUnit() + ":";
	}

	static String tripKills(Words words)
	{
		return words.unitsTitle() + ":";
	}

	static String average(Words words)
	{
		return "Avg " + words.getUnit() + ":";
	}
	private static final String NOT_AVAILABLE = "N/A";
	/**
	 * LineComponent needs a few pixels between its two sides.
	 */
	private static final int SIDES_GAP = 4;
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
	private DrynessView luckFor;
	private LuckTier luckTier;

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
		OverlayRows settings = OverlayRows.of(config);
		GoalView goal = s.getGoal();
		TripView trip = s.getCurrentTrip();
		List<Row> rows = new ArrayList<>(3);
		for (OverlayStat stat : settings.stats())
		{
			addRow(rows, stat, s, goal, trip, now);
		}
		boolean bar = settings.isEnabled() && goal != null && config.overlayProgressBar();
		if (rows.isEmpty() && !bar)
		{
			return null;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		panelComponent.setPreferredSize(new Dimension(boxWidth(graphics.getFontMetrics(), rows), 0));
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
	private static LayoutableRenderableEntity stack(List<Row> rows)
	{
		LayoutableRenderableEntity stacked = null;
		for (int i = rows.size() - 1; i >= 0; i--)
		{
			LineComponent line = rows.get(i).component();
			stacked = stacked == null ? line : SplitComponent.builder()
				.first(line)
				.second(stacked)
				.orientation(ComponentOrientation.VERTICAL)
				.build();
		}
		return stacked;
	}

	/**
	 * The standard width, or wider when a row wouldn't fit beside the icon (it would wrap otherwise).
	 */
	static int boxWidth(FontMetrics metrics, List<Row> rows)
	{
		int width = ComponentConstants.STANDARD_WIDTH;
		for (Row row : rows)
		{
			int needed = metrics.stringWidth(row.left) + SIDES_GAP + metrics.stringWidth(row.right);
			width = Math.max(width, needed + ComponentConstants.STANDARD_WIDTH - ROW_WIDTH);
		}
		return width;
	}

	/**
	 * Adds the row for a stat, or nothing when what it shows isn't there (no goal, no trip).
	 */
	private void addRow(List<Row> rows, OverlayStat stat, PanelState s, GoalView goal, TripView trip, long now)
	{
		Words words = Words.of(s.getBoss());
		// Time-based numbers are greyed while the clocks are stopped, as in the panel
		Color goalClock = goal != null && goal.isRunning() ? Color.WHITE : MUTED;
		switch (stat)
		{
			case KILLS_PER_HOUR:
				if (goal != null)
				{
					double killsPerHour = goal.killsPerHourAt(now);
					rows.add(line(killsPerHour(words), killsPerHour > 0 ? String.format(Locale.ROOT, "%.1f", killsPerHour) : NOT_AVAILABLE, goalClock));
				}
				break;
			case TIME_TO_GOAL:
				if (goal != null)
				{
					Long toGoal = goal.msToGoalAt(now);
					rows.add(line(TIME_TO_GOAL, goal.getRemaining() == 0 ? "Done" : toGoal != null ? GoalCard.timeToGoal(toGoal)
						: NOT_AVAILABLE, goalClock));
				}
				break;
			case KILLS_DONE:
				if (goal != null)
				{
					rows.add(line(done(words), GoalCard.count(goal.getDone()), Color.WHITE));
				}
				break;
			case KILLS_LEFT:
				if (goal != null)
				{
					rows.add(line(left(words), GoalCard.count(goal.getRemaining()), Color.WHITE));
				}
				break;
			case CURRENT_KILL:
				if (trip != null)
				{
					Long start = s.getKillStartedAt();
					// Counts from the boss spawning, like the game's Fight duration; the last kill's time between kills
					rows.add(start != null ? line(current(words), UiFormat.duration(now - start), Color.WHITE)
						: line(last(words), UiFormat.killTime(trip.getLastKillMs()), MUTED));
				}
				break;
			case TRIP_TIME:
				if (trip != null)
				{
					rows.add(line(TRIP_TIME, tripTime(trip.activeMsAt(now)), s.getPauseText() != null ? MUTED : Color.WHITE));
				}
				break;
			case KILLS:
				if (trip != null)
				{
					rows.add(line(tripKills(words), String.valueOf(trip.getKills()), Color.WHITE));
				}
				break;
			case AVERAGE_KILL:
				if (trip != null)
				{
					rows.add(line(average(words), UiFormat.killTime(trip.getAverageKillMs()), Color.WHITE));
				}
				break;
			case PB:
				if (trip != null)
				{
					rows.add(line(PB, UiFormat.killTime(trip.getFastestKillMs()), Color.WHITE));
				}
				break;
			case NET_PROFIT:
				if (trip != null)
				{
					rows.add(line(NET_PROFIT, UiFormat.gp(trip.getNetProfit()), UiFormat.profitColor(trip.getNetProfit())));
				}
				break;
			case NET_GP_PER_HOUR:
				if (trip != null)
				{
					long rate = TripMath.gpPerHour(trip.getNetProfit(), trip.activeMsAt(now));
					rows.add(line(NET_GP_PER_HOUR, UiFormat.gp(rate), UiFormat.profitColor(rate)));
				}
				break;
			case LUCK:
				// The Luck card's tier, from the same numbers
				LuckTier tier = luckTier(s.getLifetime().getDryness());
				rows.add(tier == null ? line(LUCK, NOT_AVAILABLE, MUTED) : line(LUCK, tier.getLabel(), UiFormat.tierColor(tier)));
				break;
			default:
				break;
		}
	}

	/**
	 * Worked out once per luck view rather than every frame.
	 */
	private LuckTier luckTier(DrynessView dryness)
	{
		if (dryness != luckFor)
		{
			luckFor = dryness;
			luckTier = dryness == null ? null : LuckSummary.of(dryness).getTier();
		}
		return luckTier;
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

	private static Row line(String left, String right, Color rightColor)
	{
		return new Row(left, right, rightColor);
	}

	/**
	 * A label and its value, kept as text so the box can be sized to fit before the components are built.
	 */
	@Value
	static class Row
	{
		String left;
		String right;
		Color rightColor;

		LineComponent component()
		{
			return LineComponent.builder()
				.left(left)
				.right(right)
				.rightColor(rightColor)
				.build();
		}
	}
}
