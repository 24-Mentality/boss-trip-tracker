package com.bosstriptracker.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * A small camera for the share card button. Drawn in code so no image resource is needed.
 */
class CameraIcon implements Icon
{
	private static final int WIDTH = 18;
	private static final int HEIGHT = 14;

	private final Color color;

	CameraIcon(Color color)
	{
		this.color = color;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.translate(x, y);
		g2.setColor(color);
		g2.setStroke(new BasicStroke(1.5f));
		// Viewfinder bump, body and lens
		g2.fillRect(6, 1, 6, 3);
		g2.draw(new RoundRectangle2D.Double(1, 3.5, WIDTH - 2, HEIGHT - 4.5, 3, 3));
		g2.draw(new Ellipse2D.Double(WIDTH / 2.0 - 3.5, 5.5, 7, 7));
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
