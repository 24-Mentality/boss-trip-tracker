package com.bosstriptracker.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.SwingUtilities;

/**
 * Two equal columns, like a two-column GridLayout, unless the widest cell wouldn't fit in half the width (a long
 * loot category name with a large value); then one column, so no cell is ever cut off.
 */
class StatGridLayout implements LayoutManager
{
	private final int hgap;
	/**
	 * The column count the last preferred size was worked out with, to catch a width change that needs more rows.
	 */
	private int assumedColumns = 2;

	StatGridLayout(int hgap)
	{
		this.hgap = hgap;
	}

	/**
	 * @param available the width inside the container's insets
	 */
	int columns(Container parent, int available)
	{
		int widest = 0;
		for (Component cell : parent.getComponents())
		{
			widest = Math.max(widest, cell.getPreferredSize().width);
		}
		return available <= 0 || 2 * widest + hgap <= available ? 2 : 1;
	}

	@Override
	public void layoutContainer(Container parent)
	{
		Insets insets = parent.getInsets();
		int available = parent.getWidth() - insets.left - insets.right;
		int columns = columns(parent, available);
		if (columns != assumedColumns)
		{
			// The height was worked out for the other column count; ask for a new one
			assumedColumns = columns;
			SwingUtilities.invokeLater(parent::revalidate);
		}
		int cellWidth = (available - hgap * (columns - 1)) / columns;
		int rowHeight = rowHeight(parent);
		Component[] cells = parent.getComponents();
		for (int i = 0; i < cells.length; i++)
		{
			int x = insets.left + (i % columns) * (cellWidth + hgap);
			int y = insets.top + (i / columns) * rowHeight;
			cells[i].setBounds(x, y, cellWidth, rowHeight);
		}
	}

	@Override
	public Dimension preferredLayoutSize(Container parent)
	{
		Insets insets = parent.getInsets();
		int available = parent.getWidth() - insets.left - insets.right;
		int columns = columns(parent, available);
		assumedColumns = columns;
		int count = parent.getComponentCount();
		int rows = (count + columns - 1) / columns;
		int widest = 0;
		for (Component cell : parent.getComponents())
		{
			widest = Math.max(widest, cell.getPreferredSize().width);
		}
		return new Dimension(insets.left + insets.right + widest,
			insets.top + insets.bottom + rows * rowHeight(parent));
	}

	@Override
	public Dimension minimumLayoutSize(Container parent)
	{
		return preferredLayoutSize(parent);
	}

	private static int rowHeight(Container parent)
	{
		int height = 0;
		for (Component cell : parent.getComponents())
		{
			height = Math.max(height, cell.getPreferredSize().height);
		}
		return height;
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
