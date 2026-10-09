package com.bosstriptracker.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import org.junit.Test;

public class DropBarTest
{
	@Test
	public void expectedBarsAreOneHueGettingBrighter()
	{
		Color empty = DropBar.expectedColor(0);
		Color full = DropBar.expectedColor(1);
		assertEquals(new Color(20, 60, 120), empty);
		assertEquals(new Color(40, 130, 230), full);
		// Blue throughout, so it never reads as the red-green scale colour-blind players can't tell apart
		for (int i = 0; i <= 10; i++)
		{
			Color c = DropBar.expectedColor(i / 10.0);
			assertTrue(c.getBlue() > c.getGreen() && c.getGreen() > c.getRed());
		}
	}
}
