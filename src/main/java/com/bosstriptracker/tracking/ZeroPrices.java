package com.bosstriptracker.tracking;

import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lines recorded while RuneLite's price list wasn't loaded (or failed to fetch) were saved at 0 gp. A tradeable item
 * at 0 gp is taken as one of those, and repriced at the current price once prices are loaded. Untradeable items
 * (pets, crystal shards) and tarnished drops waiting to be polished keep their 0.
 */
final class ZeroPrices
{
	interface Pricing
	{
		/**
		 * Whether the line's item (for charges, the item that recharges it) has a GE price.
		 */
		boolean tradeable(ItemEntry entry);

		/**
		 * The line's price each now: per dose for potion lines, per recharge for charge lines.
		 */
		long price(ItemEntry entry);
	}

	private ZeroPrices()
	{
	}

	/**
	 * @param junkPrice dropped items repriced below this each are junk and are removed
	 * @return how many lines were repriced
	 */
	static int reprice(Iterable<Trip> trips, Pricing pricing, long junkPrice)
	{
		int repriced = 0;
		for (Trip trip : trips)
		{
			for (Kill kill : trip.getKills())
			{
				repriced += reprice(kill.getLoot(), pricing, -1);
			}
			repriced += reprice(trip.getSupplies(), pricing, -1);
			repriced += reprice(trip.getDropped(), pricing, junkPrice);
		}
		return repriced;
	}

	private static int reprice(List<ItemEntry> lines, Pricing pricing, long junkPrice)
	{
		int repriced = 0;
		for (Iterator<ItemEntry> it = lines.iterator(); it.hasNext(); )
		{
			ItemEntry entry = it.next();
			if (entry.getPriceEach() != 0 || entry.isPending() || !pricing.tradeable(entry))
			{
				continue;
			}
			long price = pricing.price(entry);
			if (price <= 0)
			{
				continue;
			}
			entry.setPriceEach(price);
			repriced++;
			if (price < junkPrice)
			{
				it.remove();
			}
		}
		if (repriced > 0)
		{
			// A line recorded while prices were missing is kept apart from the priced one; join them again
			List<ItemEntry> joined = new ArrayList<>();
			for (ItemEntry entry : lines)
			{
				if (entry.isPending() || entry.getPolishedFrom() != 0)
				{
					joined.add(entry);
				}
				else
				{
					ItemEntries.merge(joined, entry);
				}
			}
			lines.clear();
			lines.addAll(joined);
		}
		return repriced;
	}
}
