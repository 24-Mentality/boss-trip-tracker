package com.bosstriptracker.tracking;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.client.util.Text;

/**
 * One page of the Collection Log as the game draws it: the header's lines (the page's name first, then "Obtained:
 * 6/6", "Personal Best: 1:08.40" and counts such as "Vorkath kills: 4,340") and its item slots. From the pages
 * recorded on 2026-10-23: a boss page counts "&lt;name&gt; kills", the Nightmare's has both "Phosani's Nightmare
 * kills" and "Nightmare kills", a raid's has "&lt;raid&gt; completions" with "(Entry)" and "(Hard)" lines, and the
 * Doom of Mokhaiotl's has "Deepest delve", "Deep delves" and "Total delves".
 */
@Value
public class CollectionLogPage
{
	/**
	 * "Label: 4,340" header lines; "Obtained: 6/6" and "Personal Best: 1:08.40" don't match.
	 */
	private static final Pattern COUNT = Pattern.compile("^(.+?):\\s*([\\d,]+)$");

	String name;
	/**
	 * Header counts by label, in the header's order.
	 */
	Map<String, Integer> counts;
	/**
	 * Item id to the count obtained; items not obtained aren't listed.
	 */
	Map<Integer, Integer> items;

	/**
	 * One item slot of the page.
	 */
	@Value
	public static class Slot
	{
		int itemId;
		int quantity;
		boolean obtained;
	}

	/**
	 * @param headerTexts the header's lines, colour tags allowed
	 * @return the page, or null without a name
	 */
	public static CollectionLogPage parse(List<String> headerTexts, List<Slot> slots)
	{
		String name = null;
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String raw : headerTexts)
		{
			if (raw == null)
			{
				continue;
			}
			String text = Text.removeTags(raw).trim();
			if (text.isEmpty())
			{
				continue;
			}
			if (name == null)
			{
				name = text;
				continue;
			}
			Matcher m = COUNT.matcher(text);
			if (m.matches())
			{
				counts.put(m.group(1).trim(), Integer.parseInt(m.group(2).replace(",", "")));
			}
		}
		if (name == null)
		{
			return null;
		}
		Map<Integer, Integer> items = new LinkedHashMap<>();
		for (Slot slot : slots)
		{
			if (slot.getItemId() > 0 && slot.isObtained())
			{
				items.merge(slot.getItemId(), Math.max(1, slot.getQuantity()), Integer::sum);
			}
		}
		return new CollectionLogPage(name, counts, items);
	}
}
