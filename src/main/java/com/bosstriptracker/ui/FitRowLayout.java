package com.bosstriptracker.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * One row where each cell gets its preferred width and the space left over is shared equally, so a narrow value
 * (e.g. a kill count) leaves room for a wide one (e.g. a trip time) instead of every cell getting the same width.
 * If the row is too narrow, cells shrink in proportion to their preferred widths.
 */
class FitRowLayout implements LayoutManager
{
	private final int gap;

	FitRowLayout(int gap)
	{
		this.gap = gap;
	}

	@Override
	public void layoutContainer(Container parent)
	{
		Insets insets = parent.getInsets();
		Component[] cells = parent.getComponents();
		if (cells.length == 0)
		{
			return;
		}
		int available = parent.getWidth() - insets.left - insets.right - gap * (cells.length - 1);
		int height = parent.getHeight() - insets.top - insets.bottom;

		int[] widths = new int[cells.length];
		int preferred = 0;
		for (int i = 0; i < cells.length; i++)
		{
			widths[i] = cells[i].getPreferredSize().width;
			preferred += widths[i];
		}

		int used = 0;
		for (int i = 0; i < cells.length; i++)
		{
			widths[i] = preferred <= available
				? widths[i] + (available - preferred) / cells.length
				: (int) ((long) widths[i] * available / Math.max(1, preferred));
			used += widths[i];
		}
		// Rounding leftovers go to the last cell
		widths[cells.length - 1] += available - used;

		int x = insets.left;
		for (int i = 0; i < cells.length; i++)
		{
			cells[i].setBounds(x, insets.top, widths[i], height);
			x += widths[i] + gap;
		}
	}

	@Override
	public Dimension preferredLayoutSize(Container parent)
	{
		Insets insets = parent.getInsets();
		int width = 0;
		int height = 0;
		for (Component cell : parent.getComponents())
		{
			Dimension size = cell.getPreferredSize();
			width += size.width;
			height = Math.max(height, size.height);
		}
		width += gap * Math.max(0, parent.getComponentCount() - 1);
		return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
	}

	@Override
	public Dimension minimumLayoutSize(Container parent)
	{
		Dimension preferred = preferredLayoutSize(parent);
		return new Dimension(0, preferred.height);
	}

	@Override
	public void addLayoutComponent(String name, Component comp)
	{
	}

	@Override
	public void removeLayoutComponent(Component comp)
	{
	}
}
