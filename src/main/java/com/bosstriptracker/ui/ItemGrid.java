package com.bosstriptracker.ui;

import com.bosstriptracker.view.ItemView;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * Item icons in rows of five with quantity and value tooltips, like the core Loot Tracker.
 * Uniques get a gold border and pending tarnished drops a dashed one.
 */
class ItemGrid extends JPanel
{
	private static final int COLUMNS = 5;
	private static final Border NORMAL_BORDER = BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR, 1);
	private static final Border UNIQUE_BORDER = BorderFactory.createLineBorder(UiFormat.UNIQUE_BORDER, 1);
	private static final Border PENDING_BORDER = BorderFactory.createDashedBorder(ColorScheme.LIGHT_GRAY_COLOR, 3, 2);

	private final ItemManager itemManager;
	private final List<JLabel> cells = new ArrayList<>();
	private List<ItemView> items;

	ItemGrid(ItemManager itemManager, List<ItemView> items)
	{
		this.itemManager = itemManager;
		this.items = items;
		setLayout(new GridLayout(0, COLUMNS, 2, 2));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (ItemView item : items)
		{
			JLabel cell = cell(itemManager, item);
			cells.add(cell);
			add(cell);
		}
		// Pad the last row so icons keep their size
		int remainder = items.size() % COLUMNS;
		for (int i = remainder == 0 ? COLUMNS : remainder; i < COLUMNS; i++)
		{
			add(emptyCell());
		}
	}

	/**
	 * Shows new quantities and values in the cells already there, keeping icons and hovered tooltips.
	 *
	 * @return false if the items themselves changed (added, removed or reordered): build a new grid instead
	 */
	boolean update(List<ItemView> newItems)
	{
		if (!sameLayout(items, newItems))
		{
			return false;
		}
		for (int i = 0; i < newItems.size(); i++)
		{
			ItemView before = items.get(i);
			ItemView after = newItems.get(i);
			if (before.equals(after))
			{
				continue;
			}
			JLabel cell = cells.get(i);
			if (before.getQuantity() != after.getQuantity())
			{
				setImage(itemManager, cell, after);
			}
			UiFormat.setToolTip(cell, tooltip(after));
		}
		items = newItems;
		return true;
	}

	/**
	 * Whether two lists show the same items in the same cells (quantities and values aside).
	 */
	static boolean sameLayout(List<ItemView> a, List<ItemView> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		for (int i = 0; i < a.size(); i++)
		{
			ItemView x = a.get(i);
			ItemView y = b.get(i);
			if (x.getItemId() != y.getItemId() || x.isPerDose() != y.isPerDose() || x.isPending() != y.isPending()
				|| x.isUnique() != y.isUnique() || !Objects.equals(x.getChargeItemName(), y.getChargeItemName())
				|| !Objects.equals(x.getPolishedFromName(), y.getPolishedFromName()))
			{
				return false;
			}
		}
		return true;
	}

	private static void setImage(ItemManager itemManager, JLabel label, ItemView item)
	{
		int shownQuantity = (int) Math.min(item.getQuantity(), Integer.MAX_VALUE);
		AsyncBufferedImage image = itemManager.getImage(item.getItemId(), shownQuantity, shownQuantity > 1);
		image.addTo(label);
	}

	private static JLabel cell(ItemManager itemManager, ItemView item)
	{
		JLabel label = new JLabel();
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setPreferredSize(new Dimension(40, 40));
		label.setOpaque(true);
		label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		label.setBorder(item.isPending() ? PENDING_BORDER : item.isUnique() ? UNIQUE_BORDER : NORMAL_BORDER);

		setImage(itemManager, label, item);
		label.setToolTipText(tooltip(item));
		return label;
	}

	private static JLabel emptyCell()
	{
		JLabel label = new JLabel();
		label.setPreferredSize(new Dimension(40, 40));
		label.setOpaque(true);
		label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return label;
	}

	private static String tooltip(ItemView item)
	{
		StringBuilder sb = new StringBuilder("<html>")
			.append(UiFormat.html(item.getName()));
		if (item.isCharges())
		{
			sb.append(": ").append(QuantityFormatter.formatNumber(item.getQuantity()))
				.append(item.getQuantity() == 1 ? " charge" : " charges")
				.append("<br>").append(QuantityFormatter.formatNumber(item.getChargesPerItem()))
				.append(item.getChargesPerItem() == 1 ? " charge from " : " charges from ")
				.append(UiFormat.html(item.getChargeItemName()))
				.append("<br>").append(UiFormat.fullGp(item.getTotalValue()));
			if (item.getNote() != null)
			{
				sb.append("<br><i>").append(UiFormat.html(item.getNote())).append("</i>");
			}
			return sb.append("</html>").toString();
		}
		sb.append(" x ").append(QuantityFormatter.formatNumber(item.getQuantity()));
		if (item.isPerDose())
		{
			sb.append(item.getQuantity() == 1 ? " dose" : " doses");
		}
		if (item.getPolishedFromName() != null)
		{
			sb.append("<br>Polished from ").append(UiFormat.html(item.getPolishedFromName()));
		}
		if (item.isPending())
		{
			sb.append("<br>Pending: value is known once polished");
		}
		else
		{
			sb.append("<br>").append(UiFormat.fullGp(item.getTotalValue()));
		}
		return sb.append("</html>").toString();
	}

	static JLabel emptyMessage(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(UiFormat.MUTED_TEXT);
		label.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
		return label;
	}
}
