package com.bosstriptracker.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;
import javax.swing.JComponent;
import net.runelite.client.ui.FontManager;

/**
 * A bar in the drop chances card. Expected mode fills left to right in blue, brighter as it fills, with a percentage;
 * received mode grows from the centre, blue to the right when ahead of expectation and orange to the left when
 * behind, with a + or − difference. Blue and orange stay apart for colour-blind players, unlike green and red.
 */
class DropBar extends JComponent
{
	private static final Color BACKGROUND = new Color(30, 30, 30);
	private static final Color BORDER = new Color(165, 165, 165);
	private static final Color CENTRE_LINE = new Color(192, 192, 192);
	private static final Color AHEAD = new Color(30, 110, 210);
	private static final Color BEHIND = new Color(220, 120, 20);

	private boolean received;
	private double fraction;
	private double difference;
	private double scale = 1;

	DropBar()
	{
		setPreferredSize(new Dimension(0, 20));
		setFont(FontManager.getRunescapeSmallFont());
	}

	void showExpected(double fraction)
	{
		this.received = false;
		this.fraction = Math.max(0, Math.min(1, fraction));
		repaint();
	}

	/**
	 * @param scale the largest difference among the rows (at least 1), which fills half the bar
	 */
	void showReceived(double difference, double scale)
	{
		this.received = true;
		this.difference = difference;
		this.scale = Math.max(1, scale);
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		if (received)
		{
			paintReceived(g2, getFont(), 0, 0, getWidth(), getHeight(), difference, scale);
		}
		else
		{
			paintExpected(g2, getFont(), 0, 0, getWidth(), getHeight(), fraction);
		}
		g2.dispose();
	}

	/**
	 * An Expected bar: filled to {@code fraction} (0 to 1) with its percentage. Also drawn on the share card.
	 */
	static void paintExpected(Graphics2D g, Font font, int x, int y, int width, int height, double fraction)
	{
		fraction = Math.max(0, Math.min(1, fraction));
		g.setColor(BACKGROUND);
		g.fillRect(x, y, width, height);
		g.setColor(expectedColor(fraction));
		g.fillRect(x + 1, y + 1, (int) Math.round((width - 2) * fraction), height - 2);
		FontMetrics metrics = g.getFontMetrics(font);
		String text = String.format(Locale.ROOT, "%.1f%%", fraction * 100);
		g.setFont(font);
		g.setColor(Color.WHITE);
		g.drawString(text, x + (width - metrics.stringWidth(text)) / 2, textY(metrics, y, height));
		g.setColor(BORDER);
		g.drawRect(x, y, width - 1, height - 1);
	}

	/**
	 * A Received bar: from the centre, green to the right when ahead of expectation and red to the left when
	 * behind, with the difference. Also drawn on the share card.
	 *
	 * @param scale the largest difference among the rows (at least 1), which fills half the bar
	 */
	static void paintReceived(Graphics2D g, Font font, int x, int y, int width, int height, double difference, double scale)
	{
		scale = Math.max(1, scale);
		g.setColor(BACKGROUND);
		g.fillRect(x, y, width, height);
		int centre = x + width / 2;
		int half = (width - 2) / 2;
		int length = (int) Math.round(half * Math.min(1, Math.abs(difference) / scale));
		g.setColor(difference >= 0 ? AHEAD : BEHIND);
		if (difference >= 0)
		{
			g.fillRect(centre, y + 1, length, height - 2);
		}
		else
		{
			g.fillRect(centre - length, y + 1, length, height - 2);
		}
		g.setColor(CENTRE_LINE);
		g.drawLine(centre, y + 1, centre, y + height - 2);

		// The number sits on the empty side of the centre line
		FontMetrics metrics = g.getFontMetrics(font);
		String text = String.format(Locale.ROOT, "%+.2f", difference);
		int textWidth = metrics.stringWidth(text);
		g.setFont(font);
		g.setColor(Color.WHITE);
		g.drawString(text, difference >= 0 ? centre - 4 - textWidth : centre + 4, textY(metrics, y, height));
		g.setColor(BORDER);
		g.drawRect(x, y, width - 1, height - 1);
	}

	private static int textY(FontMetrics metrics, int y, int height)
	{
		return y + (height - metrics.getHeight()) / 2 + metrics.getAscent();
	}

	/**
	 * Dark blue at 0%, bright blue at 100%.
	 */
	static Color expectedColor(double fraction)
	{
		fraction = Math.max(0, Math.min(1, fraction));
		return new Color((int) Math.round(20 + 20 * fraction), (int) Math.round(60 + 70 * fraction),
			(int) Math.round(120 + 110 * fraction));
	}
}
