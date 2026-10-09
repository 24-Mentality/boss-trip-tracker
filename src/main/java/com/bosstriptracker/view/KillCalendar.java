package com.bosstriptracker.view;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * Tracked kills by calendar day, for the History tab's charts: a week or a month as a bar per day, a year as a bar per
 * month, and a year as a heatmap of days. Weeks start on Monday. Only kills this plugin saw have a date.
 */
public final class KillCalendar
{
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

	public enum Period
	{
		WEEK("Week"),
		MONTH("Month"),
		YEAR("Year");

		private final String label;

		Period(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	@Value
	public static class Bar
	{
		/**
		 * Under the bar: "M" for a weekday, "12" for a day of the month, "J" for a month; may be empty.
		 */
		String label;
		/**
		 * For the tooltip, e.g. "Mon 6 Oct 2026" or "October 2026".
		 */
		String name;
		int kills;
	}

	/**
	 * Kills today, this week, this month and this year.
	 */
	@Value
	public static class Totals
	{
		int today;
		int week;
		int month;
		int year;
	}

	private KillCalendar()
	{
	}

	/**
	 * The first day of the period that contains {@code day}.
	 */
	public static LocalDate start(Period period, LocalDate day)
	{
		switch (period)
		{
			case WEEK:
				return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			case MONTH:
				return day.withDayOfMonth(1);
			default:
				return day.withDayOfYear(1);
		}
	}

	/**
	 * The first day after the period starting at {@code start}.
	 */
	public static LocalDate end(Period period, LocalDate start)
	{
		switch (period)
		{
			case WEEK:
				return start.plusWeeks(1);
			case MONTH:
				return start.plusMonths(1);
			default:
				return start.plusYears(1);
		}
	}

	/**
	 * The period {@code steps} before (negative) or after the one starting at {@code start}.
	 */
	public static LocalDate step(Period period, LocalDate start, int steps)
	{
		switch (period)
		{
			case WEEK:
				return start.plusWeeks(steps);
			case MONTH:
				return start.plusMonths(steps);
			default:
				return start.plusYears(steps);
		}
	}

	/**
	 * E.g. "29 Sep - 5 Oct 2026", "October 2026" or "2026".
	 */
	public static String title(Period period, LocalDate start)
	{
		switch (period)
		{
			case WEEK:
				LocalDate last = start.plusDays(6);
				return SHORT_DAY.format(start) + " - " + SHORT_DAY.format(last) + " " + last.getYear();
			case MONTH:
				return MONTH.format(start);
			default:
				return String.valueOf(start.getYear());
		}
	}

	/**
	 * A bar per day for a week or month, a bar per month for a year.
	 */
	public static List<Bar> bars(Map<LocalDate, Integer> kills, Period period, LocalDate start)
	{
		List<Bar> bars = new ArrayList<>();
		if (period == Period.YEAR)
		{
			for (int month = 1; month <= 12; month++)
			{
				YearMonth ym = YearMonth.of(start.getYear(), month);
				int total = total(kills, ym.atDay(1), ym.plusMonths(1).atDay(1));
				bars.add(new Bar(ym.getMonth().getDisplayName(TextStyle.NARROW, Locale.ENGLISH), MONTH.format(ym), total));
			}
			return bars;
		}
		LocalDate end = end(period, start);
		for (LocalDate day = start; day.isBefore(end); day = day.plusDays(1))
		{
			String label = period == Period.WEEK ? day.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
				: day.getDayOfMonth() == 1 || day.getDayOfMonth() % 7 == 1 ? String.valueOf(day.getDayOfMonth()) : "";
			bars.add(new Bar(label, DAY.format(day), kills.getOrDefault(day, 0)));
		}
		return bars;
	}

	/**
	 * Kills from {@code from} up to (not including) {@code to}.
	 */
	public static int total(Map<LocalDate, Integer> kills, LocalDate from, LocalDate to)
	{
		int total = 0;
		for (Map.Entry<LocalDate, Integer> e : kills.entrySet())
		{
			if (!e.getKey().isBefore(from) && e.getKey().isBefore(to))
			{
				total += e.getValue();
			}
		}
		return total;
	}

	public static Totals totals(Map<LocalDate, Integer> kills, LocalDate today)
	{
		return new Totals(kills.getOrDefault(today, 0),
			total(kills, start(Period.WEEK, today), today.plusDays(1)),
			total(kills, start(Period.MONTH, today), today.plusDays(1)),
			total(kills, start(Period.YEAR, today), today.plusDays(1)));
	}

	/**
	 * For a day's tooltip, e.g. "Mon 6 Oct 2026".
	 */
	public static String dayName(LocalDate day)
	{
		return DAY.format(day);
	}

	/**
	 * Heatmap shade for a day: 0 for no kills, else 1 (fewest) to 4 (most), relative to the busiest day shown.
	 */
	public static int level(int kills, int most)
	{
		if (kills <= 0 || most <= 0)
		{
			return 0;
		}
		return Math.max(1, Math.min(4, (int) Math.ceil(4.0 * kills / most)));
	}
}
