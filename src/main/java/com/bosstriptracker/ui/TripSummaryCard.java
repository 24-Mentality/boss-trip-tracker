package com.bosstriptracker.ui;

import com.bosstriptracker.CanvasSection;
import com.bosstriptracker.model.TripMath;
import com.bosstriptracker.view.TripView;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Headline numbers for one trip: a time card (time, kills, average and fastest kill) and a profit card
 * (net, GP/hr, a boss-specific cell, loot, costs, deaths). For a trip in progress, {@link #tick(long)} keeps the timer
 * and GP/hr live.
 */
class TripSummaryCard extends JPanel
{
	private final StatCell time = new StatCell("Time", false)
		.help("Time spent inside the lair this trip. Time outside (banking, logged out) and time paused with the Pause"
			+ " button isn't counted.");
	private final StatCell kills = new StatCell("Kills", false)
		.help("Kills this trip, from the game's kill-count message.");
	private final StatCell averageKill = new StatCell("Avg kill", false)
		.help("Average of the game's \"Fight duration\" for this trip's kills.");
	private final StatCell fastestKill = new StatCell("PB", false)
		.help("This trip's fastest kill (shortest \"Fight duration\"), not your all-time personal best.");
	/**
	 * Live timer for the kill in progress, or the last kill's time between kills.
	 */
	private final StatCell currentKill = new StatCell("Current", false);

	private final StatCell net = new StatCell("Net profit", true)
		.help("Loot minus costs (supplies, dropped items and death costs), at the GE prices recorded at the time.");
	private final StatCell gpPerHour = new StatCell("Net GP/hr", true)
		.help("Net profit per hour of fighting time (loot minus supplies, dropped items and death costs; paused time"
			+ " excluded).");
	private final StatCell loot = new StatCell("Loot", false)
		.help("GE value of everything received, including overflow picked up from the ground. Tarnished drops count"
			+ " once polished.");
	private final StatCell costs = new StatCell("Costs", false)
		.help("Supplies used + items dropped and left behind + death costs. Hover the value for the split.");
	/**
	 * Boss-specific: for the Maggot King, Stom / Eggs.
	 */
	private final StatCell bossStat = new StatCell("", false);
	private final StatCell deaths = new StatCell("Deaths", false)
		.help("Deaths in the lair this trip.");

	private final JPanel timeCard;
	private final JPanel profitCard;
	private final JPanel profitGrid = new JPanel();
	private final JLabel eye = new JLabel();
	/**
	 * Collapsed with the eye icon; remembered for the session, like the other cards.
	 */
	private boolean profitCollapsed;
	private TripView trip;
	private boolean paused;
	private Long killStartedAt;

	TripSummaryCard()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);

		timeCard = card(new FitRowLayout(4), time, kills, averageKill, fastestKill, currentKill);

		// The profit card collapses with the eye icon to just net profit and net GP/hr
		profitGrid.setOpaque(false);
		eye.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		eye.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
		eye.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					profitCollapsed = !profitCollapsed;
					layoutProfit();
				}
			}
		});
		JPanel eyeHolder = new JPanel(new BorderLayout());
		eyeHolder.setOpaque(false);
		eyeHolder.add(eye, BorderLayout.NORTH);
		profitCard = new JPanel(new BorderLayout());
		profitCard.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		profitCard.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		profitCard.setAlignmentX(LEFT_ALIGNMENT);
		profitCard.add(profitGrid, BorderLayout.CENTER);
		profitCard.add(eyeHolder, BorderLayout.EAST);
		layoutProfit();

		JPanel gap = new JPanel();
		gap.setOpaque(false);
		gap.setAlignmentX(LEFT_ALIGNMENT);
		gap.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));

		add(timeCard);
		add(gap);
		add(profitCard);
	}

	void setProfitCollapsed(boolean collapsed)
	{
		profitCollapsed = collapsed;
		layoutProfit();
	}

	/**
	 * All six cells, or collapsed just net profit and net GP/hr, which keep their green / red colours.
	 */
	private void layoutProfit()
	{
		profitGrid.removeAll();
		if (profitCollapsed)
		{
			profitGrid.setLayout(new GridLayout(1, 2, 4, 0));
			profitGrid.add(net);
			profitGrid.add(gpPerHour);
		}
		else
		{
			profitGrid.setLayout(new GridLayout(2, 3, 4, 3));
			for (StatCell cell : new StatCell[]{net, gpPerHour, bossStat, loot, costs, deaths})
			{
				profitGrid.add(cell);
			}
		}
		eye.setIcon(new EyeIcon(profitCollapsed));
		eye.setToolTipText(profitCollapsed ? "Show loot, costs and deaths" : "Show only net profit and net GP/hr");
		revalidate();
		repaint();
	}

	private static JPanel card(LayoutManager layout, StatCell... cells)
	{
		JPanel card = new JPanel(layout);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		card.setAlignmentX(LEFT_ALIGNMENT);
		for (StatCell cell : cells)
		{
			card.add(cell);
		}
		return card;
	}

	void setTrip(TripView trip, long now)
	{
		this.trip = trip;
		kills.setValue(String.valueOf(trip.getKills()));
		averageKill.setValue(UiFormat.killTime(trip.getAverageKillMs()));
		fastestKill.setValue(UiFormat.killTime(trip.getFastestKillMs()));

		net.setValue(UiFormat.gp(trip.getNetProfit()), UiFormat.profitColor(trip.getNetProfit()), UiFormat.fullGp(trip.getNetProfit()));
		loot.setValue(UiFormat.gp(trip.getLootValue()), ColorScheme.LIGHT_GRAY_COLOR, UiFormat.fullGp(trip.getLootValue()));
		long totalCosts = trip.getSupplyCost() + trip.getDroppedCost() + trip.getDeathCost();
		costs.setValue(UiFormat.gp(totalCosts), ColorScheme.LIGHT_GRAY_COLOR, "<html>Supplies: " + UiFormat.fullGp(trip.getSupplyCost())
			+ "<br>Dropped: " + UiFormat.fullGp(trip.getDroppedCost())
			+ "<br>Deaths: " + UiFormat.fullGp(trip.getDeathCost()) + "</html>");
		bossStat.setCaption(trip.getBossStat().getLabel());
		bossStat.help(trip.getBossStat().getHelp());
		bossStat.setValue(trip.getBossStat().getValue());
		deaths.setValue(trip.getDeaths() + (trip.isPet() ? " · Pet!" : ""));
		tick(now);
	}

	/**
	 * Adds the right-click Add to canvas menus: the time card for the overlay's trip row, the profit card for its
	 * profit row.
	 */
	void attachCanvasMenus(PanelActions actions)
	{
		CanvasMenu.attach(timeCard, CanvasSection.TRIP, actions);
		CanvasMenu.attach(profitCard, CanvasSection.LOOT, actions);
	}

	/**
	 * @param killStartedAt when the boss you're fighting spawned; null between kills
	 */
	void setKillStartedAt(Long killStartedAt)
	{
		this.killStartedAt = killStartedAt;
	}

	/**
	 * Greys the timer while the trip is paused with the Pause button.
	 */
	void setPaused(boolean paused)
	{
		this.paused = paused;
	}

	void tick(long now)
	{
		if (trip == null)
		{
			return;
		}
		long activeMs = trip.activeMsAt(now);
		time.setValue(UiFormat.duration(activeMs), paused ? UiFormat.MUTED_TEXT : ColorScheme.LIGHT_GRAY_COLOR,
			paused ? "Paused: the clock resumes when you press Resume" + " or attack the boss (if auto-resume is on)." : null);
		long rate = TripMath.gpPerHour(trip.getNetProfit(), activeMs);
		gpPerHour.setValue(UiFormat.gp(rate), UiFormat.profitColor(rate), UiFormat.fullGp(rate) + " net per hour in the lair");

		if (killStartedAt != null)
		{
			// Counts from the boss spawning, like the game's Fight duration
			currentKill.setCaption("Current");
			currentKill.help("Time since the boss spawned, like the game's \"Fight duration\". It stops at the kill.");
			currentKill.setValue(UiFormat.duration(now - killStartedAt), ColorScheme.LIGHT_GRAY_COLOR, null);
		}
		else
		{
			currentKill.setCaption("Last");
			currentKill.help("The \"Fight duration\" of this trip's last kill. During a kill this shows a live timer.");
			currentKill.setValue(UiFormat.killTime(trip.getLastKillMs()), UiFormat.MUTED_TEXT, null);
		}
	}
}
