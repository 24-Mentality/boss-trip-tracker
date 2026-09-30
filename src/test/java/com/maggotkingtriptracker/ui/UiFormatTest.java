package com.maggotkingtriptracker.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class UiFormatTest
{
	@Test
	public void oneInShowsWholeRatesWithoutDecimals()
	{
		assertEquals("205.6", UiFormat.oneIn(1 / 205.6));
		assertEquals("340", UiFormat.oneIn(1 / 340.0));
		assertEquals("3,500", UiFormat.oneIn(1 / 3500.0));
		assertEquals("111.1", UiFormat.oneIn(1 / 111.1));
	}
}
