package com.bosstriptracker.ui;

import com.bosstriptracker.model.TripEndReason;
import com.bosstriptracker.view.TripView;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One completed trip in the History tab. Click to expand; right-click to delete.
 */
class TripCard extends JPanel
{
	private final ItemManager itemManager;
	private final SectionStates sectionStates;
	private final TripView trip;
	private final JPanel header = new JPanel(new BorderLayout());
	private JPanel body;
	private boolean expanded;

	TripCard(ItemManager itemManager, SectionStates sectionStates, TripView trip, boolean expanded,
		Consumer<TripCard> onToggle, Consumer<TripView> onDelete)
	{
		this.itemManager = itemManager;
		this.sectionStates = sectionStates;
		this.trip = trip;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR));

		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel date = new JLabel(UiFormat.dateTime(trip.getStartedAt()));
		date.setFont(FontManager.getRunescapeBoldFont());
		date.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel net = new JLabel(UiFormat.gp(trip.getNetProfit()));
		net.setFont(FontManager.getRunescapeBoldFont());
		net.setForeground(UiFormat.profitColor(trip.getNetProfit()));
		net.setToolTipText(UiFormat.fullGp(trip.getNetProfit()));

		JLabel summary = new JLabel((trip.getDetail() != null ? trip.getDetail()
			: trip.getKills() == 0 ? "No kills" : trip.getKills() + (trip.getKills() == 1 ? " kill" : " kills"))
			+ " · " + UiFormat.duration(trip.getActiveMs())
			// A completed raid is the usual end, so only other ends are named
			+ (trip.getEndReason() == TripEndReason.COMPLETED ? "" : " · " + CurrentTripPanel.endReason(trip.getEndReason())));
		summary.setFont(FontManager.getRunescapeSmallFont());
		summary.setForeground(UiFormat.MUTED_TEXT);

		header.add(date, BorderLayout.WEST);
		header.add(net, BorderLayout.EAST);
		header.add(summary, BorderLayout.SOUTH);
		add(header, BorderLayout.NORTH);

		JPopupMenu menu = new JPopupMenu();
		JMenuItem delete = new JMenuItem("Delete trip");
		delete.addActionListener(e -> onDelete.accept(trip));
		menu.add(delete);
		header.setComponentPopupMenu(menu);

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					setExpanded(!TripCard.this.expanded);
					onToggle.accept(TripCard.this);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});

		setExpanded(expanded);
	}

	String getTripId()
	{
		return trip.getId();
	}

	boolean isExpanded()
	{
		return expanded;
	}

	private void setExpanded(boolean expanded)
	{
		this.expanded = expanded;
		if (expanded && body == null)
		{
			// Built on first expand so long histories stay cheap
			body = new JPanel(new BorderLayout(0, 4));
			body.setOpaque(false);
			// No side padding: the profit card's six cells need the full width
			body.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
			TripSummaryCard card = new TripSummaryCard();
			card.setTrip(trip, System.currentTimeMillis());
			body.add(card, BorderLayout.NORTH);
			body.add(new TripDetails(itemManager, sectionStates, "history", trip), BorderLayout.CENTER);
			add(body, BorderLayout.CENTER);
		}
		if (body != null)
		{
			body.setVisible(expanded);
		}
		revalidate();
		repaint();
	}
}
