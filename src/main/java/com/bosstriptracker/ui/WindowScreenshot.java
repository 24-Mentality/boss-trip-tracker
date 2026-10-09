package com.bosstriptracker.ui;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.DrawManager;

/**
 * An image of the whole RuneLite window: the game as last drawn, with the sidebar (and whatever panel is open in it)
 * and RuneLite's own title bar around it. The game canvas can't be painted like the rest of the window, so its last
 * frame is drawn in its place. Nothing outside the RuneLite window is captured.
 */
final class WindowScreenshot
{
	private WindowScreenshot()
	{
	}

	/**
	 * @param canvas  the game canvas
	 * @param onImage gets the image on the Swing thread, or nothing if the window isn't showing
	 */
	static void capture(DrawManager drawManager, Component canvas, Consumer<BufferedImage> onImage)
	{
		drawManager.requestNextFrameListener(frame -> SwingUtilities.invokeLater(() ->
		{
			BufferedImage image = compose(canvas, frame);
			if (image != null)
			{
				onImage.accept(image);
			}
		}));
	}

	private static BufferedImage compose(Component canvas, Image frame)
	{
		Window window = SwingUtilities.getWindowAncestor(canvas);
		if (!(window instanceof RootPaneContainer) || !window.isShowing())
		{
			return null;
		}
		JRootPane root = ((RootPaneContainer) window).getRootPane();
		int width = root.getWidth();
		int height = root.getHeight();
		if (width <= 0 || height <= 0)
		{
			return null;
		}
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		root.printAll(g);
		Point at = SwingUtilities.convertPoint(canvas, 0, 0, root);
		g.drawImage(frame, at.x, at.y, canvas.getWidth(), canvas.getHeight(), null);
		g.dispose();
		return image;
	}
}
