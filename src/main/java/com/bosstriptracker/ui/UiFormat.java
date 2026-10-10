package com.bosstriptracker.ui;

import com.bosstriptracker.model.LuckTier;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

final class UiFormat
{
	static final Color PROFIT = ColorScheme.PROGRESS_COMPLETE_COLOR;
	static final Color LOSS = ColorScheme.PROGRESS_ERROR_COLOR;
	static final Color UNIQUE_BORDER = new Color(255, 196, 0);
	static final Color MUTED_TEXT = ColorScheme.LIGHT_GRAY_COLOR.darker();

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

	private UiFormat()
	{
	}

	/**
	 * Short gp value such as "1.25M" or "-340K".
	 */
	static String gp(long value)
	{
		String formatted = QuantityFormatter.quantityToStackSize(Math.abs(value));
		return value < 0 ? "-" + formatted : formatted;
	}

	/**
	 * The "N" of a 1/N drop rate: whole numbers with separators ("3,500"), otherwise one decimal ("205.6").
	 */
	/**
	 * The share of players this dry, as "1 in 58" (whole numbers from 10 up, "1 in 2.5" below); "-" when it isn't
	 * known.
	 */
	static String streakOdds(double chanceThisDry)
	{
		if (chanceThisDry <= 0 || chanceThisDry > 1)
		{
			return "-";
		}
		double n = 1 / chanceThisDry;
		return "1 in " + (n >= 9.95 ? String.format(Locale.ROOT, "%,d", Math.round(n)) : oneIn(chanceThisDry));
	}

	static String oneIn(double rate)
	{
		double n = 1 / rate;
		return Math.abs(n - Math.round(n)) < 0.05 ? String.format(Locale.ROOT, "%,d", Math.round(n))
			: String.format(Locale.ROOT, "%,.1f", n);
	}

	static Color tierColor(LuckTier tier)
	{
		switch (tier)
		{
			case LUCKY_AS_RUCK:
				return UiFormat.UNIQUE_BORDER;
			case LUCKY:
				return UiFormat.PROFIT;
			case DRY:
				return ColorScheme.BRAND_ORANGE;
			case DRY_AS_RUCK:
				return UiFormat.LOSS;
			default:
				return ColorScheme.LIGHT_GRAY_COLOR;
		}
	}

	static String fullGp(long value)
	{
		return QuantityFormatter.formatNumber(value) + " gp";
	}

	static Color profitColor(long value)
	{
		return value < 0 ? LOSS : PROFIT;
	}

	static String duration(long ms)
	{
		long totalSeconds = Math.max(0, ms / 1000);
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		return hours > 0
			? String.format("%d:%02d:%02d", hours, minutes, seconds)
			: String.format("%d:%02d", minutes, seconds);
	}

	static String killTime(Long ms)
	{
		if (ms == null)
		{
			return "-";
		}
		long tenths = Math.round(ms / 100.0);
		return String.format("%d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10);
	}

	static String date(long epochMs)
	{
		return DATE.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	static String dateTime(long epochMs)
	{
		return DATE_TIME.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
	}

	/**
	 * "Label: value" in the style of RuneLite's XP and loot trackers: grey label, white value.
	 */
	static String pair(String label, String value)
	{
		return pair(label, value, null);
	}

	static String pair(String label, String value, Color valueColor)
	{
		String color = valueColor == null ? "#ffffff"
			: String.format("#%02x%02x%02x", valueColor.getRed(), valueColor.getGreen(), valueColor.getBlue());
		return "<html><font color='#a5a5a5'>" + html(label) + ":</font> <font color='" + color + "'>" + html(value) + "</font></html>";
	}

	/**
	 * Wraps plain text as a tooltip no wider than the sidebar allows comfortably.
	 */
	static String tooltip(String text)
	{
		return "<html><div style='width:180px'>" + html(text).replace("\n", "<br>") + "</div></html>";
	}

	/**
	 * Plain-text help for the Theatre of Blood's team dry streak.
	 *
	 * @param fromGame whether it starts from the game's own count
	 */
	static String teamDryStreakHelp(boolean fromGame)
	{
		return "Raids since you last saw a purple from anyone in your team. This isn't your personal dry streak: a"
			+ " teammate's purple resets it too, and Entry Mode raids count."
			+ (fromGame
			? " It starts from the game's own count (\"You have completed 7 raids since you've seen any purple.\" when"
				+ " you enter the vault), plus raids since."
			: " Counted from the raids this plugin has tracked until the game's own count shows up when you next enter"
				+ " the vault.");
	}

	static String html(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/**
	 * Sets the text only when it changed: setText always lays out and repaints, which adds up on per-second updates.
	 */
	static void setText(JLabel label, String text)
	{
		if (!Objects.equals(label.getText(), text))
		{
			label.setText(text);
		}
	}

	static void setText(AbstractButton button, String text)
	{
		if (!Objects.equals(button.getText(), text))
		{
			button.setText(text);
		}
	}

	static void setToolTip(JComponent component, String tooltip)
	{
		if (!Objects.equals(component.getToolTipText(), tooltip))
		{
			component.setToolTipText(tooltip);
		}
	}

	static void setForeground(JComponent component, Color color)
	{
		if (!Objects.equals(component.getForeground(), color))
		{
			component.setForeground(color);
		}
	}

	/**
	 * Lets every child show the container's right-click menu.
	 */
	static void inheritPopupMenu(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JComponent)
			{
				((JComponent) child).setInheritsPopupMenu(true);
			}
			if (child instanceof Container)
			{
				inheritPopupMenu((Container) child);
			}
		}
	}
}
