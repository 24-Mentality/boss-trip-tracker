package com.bosstriptracker.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A small caption above a value, sized to fit three across the sidebar.
 */
class StatCell extends JPanel
{
	private final JLabel caption;
	private final JLabel value = new JLabel();

	StatCell(String caption, boolean emphasised)
	{
		super(new BorderLayout());
		setOpaque(false);

		this.caption = new JLabel(caption);
		this.caption.setFont(FontManager.getRunescapeSmallFont());
		this.caption.setForeground(UiFormat.MUTED_TEXT);
		add(this.caption, BorderLayout.NORTH);

		value.setFont(emphasised ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		value.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(value, BorderLayout.CENTER);
	}

	void setCaption(String text)
	{
		UiFormat.setText(caption, text);
	}

	void setValue(String text)
	{
		UiFormat.setText(value, text);
	}

	void setValue(String text, Color color, String tooltip)
	{
		UiFormat.setText(value, text);
		UiFormat.setForeground(value, color);
		UiFormat.setToolTip(value, tooltip);
	}

	/**
	 * Explains the stat when hovering its caption.
	 */
	StatCell help(String text)
	{
		UiFormat.setToolTip(caption, text == null ? null : UiFormat.tooltip(text));
		return this;
	}
}
