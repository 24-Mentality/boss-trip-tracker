package com.bosstriptracker.tracking;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.BossRegistry;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.pricing.PriceService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;

/**
 * Tarnished drops (Maggot King): recorded as pending, and resolved FIFO when one is polished anywhere.
 * Client thread only.
 */
@Slf4j
class PolishTracker
{
	static final String OPTION_POLISH = "Polish";

	private final BossRegistry registry;
	private final PriceService prices;
	private final TrackerHost host;
	private final List<PendingPolish> pendingPolishes = new ArrayList<>();

	PolishTracker(BossRegistry registry, PriceService prices, TrackerHost host)
	{
		this.registry = registry;
		this.prices = prices;
		this.host = host;
	}

	boolean isTarnished(int itemId)
	{
		return registry.forTarnished(itemId) != null;
	}

	/**
	 * Forgets polishes in progress, e.g. on logout.
	 */
	void reset()
	{
		pendingPolishes.clear();
	}

	void menuClicked(String option, int itemId, int tick)
	{
		if (OPTION_POLISH.equals(option) && isTarnished(itemId))
		{
			pendingPolishes.add(new PendingPolish(itemId, tick));
		}
	}

	/**
	 * The Loot Tracker's polish event lists every inventory change in that tick, which can include a potion sip
	 * or gear that was just equipped. Its items are only candidates; see {@link #tick}.
	 */
	void lootEvent(LootReceived event, int tick)
	{
		int tarnishedId = tarnishedIdForName(event.getName());
		if (tarnishedId < 0)
		{
			return;
		}

		PendingPolish polish = null;
		for (PendingPolish pending : pendingPolishes)
		{
			if (pending.tarnishedId == tarnishedId && pending.eventItems.isEmpty())
			{
				polish = pending;
				break;
			}
		}
		if (polish == null)
		{
			polish = new PendingPolish(tarnishedId, tick);
			pendingPolishes.add(polish);
		}
		for (ItemStack stack : event.getItems())
		{
			if (isResultCandidate(stack.getId()))
			{
				polish.eventItems.add(stack.getId());
			}
		}
	}

	/**
	 * Net gains across inventory, equipment and rune pouch just after a Polish click. Equipping gear in the same
	 * tick nets out here, so it can't be mistaken for the result.
	 */
	void gained(Map<Integer, Long> gained, int tick)
	{
		for (PendingPolish polish : pendingPolishes)
		{
			if (tick - polish.tick <= RecentClicks.MATCH_TICKS)
			{
				for (int itemId : gained.keySet())
				{
					if (isResultCandidate(itemId))
					{
						polish.netGains.add(itemId);
					}
				}
			}
		}
	}

	/**
	 * Resolves polishes whose results have all arrived.
	 */
	void tick(int tick)
	{
		for (Iterator<PendingPolish> it = pendingPolishes.iterator(); it.hasNext(); )
		{
			PendingPolish polish = it.next();
			if (tick - polish.tick <= RecentClicks.MATCH_TICKS + 1)
			{
				continue;
			}
			it.remove();

			Integer result = choosePolishResult(polish.eventItems, polish.netGains);
			if (result != null)
			{
				resolve(polish.tarnishedId, result);
			}
			else if (!polish.eventItems.isEmpty() || !polish.netGains.isEmpty())
			{
				// Leave the drop pending rather than guess
				log.debug("Ambiguous polish result for {}: event {} gains {}", polish.tarnishedId, polish.eventItems, polish.netGains);
			}
		}
	}

	/**
	 * Picks the polished item: one the Loot Tracker reported that is also a real net gain; otherwise the only
	 * net gain; otherwise the only reported item if nothing was gained. Returns null when ambiguous.
	 */
	static Integer choosePolishResult(Set<Integer> eventItems, Set<Integer> netGains)
	{
		for (int itemId : eventItems)
		{
			if (netGains.contains(itemId))
			{
				return itemId;
			}
		}
		if (netGains.size() == 1)
		{
			return netGains.iterator().next();
		}
		if (netGains.isEmpty() && eventItems.size() == 1)
		{
			return eventItems.iterator().next();
		}
		return null;
	}

	/**
	 * Records the outcome and gives the oldest pending drop of this type its real item and value.
	 */
	private void resolve(int tarnishedId, int resultId)
	{
		BossDefinition boss = registry.forTarnished(tarnishedId);
		BossHistory history = boss == null ? null : host.writableHistory(boss);
		if (history == null)
		{
			return;
		}

		history.getPolishOutcomes().computeIfAbsent(tarnishedId, k -> new HashMap<>()).merge(resultId, 1, Integer::sum);

		ItemEntry pending = oldestPending(history, tarnishedId);
		if (pending != null)
		{
			pending.setItemId(resultId);
			pending.setPriceEach(prices.price(resultId));
			pending.setPending(false);
			pending.setPolishedFrom(tarnishedId);
			host.alertForDrop(boss, resultId, 1);
		}
		host.historyChanged();
	}

	private static ItemEntry oldestPending(BossHistory history, int tarnishedId)
	{
		for (Trip trip : history.getTrips())
		{
			for (Kill kill : trip.getKills())
			{
				for (ItemEntry entry : kill.getLoot())
				{
					if (entry.isPending() && entry.getItemId() == tarnishedId)
					{
						return entry;
					}
				}
			}
		}
		return null;
	}

	private int tarnishedIdForName(String name)
	{
		for (BossDefinition boss : registry.all())
		{
			for (int id : boss.getTarnishedItems())
			{
				if (prices.name(id).equalsIgnoreCase(name))
				{
					return id;
				}
			}
		}
		return -1;
	}

	private boolean isResultCandidate(int itemId)
	{
		return itemId != ItemID.VIAL_EMPTY && itemId != ItemID.COINS && !isTarnished(itemId)
			&& prices.doseInfo(itemId) == null;
	}

	private static class PendingPolish
	{
		final int tarnishedId;
		final int tick;
		final Set<Integer> eventItems = new LinkedHashSet<>();
		final Set<Integer> netGains = new LinkedHashSet<>();

		PendingPolish(int tarnishedId, int tick)
		{
			this.tarnishedId = tarnishedId;
			this.tick = tick;
		}
	}
}
