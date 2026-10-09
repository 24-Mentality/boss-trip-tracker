package com.bosstriptracker.view;

import static org.junit.Assert.assertEquals;
import com.google.common.collect.ImmutableMap;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class KillCalendarTest
{
	private static final LocalDate THU = LocalDate.of(2026, 10, 8);
	private static final Map<LocalDate, Integer> KILLS = ImmutableMap.of(
		LocalDate.of(2026, 10, 5), 10,
		LocalDate.of(2026, 10, 8), 3,
		LocalDate.of(2026, 9, 30), 7,
		LocalDate.of(2025, 12, 31), 2);

	@Test
	public void weeksStartOnMonday()
	{
		LocalDate start = KillCalendar.start(KillCalendar.Period.WEEK, THU);
		assertEquals(LocalDate.of(2026, 10, 5), start);
		assertEquals("5 Oct - 11 Oct 2026", KillCalendar.title(KillCalendar.Period.WEEK, start));

		List<KillCalendar.Bar> bars = KillCalendar.bars(KILLS, KillCalendar.Period.WEEK, start);
		assertEquals(7, bars.size());
		assertEquals("M", bars.get(0).getLabel());
		assertEquals(10, bars.get(0).getKills());
		assertEquals(3, bars.get(3).getKills());
		assertEquals("Thu 8 Oct 2026", bars.get(3).getName());
	}

	@Test
	public void aMonthHasADayPerBarAndAYearAMonthPerBar()
	{
		List<KillCalendar.Bar> month = KillCalendar.bars(KILLS, KillCalendar.Period.MONTH,
			KillCalendar.start(KillCalendar.Period.MONTH, THU));
		assertEquals(31, month.size());
		assertEquals("1", month.get(0).getLabel());
		assertEquals("", month.get(1).getLabel());
		assertEquals(13, month.stream().mapToInt(KillCalendar.Bar::getKills).sum());

		List<KillCalendar.Bar> year = KillCalendar.bars(KILLS, KillCalendar.Period.YEAR,
			KillCalendar.start(KillCalendar.Period.YEAR, THU));
		assertEquals(12, year.size());
		assertEquals("O", year.get(9).getLabel());
		assertEquals(13, year.get(9).getKills());
		assertEquals(7, year.get(8).getKills());
		assertEquals("October 2026", year.get(9).getName());
	}

	@Test
	public void stepsBackAndForth()
	{
		LocalDate start = KillCalendar.start(KillCalendar.Period.MONTH, THU);
		assertEquals(LocalDate.of(2026, 9, 1), KillCalendar.step(KillCalendar.Period.MONTH, start, -1));
		assertEquals("2025", KillCalendar.title(KillCalendar.Period.YEAR,
			KillCalendar.step(KillCalendar.Period.YEAR, KillCalendar.start(KillCalendar.Period.YEAR, THU), -1)));
	}

	@Test
	public void totalsForTodayThisWeekMonthAndYear()
	{
		KillCalendar.Totals totals = KillCalendar.totals(KILLS, THU);
		assertEquals(3, totals.getToday());
		assertEquals(13, totals.getWeek());
		assertEquals(13, totals.getMonth());
		assertEquals(20, totals.getYear());
	}

	@Test
	public void heatmapLevels()
	{
		assertEquals(0, KillCalendar.level(0, 10));
		assertEquals(1, KillCalendar.level(1, 10));
		assertEquals(2, KillCalendar.level(5, 10));
		assertEquals(4, KillCalendar.level(10, 10));
	}
}
