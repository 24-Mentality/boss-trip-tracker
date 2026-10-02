package com.bosstriptracker.ui;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.view.GoalView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Kill goal progress in the style of RuneLite's XP tracker: the boss icon, KPH / Kills Done / TTG /
 * Kills Left, and a progress bar with the percentage.
 */
class GoalCard extends JPanel
{
	private static final Color BAR_BACKGROUND = new Color(61, 56, 49);
	private static final String NOT_AVAILABLE = "N/A";

	private final JLabel kph = new JLabel();
	private final JLabel done = new JLabel();
	private final JLabel ttg = new JLabel();
	private final JLabel left = new JLabel();
	private final ProgressBar progress = new ProgressBar();
	private final JButton resetButton = smallButton("Reset");
	private final JButton pauseButton = smallButton("Pause");
	/**
	 * The goal clock isn't running (outside the lair, paused or idle); time-based stats are greyed.
	 */
	private boolean paused;

	private GoalView goal;
	private final ItemManager itemManager;
	private final JLabel icon = new JLabel();
	private int iconItemId = -1;

	GoalCard(ItemManager itemManager, Runnable onSetGoal, Runnable onPause, Runnable onReset)
	{
		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		this.itemManager = itemManager;
		icon.setPreferredSize(new Dimension(30, 28));
		icon.setHorizontalAlignment(SwingConstants.CENTER);

		JPanel stats = new JPanel(new GridLayout(2, 2, 3, 0));
		stats.setOpaque(false);
		for (JLabel label : new JLabel[]{kph, done, ttg, left})
		{
			label.setFont(FontManager.getRunescapeSmallFont());
			stats.add(label);
		}

		// The icon sits at the top left beside the stats, as in RuneLite's XP tracker
		JPanel top = new JPanel(new BorderLayout(3, 0));
		top.setOpaque(false);
		top.add(icon, BorderLayout.WEST);
		top.add(stats, BorderLayout.CENTER);

		progress.setBackground(BAR_BACKGROUND);
		progress.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		progress.setPreferredSize(new Dimension(0, 16));
		progress.setLeftLabel("");
		progress.setRightLabel("");

		JButton setButton = smallButton("Set goal");
		setButton.addActionListener(e -> onSetGoal.run());
		pauseButton.addActionListener(e -> onPause.run());
		pauseButton.setToolTipText(UiFormat.tooltip("Stop the trip and goal clocks now, before the automatic idle pause."
			+ " Kills, loot and supplies still count. Resumes when you press it again or attack the boss. The clocks only"
			+ " run in the lair while you're fighting."));
		resetButton.addActionListener(e -> onReset.run());
		// Equal widths: a grid, not a row of natural-width buttons
		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		buttons.setOpaque(false);
		buttons.add(setButton);
		buttons.add(pauseButton);
		buttons.add(resetButton);

		add(top, BorderLayout.NORTH);
		add(progress, BorderLayout.CENTER);
		add(buttons, BorderLayout.SOUTH);
	}

	/**
	 * Shows the boss's icon beside the stats.
	 */
	void setBoss(BossDefinition boss)
	{
		int itemId = boss.getIconItemId();
		if (itemId == iconItemId || itemManager == null)
		{
			return;
		}
		iconItemId = itemId;
		AsyncBufferedImage image = itemManager.getImage(itemId);
		Runnable scaled = () ->
		{
			if (iconItemId == itemId)
			{
				icon.setIcon(new ImageIcon(ImageUtil.resizeImage(image, 30, 27)));
			}
		};
		image.onLoaded(scaled);
		scaled.run();
	}

	private static JButton smallButton(String text)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setMargin(new Insets(0, 4, 0, 4));
		button.setFocusPainted(false);
		return button;
	}

	/**
	 * @param pausedInLair the button resumes rather than pauses
	 * @param canPause only in the lair on an open trip
	 */
	void setPauseState(boolean pausedInLair, boolean canPause)
	{
		pauseButton.setText(pausedInLair ? "Resume" : "Pause");
		pauseButton.setEnabled(canPause);
	}

	void setGoal(GoalView goal, long now)
	{
		this.goal = goal;
		paused = goal == null || !goal.isRunning();
		resetButton.setEnabled(goal != null);
		if (goal == null)
		{
			setStats(NOT_AVAILABLE, NOT_AVAILABLE, NOT_AVAILABLE, NOT_AVAILABLE);
			progress.setMaximumValue(1);
			progress.setValue(0);
			progress.setCenterLabel("No goal set");
			setToolTipText(null);
			return;
		}
		tick(now);
	}

	void tick(long now)
	{
		if (goal == null)
		{
			return;
		}

		int doneKills = goal.getDone();
		int target = goal.getTarget();
		int remaining = goal.getRemaining();
		long activeMs = goal.activeMsAt(now);
		double killsPerHour = goal.killsPerHourAt(now);
		Long toGoal = goal.msToGoalAt(now);

		setStats(
			killsPerHour > 0 ? String.format(Locale.ROOT, "%.1f", killsPerHour) : NOT_AVAILABLE,
			count(doneKills),
			remaining == 0 ? "Done" : toGoal != null ? timeToGoal(toGoal) : NOT_AVAILABLE,
			count(remaining));

		progress.setMaximumValue(Math.max(1, target));
		progress.setValue(Math.min(doneKills, target));
		double percent = Math.min(100, doneKills * 100.0 / Math.max(1, target));
		progress.setCenterLabel(String.format(Locale.ROOT, "%.1f%%", percent));
		UiFormat.setToolTip(this, "Goal: " + String.format(Locale.ROOT, "%,d", target) + " kills · " + UiFormat.duration(activeMs)
			+ " of fighting time" + (paused ? " · clock stopped" : "") + " · counting since " + UiFormat.dateTime(goal.getStartedAt()));
	}

	/**
	 * Exact up to 9,999, then compact ("12.3K") so the stat still fits beside the icon.
	 */
	static String count(int n)
	{
		return n >= 10_000 ? String.format(Locale.ROOT, "%.1fK", n / 1000.0) : String.format(Locale.ROOT, "%,d", n);
	}

	static String timeToGoal(long ms)
	{
		return ms >= 100L * 3_600_000 ? "100h+" : UiFormat.duration(ms);
	}

	private void setStats(String kphValue, String doneValue, String ttgValue, String leftValue)
	{
		// Time-based values are greyed while paused
		Color clock = paused ? UiFormat.MUTED_TEXT : null;
		UiFormat.setText(kph, UiFormat.pair("KPH", kphValue, clock));
		UiFormat.setText(done, UiFormat.pair("Kills Done", doneValue));
		UiFormat.setText(ttg, UiFormat.pair("TTG", ttgValue, clock));
		UiFormat.setText(left, UiFormat.pair("Kills Left", leftValue));
	}
}
