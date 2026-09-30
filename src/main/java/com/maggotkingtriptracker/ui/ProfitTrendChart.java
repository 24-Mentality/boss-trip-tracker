package com.maggotkingtriptracker.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * Net profit per trip as green/red bars around a zero line, newest on the right.
 */
class ProfitTrendChart extends JPanel
{
	static final int MAX_TRIPS = 50;

	private List<Long> values = Collections.emptyList();

	ProfitTrendChart()
	{
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(0, 56));
	}

	void setValues(List<Long> allTrips)
	{
		values = allTrips.subList(Math.max(0, allTrips.size() - MAX_TRIPS), allTrips.size());
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (values.isEmpty())
		{
			return;
		}

		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		int pad = 4;
		int width = getWidth() - pad * 2;
		int height = getHeight() - pad * 2;

		long max = 0;
		long min = 0;
		for (long v : values)
		{
			max = Math.max(max, v);
			min = Math.min(min, v);
		}
		double range = Math.max(1, max - min);
		int zeroY = pad + (int) Math.round(height * (max / range));

		double slot = (double) width / values.size();
		int barWidth = Math.max(1, (int) Math.floor(slot) - 1);
		for (int i = 0; i < values.size(); i++)
		{
			long v = values.get(i);
			int barHeight = (int) Math.round(height * (Math.abs(v) / range));
			int x = pad + (int) Math.round(i * slot);
			int y = v >= 0 ? zeroY - barHeight : zeroY;
			g2.setColor(UiFormat.profitColor(v));
			g2.fillRect(x, y, barWidth, Math.max(1, barHeight));
		}

		g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g2.drawLine(pad, zeroY, pad + width, zeroY);
	}
}
