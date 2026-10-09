package com.bosstriptracker.ui;

import com.bosstriptracker.CanvasSection;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import javax.swing.Icon;
import javax.swing.JLabel;
import net.runelite.client.ui.ColorScheme;

/**
 * A small pin on a panel card that puts its numbers on the overlay, or takes them off: filled while they're shown.
 */
class CanvasPin extends JLabel
{
	private final CanvasSection section;
	private final PanelActions actions;

	CanvasPin(CanvasSection section, PanelActions actions)
	{
		this.section = section;
		this.actions = actions;
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					actions.toggleCanvas(section);
					refresh();
				}
			}
		});
		refresh();
	}

	/**
	 * Reads whether the card's numbers are on the overlay (after a change in the settings, say).
	 */
	void refresh()
	{
		showPinned(actions.isOnCanvas(section));
	}

	private void showPinned(boolean pinned)
	{
		setIcon(new PinIcon(pinned));
		UiFormat.setToolTip(this, pinned ? "On the overlay: click to remove these numbers from it"
			: "Click to show these numbers on the overlay");
	}

	private static class PinIcon implements Icon
	{
		private static final Color OFF = new Color(120, 120, 120);
		private final boolean pinned;

		PinIcon(boolean pinned)
		{
			this.pinned = pinned;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.translate(x, y);
			g2.setColor(pinned ? ColorScheme.BRAND_ORANGE : OFF);
			g2.setStroke(new BasicStroke(1.5f));
			Ellipse2D head = new Ellipse2D.Double(2, 0.75, 7, 7);
			if (pinned)
			{
				g2.fill(head);
			}
			else
			{
				g2.draw(head);
			}
			g2.drawLine(5, 8, 5, 12);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 11;
		}

		@Override
		public int getIconHeight()
		{
			return 12;
		}
	}
}
