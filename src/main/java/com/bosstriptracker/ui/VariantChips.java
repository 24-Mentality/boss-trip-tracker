package com.bosstriptracker.ui;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.BossVariant;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A row of chips under the boss dropdown for bosses with variants, e.g. Theatre of Blood [All | Normal | Hard].
 * Hidden for bosses without variants.
 */
class VariantChips extends JPanel
{
	private final Consumer<String> onSelect;
	private BossDefinition boss;
	private String selected;
	private final List<JLabel> chips = new ArrayList<>();
	private final List<String> ids = new ArrayList<>();

	VariantChips(Consumer<String> onSelect)
	{
		super(new GridLayout(1, 0, 3, 0));
		this.onSelect = onSelect;
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		setVisible(false);
	}

	/**
	 * @param variant selected variant id, or null for All
	 */
	void update(BossDefinition boss, String variant)
	{
		if (boss != this.boss)
		{
			this.boss = boss;
			rebuild();
		}
		selected = variant;
		style();
	}

	private void rebuild()
	{
		removeAll();
		chips.clear();
		ids.clear();
		List<BossVariant> variants = boss.getShownVariants();
		setVisible(!variants.isEmpty());
		if (variants.isEmpty())
		{
			return;
		}
		addChip("All", null, "Every variant, each kill at its own rate");
		for (BossVariant variant : variants)
		{
			if (variant.isBeta())
			{
				addChip(variant.getLabel() + " (beta)", variant.getId(), "Only " + variant.getLabel()
					+ ". Beta: not yet confirmed in game, so its tracking and numbers may be off");
			}
			else
			{
				addChip(variant.getLabel(), variant.getId(), "Only " + variant.getLabel());
			}
		}
		revalidate();
	}

	private void addChip(String label, String id, String tooltip)
	{
		JLabel chip = new JLabel(label, SwingConstants.CENTER);
		chip.setFont(FontManager.getRunescapeSmallFont());
		chip.setOpaque(true);
		chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		chip.setToolTipText(tooltip);
		chip.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (!Objects.equals(id, selected))
				{
					selected = id;
					style();
					onSelect.accept(id);
				}
			}
		});
		chips.add(chip);
		ids.add(id);
		add(chip);
	}

	private void style()
	{
		for (int i = 0; i < chips.size(); i++)
		{
			boolean on = Objects.equals(ids.get(i), selected);
			JLabel chip = chips.get(i);
			chip.setBackground(on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
			chip.setForeground(on ? Color.WHITE : UiFormat.MUTED_TEXT);
			chip.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		}
	}
}
