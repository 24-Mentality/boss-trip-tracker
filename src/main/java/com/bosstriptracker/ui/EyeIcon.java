package com.bosstriptracker.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * A small eye, crossed out when the section's items are hidden. Drawn in code so no image resource is needed.
 */
class EyeIcon implements Icon
{
	private static final int WIDTH = 16;
	private static final int HEIGHT = 12;
	private static final Color COLOR = new Color(120, 120, 120);

	private final boolean crossed;

	EyeIcon(boolean crossed)
	{
		this.crossed = crossed;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.translate(x, y);
		g2.setColor(COLOR);
		g2.setStroke(new BasicStroke(1.5f));

		Path2D eye = new Path2D.Double();
		eye.moveTo(1, HEIGHT / 2.0);
		eye.quadTo(WIDTH / 2.0, -2, WIDTH - 1, HEIGHT / 2.0);
		eye.quadTo(WIDTH / 2.0, HEIGHT + 2, 1, HEIGHT / 2.0);
		g2.draw(eye);
		g2.fill(new Ellipse2D.Double(WIDTH / 2.0 - 2.5, HEIGHT / 2.0 - 2.5, 5, 5));

		if (crossed)
		{
			g2.setStroke(new BasicStroke(2f));
			g2.drawLine(2, HEIGHT - 1, WIDTH - 2, 1);
		}
		g2.dispose();
	}

	@Override
	public int getIconWidth()
	{
		return WIDTH;
	}

	@Override
	public int getIconHeight()
	{
		return HEIGHT;
	}
}
