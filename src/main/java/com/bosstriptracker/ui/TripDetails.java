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

	private final ItemSection loot;
	private final ItemSection supplies;
	private final ItemSection dropped;

	/**
	 * @param statePrefix "trip" for the Trip tab, "history" for History cards (which all share one state per box)
	 */
	TripDetails(ItemManager itemManager, SectionStates states, String statePrefix, TripView trip)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);

		loot = new ItemSection(itemManager, states, statePrefix + ".loot", false, "Loot", trip.getLoot(),
			"No loot yet", lootStats(trip));
		add(section(loot));
		supplies = new ItemSection(itemManager, states, statePrefix + ".supplies", false, "Supplies", trip.getSupplies(),
			"No supplies used yet", supplyStats(trip));
		add(section(supplies));
		if (!trip.getDropped().isEmpty())
		{
			dropped = new ItemSection(itemManager, states, statePrefix + ".dropped", false, "Dropped", trip.getDropped(),
				null, droppedStats(trip));
			add(section(dropped));
		}
		else
		{
			dropped = null;
		}
	}

	/**
	 * Shows the trip's new numbers in place, keeping the boxes and their item grids where it can.
	 *
	 * @return false if the boxes changed (a Dropped box appearing, a new loot category): build new details instead
	 */
	boolean update(TripView trip)
	{
		if ((dropped == null) != trip.getDropped().isEmpty())
		{
			return false;
		}
		return loot.update(trip.getLoot(), lootStats(trip))
			&& supplies.update(trip.getSupplies(), supplyStats(trip))
			&& (dropped == null || dropped.update(trip.getDropped(), droppedStats(trip)));
	}

	private static List<SectionStat> lootStats(TripView trip)
	{
		long lootPerKill = trip.getKills() > 0 ? trip.getLootValue() / trip.getKills() : 0;
		List<SectionStat> lootStats = new ArrayList<>();
		lootStats.add(SectionStat.of("Total GP", UiFormat.gp(trip.getLootValue()),
			UiFormat.fullGp(trip.getLootValue()) + ": everything looted this trip, at the GE prices recorded when each"
				+ " drop came in, before costs."));
		lootStats.add(SectionStat.of("GP/" + trip.getWords().unitTitle(), UiFormat.gp(lootPerKill),
			"Loot value divided by " + trip.getWords().units() + ": " + UiFormat.fullGp(trip.getLootValue()) + " / "
				+ trip.getKills() + "."));
		lootStats.addAll(categoryStats(trip.getLootCategories()));
		return lootStats;
	}

	private static List<SectionStat> supplyStats(TripView trip)
	{
		List<SectionStat> supplyStats = new ArrayList<>();
		supplyStats.add(SectionStat.of("Total GP", UiFormat.gp(trip.getSupplyCost()),
			"Everything used up in the " + trip.getWords().getArea() + ": the sum of the categories below (charges, runes, potions, food and other)."
				+ " Dropped items and death costs are counted separately under Costs."));
		for (SupplyCategory category : trip.getSupplyCategories())
		{
			supplyStats.add(SectionStat.of(category.getName(), UiFormat.gp(category.getValue()), categoryHelp(category.getName())));
		}
		return supplyStats;
	}

	private static List<SectionStat> droppedStats(TripView trip)
	{
		List<SectionStat> droppedStats = new ArrayList<>();
		droppedStats.add(SectionStat.of("Total GP", UiFormat.gp(trip.getDroppedCost()),
			"Items dropped in the " + trip.getWords().getArea() + " and not picked back up before leaving, at GE price. Items under 100 gp are ignored."));
		return droppedStats;
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
				return "Charges used, counted from your attacks and priced from what recharges them: Amulet of blood"
					+ " fury (a blood shard per 10,000), Tome of fire (a page per 20), revenant bows such as the"
					+ " Webweaver bow (a revenant ether per shot), Scythe of Vitur (a vial of blood and 200 blood runes"
					+ " per 100), Tumeken's shadow (2 soul and 5 chaos runes a cast), Sanguinesti staff (2 blood runes"
					+ " a cast), Trident of the swamp and of the seas (runes plus a Zulrah's scale or 10 coins a cast),"
					+ " Eye of Ayak (a demon tear or runes a cast), Toxic blowpipe (scales and the darts lost) and"
					+ " Crystal halberd (crystal shards, 0 gp).";
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
