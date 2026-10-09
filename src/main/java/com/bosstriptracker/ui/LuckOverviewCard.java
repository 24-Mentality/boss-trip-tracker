package com.bosstriptracker.ui;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.view.DrynessView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * The share card's luck section as a panel card: tier, uniques received vs expected, kills since the last unique,
 * how far past the rate you are, the rate, and a count for each unique and the pet. The eye icon collapses it to
 * the title row, which keeps the tier. Right-click to enter the kill count of a unique from before tracking.
 */
class LuckOverviewCard extends JPanel
{
	private static final Color HEADER_BORDER = new Color(57, 57, 57);
	private static final int ICON_WIDTH = 27;
	private static final int ICON_HEIGHT = 24;
	/**
	 * Uniques plus the pet that fit on one row beside the source note.
	 */
	private static final int MAX_IN_ROW = 4;

	private final ItemManager itemManager;
	private final JLabel title = new JLabel("Luck Status:");
	private final JLabel tier = new JLabel();
	private final JLabel source = new JLabel();
	private final JLabel uniques = statLabel();
	private final JLabel since = statLabel();
	private final JLabel due = statLabel();
	private final JLabel rate = statLabel();
	private final JLabel longest = statLabel();
	private final JLabel lastUnique = statLabel();
	private final JLabel teamDry = statLabel();
	private final JPanel stats = new JPanel(new GridLayout(4, 1, 0, 0));
	private final JPanel drops = new JPanel();
	private final JPanel dropRow = new JPanel();
	private final List<JLabel> counts = new ArrayList<>();
	private final JPanel body = new JPanel(new BorderLayout(0, 4));
	private final JLabel eye = new JLabel();
	/**
	 * Shown when the dry streak can't be placed: no tracked unique and no kill count entered.
	 */
	private final JLabel setKcLink = new JLabel("Dry streak off? Set your last unique's KC");
	private final SectionStates states;
	private BossDefinition boss;
	/**
	 * Collapsed with the eye icon; saved in the settings.
	 */
	private boolean hidden;
	private static final String STATE_KEY = "trip.luck";

