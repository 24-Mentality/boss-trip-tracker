package com.maggotkingtriptracker.tracking;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;

/**
 * Menu clicks from the last few ticks, to explain the container changes and ground spawns they cause.
 */
class RecentClicks
{
	/**
	 * How many ticks a menu click may precede the container change or ground spawn it caused.
	 */
	static final int MATCH_TICKS = 3;

	private final Deque<Click> clicks = new ArrayDeque<>();

	void add(int tick, String option, int itemId)
	{
		clicks.addLast(new Click(tick, option, itemId, false));
	}

	Iterable<Click> all()
	{
		return clicks;
	}

	/**
	 * @param itemId the clicked item, or -1 for any
	 */
	boolean has(String option, int itemId, int tick)
	{
		return has(itemId, tick, option::equals);
	}

	/**
	 * @param itemId the clicked item, or -1 for any
	 */
	boolean has(int itemId, int tick, Predicate<String> option)
	{
		for (Click click : clicks)
		{
			if (tick - click.tick <= MATCH_TICKS && (itemId < 0 || click.itemId == itemId) && option.test(click.option))
			{
				return true;
			}
		}
		return false;
	}

	void prune(int tick)
	{
		while (!clicks.isEmpty() && tick - clicks.peekFirst().tick > MATCH_TICKS * 2)
		{
			clicks.removeFirst();
		}
	}

	void clear()
	{
		clicks.clear();
	}

	@AllArgsConstructor
	static class Click
	{
		final int tick;
		final String option;
		final int itemId;
		/**
		 * Already matched to a container change.
		 */
		boolean consumed;
	}
}
