package com.maggotkingtriptracker.ui;

import com.maggotkingtriptracker.boss.BossDefinition;
import com.maggotkingtriptracker.model.DropOdds;
import com.maggotkingtriptracker.model.LuckTier;
import com.maggotkingtriptracker.view.DrynessView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.ProgressBar;

/**
 * Luck on the shown boss's uniques: received vs expected, how dry you are, and when the next unique is due at the
 * drop rate. Right-click to enter the kill count of a unique from before tracking began.
 */
class LuckCard extends JPanel
{
	private static final Color HEADER_BORDER = new Color(57, 57, 57);
	private static final Color BAR_BACKGROUND = new Color(61, 56, 49);

	private final JLabel title = new JLabel("Luck Status:");
	private final JLabel uniques = statLabel();
	private final JLabel tier = new JLabel();
	private final JLabel dry = statLabel();
	private final JLabel byNow = statLabel();
	private final JLabel next = statLabel();
	private final JLabel odds = statLabel();
	/**
	 * Theatre of Blood only.
	 */
	private final JLabel teamDry = statLabel();
	private final ProgressBar progress = new ProgressBar();
	private final JPanel body = new JPanel(new BorderLayout(0, 3));
	private final JLabel eye = new JLabel();
	private final JMenuItem clearKc = new JMenuItem("Clear KC of my last unique");
	private boolean hidden;

