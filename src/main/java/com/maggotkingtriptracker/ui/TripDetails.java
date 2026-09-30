package com.maggotkingtriptracker.ui;

import com.maggotkingtriptracker.model.TripMath;
import com.maggotkingtriptracker.view.SupplyCategory;
import com.maggotkingtriptracker.view.TripView;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;

/**
 * Loot, supplies and dropped items for one trip, each as a loot-tracker style box.
 */
class TripDetails extends JPanel
{
	TripDetails(ItemManager itemManager, TripView trip)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);

		long activeMs = trip.activeMsAt(System.currentTimeMillis());
		long lootPerKill = trip.getKills() > 0 ? trip.getLootValue() / trip.getKills() : 0;
		long net = trip.getNetProfit();
		List<SectionStat> lootStats = new ArrayList<>();
		lootStats.add(SectionStat.of("GP/Kill", UiFormat.gp(lootPerKill),
			"Loot value divided by kills: " + UiFormat.fullGp(trip.getLootValue()) + " / " + trip.getKills() + "."));
		lootStats.add(SectionStat.of("Kills", String.valueOf(trip.getKills()),
			"Kills this trip, counted from the game's kill-count message."));
		lootStats.add(SectionStat.of("Loot GP/hr", UiFormat.gp(TripMath.gpPerHour(trip.getLootValue(), activeMs)),
			"Loot value per hour inside the lair (" + UiFormat.duration(activeMs) + "), before costs. The profit card's"
				+ " Net GP/hr is after costs."));
		lootStats.add(new SectionStat("Net", UiFormat.gp(net), UiFormat.profitColor(net),
			"Net profit: loot " + UiFormat.fullGp(trip.getLootValue()) + " minus supplies, dropped items and death costs ("
				+ UiFormat.fullGp(trip.getSupplyCost() + trip.getDroppedCost() + trip.getDeathCost()) + "). Green is a profit, red a loss."));
		add(section(new ItemSection(itemManager, "Loot", trip.getLoot(), "No loot yet", lootStats)));

		List<SectionStat> supplyStats = new ArrayList<>();
		supplyStats.add(SectionStat.of("Total", UiFormat.gp(trip.getSupplyCost()),
			"Everything used up in the lair: the sum of the categories below (charges, runes, potions, food and other)."
				+ " Dropped items and death costs are counted separately under Costs."));
		for (SupplyCategory category : trip.getSupplyCategories())
		{
			supplyStats.add(SectionStat.of(category.getName(), UiFormat.gp(category.getValue()), categoryHelp(category.getName())));
		}
		add(section(new ItemSection(itemManager, "Supplies", trip.getSupplies(), "No supplies used yet", supplyStats)));

		if (!trip.getDropped().isEmpty())
		{
			List<SectionStat> droppedStats = new ArrayList<>();
			droppedStats.add(SectionStat.of("Total", UiFormat.gp(trip.getDroppedCost()),
				"Items dropped in the lair and not picked back up before leaving, at GE price. Items under 100 gp are ignored."));
			add(section(new ItemSection(itemManager, "Dropped", trip.getDropped(), null, droppedStats)));
		}
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
