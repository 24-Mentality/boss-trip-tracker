package com.bosstriptracker.ui;

import static org.junit.Assert.assertTrue;
import com.bosstriptracker.view.LifetimeView;
import com.bosstriptracker.view.Words;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.Test;

public class ProfitTrendChartTest
{
	@Test
	public void tooltipShowsTheTripsDayKillsAndNet()
	{
		ProfitTrendChart chart = new ProfitTrendChart();
		chart.setValues(Arrays.asList(-250_000L, 1_234_567L), Arrays.asList(
			new LifetimeView.TripPoint(LocalDate.of(2026, 10, 8), 3, -250_000L),
			new LifetimeView.TripPoint(LocalDate.of(2026, 10, 9), 26, 1_234_567L)), Words.KILLS);
		String last = chart.tooltip(1);
		assertTrue(last, last.contains("Last trip · Fri 9 Oct 2026"));
		assertTrue(last, last.contains("26 kills"));
		assertTrue(last, last.contains("1,234,567 gp"));
		String before = chart.tooltip(0);
		assertTrue(before, before.contains("1 trip before the last · Thu 8 Oct 2026"));
		assertTrue(before, before.contains("-250,000 gp"));
	}

	@Test
	public void tooltipWithoutTripDetailsStillShowsTheNet()
	{
		ProfitTrendChart chart = new ProfitTrendChart();
		chart.setValues(Arrays.asList(5_000L), java.util.Collections.emptyList(), Words.KILLS);
		assertTrue(chart.tooltip(0).contains("Last trip<br>"));
		assertTrue(chart.tooltip(0).contains("5,000 gp"));
	}
}
