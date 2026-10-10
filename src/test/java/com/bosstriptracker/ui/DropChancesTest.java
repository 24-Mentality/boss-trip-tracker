package com.bosstriptracker.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class DropChancesTest
{
	@Test
	public void expectedBarIsThePartAfterTheWholeDrops()
	{
		assertEquals(0.62, new DropChances.Row(1, 1.62, 0).fraction(), 1e-9);
		assertEquals(0.25, new DropChances.Row(1, 0.25, 0).fraction(), 1e-9);
		// A whole number of expected drops starts an empty bar
		assertEquals(0, new DropChances.Row(1, 3.0, 2).fraction(), 1e-9);
	}
}
