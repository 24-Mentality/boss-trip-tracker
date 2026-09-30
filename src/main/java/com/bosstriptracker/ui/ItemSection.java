package com.bosstriptracker.ui;

import com.bosstriptracker.view.ItemView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A loot-tracker style box: a bordered header with the section title, "Label: value" stats and an eye toggle,
 * above the section's item grid.
 */
class ItemSection extends JPanel
{
	private static final Color HEADER_BORDER = new Color(57, 57, 57);

	/**
	 * Sections the user has hidden, by title, for this client session.
	 */
	private static final Set<String> HIDDEN = new HashSet<>();

	private final String title;
	private final JLabel eye = new JLabel();
	private final JPanel body;

	/**
	 * @param stats label and value pairs, shown two per row, each with a hover explanation
	 */
	ItemSection(ItemManager itemManager, String title, List<ItemView> items, String emptyText, List<SectionStat> stats)
	{
		this(itemManager, title, items, emptyText, stats, null);
	}

	/**
	 * @param titleExtra shown in the title row between the title and the eye (e.g. a Tracked / All-time switch);
	 * null for none
	 */
	ItemSection(ItemManager itemManager, String title, List<ItemView> items, String emptyText, List<SectionStat> stats,
		JComponent titleExtra)
	{
		this.title = title;
		setLayout(new BorderLayout(0, 3));
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		eye.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		eye.setHorizontalAlignment(SwingConstants.RIGHT);
		eye.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				toggle();
			}
		});

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(titleLabel, BorderLayout.WEST);
		if (titleExtra != null)
		{
			titleRow.add(titleExtra, BorderLayout.CENTER);
		}
		titleRow.add(eye, BorderLayout.EAST);

		JPanel header = new JPanel(new BorderLayout(0, 1));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(HEADER_BORDER, 1),
			BorderFactory.createEmptyBorder(3, 6, 4, 6)));
		header.add(titleRow, BorderLayout.NORTH);

		if (!stats.isEmpty())
		{
			JPanel statGrid = new JPanel(new GridLayout(0, 2, 6, 0));
			statGrid.setOpaque(false);
			for (SectionStat stat : stats)
			{
				JLabel label = new JLabel(UiFormat.pair(stat.getLabel(), stat.getValue(), stat.getValueColor()));
				label.setFont(FontManager.getRunescapeSmallFont());
				label.setToolTipText(stat.getTooltip() == null ? null : UiFormat.tooltip(stat.getTooltip()));
				statGrid.add(label);
			}
			header.add(statGrid, BorderLayout.CENTER);
		}

		body = items.isEmpty() ? wrap(ItemGrid.emptyMessage(emptyText)) : new ItemGrid(itemManager, items);

		add(header, BorderLayout.NORTH);
		add(body, BorderLayout.CENTER);
		applyVisibility();
	}

	private static JPanel wrap(JLabel label)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.add(label, BorderLayout.CENTER);
		return panel;
	}

	private void toggle()
	{
		if (!HIDDEN.remove(title))
		{
			HIDDEN.add(title);
		}
		applyVisibility();
	}

	private void applyVisibility()
	{
		boolean hidden = HIDDEN.contains(title);
		body.setVisible(!hidden);
		eye.setIcon(new EyeIcon(hidden));
		eye.setToolTipText(hidden ? "Show items" : "Hide items");
		revalidate();
		repaint();
	}
}