	/**
	 * @param onSetLastUniqueKc asks for the kill count of your last unique from before tracking
	 */
	LuckCard(Runnable onSetLastUniqueKc, Runnable onClearLastUniqueKc)
	{
		setLayout(new BorderLayout(0, 1));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(HEADER_BORDER, 1),
			BorderFactory.createEmptyBorder(3, 6, 5, 6)));

		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

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
					applyVisibility();
				}
			}
		});

		// The tier sits beside the title so it stays visible when the card is collapsed
		tier.setFont(FontManager.getRunescapeBoldFont());
		JPanel titleLeft = new JPanel(new BorderLayout(6, 0));
		titleLeft.setOpaque(false);
		titleLeft.add(title, BorderLayout.WEST);
		titleLeft.add(tier, BorderLayout.CENTER);

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(titleLeft, BorderLayout.CENTER);
		titleRow.add(eye, BorderLayout.EAST);

		JPanel stats = new JPanel(new GridLayout(2, 2, 4, 0));
		stats.setOpaque(false);
		for (JLabel label : new JLabel[]{uniques, dry, byNow, next})
		{
			stats.add(label);
		}

		JPanel statRows = new JPanel(new BorderLayout());
		statRows.setOpaque(false);
		statRows.add(stats, BorderLayout.NORTH);
		statRows.add(odds, BorderLayout.CENTER);
		statRows.add(teamDry, BorderLayout.SOUTH);

		progress.setBackground(BAR_BACKGROUND);
		progress.setForeground(ColorScheme.BRAND_ORANGE);
		progress.setPreferredSize(new Dimension(0, 14));
		progress.setLeftLabel("");
		progress.setRightLabel("");

		body.setOpaque(false);
		body.add(statRows, BorderLayout.NORTH);
		body.add(progress, BorderLayout.CENTER);

		add(titleRow, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);

		JPopupMenu menu = new JPopupMenu();
		JMenuItem setKc = new JMenuItem("Set KC of my last unique...");
		setKc.addActionListener(e -> onSetLastUniqueKc.run());
		clearKc.addActionListener(e -> onClearLastUniqueKc.run());
		menu.add(setKc);
		menu.add(clearKc);
		setComponentPopupMenu(menu);
		inheritPopupMenu(this);
		applyVisibility();
	}

	/**
	 * Children with tooltips would otherwise swallow the right-click.
	 */
	static void inheritPopupMenu(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JComponent)
			{
				((JComponent) child).setInheritsPopupMenu(true);
			}
			if (child instanceof Container)
			{
				inheritPopupMenu((Container) child);
			}
		}
	}

	private static JLabel statLabel()
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private void applyVisibility()
	{
		body.setVisible(!hidden);
		eye.setIcon(new EyeIcon(hidden));
		eye.setToolTipText(hidden ? "Show luck" : "Hide luck");
		revalidate();
		repaint();
	}

	void update(DrynessView dryness, BossDefinition boss)
	{
		double rate = dryness.getAnyUniqueRate();
		String oneIn = UiFormat.oneIn(rate);
		String luckKills = boss.getLuckKillsName();
		String note = boss.getLuckNote().isEmpty() ? "" : " (" + lowerFirst(boss.getLuckNote().replaceAll("\\.$", "")) + ")";
		title.setToolTipText(UiFormat.tooltip("Based on the " + luckKills + " this plugin has tracked" + note + ", or"
			+ " RuneLite's Loot Tracker when it has a record. The in-game collection log can't be read unless it's"
			+ " open. Right-click to enter the kill count of your last unique from before tracking began."));

		LuckSummary luck = LuckSummary.of(dryness);
		int received = luck.getReceived();
		double expected = luck.getExpected();
		int basisKills = luck.getBasisKills();
		int since = dryness.getKillsSinceUnique();
		Integer currentKc = dryness.getCurrentKc();
		Integer lastKc = dryness.getLastUniqueKc();

		StringBuilder breakdown = new StringBuilder();
		for (DrynessView.Drop unique : luck.getUniques())
		{
			breakdown.append('\n').append(unique.getName()).append(": ").append(unique.getReceived())
				.append(" (").append(String.format(Locale.ROOT, "%.2f", unique.getExpected())).append(" expected, 1/")
				.append(UiFormat.oneIn(unique.getRate())).append(')');
		}
		String basis = luck.isAllTime()
			? String.format(Locale.ROOT, "%,d kills recorded by RuneLite's Loot Tracker (all-time)", basisKills)
			: basisKills + " " + luckKills + " tracked by this plugin";
		// One decimal once expected reaches 10, so large counts still fit half the card
		set(uniques, "Uniques", received + " / " + String.format(Locale.ROOT, expected >= 10 ? "%.1f" : "%.2f", expected), null,
			"Uniques received vs expected from " + basis + " at 1/" + oneIn + "." + breakdown);

		if (luck.getTier() == null)
		{
			// No tier without kills
			tier.setText("");
			tier.setToolTipText(null);
		}
		else
		{
			LuckTier luckTier = luck.getTier();
			tier.setText(luckTier.getLabel());
			tier.setForeground(UiFormat.tierColor(luckTier));
			tier.setToolTipText(luck.tierHelp());
		}

		String sinceWhat;
		if (dryness.isSinceWholeKillCount())
		{
			sinceWhat = "your first kill: you haven't had a unique yet (RuneLite's Loot Tracker has none either)";
		}
		else if (dryness.isSinceFromGameCount())
		{
			sinceWhat = "where the game's own dry streak count places your last unique (it counts Entry Mode raids too)";
		}
		else if (dryness.isSinceFromEnteredKc())
		{
			sinceWhat = "the kill count you entered for your last unique (KC " + String.format(Locale.ROOT, "%,d", lastKc)
				+ "). Kills between that and the start of tracking are counted from your kill count, so they aren't"
				+ " limited to " + luckKills;
		}
		else
		{
			sinceWhat = lastKc != null ? "your last unique at KC " + String.format(Locale.ROOT, "%,d", lastKc)
				: dryness.getFirstTrackedKc() != null ? "tracking began at KC " + String.format(Locale.ROOT, "%,d", dryness.getFirstTrackedKc())
				: "tracking began";
		}
		set(dry, "Dry", since + " kc", null, upperFirst(luckKills) + " since " + sinceWhat + "."
			+ " Right-click to set the kill count of your last unique.");

		double chance = DropOdds.chanceByNow(rate, since);
		set(byNow, "By now", percent(chance), null, "Chance of at least one unique in " + since
			+ " kills at 1/" + oneIn + ". This is how many players would have had one by now.");

		int onRate = (int) Math.ceil(1 / rate);
		int toGo = onRate - since;
		int half = DropOdds.killsForChance(rate, 0.5);
		int ninety = DropOdds.killsForChance(rate, 0.9);
		if (toGo > 0)
		{
			set(next, "Next", currentKc != null ? "KC " + String.format(Locale.ROOT, "%,d", currentKc + toGo) : toGo + " kc", null,
				"On average one unique drops every " + oneIn + " kills, so the next is due in " + toGo + " more kills"
					+ (currentKc != null ? " (KC " + String.format(Locale.ROOT, "%,d", currentKc + toGo) + ")" : "") + ".");
		}
		else
		{
			set(next, "Overdue", "+" + (-toGo) + " kc", UiFormat.LOSS, "You're " + (-toGo)
				+ " kills past the " + oneIn + "-kill average. Each kill is still 1/" + oneIn
				+ ": drops don't become more likely.");
		}
		set(odds, "50% / 90% of players by", half + " / " + ninety + " kc", null, "Kills after a unique by which 50%"
			+ " and 90% of players get the next one: " + half + " and " + ninety + ".");

		teamDry.setVisible(dryness.getTeamDryStreak() != null);
		if (dryness.getTeamDryStreak() != null)
		{
			set(teamDry, "Team dry streak", String.format(Locale.ROOT, "%,d kc", dryness.getTeamDryStreak()), null,
				UiFormat.teamDryStreakHelp(dryness.isTeamDryStreakFromGame()));
		}

		progress.setMaximumValue(onRate);
		progress.setValue(Math.min(since, onRate));
		// The bar's centre label only gets a third of its width, so keep it short
		progress.setCenterLabel(toGo > 0 ? String.format(Locale.ROOT, "%.0f%%", since * 100.0 / onRate) : "Past rate");
		progress.setToolTipText(UiFormat.tooltip("Progress to the drop rate: kills since your last unique (" + since
			+ ") out of the " + oneIn + "-kill average between uniques."));

		clearKc.setEnabled(dryness.getEnteredLastUniqueKc() != null);
	}

	private static String lowerFirst(String text)
	{
		return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
	}

	private static String upperFirst(String text)
	{
		return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	private static void set(JLabel label, String name, String value, Color color, String help)
	{
		label.setText(UiFormat.pair(name, value, color));
		label.setToolTipText(UiFormat.tooltip(help));
	}

	private static String percent(double chance)
	{
		double pct = chance * 100;
		return String.format(Locale.ROOT, pct < 1 ? "%.2f%%" : "%.1f%%", pct);
	}
}
