package com.bosstriptracker.ui;

import com.bosstriptracker.view.KillCalendar;
import com.bosstriptracker.view.Words;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The top of the History tab: tracked kills per day as bars for a week or a month (a bar per month for a year), with
 * arrows to step back, a heatmap of the year's days, then kills today, this week, this month and this year, and the
 * most kills in one day (PB) this week, this month, this year and of all time. Hovering a bar or a day shows its kills.
 */
class KillChartsPanel extends JPanel
{
	private static final Color BAR = new Color(64, 196, 99);
	/**
	 * Heatmap shades from fewest to most kills in a day: lighter is fewer, darker is more.
	 */
	private static final Color[] SHADES = {new Color(155, 233, 168), new Color(64, 196, 99), new Color(48, 161, 78),
		new Color(33, 110, 57)};
	private static final Color EMPTY = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final int WEEKS = 53;

	/**
	 * Remembered for the session.
	 */
	private static KillCalendar.Period period = KillCalendar.Period.WEEK;
	/**
	 * Periods back from the current one (0 is this week, month or year).
	 */
	private static int offset;

	private final Supplier<LocalDate> today;
	private final JLabel[] periodTabs = new JLabel[KillCalendar.Period.values().length];
	private final JLabel title = new JLabel();
	private final JLabel next = arrow(1);
	private final BarChart bars = new BarChart();
	private final JLabel heatmapTitle = new JLabel();
	private final Heatmap heatmap = new Heatmap();
	private final JLabel todayKc = stat();
	private final JLabel weekKc = stat();
	private final JLabel monthKc = stat();
	private final JLabel yearKc = stat();
	private final JLabel pbWeek = stat();
	private final JLabel pbMonth = stat();
	private final JLabel pbYear = stat();
	private final JLabel pbAllTime = stat();
	private Map<LocalDate, Integer> kills = Collections.emptyMap();
	private final JLabel heading = new JLabel();
	private Words words = Words.KILLS;
	private LocalDate shownToday;

	KillChartsPanel(Supplier<LocalDate> today)
	{
		this.today = today;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);

