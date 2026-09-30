package com.bosstriptracker.ui;

import com.bosstriptracker.view.BossOption;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * The boss dropdown at the top of the panel: each boss's icon and name, with a green dot for the boss whose trip
 * is in progress. Choosing a boss only changes what the tabs show.
 */
class BossSelector extends JComboBox<BossOption>
{
	private static final int ICON_WIDTH = 22;
	private static final int ICON_HEIGHT = 20;
	private static final Color LIVE = ColorScheme.PROGRESS_COMPLETE_COLOR;

	private final ItemManager itemManager;
	private final Map<Integer, ImageIcon> icons = new HashMap<>();
	private List<BossOption> shown;
	/**
	 * Set while the list is changed from code, so it isn't taken as the user choosing a boss.
	 */
	private boolean updating;

	BossSelector(ItemManager itemManager, Consumer<String> onSelect)
	{
		this.itemManager = itemManager;
		setFont(FontManager.getRunescapeFont());
		setForeground(Color.WHITE);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setFocusable(false);
		setMaximumRowCount(12);
		setRenderer(new Renderer());
		setToolTipText("Boss shown in the Trip, History and Lifetime tabs. Entering a boss's area selects it.");
		addActionListener(e ->
		{
			BossOption option = (BossOption) getSelectedItem();
			if (!updating && option != null)
			{
				onSelect.accept(option.getId());
			}
		});
	}

	void update(List<BossOption> options, String selectedId)
	{
		updating = true;
		try
		{
			if (!Objects.equals(options, shown))
			{
				shown = options;
				setModel(new DefaultComboBoxModel<>(options.toArray(new BossOption[0])));
			}
			for (BossOption option : options)
			{
				if (option.getId().equals(selectedId) && getSelectedItem() != option)
				{
					setSelectedItem(option);
				}
			}
		}
		finally
		{
			updating = false;
		}
	}

	private Icon icon(int itemId)
	{
		if (itemManager == null)
		{
			return null;
		}
		return icons.computeIfAbsent(itemId, id ->
		{
			ImageIcon icon = new ImageIcon();
			AsyncBufferedImage image = itemManager.getImage(id);
			Runnable scaled = () ->
			{
				icon.setImage(ImageUtil.resizeImage(image, ICON_WIDTH, ICON_HEIGHT));
				repaint();
			};
			image.onLoaded(scaled);
			scaled.run();
			return icon;
		});
	}

	private class Renderer implements ListCellRenderer<BossOption>
	{
		private final JPanel panel = new JPanel(new BorderLayout(5, 0));
		private final JLabel name = new JLabel();
		private final JLabel dot = new JLabel(new LiveDot());

		Renderer()
		{
			panel.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
			name.setFont(FontManager.getRunescapeFont());
			name.setIconTextGap(5);
			panel.add(name, BorderLayout.CENTER);
			panel.add(dot, BorderLayout.EAST);
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends BossOption> list, BossOption option, int index,
			boolean selected, boolean focused)
		{
			boolean highlighted = selected && index >= 0;
			panel.setBackground(highlighted ? ColorScheme.DARK_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			name.setForeground(Color.WHITE);
			if (option == null)
			{
				name.setText("");
				name.setIcon(null);
				dot.setVisible(false);
				return panel;
			}
			name.setText(option.getName());
			name.setIcon(icon(option.getIconItemId()));
			dot.setVisible(option.isLive());
			dot.setToolTipText(option.isLive() ? "Trip in progress" : null);
			return panel;
		}
	}

	/**
	 * A small green circle marking the boss with a trip in progress.
	 */
	private static class LiveDot implements Icon
	{
		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(LIVE);
			g2.fillOval(x + 1, y + 1, 7, 7);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 9;
		}

		@Override
		public int getIconHeight()
		{
			return 9;
		}
	}

	@Override
	public Dimension getPreferredSize()
	{
		// Room for the icon without the row growing past the tab height
		Dimension size = super.getPreferredSize();
		return new Dimension(size.width, Math.max(size.height, ICON_HEIGHT + 8));
	}
}
