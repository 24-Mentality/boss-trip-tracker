package com.bosstriptracker.ui;

import com.bosstriptracker.view.LifetimeView;
import com.bosstriptracker.view.TripView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The History tab: the profit per trip chart and the trip count and net for the selected chip, then one card per
 * completed trip, newest first.
 */
class HistoryPanel extends JPanel
{
	private final ItemManager itemManager;
	private final Consumer<TripView> onDelete;
	private final Set<String> expandedTrips = new HashSet<>();
	private final JLabel chartTitle = new JLabel();
	private final ProfitTrendChart chart = new ProfitTrendChart();
	private final JLabel count = new JLabel();
	private final JPanel cards = new JPanel();
	private List<TripView> shown;

	HistoryPanel(ItemManager itemManager, Consumer<TripView> onDelete)
	{
		this.itemManager = itemManager;
		this.onDelete = onDelete;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		chartTitle.setFont(FontManager.getRunescapeSmallFont());
		chartTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		chartTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		chartTitle.setAlignmentX(LEFT_ALIGNMENT);
		chart.setAlignmentX(LEFT_ALIGNMENT);
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(UiFormat.MUTED_TEXT);
		count.setBorder(BorderFactory.createEmptyBorder(3, 0, 6, 0));
		count.setAlignmentX(LEFT_ALIGNMENT);
		cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
		cards.setOpaque(false);
		cards.setAlignmentX(LEFT_ALIGNMENT);

		add(chartTitle);
		add(chart);
		add(count);
		add(cards);
	}

	/**
	 * @param lifetime the selected boss and chip's totals, for the chart and net; null before the history loads
	 */
	void update(List<TripView> trips, LifetimeView lifetime)
	{
		boolean charted = lifetime != null && !trips.isEmpty();
		chartTitle.setVisible(charted);
		chart.setVisible(charted);
		if (charted)
		{
			int shownTrips = Math.min(lifetime.getNetPerTrip().size(), ProfitTrendChart.MAX_TRIPS);
			chartTitle.setText("Profit per trip (last " + shownTrips + ")");
			chart.setValues(lifetime.getNetPerTrip());
		}

		// Completed trips only, like the list (the Lifetime totals include a trip in progress)
		long net = lifetime == null ? 0 : lifetime.getNetPerTrip().stream().mapToLong(Long::longValue).sum();
		String tripCount = trips.size() + (trips.size() == 1 ? " trip" : " trips");
		count.setText(trips.isEmpty() ? "No completed trips yet."
			: lifetime == null ? tripCount
			: UiFormat.pair(tripCount + " · Net", UiFormat.gp(net), UiFormat.profitColor(net)));
		count.setToolTipText(trips.isEmpty() ? null : UiFormat.tooltip("Right-click a trip to delete it."
			+ (lifetime != null ? " Net profit of these trips: " + UiFormat.fullGp(net) + "." : "")));

		// Rebuilding the cards loses hover and scroll state, so only when the trips changed
		if (trips != shown)
		{
			shown = trips;
			cards.removeAll();
			for (TripView trip : trips)
			{
				TripCard card = new TripCard(itemManager, trip, expandedTrips.contains(trip.getId()), this::toggled, onDelete);
				card.setAlignmentX(LEFT_ALIGNMENT);
				cards.add(card);
			}
		}
		revalidate();
		repaint();
	}

	private void toggled(TripCard card)
	{
		if (card.isExpanded())
		{
			expandedTrips.add(card.getTripId());
		}
		else
		{
			expandedTrips.remove(card.getTripId());
		}
	}
}
