package com.bosstriptracker.ui;

import com.bosstriptracker.CanvasSection;
import com.bosstriptracker.model.TripEndReason;
import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.view.TripView;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

class CurrentTripPanel extends JPanel
{
	private final ItemManager itemManager;
	private final SectionStates sectionStates;
	private final GoalCard goalCard;
	private final JLabel status = new JLabel();
	private final TripSummaryCard summary = new TripSummaryCard();
	/**
	 * The share card's luck section as a panel card.
	 */
	private final LuckOverviewCard luckOverview;
	private final JPanel luckHolder = new JPanel(new BorderLayout());
	private final JPanel detailsHolder = new JPanel();
	private TripView shownDetails;
	private TripDetails details;

	CurrentTripPanel(ItemManager itemManager, SectionStates sectionStates, PanelActions actions, Runnable onSetGoal,
		Runnable onPause, Runnable onResetGoal, Runnable onSetLastUniqueKc, Runnable onClearLastUniqueKc)
	{
		this.itemManager = itemManager;
		this.sectionStates = sectionStates;
		this.goalCard = new GoalCard(itemManager, onSetGoal, onPause, onResetGoal);
		this.luckOverview = new LuckOverviewCard(itemManager, onSetLastUniqueKc, onClearLastUniqueKc);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(UiFormat.MUTED_TEXT);
		status.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
		status.setAlignmentX(LEFT_ALIGNMENT);
		summary.setAlignmentX(LEFT_ALIGNMENT);
		detailsHolder.setLayout(new BoxLayout(detailsHolder, BoxLayout.Y_AXIS));
		detailsHolder.setOpaque(false);
		detailsHolder.setAlignmentX(LEFT_ALIGNMENT);

		goalCard.setAlignmentX(LEFT_ALIGNMENT);
		JPanel goalSpacer = new JPanel();
		goalSpacer.setOpaque(false);
		goalSpacer.setAlignmentX(LEFT_ALIGNMENT);
		goalSpacer.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));

		// The goal and luck (all-time; collapses to its title row with the eye icon), then this trip: its times and
		// profit, then its loot and supplies. Drop chances are on the Lifetime tab
		add(goalCard);
		luckHolder.setOpaque(false);
		luckHolder.setAlignmentX(LEFT_ALIGNMENT);
		luckHolder.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		luckOverview.setAlignmentX(LEFT_ALIGNMENT);
		luckHolder.add(luckOverview, BorderLayout.CENTER);
		add(luckHolder);
		add(goalSpacer);
		add(status);
		add(summary);
		add(detailsHolder);

		// Right-click a card to put its numbers on the overlay; the goal card also restarts its count from a chosen trip
		JMenuItem countFrom = new JMenuItem("Count from...");
		countFrom.addActionListener(e -> onResetGoal.run());
		CanvasMenu.attach(goalCard, CanvasSection.GOAL, actions, countFrom);
		summary.attachCanvasMenus(actions);
	}

	void update(PanelState state, long now)
	{
		TripView trip = state.getCurrentTrip();
		goalCard.setBoss(state.getBoss());
		goalCard.setVisible(state.getLifetime() != null);
		goalCard.setPauseState(state.isPausedInLair(), state.isCanPause());
		goalCard.setGoal(state.getGoal(), now);
		summary.setPaused(state.getPauseText() != null);
		summary.setKillStartedAt(state.getKillStartedAt());
		luckHolder.setVisible(state.getLifetime() != null);
		if (state.getLifetime() != null)
		{
			luckOverview.update(state.getLifetime().getDryness(), state.getBoss());
		}
		UiFormat.setText(status, statusText(state));

		summary.setVisible(trip != null);
		if (trip != null)
		{
			summary.setTrip(trip, now);
		}

		// Rebuilding icon grids resets hovered tooltips, so only when the items changed, and then in place if it can be
		if (!sameItems(trip, shownDetails))
		{
			boolean updated = trip != null && shownDetails != null && details != null
				&& shownDetails.getId().equals(trip.getId()) && details.update(trip);
			shownDetails = trip;
			if (!updated)
			{
				detailsHolder.removeAll();
				details = trip == null ? null : new TripDetails(itemManager, sectionStates, "trip", trip);
				if (details != null)
				{
					detailsHolder.add(details);
				}
			}
		}
		revalidate();
		repaint();
	}

	void tick(long now)
	{
		goalCard.tick(now);
		// The kill timer runs even while the trip clock is paused (e.g. idle before the boss is attacked)
		summary.tick(now);
	}

	private static boolean sameItems(TripView a, TripView b)
	{
		if (a == null || b == null)
		{
			return a == b;
		}
		return a.getKills() == b.getKills()
			&& a.getLoot().equals(b.getLoot())
			&& a.getSupplies().equals(b.getSupplies())
			&& a.getDropped().equals(b.getDropped());
	}

	private static String statusText(PanelState state)
	{
		switch (state.getStatus())
		{
			case LOGGED_OUT:
				return "Log in to track your trips.";
			case LOADING:
				return "Loading history...";
			case UNTRACKED_WORLD:
				return "Not tracked on this world type";
			case IN_TRIP:
				return "Trip in progress";
			case PAUSED:
			case AFK_PAUSED:
				return state.getPauseText();
			default:
				TripView last = state.getCurrentTrip();
				if (last == null)
				{
					return state.getBoss().getEmptyStateText();
				}
				return "Last trip · " + UiFormat.dateTime(last.getStartedAt()) + " · " + endReason(last.getEndReason());
		}
	}

	static String endReason(TripEndReason reason)
	{
		if (reason == null)
		{
			return "in progress";
		}
		switch (reason)
		{
			case WALKED_OUT:
				return "walked out";
			case TELEPORT:
				return "teleported";
			case DEATH:
				return "died";
			case LOGOUT:
				return "logged out";
			default:
				return reason.name().toLowerCase();
		}
	}
}
