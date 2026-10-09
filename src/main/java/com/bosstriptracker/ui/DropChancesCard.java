package com.bosstriptracker.ui;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.DropKind;
import com.bosstriptracker.boss.ExpectedDrop;
import com.bosstriptracker.view.DrynessView;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Icon;
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
 * Expected vs received drops per unique, with an Expected tab (the chance of at least one by now) and a
 * Received tab (luck: actual minus expected). Rows follow the shown boss: any unique, each unique, the pet.
 * The two-tab layout follows the look of Speaax's Delve calculator plugin (BSD-2-Clause); the code is this plugin's
 * own.
 */
class DropChancesCard extends JPanel
{
	private static final String HELP = "<html><b>Expected:</b> each bar shows the share of players with as many kills"
		+ " who would have at least one of that drop by now, and the number is how many the rate predicts so far.<br><br>"
		+ "<b>Received:</b> each bar shows how many drops you are ahead of (green, to the right) or behind (red, to the"
		+ " left) that prediction, and the number is how many you have actually received.<br><br>"
		+ "%s All-time numbers come from RuneLite's Loot Tracker when it has a record for this account.</html>";

	/**
	 * The Received tab is shown; saved in the settings.
	 */
	private boolean showReceived;
	private final SectionStates states;
	private static final String STATE_KEY = "lifetime.chances.received";

	private final ItemManager itemManager;
	private final JLabel expectedTab = tabLabel("Expected");
	private final JLabel receivedTab = tabLabel("Received");
	private final JLabel help = new JLabel(new HelpIcon());
	private final JPanel rowPanel = new JPanel(new GridLayout(0, 1, 0, 3));
	private final List<Row> rows = new ArrayList<>();
	private final JLabel source = new JLabel();
	private DrynessView dryness;
	private BossDefinition boss;

	DropChancesCard(ItemManager itemManager, SectionStates states)
	{
		this.itemManager = itemManager;
		this.states = states;
		this.showReceived = states.isOpen(STATE_KEY, false);
		setLayout(new BorderLayout(0, 3));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(3, 6, 6, 6));

		expectedTab.addMouseListener(select(false));
		receivedTab.addMouseListener(select(true));
		JPanel tabs = new JPanel(new GridLayout(1, 2, 4, 0));
		tabs.setOpaque(false);
		tabs.add(expectedTab);
		tabs.add(receivedTab);

		JPanel header = new JPanel(new BorderLayout(4, 0));
		header.setOpaque(false);
		header.add(tabs, BorderLayout.CENTER);
		header.add(help, BorderLayout.EAST);

		rowPanel.setOpaque(false);

		source.setFont(FontManager.getRunescapeSmallFont());
		source.setForeground(UiFormat.MUTED_TEXT);

