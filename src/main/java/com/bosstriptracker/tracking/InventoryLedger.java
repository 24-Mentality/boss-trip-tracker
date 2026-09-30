package com.bosstriptracker.tracking;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;

/**
 * One combined count of everything the player carries: inventory, worn equipment and rune pouch.
 * Combining them means gear switches and moving runes into the pouch net out to zero.
 * Must be used on the client thread.
 */
class InventoryLedger
{
	private static final List<int[]> RUNE_POUCH_SLOTS = ImmutableList.of(
		new int[]{VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_QUANTITY_1},
		new int[]{VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_QUANTITY_2},
		new int[]{VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_QUANTITY_3},
		new int[]{VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_QUANTITY_4},
		new int[]{VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_QUANTITY_5},
		new int[]{VarbitID.RUNE_POUCH_TYPE_6, VarbitID.RUNE_POUCH_QUANTITY_6}
	);

	static final Set<Integer> RUNE_POUCH_VARBITS = new HashSet<>();

	static
	{
		for (int[] slot : RUNE_POUCH_SLOTS)
		{
			RUNE_POUCH_VARBITS.add(slot[0]);
			RUNE_POUCH_VARBITS.add(slot[1]);
		}
	}

	private final Client client;
	private Map<Integer, Long> previous;
	private boolean dirty;

	InventoryLedger(Client client)
	{
		this.client = client;
	}

	void markDirty()
	{
		dirty = true;
	}

	/**
	 * Forget the baseline, e.g. on logout. The next snapshot becomes the new baseline.
	 */
	void reset()
	{
		previous = null;
		dirty = true;
	}

	/**
	 * @return item id to quantity change since the last call, or an empty map if nothing changed
	 */
	Map<Integer, Long> poll()
	{
		if (!dirty)
		{
			return Map.of();
		}
		dirty = false;

		Map<Integer, Long> current = snapshot();
		Map<Integer, Long> before = previous;
		previous = current;
		if (before == null)
		{
			return Map.of();
		}

		Map<Integer, Long> delta = new HashMap<>();
		for (Map.Entry<Integer, Long> e : current.entrySet())
		{
			long change = e.getValue() - before.getOrDefault(e.getKey(), 0L);
			if (change != 0)
			{
				delta.put(e.getKey(), change);
			}
		}
		for (Map.Entry<Integer, Long> e : before.entrySet())
		{
			if (!current.containsKey(e.getKey()))
			{
				delta.put(e.getKey(), -e.getValue());
			}
		}
		return delta;
	}

	private Map<Integer, Long> snapshot()
	{
		Map<Integer, Long> counts = new HashMap<>();
		addContainer(counts, client.getItemContainer(InventoryID.INV));
		addContainer(counts, client.getItemContainer(InventoryID.WORN));

		EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int[] slot : RUNE_POUCH_SLOTS)
		{
			int type = client.getVarbitValue(slot[0]);
			int quantity = client.getVarbitValue(slot[1]);
			if (type > 0 && quantity > 0 && runeEnum != null)
			{
				int runeId = runeEnum.getIntValue(type);
				if (runeId > 0)
				{
					counts.merge(runeId, (long) quantity, Long::sum);
				}
			}
		}
		return counts;
	}

	private static void addContainer(Map<Integer, Long> counts, ItemContainer container)
	{
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() >= 0 && item.getQuantity() > 0)
			{
				counts.merge(item.getId(), (long) item.getQuantity(), Long::sum);
			}
		}
	}
}