		JPanel tabs = new JPanel(new GridLayout(1, 0, 4, 0));
		tabs.setOpaque(false);
		for (KillCalendar.Period p : KillCalendar.Period.values())
		{
			JLabel tab = new JLabel(p.getLabel(), SwingConstants.CENTER);
			tab.setFont(FontManager.getRunescapeSmallFont());
			tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tab.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					period = p;
					offset = 0;
					refresh();
				}
			});
			periodTabs[p.ordinal()] = tab;
			tabs.add(tab);
		}
		heading.setText("Kills");
		heading.setFont(FontManager.getRunescapeBoldFont());
		heading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		top.add(heading, BorderLayout.WEST);
		top.add(tabs, BorderLayout.EAST);

		title.setFont(FontManager.getRunescapeSmallFont());
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		title.setHorizontalAlignment(SwingConstants.CENTER);
		JPanel nav = new JPanel(new BorderLayout());
		nav.setOpaque(false);
		nav.add(arrow(-1), BorderLayout.WEST);
		nav.add(title, BorderLayout.CENTER);
		nav.add(next, BorderLayout.EAST);

		heatmapTitle.setFont(FontManager.getRunescapeSmallFont());
		heatmapTitle.setForeground(UiFormat.MUTED_TEXT);
		heatmapTitle.setBorder(BorderFactory.createEmptyBorder(4, 0, 1, 0));

		JPanel totals = new JPanel(new GridLayout(4, 2, 4, 0));
		totals.setOpaque(false);
		totals.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
		totals.add(todayKc);
		totals.add(weekKc);
		totals.add(monthKc);
		totals.add(yearKc);
		totals.add(pbWeek);
		totals.add(pbMonth);
		totals.add(pbYear);
		totals.add(pbAllTime);

		for (JComponent c : new JComponent[]{top, nav, bars, heatmapTitle, heatmap, totals})
		{
			c.setAlignmentX(LEFT_ALIGNMENT);
			add(c);
		}
		setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
	}

	/**
	 * @param kills tracked kills per day for the shown boss and chip
	 */
	void update(Map<LocalDate, Integer> kills, Words words)
	{
		LocalDate now = today.get();
		if (kills.equals(this.kills) && now.equals(shownToday) && words.equals(this.words))
		{
			return;
		}
		this.kills = kills;
		this.words = words;
		heading.setText(words.unitsTitle());
		refresh();
	}

	private void refresh()
	{
		LocalDate now = today.get();
		shownToday = now;
		LocalDate start = KillCalendar.step(period, KillCalendar.start(period, now), offset);
		for (KillCalendar.Period p : KillCalendar.Period.values())
		{
			periodTabs[p.ordinal()].setForeground(p == period ? Color.WHITE : UiFormat.MUTED_TEXT);
		}
		List<KillCalendar.Bar> shown = KillCalendar.bars(kills, period, start);
		int total = shown.stream().mapToInt(KillCalendar.Bar::getKills).sum();
		UiFormat.setText(title, KillCalendar.title(period, start) + String.format(Locale.ROOT, " · %,d", total));
		next.setVisible(offset < 0);
		bars.setBars(shown);

		int year = start.getYear();
		LocalDate jan1 = LocalDate.of(year, 1, 1);
		UiFormat.setText(heatmapTitle, year + " · " + words.count(KillCalendar.total(kills, jan1, jan1.plusYears(1))));
		heatmap.setYear(year);

		KillCalendar.Totals totals = KillCalendar.totals(kills, now);
		UiFormat.setText(todayKc, UiFormat.pair("Today", words.kc(totals.getToday())));
		UiFormat.setText(weekKc, UiFormat.pair("This week", words.kc(totals.getWeek())));
		UiFormat.setText(monthKc, UiFormat.pair("This month", words.kc(totals.getMonth())));
		UiFormat.setText(yearKc, UiFormat.pair("This year", words.kc(totals.getYear())));
		setBest(pbWeek, "week", "this week", totals.getBestWeek());
		setBest(pbMonth, "month", "this month", totals.getBestMonth());
		setBest(pbYear, "year", "this year", totals.getBestYear());
		setBest(pbAllTime, "all", "of all time", totals.getBestAllTime());
		revalidate();
		repaint();
	}

	/**
	 * E.g. "PB (week): 200 kc", with the day in the tooltip.
	 */
	private void setBest(JLabel label, String period, String periodWords, KillCalendar.BestDay best)
	{
		UiFormat.setText(label, UiFormat.pair("PB (" + period + ")", best == null ? "-" : words.kc(best.getKills())));
		UiFormat.setToolTip(label, UiFormat.tooltip(best == null
			? "No tracked " + words.units() + " " + periodWords + " yet."
			: "Most " + words.units() + " in one day " + periodWords + ": " + words.kc(best.getKills()) + ", on "
				+ KillCalendar.dayName(best.getDay()) + "."));
	}

	private JLabel arrow(int step)
	{
		JLabel arrow = new JLabel(new ArrowIcon(step < 0));
		arrow.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
		arrow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		arrow.setToolTipText(step < 0 ? "Earlier" : "Later");
		arrow.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				offset = Math.min(0, offset + step);
				refresh();
			}
		});
		return arrow;
	}

	/**
	 * A small triangle pointing left or right (the RuneScape font has no arrow characters).
	 */
	private static class ArrowIcon implements Icon
	{
		private final boolean left;

		ArrowIcon(boolean left)
		{
			this.left = left;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			int w = getIconWidth();
			int h = getIconHeight();
			int[] xs = left ? new int[]{x + w, x, x + w} : new int[]{x, x + w, x};
			g2.fillPolygon(xs, new int[]{y, y + h / 2, y + h}, 3);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 5;
		}

		@Override
		public int getIconHeight()
		{
			return 9;
		}
	}

	private static JLabel stat()
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	/**
	 * Bars with their labels underneath; hovering a bar shows its day (or month) and kills.
	 */
	private class BarChart extends JComponent
	{
		private static final int HEIGHT = 64;
		private static final int LABEL = 11;
		private List<KillCalendar.Bar> bars = Collections.emptyList();

		BarChart()
		{
			setFont(FontManager.getRunescapeSmallFont());
			setPreferredSize(new Dimension(0, HEIGHT));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
			ToolTipManager.sharedInstance().registerComponent(this);
		}

		void setBars(List<KillCalendar.Bar> bars)
		{
			this.bars = bars;
			repaint();
		}

		@Override
		public String getToolTipText(MouseEvent e)
		{
			int i = index(e.getX());
			if (i < 0)
			{
				return null;
			}
			KillCalendar.Bar bar = bars.get(i);
			return bar.getName() + ": " + words.kc(bar.getKills());
		}

		private int index(int x)
		{
			if (bars.isEmpty() || getWidth() <= 0)
			{
				return -1;
			}
			int i = x * bars.size() / getWidth();
			return i >= 0 && i < bars.size() ? i : -1;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			int w = getWidth();
			int chartHeight = getHeight() - LABEL - 1;
			int most = Math.max(1, bars.stream().mapToInt(KillCalendar.Bar::getKills).max().orElse(0));
			FontMetrics metrics = g2.getFontMetrics(getFont());
			g2.setFont(getFont());
			g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
			g2.fillRect(0, 0, w, chartHeight);
			int n = bars.size();
			for (int i = 0; i < n; i++)
			{
				int x0 = i * w / n;
				int x1 = (i + 1) * w / n;
				int gap = x1 - x0 > 3 ? 1 : 0;
				KillCalendar.Bar bar = bars.get(i);
				int h = (int) Math.round((chartHeight - 2) * (double) bar.getKills() / most);
				if (h > 0)
				{
					g2.setColor(BAR);
					g2.fillRect(x0 + gap, chartHeight - h, Math.max(1, x1 - x0 - 2 * gap), h);
				}
				if (!bar.getLabel().isEmpty())
				{
					g2.setColor(UiFormat.MUTED_TEXT);
					int lx = x0 + (x1 - x0 - metrics.stringWidth(bar.getLabel())) / 2;
					g2.drawString(bar.getLabel(), Math.max(0, Math.min(w - metrics.stringWidth(bar.getLabel()), lx)),
						getHeight() - 1);
				}
			}
			// The scale, top right
			String scale = String.format(Locale.ROOT, "%,d", most);
			g2.setColor(UiFormat.MUTED_TEXT);
			g2.drawString(scale, w - metrics.stringWidth(scale) - 2, metrics.getAscent());
			g2.dispose();
		}
	}

	/**
	 * A year's days as small squares, a column per week (Monday at the top); darker squares had more kills.
	 */
	private class Heatmap extends JComponent
	{
		private int year;

		Heatmap()
		{
			setPreferredSize(new Dimension(0, 7 * 4));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 7 * 4));
			ToolTipManager.sharedInstance().registerComponent(this);
		}

		void setYear(int year)
		{
			this.year = year;
			repaint();
		}

		private int cell()
		{
			return Math.max(2, getWidth() / WEEKS);
		}

		private LocalDate firstMonday()
		{
			return KillCalendar.start(KillCalendar.Period.WEEK, LocalDate.of(year, 1, 1));
		}

		@Override
		public Dimension getPreferredSize()
		{
			int width = getParent() == null ? 0 : getParent().getWidth();
			int cell = Math.max(2, width / WEEKS);
			return new Dimension(0, 7 * cell);
		}

		@Override
		public String getToolTipText(MouseEvent e)
		{
			int cell = cell();
			int week = e.getX() / cell;
			int dow = e.getY() / cell;
			if (week < 0 || week >= WEEKS || dow < 0 || dow >= 7)
			{
				return null;
			}
			LocalDate day = firstMonday().plusDays(week * 7L + dow);
			if (day.getYear() != year)
			{
				return null;
			}
			int count = kills.getOrDefault(day, 0);
			return KillCalendar.dayName(day) + ": " + words.kc(count);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			int cell = cell();
			int most = 0;
			for (Map.Entry<LocalDate, Integer> e : kills.entrySet())
			{
				if (e.getKey().getYear() == year)
				{
					most = Math.max(most, e.getValue());
				}
			}
			LocalDate start = firstMonday();
			for (int week = 0; week < WEEKS; week++)
			{
				for (int dow = 0; dow < 7; dow++)
				{
					LocalDate day = start.plusDays(week * 7L + dow);
					if (day.getYear() != year)
					{
						continue;
					}
					int level = KillCalendar.level(kills.getOrDefault(day, 0), most);
					g.setColor(level == 0 ? EMPTY : SHADES[level - 1]);
					g.fillRect(week * cell, dow * cell, cell - 1, cell - 1);
				}
			}
		}
	}
}
