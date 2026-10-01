package com.bosstriptracker.ui;

import com.bosstriptracker.view.ItemView;
import com.bosstriptracker.view.LootCategory;
import com.bosstriptracker.view.SupplyCategory;
import com.bosstriptracker.view.TripView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;

/**
 * Loot, supplies and dropped items for one trip, each as a loot-tracker style box.
 */
class TripDetails extends JPanel
{
	/**
	 * Most items listed in a loot category's tooltip.
	 */
	static final int MAX_TOOLTIP_ITEMS = 10;

	/**
	 * @param statePrefix "trip" for the Trip tab, "history" for History cards (which all share one state per box)
	 */
	TripDetails(ItemManager itemManager, SectionStates states, String statePrefix, TripView trip)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);

		long lootPerKill = trip.getKills() > 0 ? trip.getLootValue() / trip.getKills() : 0;
		List<SectionStat> lootStats = new ArrayList<>();
		lootStats.add(SectionStat.of("Total GP", UiFormat.gp(trip.getLootValue()),
			UiFormat.fullGp(trip.getLootValue()) + ": everything looted this trip, at the GE prices recorded when each"
				+ " drop came in, before costs."));
		lootStats.add(SectionStat.of("GP/Kill", UiFormat.gp(lootPerKill),
			"Loot value divided by kills: " + UiFormat.fullGp(trip.getLootValue()) + " / " + trip.getKills() + "."));
		lootStats.addAll(categoryStats(trip.getLootCategories()));
		add(section(new ItemSection(itemManager, states, statePrefix + ".loot", false, "Loot", trip.getLoot(),
			"No loot yet", lootStats)));

		List<SectionStat> supplyStats = new ArrayList<>();
		supplyStats.add(SectionStat.of("Total GP", UiFormat.gp(trip.getSupplyCost()),
			"Everything used up in the lair: the sum of the categories below (charges, runes, potions, food and other)."
				+ " Dropped items and death costs are counted separately under Costs."));
		for (SupplyCategory category : trip.getSupplyCategories())
		{
			supplyStats.add(SectionStat.of(category.getName(), UiFormat.gp(category.getValue()), categoryHelp(category.getName())));
		}
		add(section(new ItemSection(itemManager, states, statePrefix + ".supplies", false, "Supplies", trip.getSupplies(),
			"No supplies used yet", supplyStats)));

		if (!trip.getDropped().isEmpty())
		{
			List<SectionStat> droppedStats = new ArrayList<>();
			droppedStats.add(SectionStat.of("Total GP", UiFormat.gp(trip.getDroppedCost()),
				"Items dropped in the lair and not picked back up before leaving, at GE price. Items under 100 gp are ignored."));
			add(section(new ItemSection(itemManager, states, statePrefix + ".dropped", false, "Dropped", trip.getDropped(),
				null, droppedStats)));
		}
	}

	/**
	 * One stat per loot category, each listing its items in the tooltip.
	 */
	static List<SectionStat> categoryStats(List<LootCategory> categories)
	{
		List<SectionStat> stats = new ArrayList<>();
		for (LootCategory category : categories)
		{
			stats.add(SectionStat.of(category.getName(), UiFormat.gp(category.getValue()), categoryTooltip(category)));
		}
		return stats;
	}

	/**
	 * "Elder venator fang x 1: 41.2M" per item, highest value first, at most {@link #MAX_TOOLTIP_ITEMS} lines.
	 */
	static String categoryTooltip(LootCategory category)
	{
		StringBuilder sb = new StringBuilder(category.getName()).append(": ").append(UiFormat.fullGp(category.getValue()));
		List<ItemView> items = category.getItems();
		for (int i = 0; i < items.size() && i < MAX_TOOLTIP_ITEMS; i++)
		{
			ItemView item = items.get(i);
			sb.append('\n').append(item.getName()).append(String.format(Locale.ROOT, " x %,d: ", item.getQuantity()))
				.append(UiFormat.gp(item.getTotalValue()));
		}
		if (items.size() > MAX_TOOLTIP_ITEMS)
		{
			sb.append("\nand ").append(items.size() - MAX_TOOLTIP_ITEMS).append(" more");
		}
		return sb.toString();
	}

	static String categoryHelp(String category)
	{
		switch (category)
		{
			case "Charges":
				return "Charges used, priced from what recharges them: Amulet of blood fury (blood shard / 10,000),"
					+ " Tome of fire (page / 20) and revenant bows (1 revenant ether per shot).";
			case "Runes":
				return "Runes used from your inventory and rune pouch, at GE price.";
			case "Potions":
				return "Potion doses drunk, priced per dose from the highest-dose potion's GE price.";
			case "Food":
				return "Food eaten (anything with an Eat option), at GE price.";
			default:
				return "Other items used up, such as ammunition or teleports, at GE price.";
		}
	}

	private static ItemSection section(ItemSection section)
	{
		section.setAlignmentX(LEFT_ALIGNMENT);
		return section;
	}
}
