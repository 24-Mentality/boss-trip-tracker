package com.bosstriptracker.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.IntFunction;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Draws a {@link ShareCard} in the side panel's style. It is laid out at sidebar-like sizes and drawn at twice the
 * size, so the pixel fonts stay sharp and readable when Discord shrinks the image; the result is 760 px wide.
 */
final class ShareCardRenderer
{
	static final int SCALE = 2;
	static final int WIDTH = 380;

	static final int PAD = 8;
	private static final int GAP = 6;
	static final int INNER = 8;
	private static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color TEXT = ColorScheme.LIGHT_GRAY_COLOR;
	private static final Color ACCENT = ColorScheme.BRAND_ORANGE;
	private static final DateTimeFormatter TRIP_DATE = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter CARD_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private final Font regular = FontManager.getRunescapeFont();
	private final Font bold = FontManager.getRunescapeBoldFont();
	private final Font small = FontManager.getRunescapeSmallFont();
	private final ZoneId zone;

	ShareCardRenderer(ZoneId zone)
	{
		this.zone = zone;
	}

	/**
	 * @param icons item id to its icon; may return null for an icon that isn't available
	 */
	BufferedImage render(ShareCard card, IntFunction<Image> icons)
	{
		int height = PAD + header() + GAP + luck(card) + GAP + chances(card) + GAP + stats() + GAP + trips(card) + GAP
			+ footer() + PAD;
		BufferedImage image = new BufferedImage(WIDTH * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		try
		{
			// Pixel fonts and item sprites look right unsmoothed at a whole-number scale
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.scale(SCALE, SCALE);
			g.setColor(BACKGROUND);
			g.fillRect(0, 0, WIDTH, height);

			int y = PAD;
			y = drawHeader(g, card, icons, y) + GAP;
			y = drawLuck(g, card, icons, y) + GAP;
			y = drawChances(g, card, icons, y) + GAP;
			y = drawStats(g, card, y) + GAP;
			y = drawTrips(g, card, y) + GAP;
			drawFooter(g, card, y);
		}
		finally
		{
			g.dispose();
		}
		return image;
	}

	// ---- Sections: each returns the y below it ----

	private static int header()
	{
		return 44;
	}

	private int drawHeader(Graphics2D g, ShareCard card, IntFunction<Image> icons, int y)
	{
		int h = header();
		card(g, y, h);
		// Orange strip on the left, like a selected tab
		g.setColor(ACCENT);
		g.fillRect(PAD, y, 2, h);

		drawIcon(g, icons.apply(card.getIconItemId()), PAD + INNER, y + 6, 36, 32);
		int x = headerTextLeft();
		String title = card.getBossName() + (card.getVariant() != null ? " (" + card.getVariant() + ")" : "");
		text(g, bold, Color.WHITE, title, x, y + 19);
		String name = card.getPlayerName() != null ? card.getPlayerName() : "Boss Trip Tracker";
		text(g, regular, card.getPlayerName() != null ? TEXT : UiFormat.MUTED_TEXT, name, x, y + 36);

		// Kill count, then total loot at the right edge
		int right = WIDTH - PAD - INNER;
		textRight(g, small, UiFormat.MUTED_TEXT, TOTAL_LOOT, right, y + 17);
		textRight(g, bold, Color.WHITE, UiFormat.gp(card.getTotalLoot()), right, y + 35);
		int kcRight = right - headerColumnWidth(g, TOTAL_LOOT, UiFormat.gp(card.getTotalLoot())) - HEADER_COLUMN_GAP;
		textRight(g, small, UiFormat.MUTED_TEXT, KILL_COUNT, kcRight, y + 17);
		textRight(g, bold, Color.WHITE, killCount(card), kcRight, y + 35);
		return y + h;
	}

	private static final String KILL_COUNT = "Kill count";
	private static final String TOTAL_LOOT = "Total loot";
	private static final int HEADER_COLUMN_GAP = 14;

	private static String killCount(ShareCard card)
	{
		return card.getKillCount() != null ? String.format(Locale.ROOT, "%,d", card.getKillCount()) : "-";
	}

	private int headerColumnWidth(Graphics2D g, String label, String value)
	{
		return Math.max(width(g, small, label), width(g, bold, value));
	}

	/**
	 * Where the header's kill count column starts; the title and name must end before it.
	 */
	int headerStatsLeft(Graphics2D g, ShareCard card)
	{
		int right = WIDTH - PAD - INNER;
		int kcRight = right - headerColumnWidth(g, TOTAL_LOOT, UiFormat.gp(card.getTotalLoot())) - HEADER_COLUMN_GAP;
		return kcRight - headerColumnWidth(g, KILL_COUNT, killCount(card));
	}

	/**
	 * Where the header's title and name start.
	 */
	static int headerTextLeft()
	{
		return PAD + INNER + 42;
	}

	/**
	 * Unique icons go in fixed-width cells, wrapping onto more rows for bosses with many uniques (the Nightmare).
	 */
	private static final int DROP_CELL = 64;
	private static final int DROP_ROW = 26;
	private static final int DROPS_PER_ROW = (WIDTH - 2 * PAD - 2 * INNER) / DROP_CELL;

	private static int luck(ShareCard card)
	{
		int rows = Math.max(1, (card.getDrops().size() + DROPS_PER_ROW - 1) / DROPS_PER_ROW);
		return 112 + (rows - 1) * DROP_ROW;
	}

	private int drawLuck(Graphics2D g, ShareCard card, IntFunction<Image> icons, int y)
	{
		int h = luck(card);
		card(g, y, h);
		int left = PAD + INNER;
		int right = WIDTH - PAD - INNER;

		text(g, bold, TEXT, "Luck Status:", left, y + 17);
		if (card.getTier() != null)
		{
			text(g, bold, UiFormat.tierColor(card.getTier()), card.getTier().getLabel(), left + width(g, bold, "Luck Status:") + 6, y + 17);
		}
		textRight(g, small, UiFormat.MUTED_TEXT, (card.isAllTime() ? "All-time (Loot Tracker)" : "Tracked " + card.getWords().units())
			+ (card.isApproximate() ? " · approx." : ""), right, y + 16);

		String expected = String.format(Locale.ROOT, card.getUniquesExpected() >= 10 ? "%.1f" : "%.2f", card.getUniquesExpected());
		int half = (right - left) / 2;
		pair(g, "Uniques", card.getUniquesReceived() + " / " + expected, left, y + 36);
		pair(g, "Dry streak", card.getWords().kc(card.getDryKills()), left + half, y + 36);
		pair(g, "Dry streak vs rate", String.format(Locale.ROOT, "%.1f\u00d7", card.getDryVsRate()), left, y + 54);
		pair(g, "Rate", "1/" + UiFormat.oneIn(card.getUniqueRate()), left + half, y + 54);
		pair(g, "Worst dry streak", card.getWords().kc(card.getLongestDryStreak()), left, y + 72);
		if (card.getTeamDryStreak() != null)
		{
			// Anyone's purple resets it, unlike the dry streak above
			pair(g, "Team dry streak", card.getWords().kc(card.getTeamDryStreak()), left + half, y + 72);
		}
		else if (card.getLastUniqueKc() != null)
		{
			pair(g, "Last Unique", String.format(Locale.ROOT, "%,d", card.getLastUniqueKc()), left + half, y + 72);
		}

		// One icon and count per unique, then the pet
		for (int i = 0; i < card.getDrops().size(); i++)
		{
			ShareCard.Drop drop = card.getDrops().get(i);
			int x = left + (i % DROPS_PER_ROW) * DROP_CELL;
			int rowY = y + 82 + (i / DROPS_PER_ROW) * DROP_ROW;
			drawIcon(g, icons.apply(drop.getItemId()), x, rowY, 27, 24);
			String count = "x" + drop.getCount();
			text(g, bold, drop.getCount() > 0 ? UiFormat.UNIQUE_BORDER : UiFormat.MUTED_TEXT, count, x + 29, rowY + 18);
		}
		return y + h;
	}

	/**
	 * One drop chances row: the item, then its Expected bar and count, then its Received bar and count.
	 */
	static final int CHANCE_ROW = 20;
	private static final int CHANCE_BAR = 16;
	private static final int CHANCE_NUMBER = 24;

	static int chances(ShareCard card)
	{
		return 40 + card.getChances().size() * CHANCE_ROW;
	}

	/**
	 * Both of the panel's drop chances tabs side by side: Expected (the chance of at least one by now, and the
	 * drops expected) and Received (ahead of or behind expectation, and the drops received).
	 */
	private int drawChances(Graphics2D g, ShareCard card, IntFunction<Image> icons, int y)
	{
		int h = chances(card);
		card(g, y, h);
		int left = PAD + INNER;
		int right = WIDTH - PAD - INNER;
		text(g, bold, TEXT, "Drop chances", left, y + 17);
		textRight(g, small, UiFormat.MUTED_TEXT, card.getChancesSource(), right, y + 16);

		int barsLeft = left + 28;
		int column = (right - barsLeft - 8) / 2;
		int barWidth = column - CHANCE_NUMBER;
		int receivedLeft = barsLeft + column + 8;
		text(g, small, UiFormat.MUTED_TEXT, "Expected", barsLeft, y + 32);
		text(g, small, UiFormat.MUTED_TEXT, "Received", receivedLeft, y + 32);

		double scale = DropChances.luckScale(card.getChances());
		int rowY = y + 36;
		for (DropChances.Row row : card.getChances())
		{
			if (row.getItemId() == DropChances.ANY)
			{
				text(g, small, Color.YELLOW, "Any", left + 2, rowY + 13);
			}
			else
			{
				drawIcon(g, icons.apply(row.getItemId()), left, rowY, 23, 20);
			}
			int barY = rowY + (CHANCE_ROW - CHANCE_BAR) / 2;
			DropBar.paintExpected(g, small, barsLeft, barY, barWidth, CHANCE_BAR, row.getChanceOfOne());
			textRight(g, small, TEXT, String.valueOf((int) Math.floor(row.getExpected())), barsLeft + column, barY + 12);
			DropBar.paintReceived(g, small, receivedLeft, barY, barWidth, CHANCE_BAR, row.luck(), scale);
			textRight(g, small, TEXT, String.valueOf(row.getReceived()), receivedLeft + column, barY + 12);
			rowY += CHANCE_ROW;
		}
		return y + h;
	}

	private static int stats()
	{
		return 58;
	}

	private int drawStats(Graphics2D g, ShareCard card, int y)
	{
		int h = stats();
		card(g, y, h);
		// The totals only cover what the plugin has tracked, so say from where
		text(g, bold, TEXT, "Tracked totals", PAD + INNER, y + 17);
		String since = card.getWords().count(card.getTrackedKills())
			+ (card.getTrackedFromKc() != null ? String.format(Locale.ROOT, " since KC %,d", card.getTrackedFromKc()) : "");
		textRight(g, small, UiFormat.MUTED_TEXT, since, WIDTH - PAD - INNER, y + 16);
		int row = y + 20;
		String[] captions = {"Loot", "Costs", "Net profit", "Net GP/hr"};
		long[] values = {card.getLoot(), card.getCosts(), card.getNet(), card.getGpPerHour()};
		int cell = (WIDTH - 2 * PAD - 2 * INNER) / captions.length;
		for (int i = 0; i < captions.length; i++)
		{
			int x = PAD + INNER + i * cell;
			Color color = i >= 2 ? UiFormat.profitColor(values[i]) : TEXT;
			text(g, small, UiFormat.MUTED_TEXT, captions[i], x, row + 15);
			text(g, bold, color, UiFormat.gp(values[i]), x, row + 31);
		}
		return y + h;
	}

	private static int trips(ShareCard card)
	{
		return 24 + Math.max(1, card.getRecentTrips().size()) * 16;
	}

	private int drawTrips(Graphics2D g, ShareCard card, int y)
	{
		int h = trips(card);
		card(g, y, h);
		int left = PAD + INNER;
		int right = WIDTH - PAD - INNER;
		text(g, bold, TEXT, card.getRecentTrips().isEmpty() ? "Recent trips" : "Last " + card.getRecentTrips().size()
			+ (card.getRecentTrips().size() == 1 ? " trip" : " trips"), left, y + 17);
		textRight(g, small, UiFormat.MUTED_TEXT, String.format(Locale.ROOT, "%,d trips tracked", card.getTrips()), right, y + 16);

		int row = y + 36;
		if (card.getRecentTrips().isEmpty())
		{
			text(g, regular, UiFormat.MUTED_TEXT, "No completed trips yet", left, row);
		}
		for (ShareCard.TripLine trip : card.getRecentTrips())
		{
			text(g, regular, TEXT, TRIP_DATE.format(Instant.ofEpochMilli(trip.getStartedAt()).atZone(zone)), left, row);
			text(g, regular, TEXT, trip.getDetail() != null ? trip.getDetail()
				: card.getWords().count(trip.getKills()), left + 150, row);
			textRight(g, bold, UiFormat.profitColor(trip.getNet()), UiFormat.gp(trip.getNet()), right, row);
			row += 16;
		}
		return y + h;
	}

	private static int footer()
	{
		return 12;
	}

	private void drawFooter(Graphics2D g, ShareCard card, int y)
	{
		text(g, small, UiFormat.MUTED_TEXT, "Boss Trip Tracker for RuneLite", PAD + 2, y + 10);
		textRight(g, small, UiFormat.MUTED_TEXT, CARD_DATE.format(Instant.ofEpochMilli(card.getCreatedAt()).atZone(zone)),
			WIDTH - PAD - 2, y + 10);
	}

	// ---- Drawing helpers ----

	private static void card(Graphics2D g, int y, int h)
	{
		g.setColor(CARD);
		g.fillRect(PAD, y, WIDTH - 2 * PAD, h);
	}

	private void pair(Graphics2D g, String label, String value, int x, int baseline)
	{
		pair(g, label, value, Color.WHITE, x, baseline);
	}

	private void pair(Graphics2D g, String label, String value, Color color, int x, int baseline)
	{
		text(g, regular, UiFormat.MUTED_TEXT, label + ":", x, baseline);
		text(g, bold, color, value, x + width(g, regular, label + ":") + 4, baseline);
	}

	private static void drawIcon(Graphics2D g, Image icon, int x, int y, int w, int h)
	{
		if (icon != null)
		{
			g.drawImage(icon, x, y, w, h, null);
		}
	}

	private static void text(Graphics2D g, Font font, Color color, String text, int x, int baseline)
	{
		g.setFont(font);
		g.setColor(color);
		g.drawString(text, x, baseline);
	}

	private static void textRight(Graphics2D g, Font font, Color color, String text, int right, int baseline)
	{
		text(g, font, color, text, right - width(g, font, text), baseline);
	}

	private static int width(Graphics2D g, Font font, String text)
	{
		return g.getFontMetrics(font).stringWidth(text);
	}
}
