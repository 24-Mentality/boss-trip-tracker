package com.maggotkingtriptracker.tracking;

import com.maggotkingtriptracker.boss.BossDefinition;
import com.maggotkingtriptracker.boss.BossRegistry;
import com.maggotkingtriptracker.model.BossHistory;
import com.maggotkingtriptracker.model.EggPop;
import com.maggotkingtriptracker.pricing.PriceService;
import java.util.Map;

/**
 * Egg pops, anywhere, for bosses that drop pet eggs (the Maggot King). An egg leaving the inventory right after
 * a click on it is a pop; a pet message shortly after belongs to that egg. Client thread only.
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
	 * Any egg option that isn't dropping, using, examining or moving it. The pop option isn't hardcoded
	 * because it has not been confirmed in game.
	 */
	static boolean isPopOption(String option)
	{
		if (option == null)
		{
			return false;
		}
		String o = option.toLowerCase();
		return !(o.equals("drop") || o.equals("use") || o.equals("examine") || o.equals("destroy")
			|| o.equals("cancel") || o.startsWith("deposit") || o.startsWith("withdraw") || o.startsWith("offer")
			|| o.startsWith("store") || o.startsWith("bank") || o.startsWith("take"));
	}
}