		add(header, BorderLayout.NORTH);
		add(rowPanel, BorderLayout.CENTER);
		add(source, BorderLayout.SOUTH);
		styleTabs();
	}

	void update(DrynessView dryness, BossDefinition boss)
	{
		this.dryness = dryness;
		if (boss != this.boss)
		{
			this.boss = boss;
			buildRows();
		}
		refresh();
	}

	private void buildRows()
	{
		rows.clear();
		rowPanel.removeAll();
		rows.add(new Row(anyLabel()));
		for (ExpectedDrop drop : boss.getDrops())
		{
			if (drop.getKind() == DropKind.UNIQUE)
			{
				rows.add(new Row(itemIcon(itemManager, drop.getItemId())));
			}
		}
		if (boss.getPet() != null)
		{
			rows.add(new Row(itemIcon(itemManager, boss.getPet().getItemId())));
		}
		for (Row row : rows)
		{
			rowPanel.add(row.panel);
		}
		String note = boss.getLuckNote();
		if (!boss.getEggPetRates().isEmpty())
		{
			note += (note.isEmpty() ? "" : " ") + "Eggs you pop add to the pet row.";
		}
		help.setToolTipText(String.format(HELP, note));
		rowPanel.revalidate();
	}

	private void refresh()
	{
		if (dryness == null || boss == null)
		{
			return;
		}

		// Rows: any unique, each unique, then the pet (the numbers are shared with the share card)
		List<DropChances.Row> chances = DropChances.rows(dryness);
		List<String> names = new ArrayList<>();
		// Extra facts per row for the tooltip: how dry you are, and the kill counts of tracked uniques
		List<String> extras = new ArrayList<>();
		DrynessView.AllTime allTime = dryness.getAllTime();
		List<DrynessView.Drop> uniques = allTime != null ? allTime.getUniques() : dryness.getUniques();
		names.add("Any unique (1/" + UiFormat.oneIn(dryness.getAnyUniqueRate()) + ")");
		extras.add(String.format(Locale.ROOT, " You're on a %,d kill dry streak: %s of players would have had a unique by now.",
			dryness.getKillsSinceUnique(), percent(1 - dryness.getChanceThisDry())));
		for (DrynessView.Drop unique : uniques)
		{
			names.add(unique.getName() + " (1/" + UiFormat.oneIn(unique.getRate()) + ")");
			extras.add(trackedKcs(unique.getItemId()));
		}

		boolean hasEggs = !dryness.getEggTiers().isEmpty();
		double eggPetExpected = dryness.getEggPetExpected();
		DrynessView.Drop pet = dryness.getPet();
		if (pet != null)
		{
			names.add(boss.getDisplayName() + " pet (" + (dryness.getPetRateNote() != null ? dryness.getPetRateNote()
				: "1/" + UiFormat.oneIn(pet.getRate()) + " per kill")
				+ (hasEggs ? ", plus eggs" : "") + ")");
			extras.add("");
		}
		int petRow = pet != null ? names.size() - 1 : -1;

		String basis;
		if (allTime != null)
		{
			// All-time: the Loot Tracker records every loot-dropping kill and its drops
			int kills = allTime.getLootKills();
			basis = String.format(Locale.ROOT, "%,d kills recorded by RuneLite's Loot Tracker since %s", kills,
				UiFormat.date(allTime.getFirstRecordedAt()))
				+ (allTime.getKillCount() != null ? String.format(Locale.ROOT, " (KC %,d)", allTime.getKillCount()) : "");
			source.setText(DropChances.source(dryness));
		}
		else
		{
			int kills = dryness.getLuckKills();
			basis = kills + " " + boss.getLuckKillsName() + " tracked by this plugin";
			source.setText("Tracked · " + kills + " kills (enable Loot Tracker for all-time)");
		}
		source.setToolTipText(UiFormat.tooltip("Based on " + basis + "."));

		double scale = DropChances.luckScale(chances);
		for (int i = 0; i < rows.size() && i < chances.size(); i++)
		{
			Row row = rows.get(i);
			DropChances.Row chance = chances.get(i);
			double byNow = chance.getChanceOfOne();
			if (showReceived)
			{
				row.bar.showReceived(chance.luck(), scale);
				row.number.setText(String.valueOf(chance.getReceived()));
			}
			else
			{
				row.bar.showExpected(byNow);
				row.number.setText(String.valueOf((int) Math.floor(chance.getExpected())));
			}
			String help = names.get(i) + ": " + chance.getReceived() + " received, "
				+ String.format(Locale.ROOT, "%.2f", chance.getExpected()) + " expected from " + basis
				+ (i == petRow && eggPetExpected > 0 ? String.format(Locale.ROOT, " plus eggs popped (%.3f)", eggPetExpected) : "")
				+ ". " + percent(byNow) + " of players with as many kills would have at least one by now."
				+ (i == petRow && allTime != null ? " The Loot Tracker doesn't record pets, so pets are the ones this plugin saw." : "")
				+ extras.get(i);
			row.panel.setToolTipText(UiFormat.tooltip(help));
			row.bar.setToolTipText(UiFormat.tooltip(help));
		}
	}

	/**
	 * @return " Tracked at KC 2,601, 2,767." for a unique this plugin tracked, or an empty string
	 */
	private String trackedKcs(int itemId)
	{
		for (DrynessView.Drop tracked : dryness.getUniques())
		{
			if (tracked.getItemId() == itemId && !tracked.getKillCounts().isEmpty())
			{
				List<String> kcs = new ArrayList<>();
				for (Integer kc : tracked.getKillCounts())
				{
					kcs.add(kc == null ? "?" : String.format(Locale.ROOT, "%,d", kc));
				}
				return " Tracked at KC " + String.join(", ", kcs) + ".";
			}
		}
		return "";
	}

	private static String percent(double chance)
	{
		double pct = chance * 100;
		return String.format(Locale.ROOT, pct < 1 ? "%.2f%%" : "%.1f%%", pct);
	}

	private MouseAdapter select(boolean received)
	{
		return new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				showReceived = received;
				states.setOpen(STATE_KEY, received);
				styleTabs();
				refresh();
			}
		};
	}

	private void styleTabs()
	{
		style(expectedTab, !showReceived);
		style(receivedTab, showReceived);
	}

	private static void style(JLabel tab, boolean selected)
	{
		tab.setForeground(selected ? Color.WHITE : UiFormat.MUTED_TEXT);
		tab.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, selected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR),
			BorderFactory.createEmptyBorder(2, 0, 2, 0)));
	}

	private static JLabel tabLabel(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(FontManager.getRunescapeFont());
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return label;
	}

	private static JLabel anyLabel()
	{
		JLabel label = new JLabel("Any", SwingConstants.CENTER);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.YELLOW);
		return label;
	}

	private static JLabel itemIcon(ItemManager itemManager, int itemId)
	{
		JLabel label = new JLabel();
		label.setHorizontalAlignment(SwingConstants.CENTER);
		if (itemManager == null)
		{
			return label;
		}
		AsyncBufferedImage image = itemManager.getImage(itemId);
		Runnable scaled = () -> label.setIcon(new ImageIcon(ImageUtil.resizeImage(image, 23, 20)));
		image.onLoaded(scaled);
		scaled.run();
		return label;
	}

	private static class Row
	{
		final JPanel panel = new JPanel(new BorderLayout(4, 0));
		final DropBar bar = new DropBar();
		final JLabel number = new JLabel("0", SwingConstants.RIGHT);

		Row(JLabel icon)
		{
			panel.setOpaque(false);
			icon.setPreferredSize(new Dimension(26, 20));
			number.setFont(FontManager.getRunescapeSmallFont());
			number.setForeground(new Color(229, 229, 229));
			number.setPreferredSize(new Dimension(22, 20));
			panel.add(icon, BorderLayout.WEST);
			panel.add(bar, BorderLayout.CENTER);
			panel.add(number, BorderLayout.EAST);
		}
	}

	/**
	 * A circled question mark.
	 */
	private static class HelpIcon implements Icon
	{
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(UiFormat.MUTED_TEXT);
			g2.setStroke(new BasicStroke(1.2f));
			g2.drawOval(x + 1, y + 1, 13, 13);
			g2.setFont(FontManager.getRunescapeSmallFont());
			g2.drawString("?", x + 5, y + 12);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 16;
		}

		@Override
		public int getIconHeight()
		{
			return 16;
		}
	}
}
