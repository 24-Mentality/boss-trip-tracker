package com.bosstriptracker.ui;

import static org.junit.Assert.assertEquals;
import java.awt.Color;
import org.junit.Test;

public class DropBarTest
{
	/**
	 * Colours sampled from the reference screenshot's Expected tab.
	 */
	@Test
	public void expectedColourMatchesReference()
	{
		assertClose(new Color(100, 32, 0), DropBar.expectedColor(0.168));
		assertClose(new Color(100, 64, 0), DropBar.expectedColor(0.329));
		assertClose(new Color(45, 100, 0), DropBar.expectedColor(0.78));
		assertClose(new Color(6, 100, 0), DropBar.expectedColor(0.975));
	}

	private static void assertClose(Color expected, Color actual)
	{
		assertEquals(expected.getRed(), actual.getRed(), 3);
		assertEquals(expected.getGreen(), actual.getGreen(), 3);
		assertEquals(expected.getBlue(), actual.getBlue(), 3);
	}
}
