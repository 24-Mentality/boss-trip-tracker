package com.bosstriptracker.tracking;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.BossRegistry;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.EggPop;
import com.bosstriptracker.pricing.PriceService;
import java.util.Map;

/**
 * Egg pops, anywhere, for bosses that drop pet eggs (the Maggot King). An egg leaving the inventory right after
 * its Pop option is a pop; a pet message shortly after belongs to that egg. Client thread only.
 */
class EggTracker
{
	/**
	 * A pet or dead-maggot message this soon after popping an egg belongs to that egg.
	 */
	private static final int EGG_WINDOW_TICKS = 10;

	private final BossRegistry registry;
	private final PriceService prices;
	private final RecentClicks clicks;
	private final TrackerHost host;

	private EggPop lastEggPop;
	private BossDefinition lastEggBoss;
	private int lastEggPopTick = -100;
	private int lastEggClickTick = -100;
	private int unclaimedPetMessageTick = -100;

	EggTracker(BossRegistry registry, PriceService prices, RecentClicks clicks, TrackerHost host)
	{
		this.registry = registry;
		this.prices = prices;
		this.clicks = clicks;
		this.host = host;
	}

	/**
	 * Forgets recent clicks and pops, e.g. on logout.
	 */
	void reset()
	{
		lastEggPop = null;
		lastEggBoss = null;
		lastEggPopTick = -100;
		lastEggClickTick = -100;
		unclaimedPetMessageTick = -100;
	}

	void menuClicked(String option, int itemId, int tick)
	{
		if (registry.forEgg(itemId) != null && isPopOption(option))
		{
			lastEggClickTick = tick;
		}
	}

	/**
	 * @return true if a pet message now is probably from an egg
	 */
	boolean recentlyClicked(int tick)
	{
		return tick - lastEggClickTick <= EGG_WINDOW_TICKS;
	}

	/**
	 * A pet message right after clicking an egg. Call only when {@link #recentlyClicked} is true.
	 */
	void petMessage(int tick)
	{
		if (lastEggPop != null && tick - lastEggPopTick <= EGG_WINDOW_TICKS)
		{
			eggPet(lastEggBoss, lastEggPop);
		}
		else
		{
			// The egg's removal hasn't been processed yet
			unclaimedPetMessageTick = tick;
		}
	}

	/**
	 * Records eggs that left the inventory right after a pop click.
	 */
	void itemsRemoved(Map<Integer, Long> removed, int tick, long now)
	{
		if (tick - lastEggClickTick > RecentClicks.MATCH_TICKS)
		{
			return;
		}

		for (Map.Entry<Integer, Long> e : removed.entrySet())
		{
			int eggId = e.getKey();
			BossDefinition boss = registry.forEgg(eggId);
			if (boss == null || !clicks.has(eggId, tick, EggTracker::isPopOption))
			{
				continue;
			}
			BossHistory history = host.writableHistory(boss);
			if (history == null)
			{
				continue;
			}
			for (long i = 0; i < e.getValue(); i++)
			{
				EggPop pop = new EggPop(eggId, now, false);
				history.getEggPops().add(pop);
				lastEggPop = pop;
				lastEggBoss = boss;
				lastEggPopTick = tick;
			}
			if (tick - unclaimedPetMessageTick <= EGG_WINDOW_TICKS)
			{
				unclaimedPetMessageTick = -100;
				eggPet(boss, lastEggPop);
			}
			host.historyChanged();
		}
	}

	private void eggPet(BossDefinition boss, EggPop pop)
	{
		if (pop.isPet())
		{
			return;
		}
		pop.setPet(true);
		host.alertPet(boss.getDisplayName() + " pet from a " + prices.name(pop.getEggItemId()) + "!");
		host.historyChanged();
	}

	/**
	 * The egg's pop option. Unverified: no egg click is in the diagnostic logs yet, and the wiki calls it popping.
	 */
	static final String OPTION_POP = "Pop";

	static boolean isPopOption(String option)
	{
		return OPTION_POP.equalsIgnoreCase(option);
	}
}
