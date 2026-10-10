package com.bosstriptracker;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * The overlay's settings as one value: on or off, and what its three rows show. The first row always shows
 * something, so the box is empty only when the overlay is off.
 */
@Value
public class OverlayRows
{
	boolean enabled;
	OverlayStat row1;
	OverlayOptionalStat row2;
	OverlayOptionalStat row3;

	public static OverlayRows of(BossTripTrackerConfig config)
	{
		return new OverlayRows(config.overlayEnabled(), config.overlayRow1(), config.overlayRow2(), config.overlayRow3());
	}

	/**
	 * The stats to draw, top to bottom; empty when the overlay is off.
	 */
	public List<OverlayStat> stats()
	{
		List<OverlayStat> stats = new ArrayList<>(3);
		if (enabled)
		{
			stats.add(row1);
			if (row2.getStat() != null)
			{
				stats.add(row2.getStat());
			}
			if (row3.getStat() != null)
			{
				stats.add(row3.getStat());
			}
		}
		return stats;
	}
}