	/**
	 * @param onSetLastUniqueKc asks for the kill count of your last unique from before tracking (or clears it)
	 */
	LuckOverviewCard(ItemManager itemManager, SectionStates states, Runnable onSetLastUniqueKc)
	{
		this.states = states;
		this.hidden = !states.isOpen(STATE_KEY, true);
		this.itemManager = itemManager;
		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(HEADER_BORDER, 1),
			BorderFactory.createEmptyBorder(3, 6, 5, 6)));

		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tier.setFont(FontManager.getRunescapeBoldFont());
		source.setFont(FontManager.getRunescapeSmallFont());
		source.setForeground(UiFormat.MUTED_TEXT);

		JPanel titleLeft = new JPanel(new BorderLayout(6, 0));
		titleLeft.setOpaque(false);
		titleLeft.add(title, BorderLayout.WEST);
		titleLeft.add(tier, BorderLayout.CENTER);
		// Two rows of two as on the share card. The sidebar is too narrow for its columns ("Since last unique" alone
		// takes over half the width), so each row sizes its cells to their contents
		stats.setOpaque(false);
		stats.add(row(uniques, due));
		stats.add(row(since, rate));
		stats.add(longest);
		stats.add(lastUnique);

		// Laid out in buildDrops as a box or a grid, never a flow: a flow would silently wrap a count out of sight
		drops.setOpaque(false);
		// The source note moves down beside the icons: next to the tier it would push LUCKY AS RUCK out
		dropRow.setLayout(new BorderLayout(4, 0));
		dropRow.setOpaque(false);
		dropRow.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		dropRow.add(drops, BorderLayout.CENTER);
		dropRow.add(source, BorderLayout.EAST);

		setKcLink.setFont(FontManager.getRunescapeSmallFont());
		setKcLink.setForeground(ColorScheme.BRAND_ORANGE);
		setKcLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setKcLink.setToolTipText(UiFormat.tooltip("Your dry streak counts from when you installed the plugin until it"
			+ " knows the kill count of your last unique. Click to enter it."));
		setKcLink.addMouseListener(clickTo(onSetLastUniqueKc));
		setKcLink.setVisible(false);
		// The Last Unique row opens the same prompt, to change or clear it
		lastUnique.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		lastUnique.addMouseListener(clickTo(onSetLastUniqueKc));

		JPanel bottom = new JPanel(new BorderLayout());
		bottom.setOpaque(false);
		bottom.add(dropRow, BorderLayout.CENTER);
		bottom.add(setKcLink, BorderLayout.SOUTH);

		body.setOpaque(false);
		body.add(stats, BorderLayout.NORTH);
		body.add(bottom, BorderLayout.CENTER);

		eye.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		eye.setHorizontalAlignment(SwingConstants.RIGHT);
		eye.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					hidden = !hidden;
					states.setOpen(STATE_KEY, !hidden);
					applyVisibility();
				}
			}
		});
		// The tier stays in the title row, so it's still visible when the card is collapsed
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(titleLeft, BorderLayout.CENTER);
		titleRow.add(eye, BorderLayout.EAST);

		add(titleRow, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);

		applyVisibility();
	}

	private static MouseAdapter clickTo(Runnable action)
	{
		return new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					action.run();
				}
			}
		};
	}

	private void applyVisibility()
	{
		body.setVisible(!hidden);
		eye.setIcon(new EyeIcon(hidden));
		eye.setToolTipText(hidden ? "Show luck" : "Hide luck");
		revalidate();
		repaint();
	}

	private static JPanel row(JLabel left, JLabel right)
	{
		JPanel row = new JPanel(new FitRowLayout(4));
		row.setOpaque(false);
		row.add(left);
		row.add(right);
		return row;
	}

	private static JLabel statLabel()
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	void update(DrynessView dryness, BossDefinition boss)
	{
		LuckSummary luck = LuckSummary.of(dryness);
		List<DrynessView.Drop> shown = new ArrayList<>(luck.getUniques());
		DrynessView.Drop pet = LuckSummary.pet(dryness);
		if (pet != null)
		{
			shown.add(pet);
		}
		if (boss != this.boss)
		{
			this.boss = boss;
			buildDrops(shown);
		}

		if (luck.getTier() == null)
		{
			tier.setText("");
		}
		else
		{
			tier.setText(luck.getTier().getLabel());
			tier.setForeground(UiFormat.tierColor(luck.getTier()));
		}
		// On the title too, so hovering "Luck Status:" explains the tiers
		String help = luck.getTier() == null ? UiFormat.tooltip("No tier until there are kills to compare.") : luck.tierHelp();
		tier.setToolTipText(help);
		title.setToolTipText(help);
		source.setText((luck.isAllTime() ? "All-time" : "Tracked") + (luck.isApproximate() ? " (approx.)" : ""));
		source.setToolTipText(UiFormat.tooltip(luck.isAllTime()
			? String.format(Locale.ROOT, "From RuneLite's Loot Tracker record: %,d kills.", luck.getBasisKills())
			: String.format(Locale.ROOT, "From the %,d %s this plugin has tracked.", luck.getBasisKills(), boss.getLuckKillsName())));

		String expected = String.format(Locale.ROOT, luck.getExpected() >= 10 ? "%.1f" : "%.2f", luck.getExpected());
		set(uniques, "Uniques", luck.getReceived() + " / " + expected, null, "Uniques received vs expected.");
		set(since, "Dry streak", String.format(Locale.ROOT, "%,d kc", dryness.getKillsSinceUnique()), null,
			(dryness.isSinceWholeKillCount()
				? "You haven't had a unique yet (RuneLite's Loot Tracker has none either), so this is your whole kill count"
				: dryness.isSinceFromGameCount()
				? "From the game's own dry streak count, which also counts Entry Mode raids"
				: "Kills since your last unique" + (dryness.isSinceFromEnteredKc() && dryness.getLastUniqueKc() != null
				? String.format(Locale.ROOT, " (KC %,d, as you entered it)", dryness.getLastUniqueKc()) : "")
				+ (dryness.getLastUniqueKc() != null ? ", by your all-time kill count, so kills done without the plugin"
				+ " count too" : ""))
				+ ". Click Last Unique to set the kill count of your last unique.");
		set(longest, "Worst dry streak", String.format(Locale.ROOT, "%,d kc", dryness.getLongestDryStreak()), null,
			"The longest gap between two of your uniques, by kill count, counting the uniques this plugin tracked and"
				+ " the kill count you entered for your last unique from before tracking (or the current streak, if"
				+ " that is longer). When RuneLite's Loot Tracker has no other uniques, the kills before your first"
				+ " one count too; otherwise earlier uniques aren't known.");
		set(lastUnique, "Last Unique", dryness.getLastUniqueKc() != null
				? String.format(Locale.ROOT, "%,d", dryness.getLastUniqueKc())
				: dryness.isSinceWholeKillCount() ? "None yet" : "Unknown", null,
			dryness.getLastUniqueKc() != null
				? "The kill count of your last unique" + (dryness.isSinceFromEnteredKc() ? ", as you entered it" : "")
				+ ". Click to change or clear it."
				: "Click to set the kill count of your last unique from before you installed the plugin.");
		setKcLink.setVisible(dryness.getLastUniqueKc() == null && !dryness.isSinceWholeKillCount()
			&& !dryness.isSinceFromGameCount());
		// Theatre of Blood only: a row of its own, shown when the boss has a team dry streak
		boolean team = dryness.getTeamDryStreak() != null;
		if (team != (teamDry.getParent() == stats))
		{
			if (team)
			{
				stats.add(teamDry);
			}
			else
			{
				stats.remove(teamDry);
			}
			stats.setLayout(new GridLayout(team ? 5 : 4, 1, 0, 0));
			stats.revalidate();
		}
		if (team)
		{
			set(teamDry, "Team dry streak", String.format(Locale.ROOT, "%,d kc", dryness.getTeamDryStreak()), null,
				UiFormat.teamDryStreakHelp(dryness.isTeamDryStreakFromGame()));
		}
		int dry = dryness.getKillsSinceUnique();
		set(due, "Vs rate", String.format(Locale.ROOT, "%.1f\u00d7", luck.dryVsRate(dry)), null,
			String.format(Locale.ROOT, "Your dry streak is %.1f times the drop rate (1/%s). %.0f%% of players would have"
				+ " had a unique within this many kills; each kill is still the same chance.", luck.dryVsRate(dry),
				UiFormat.oneIn(luck.getRate()), luck.chanceByNow(dry) * 100));
		set(rate, "Rate", "1/" + UiFormat.oneIn(luck.getRate()), null, "Chance of any unique per kill"
			+ (luck.isAllTime() ? ", averaged over the all-time record." : ", averaged over the tracked kills.")
			+ (luck.isApproximate() ? " Approximate: an equal share of the team's chance." : ""));

		for (int i = 0; i < counts.size() && i < shown.size(); i++)
		{
			DrynessView.Drop drop = shown.get(i);
			JLabel count = counts.get(i);
			count.setText("x" + drop.getReceived());
			count.setForeground(drop.getReceived() > 0 ? UiFormat.UNIQUE_BORDER : UiFormat.MUTED_TEXT);
			count.setToolTipText(UiFormat.tooltip(drop.getName() + ": " + drop.getReceived() + " received, "
				+ String.format(Locale.ROOT, "%.2f", drop.getExpected()) + " expected ("
				+ (pet != null && drop.getItemId() == pet.getItemId() && dryness.getPetRateNote() != null
				? dryness.getPetRateNote() : "1/" + UiFormat.oneIn(drop.getRate())) + ")."));
		}
	}

	private void buildDrops(List<DrynessView.Drop> shown)
	{
		drops.removeAll();
		counts.clear();
		// A row for a few uniques (the Maggot King); a grid of three across for many (the Nightmare)
		boolean grid = shown.size() > MAX_IN_ROW;
		drops.setLayout(grid ? new GridLayout(0, 3, 0, 2) : new BoxLayout(drops, BoxLayout.X_AXIS));
		// Beside a row of icons the source note fits; under a grid it gets its own line so the grid has the width
		dropRow.remove(source);
		source.setHorizontalAlignment(grid ? SwingConstants.RIGHT : SwingConstants.LEADING);
		dropRow.add(source, grid ? BorderLayout.SOUTH : BorderLayout.EAST);
		for (DrynessView.Drop drop : shown)
		{
			JLabel icon = new JLabel();
			icon.setToolTipText(drop.getName());
			if (itemManager != null)
			{
				AsyncBufferedImage image = itemManager.getImage(drop.getItemId());
				Runnable scaled = () -> icon.setIcon(new ImageIcon(ImageUtil.resizeImage(image, ICON_WIDTH, ICON_HEIGHT)));
				image.onLoaded(scaled);
				scaled.run();
			}
			else
			{
				icon.setPreferredSize(new Dimension(ICON_WIDTH, ICON_HEIGHT));
			}

			JLabel count = new JLabel();
			count.setFont(FontManager.getRunescapeBoldFont());
			count.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 7));
			counts.add(count);
			if (grid)
			{
				JPanel cell = new JPanel(new BorderLayout());
				cell.setOpaque(false);
				cell.add(icon, BorderLayout.WEST);
				cell.add(count, BorderLayout.CENTER);
				drops.add(cell);
			}
			else
			{
				drops.add(icon);
				drops.add(count);
			}
		}
		// New icon and count labels need the right-click menu too
		drops.revalidate();
	}

	private static void set(JLabel label, String name, String value, Color color, String help)
	{
		label.setText(UiFormat.pair(name, value, color));
		label.setToolTipText(UiFormat.tooltip(help));
	}
}
