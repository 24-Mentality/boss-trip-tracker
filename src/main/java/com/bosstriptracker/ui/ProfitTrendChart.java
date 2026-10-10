package com.bosstriptracker.ui;

import com.bosstriptracker.view.KillCalendar;
import com.bosstriptracker.view.LifetimeView;
import com.bosstriptracker.view.Words;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.ColorScheme;

/**
 * Net profit per trip as green/red bars around a zero line, newest on the right; hovering a bar shows the trip's day,
 * kills and net profit.
 */
class ProfitTrendChart extends JPanel
{
	static final int MAX_TRIPS = 50;

	private List<Long> values = Collections.emptyList();
	private List<LifetimeView.TripPoint> points = Collections.emptyList();
	private Words words = Words.KILLS;

	ProfitTrendChart()
	{
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(0, 56));
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		if (values.isEmpty())
		{
			return null;
		}
		int pad = 4;
		double slot = (double) (getWidth() - pad * 2) / values.size();
		int i = (int) Math.floor((e.getX() - pad) / slot);
		if (i < 0 || i >= values.size())
		{
			return null;
		}
		return tooltip(i);
	}

	/**
	 * E.g. "Last trip · Fri 9 Oct 2026" then "26 kills · Net: 1,234,567 gp".
	 */
	String tooltip(int i)
	{
		int back = values.size() - 1 - i;
		String which = back == 0 ? "Last trip" : back + (back == 1 ? " trip" : " trips") + " before the last";
		long net = values.get(i);
		LifetimeView.TripPoint point = i < points.size() ? points.get(i) : null;
		String netText = UiFormat.pair("Net", UiFormat.fullGp(net), UiFormat.profitColor(net))
			.replace("<html>", "").replace("</html>", "");
		return "<html>" + which + (point != null ? " · " + KillCalendar.dayName(point.getDay()) : "") + "<br>"
			+ (point != null ? words.count(point.getKills()) + " · " : "") + netText + "</html>";
	}

	/**
	 * @param allTrips  net profit of every completed trip, oldest first
	 * @param allPoints each one's day and kills, matching {@code allTrips} (may be empty)
	 */
	void setValues(List<Long> allTrips, List<LifetimeView.TripPoint> allPoints, Words words)
	{
		int from = Math.max(0, allTrips.size() - MAX_TRIPS);
		values = allTrips.subList(from, allTrips.size());
		points = allPoints.size() == allTrips.size() ? allPoints.subList(from, allPoints.size()) : Collections.emptyList();
		this.words = words;
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
