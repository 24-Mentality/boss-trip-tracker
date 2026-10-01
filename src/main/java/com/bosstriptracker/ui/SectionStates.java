package com.bosstriptracker.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Which item boxes are open, by key (e.g. "trip.loot", "history.supplies"), saved in one hidden config value as
 * "trip.loot=open,history.supplies=closed". Boxes without an entry use their own default. Used on the Swing thread.
 */
public class SectionStates
{
	private static final String OPEN = "open";
	private static final String CLOSED = "closed";

	private final Map<String, Boolean> open;
	private final Consumer<String> save;
	private final List<Runnable> listeners = new ArrayList<>();

	/**
	 * @param saved the saved value; null, empty or malformed entries are ignored
	 * @param save called with the new value whenever a box is opened or closed
	 */
	public SectionStates(String saved, Consumer<String> save)
	{
		this.open = parse(saved);
		this.save = save;
	}

	boolean isOpen(String key, boolean defaultOpen)
	{
		return open.getOrDefault(key, defaultOpen);
	}

	void setOpen(String key, boolean isOpen)
	{
		Boolean old = open.put(key, isOpen);
		if (old != null && old == isOpen)
		{
			return;
		}
		save.accept(format(open));
		// Copied: a listener may add or remove listeners (e.g. a box being rebuilt)
		for (Runnable listener : new ArrayList<>(listeners))
		{
			listener.run();
		}
	}

	/**
	 * Called after any box is opened or closed, so other boxes sharing a key (History cards) can follow.
	 */
	void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	void removeListener(Runnable listener)
	{
		listeners.remove(listener);
	}

	static Map<String, Boolean> parse(String saved)
	{
		Map<String, Boolean> map = new LinkedHashMap<>();
		if (saved == null)
		{
			return map;
		}
		for (String entry : saved.split(","))
		{
			int eq = entry.indexOf('=');
			if (eq <= 0)
			{
				continue;
			}
			String key = entry.substring(0, eq).trim();
			String value = entry.substring(eq + 1).trim();
			if (key.isEmpty())
			{
				continue;
			}
			if (OPEN.equals(value))
			{
				map.put(key, true);
			}
			else if (CLOSED.equals(value))
			{
				map.put(key, false);
			}
		}
		return map;
	}

	static String format(Map<String, Boolean> map)
	{
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Boolean> e : map.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(',');
			}
			sb.append(e.getKey()).append('=').append(e.getValue() ? OPEN : CLOSED);
		}
		return sb.toString();
	}
}
