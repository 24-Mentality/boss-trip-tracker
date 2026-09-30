package com.bosstriptracker.ui;

import java.awt.Color;
import lombok.Value;

/**
 * One "Label: value" stat in a section header, with a hover explanation of how it's calculated.
 */
@Value
class SectionStat
{
	String label;
	String value;
	/**
	 * Null for the default white.
	 */
	Color valueColor;
	String tooltip;

	static SectionStat of(String label, String value, String tooltip)
	{
		return new SectionStat(label, value, null, tooltip);
	}
}
