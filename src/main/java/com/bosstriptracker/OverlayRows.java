package com.bosstriptracker;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * The overlay's settings as one value: on or off, and what its three rows show. The first row always shows
 * something, so the box is empty only when the overlay is off. The card menus change it through
 * {@link #with(CanvasSection)} and {@link #without(CanvasSection)}.
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

	public boolean shows(CanvasSection section)
	{
		for (OverlayStat stat : stats())
		{
			if (stat.getSection() == section)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * "Add to canvas": turns the overlay on and, unless a row already shows the card's numbers, puts the card's
	 * default stat in the first empty row, or in the last row when none is empty.
	 */
	public OverlayRows with(CanvasSection section)
	{
		OverlayRows on = new OverlayRows(true, row1, row2, row3);
		if (on.shows(section))
		{
			return on;
		}
		OverlayOptionalStat stat = OverlayOptionalStat.of(section.defaultStat());
		if (row2.getStat() == null)
		{
			return new OverlayRows(true, row1, stat, row3);
		}
		return new OverlayRows(true, row1, row2, stat);
	}

	/**
	 * "Remove from canvas": empties the rows showing the card's numbers, moving the others up. With nothing left
	 * to show, the overlay is turned off and its rows are kept for next time.
	 */
	public OverlayRows without(CanvasSection section)
	{
		List<OverlayStat> kept = new ArrayList<>(3);
		for (OverlayStat stat : stats())
		{
			if (stat.getSection() != section)
			{
				kept.add(stat);
			}
		}
		if (kept.isEmpty())
		{
			return new OverlayRows(false, row1, row2, row3);
		}
		return new OverlayRows(true, kept.get(0),
			kept.size() > 1 ? OverlayOptionalStat.of(kept.get(1)) : OverlayOptionalStat.NOTHING,
			kept.size() > 2 ? OverlayOptionalStat.of(kept.get(2)) : OverlayOptionalStat.NOTHING);
	}
}
