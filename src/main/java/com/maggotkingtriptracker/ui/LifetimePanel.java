package com.maggotkingtriptracker.ui;

import com.maggotkingtriptracker.boss.BossDefinition;
import com.maggotkingtriptracker.model.TripMath;
import com.maggotkingtriptracker.view.DrynessView;
import com.maggotkingtriptracker.view.ItemView;
import com.maggotkingtriptracker.view.LifetimeView;
import com.maggotkingtriptracker.view.PolishView;
import com.maggotkingtriptracker.view.SupplyCategory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Lifetime tab: totals, then what they're made of (all loot and supplies), then drop chances, the boss's own
 * cards (eggs, polish results) and the data buttons.
 */
class LifetimePanel extends JPanel
{
	/**
	 * Remembered for the session. All-time (RuneLite's Loot Tracker record) when there is one.
	 */
	private static boolean trackedLoot;

	private final ItemManager itemManager;
	private final StatCell trips = new StatCell("Trips", false);
	private final StatCell kills = new StatCell("Kills", false);
	private final StatCell time = new StatCell("Time", false);
	private final StatCell averageKill = new StatCell("Avg kill", false);
	private final StatCell loot = new StatCell("Loot", false);
	private final StatCell costs = new StatCell("Costs", false);
	private final StatCell net = new StatCell("Net", true);
	private final StatCell gpPerHour = new StatCell("GP/hr", true);
	private final StatCell deaths = new StatCell("Deaths", false);
	private final JLabel details = new JLabel();
	private final JLabel todayValue = new JLabel();
	private final JLabel trackedNote = new JLabel();
	private final JPanel lootHolder = holder();
	private final JPanel suppliesHolder = holder();
	private final JLabel trackedSwitch = switchLabel("Tracked");
	private final JLabel allTimeSwitch = switchLabel("All-time");
	private final JPanel lootSwitch = new JPanel(new GridLayout(1, 2, 4, 0));
	private final DropChancesCard dropChances;
	private final InfoCard eggCard = new InfoCard("Eggs popped");
	private final InfoCard polishCard = new InfoCard("Polish results");
	private final JButton exportCsvButton = new JButton("Export CSV");
	private final JButton exportJsonButton = new JButton("Export JSON");
	private final JButton importJsonButton = new JButton("Import JSON");
	private final JButton clearButton = new JButton("Clear all");

	private LifetimeView view;
	private BossDefinition boss;
	/**
	 * What the item grids were last built from, so they're only rebuilt (losing hovered tooltips) on a change.
	 */
	private List<Object> shownLoot;
	private List<Object> shownSupplies;

	LifetimePanel(ItemManager itemManager, PanelActions actions, Runnable onClear)
	{
		this.itemManager = itemManager;
		this.dropChances = new DropChancesCard(itemManager);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel card = new JPanel(new BorderLayout(0, 3));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JPanel grid = new JPanel(new GridLayout(3, 3, 4, 3));
		grid.setOpaque(false);
		for (StatCell cell : new StatCell[]{trips, kills, time, loot, costs, net, gpPerHour, averageKill, deaths})
		{
			grid.add(cell);
		}

		JPanel footer = new JPanel(new GridLayout(0, 1, 0, 0));
		footer.setOpaque(false);
		for (JLabel label : new JLabel[]{trackedNote, details, todayValue})
		{
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setForeground(UiFormat.MUTED_TEXT);
			footer.add(label);
		}

		card.add(grid, BorderLayout.CENTER);
		card.add(footer, BorderLayout.SOUTH);
		add(card);

		add(title("Drop chances"));
		dropChances.setAlignmentX(LEFT_ALIGNMENT);
		add(dropChances);

		lootSwitch.setOpaque(false);
		lootSwitch.add(trackedSwitch);
		lootSwitch.add(allTimeSwitch);
		trackedSwitch.addMouseListener(selectLoot(true));
		allTimeSwitch.addMouseListener(selectLoot(false));
		add(lootHolder);
		add(suppliesHolder);

		for (InfoCard infoCard : new InfoCard[]{eggCard, polishCard})
		{
			add(spacer());
			add(infoCard);
		}

		add(title("Data"));
		JPanel buttons = new JPanel(new GridLayout(0, 2, 4, 4));
		buttons.setOpaque(false);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		exportCsvButton.addActionListener(e -> actions.exportCsv());
		exportJsonButton.addActionListener(e -> actions.exportJson());
		importJsonButton.addActionListener(e -> actions.importJson());
		clearButton.setForeground(UiFormat.LOSS);
		clearButton.addActionListener(e -> onClear.run());
		for (JButton button : new JButton[]{exportCsvButton, exportJsonButton, importJsonButton, clearButton})
		{
			button.setFocusPainted(false);
			button.setFont(FontManager.getRunescapeSmallFont());
			button.setMargin(new Insets(2, 2, 2, 2));
			buttons.add(button);
		}
		add(buttons);
	}

