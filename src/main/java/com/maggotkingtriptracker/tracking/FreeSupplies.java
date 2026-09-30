package com.maggotkingtriptracker.tracking;

import com.maggotkingtriptracker.model.ItemEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Supplies obtained inside a raid (Theatre of Blood supply chest purchases, items picked up) cost nothing: only use
 * beyond what was obtained there, over the whole raid, is paid for. Counted per item, and per dose for potions, so a
 * brew bought from the chest is free whether it's drunk, dropped or kept, and arrows picked back up make up for ones
 * already fired. Charges are never covered.
 */
class FreeSupplies
{
	/**
	 * Obtained and not used yet.
	 */
	private final Map<Key, Long> allowance = new HashMap<>();
	/**
	 * Used and paid for so far this raid, which something obtained later makes up for.
	 */
	private final Map<Key, Long> paid = new HashMap<>();

	/**
	 * Something obtained inside, as a supply line would count it (per dose for potions).
	 *
	 * @return how much of it makes up for use already paid for: take that off the trip's supply line
	 */
	long acquired(ItemEntry entry)
	{
		if (entry.isCharges() || entry.getQuantity() <= 0)
		{
			return 0;
		}
		Key key = Key.of(entry);
		long refund = Math.min(paid.getOrDefault(key, 0L), entry.getQuantity());
		if (refund > 0)
		{
			paid.merge(key, -refund, Long::sum);
		}
		if (entry.getQuantity() - refund > 0)
		{
			allowance.merge(key, entry.getQuantity() - refund, Long::sum);
		}
		return refund;
	}

	/**
	 * @param used supply lines about to be recorded
	 * @return the part of them that was brought in and so is paid for; lines fully covered are left out. The
	 * allowance used up is removed.
	 */
	List<ItemEntry> paidFor(List<ItemEntry> used)
	{
		List<ItemEntry> charged = new ArrayList<>();
		for (ItemEntry entry : used)
		{
			if (entry.isCharges())
			{
				charged.add(entry);
				continue;
			}
			Key key = Key.of(entry);
			long free = allowance.getOrDefault(key, 0L);
			long covered = Math.min(free, entry.getQuantity());
			if (covered > 0)
			{
				if (free - covered > 0)
				{
					allowance.put(key, free - covered);
				}
				else
				{
					allowance.remove(key);
				}
			}
			long rest = entry.getQuantity() - covered;
			if (rest > 0)
			{
				paid.merge(key, rest, Long::sum);
				ItemEntry part = new ItemEntry(entry.getItemId(), rest, entry.getPriceEach());
				part.setPerDose(entry.isPerDose());
				charged.add(part);
			}
		}
		return charged;
	}

	/**
	 * @return how much of this item (doses for potions) is still free
	 */
	long remaining(int itemId, boolean perDose)
	{
		return allowance.getOrDefault(new Key(itemId, perDose), 0L);
	}

	void clear()
	{
		allowance.clear();
		paid.clear();
	}

	private static final class Key
	{
		final int itemId;
		final boolean perDose;

		Key(int itemId, boolean perDose)
		{
			this.itemId = itemId;
			this.perDose = perDose;
		}

		static Key of(ItemEntry entry)
		{
			return new Key(entry.getItemId(), entry.isPerDose());
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Key))
			{
				return false;
			}
			Key other = (Key) o;
			return itemId == other.itemId && perDose == other.perDose;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(itemId, perDose);
		}
	}
}
