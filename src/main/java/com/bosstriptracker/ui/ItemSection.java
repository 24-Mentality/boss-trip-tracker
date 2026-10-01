package com.bosstriptracker.ui;

import com.bosstriptracker.view.ItemView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
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
 * above the section's item grid. Whether the grid is shown is saved in {@link SectionStates}; the grid is only
 * built once it's first shown.
 */
class ItemSection extends JPanel
{
	private static final Color HEADER_BORDER = new Color(57, 57, 57);

	private final ItemManager itemManager;
	private final SectionStates states;
	private final String stateKey;
	private final boolean defaultOpen;
	private final List<ItemView> items;
	private final String emptyText;
	private final JLabel eye = new JLabel();
	private final Runnable stateListener = this::applyVisibility;
	private JPanel body;

	/**
	 * @param stateKey the key its open state is saved under; boxes sharing a key open and close together
	 * @param defaultOpen whether it's open before the user has toggled it
	 * @param stats label and value pairs, shown two per row, each with a hover explanation
	 */
	ItemSection(ItemManager itemManager, SectionStates states, String stateKey, boolean defaultOpen, String title,
		List<ItemView> items, String emptyText, List<SectionStat> stats)
	{
		this(itemManager, states, stateKey, defaultOpen, title, items, emptyText, stats, null);
	}

	/**
	 * @param titleExtra shown in the title row between the title and the eye (e.g. a Tracked / All-time switch);
	 * null for none
	 */
	ItemSection(ItemManager itemManager, SectionStates states, String stateKey, boolean defaultOpen, String title,
		List<ItemView> items, String emptyText, List<SectionStat> stats, JComponent titleExtra)
	{
		this.itemManager = itemManager;
		this.states = states;
		this.stateKey = stateKey;
		this.defaultOpen = defaultOpen;
		this.items = items;
		this.emptyText = emptyText;
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
			JPanel statGrid = new JPanel(new StatGridLayout(6));
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

		add(header, BorderLayout.NORTH);
		applyVisibility();
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		states.addListener(stateListener);
		applyVisibility();
	}

	@Override
	public void removeNotify()
	{
		states.removeListener(stateListener);
		super.removeNotify();
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
		states.setOpen(stateKey, !states.isOpen(stateKey, defaultOpen));
		applyVisibility();
	}

	boolean isOpen()
	{
		return states.isOpen(stateKey, defaultOpen);
	}

	private void applyVisibility()
	{
		boolean hidden = !isOpen();
		if (!hidden && body == null)
		{
			body = items.isEmpty() ? wrap(ItemGrid.emptyMessage(emptyText)) : new ItemGrid(itemManager, items);
			add(body, BorderLayout.CENTER);
		}
		if (body != null)
		{
			body.setVisible(!hidden);
		}
		eye.setIcon(new EyeIcon(hidden));
		eye.setToolTipText(hidden ? "Show items" : "Hide items");
		revalidate();
		repaint();
	}
}