	private static JPanel holder()
	{
		JPanel holder = new JPanel();
		holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
		holder.setOpaque(false);
		holder.setAlignmentX(LEFT_ALIGNMENT);
		return holder;
	}

	private static JLabel title(String text)
	{
		JLabel title = new JLabel(text);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		title.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		title.setAlignmentX(LEFT_ALIGNMENT);
		return title;
	}

	private static JPanel spacer()
	{
		JPanel spacer = new JPanel();
		spacer.setOpaque(false);
		spacer.setAlignmentX(LEFT_ALIGNMENT);
		spacer.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		return spacer;
	}

	private static JLabel switchLabel(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return label;
	}

	private MouseAdapter selectLoot(boolean tracked)
	{
		return new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				trackedLoot = tracked;
				shownLoot = null;
				updateLoot();
				revalidate();
				repaint();
			}
		};
	}

	void update(LifetimeView view, boolean readOnly, BossDefinition boss)
	{
		if (view == null)
		{
			setVisible(false);
			return;
		}
		setVisible(true);
		this.view = view;
		this.boss = boss;

		String area = boss.getAreaNoun();
		eggCard.setVisible(!boss.getEggPetRates().isEmpty());
		polishCard.setVisible(!boss.getTarnishedItems().isEmpty());

		trips.setValue(String.valueOf(view.getTrips()));
		kills.setValue(String.valueOf(view.getKills()));
		time.setValue(UiFormat.duration(view.getActiveMs()), ColorScheme.LIGHT_GRAY_COLOR,
			"Time spent in the " + area + " across all trips (idle time left out).");
		averageKill.setValue(UiFormat.killTime(view.getAverageKillMs()));
		loot.setValue(UiFormat.gp(view.getLootValue()), ColorScheme.LIGHT_GRAY_COLOR,
			UiFormat.fullGp(view.getLootValue()) + " at recorded prices");

		long totalCosts = view.getSupplyCost() + view.getDroppedCost() + view.getDeathCost();
		costs.setValue(UiFormat.gp(totalCosts), ColorScheme.LIGHT_GRAY_COLOR, "<html>Supplies: " + UiFormat.fullGp(view.getSupplyCost())
			+ "<br>Dropped: " + UiFormat.fullGp(view.getDroppedCost())
			+ "<br>Deaths: " + UiFormat.fullGp(view.getDeathCost()) + "</html>");
		net.setValue(UiFormat.gp(view.getNetProfit()), UiFormat.profitColor(view.getNetProfit()), UiFormat.fullGp(view.getNetProfit()));
		long rate = TripMath.gpPerHour(view.getNetProfit(), view.getActiveMs());
		gpPerHour.setValue(UiFormat.gp(rate), UiFormat.profitColor(rate), UiFormat.fullGp(rate) + " per hour in the " + area);
		deaths.setValue(String.valueOf(view.getDeaths()));

		trackedNote.setText(trackedSince(view));
		trackedNote.setToolTipText(UiFormat.tooltip(trackedHelp(view, "These totals")));

		String pets = view.getPets() > 0 ? "Pets " + view.getPets() : "";
		details.setText(view.getChoiceSummary().isEmpty() ? pets
			: view.getChoiceSummary() + (pets.isEmpty() ? "" : " · " + pets));

		Long today = view.getLootValueToday();
		todayValue.setVisible(today != null);
		if (today != null)
		{
			todayValue.setText("Loot at today's prices: " + UiFormat.gp(today));
			todayValue.setToolTipText(UiFormat.fullGp(today));
		}

		updateLoot();
		updateSupplies();
		dropChances.update(view.getDryness(), boss);
		updateEggs(view.getDryness());
		updatePolish(view.getPolish());

		exportCsvButton.setEnabled(view.getTrips() > 0);
		exportJsonButton.setEnabled(view.getTrips() > 0);
		importJsonButton.setEnabled(!readOnly);
		clearButton.setEnabled(!readOnly && view.getTrips() > 0);
		revalidate();
		repaint();
	}

	/**
	 * All loot: RuneLite's Loot Tracker record at today's prices, or the loot of every tracked trip at the prices
	 * recorded then. Net profit stays on tracked trips (the totals above), as costs are only known for those.
	 */
	private void updateLoot()
	{
		boolean hasAllTime = view.getAllTimeLoot() != null;
		boolean allTime = hasAllTime && !trackedLoot;
		style(trackedSwitch, !allTime);
		style(allTimeSwitch, allTime);
		allTimeSwitch.setVisible(hasAllTime);
		allTimeSwitch.setToolTipText(UiFormat.tooltip("Everything RuneLite's Loot Tracker has recorded for this boss"
			+ (view.getAllTimeSince() > 0 ? " since " + UiFormat.date(view.getAllTimeSince()) : "")
			+ ", at today's prices (it doesn't keep prices)."));
		trackedSwitch.setToolTipText(UiFormat.tooltip("Loot of every trip this plugin tracked, at the prices recorded then."));

		List<ItemView> items = allTime ? view.getAllTimeLoot() : view.getLoot();
		List<Object> key = new ArrayList<>(items);
		key.add(allTime);
		if (key.equals(shownLoot))
		{
			return;
		}
		shownLoot = key;

		String killsName = boss.getLuckKillsName();
		List<SectionStat> stats = new ArrayList<>();
		if (allTime)
		{
			DrynessView.AllTime record = view.getDryness().getAllTime();
			int recordKills = record == null ? 0 : record.getLootKills();
			long value = view.getAllTimeLootValue();
			stats.add(SectionStat.of("Total", UiFormat.gp(value),
				UiFormat.fullGp(value) + " at today's prices: everything in RuneLite's Loot Tracker record."));
			stats.add(SectionStat.of("GP/Kill", UiFormat.gp(recordKills > 0 ? value / recordKills : 0),
				"Total divided by the " + String.format(Locale.ROOT, "%,d", recordKills) + " " + killsName + " in the record."));
			stats.add(SectionStat.of("Kills", String.format(Locale.ROOT, "%,d", recordKills),
				Character.toUpperCase(killsName.charAt(0)) + killsName.substring(1) + " in RuneLite's Loot Tracker record."));
			if (view.getAllTimeSince() > 0)
			{
				stats.add(SectionStat.of("Since", UiFormat.date(view.getAllTimeSince()),
					"When RuneLite's Loot Tracker started recording this boss on this account."));
			}
		}
		else
		{
			long value = view.getLootValue();
			stats.add(SectionStat.of("Total", UiFormat.gp(value),
				UiFormat.fullGp(value) + " at the prices recorded when each drop came in."));
			stats.add(SectionStat.of("GP/Kill", UiFormat.gp(view.getKills() > 0 ? value / view.getKills() : 0),
				"Total divided by the " + view.getKills() + " kills tracked."));
			stats.add(SectionStat.of("Kills", String.valueOf(view.getKills()), "Kills tracked by this plugin."));
			stats.add(SectionStat.of("Loot GP/hr", UiFormat.gp(TripMath.gpPerHour(value, view.getActiveMs())),
				"Loot per hour in the " + boss.getAreaNoun() + ", before costs."));
		}

		lootHolder.removeAll();
		lootHolder.add(section(new ItemSection(itemManager, "All loot", items,
			allTime ? "Nothing in the record yet" : "No loot tracked yet", stats, lootSwitch)));
	}

	/**
	 * Every tracked trip's supplies and items left behind. RuneLite doesn't record supplies anywhere, so these only
	 * go back to when the plugin started tracking.
	 */
	private void updateSupplies()
	{
		List<Object> key = new ArrayList<>(view.getSupplies());
		key.add(view.getSupplyCategories());
		key.addAll(view.getDropped());
		if (key.equals(shownSupplies))
		{
			return;
		}
		shownSupplies = key;

		List<SectionStat> stats = new ArrayList<>();
		stats.add(trackedStat(view));
		stats.add(SectionStat.of("Total", UiFormat.gp(view.getSupplyCost()),
			"Everything used up across the trips this plugin tracked (RuneLite doesn't record supplies, so there's no"
				+ " all-time count). Dropped items and death costs are counted separately under Costs."));
		for (SupplyCategory category : view.getSupplyCategories())
		{
			stats.add(SectionStat.of(category.getName(), UiFormat.gp(category.getValue()), TripDetails.categoryHelp(category.getName())));
		}
		suppliesHolder.removeAll();
		suppliesHolder.add(section(new ItemSection(itemManager, "All supplies", view.getSupplies(), "No supplies tracked yet", stats)));
		if (!view.getDropped().isEmpty())
		{
			List<SectionStat> droppedStats = Arrays.asList(trackedStat(view), SectionStat.of("Total", UiFormat.gp(view.getDroppedCost()),
				"Items dropped and left behind across all trips, at GE price."));
			suppliesHolder.add(section(new ItemSection(itemManager, "All dropped", view.getDropped(), null, droppedStats)));
		}
	}

	/**
	 * "Tracked since 27 Sep 2026 (KC 2,565)": this plugin only knows the kills since it was installed.
	 */
	private static String trackedSince(LifetimeView view)
	{
		if (view.getTrackedSince() <= 0)
		{
			return "Nothing tracked yet";
		}
		Integer firstKc = view.getDryness().getFirstTrackedKc();
		return "Tracked since " + UiFormat.date(view.getTrackedSince())
			+ (firstKc != null ? String.format(Locale.ROOT, " (KC %,d)", firstKc) : "");
	}

	private static String trackedHelp(LifetimeView view, String what)
	{
		Integer firstKc = view.getDryness().getFirstTrackedKc();
		return what + " only count kills this plugin recorded after it was installed"
			+ (view.getTrackedSince() > 0 ? ", starting " + UiFormat.date(view.getTrackedSince())
			+ (firstKc != null ? String.format(Locale.ROOT, " at KC %,d", firstKc) : "") : "")
			+ ". Earlier kills aren't included.";
	}

	/**
	 * The same note as a section stat, for the cards that only have tracked trips (supplies, dropped items).
	 */
	private static SectionStat trackedStat(LifetimeView view)
	{
		Integer firstKc = view.getDryness().getFirstTrackedKc();
		String value = view.getTrackedSince() <= 0 ? "-" : firstKc != null ? String.format(Locale.ROOT, "KC %,d", firstKc)
			: UiFormat.date(view.getTrackedSince());
		return SectionStat.of("From", value, trackedHelp(view, "These"));
	}

	private static ItemSection section(ItemSection section)
	{
		section.setAlignmentX(LEFT_ALIGNMENT);
		return section;
	}

	private static void style(JLabel label, boolean selected)
	{
		label.setForeground(selected ? Color.WHITE : UiFormat.MUTED_TEXT);
		label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, selected ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR));
	}

	private void updateEggs(DrynessView dryness)
	{
		List<String[]> eggRows = new ArrayList<>();
		int popped = 0;
		for (DrynessView.EggTier tier : dryness.getEggTiers())
		{
			// Only tiers you've popped, to keep the card short
			if (tier.getPopped() == 0)
			{
				continue;
			}
			popped += tier.getPopped();
			eggRows.add(new String[]{tier.getName() + ": " + String.format(Locale.ROOT, "%,d", tier.getPopped())
				+ (tier.getPets() > 0 ? " · pets " + tier.getPets() : ""),
				"Popped, with a 1/" + Math.round(1 / tier.getPetRate()) + " pet chance each."});
		}
		eggRows.add(popped == 0 ? new String[]{"No eggs popped yet", null}
			: new String[]{"Egg pet chance: " + percent(dryness.getEggPetChance())
			+ (dryness.getPetsFromEggs() > 0 ? " · pets " + dryness.getPetsFromEggs() : ""),
			"Chance of at least one pet from all the eggs popped so far."});
		eggCard.setRows(eggRows);
	}

	private void updatePolish(List<PolishView> polish)
	{
		// The type on one line, then one line per result
		List<String> rows = new ArrayList<>();
		for (PolishView polished : polish)
		{
			rows.add(polished.getTarnishedName() + " (" + polished.getTotal() + ")");
			for (ItemView outcome : polished.getOutcomes())
			{
				rows.add("  " + outcome.getName() + " x" + outcome.getQuantity());
			}
		}
		if (rows.isEmpty())
		{
			rows.add("Nothing polished yet");
		}
		polishCard.setLines(rows);
	}

	private static String percent(double chance)
	{
		double pct = chance * 100;
		return String.format(Locale.ROOT, pct < 1 ? "%.2f%%" : "%.1f%%", pct);
	}
}
