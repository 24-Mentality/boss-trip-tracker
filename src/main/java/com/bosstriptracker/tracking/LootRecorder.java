package com.bosstriptracker.tracking;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.LootChoice;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.pricing.PriceService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

/**
 * Kills and their loot: a kill from the kill-count message (or a loot event when that was missed), the corpse choice,
 * the Loot Tracker's events, the inventory fallback when no event comes, ground overflow, the pet, and how long each
 * kill took. Client thread.
 */
@Slf4j
class LootRecorder
{
	private static final int CLICK_MATCH_TICKS = RecentClicks.MATCH_TICKS;
	/**
	 * After a corpse click, ground spawns count as loot overflow and pet messages count for this kill.
	 */
	static final int CORPSE_WINDOW_TICKS = 10;
	/**
	 * If the Loot Tracker has not reported the kill this many ticks after the corpse click,
	 * inventory gains are used instead.
	 */
	private static final int LOOT_FALLBACK_TICKS = 8;

	/**
	 * What kills and loot need from the rest of the tracker.
	 */
	interface Host
	{
		void alertForDrop(BossDefinition boss, int itemId, long quantity);

		void alertPet(String message);

		/**
		 * Your own drop picked back up: no longer lost.
		 */
		void ownDropPickedUp(int itemId, long quantity);

		/**
		 * Your own ammo picked back up: it comes off the supply line it was costed on.
		 */
		void ownAmmoPickedUp(int itemId, long quantity);
	}

	private enum GroundKind
	{
		OWN_DROP,
		OWN_AMMO,
		LOOT_OVERFLOW,
	}

	@AllArgsConstructor
	private static class GroundEntry
	{
		final int itemId;
		long quantity;
		final WorldPoint location;
		final GroundKind kind;
		final Kill kill;
		int tick;
	}

	private final TripSession s;
	private final HistoryKeeper keeper;
	private final Client client;
	private final PriceService prices;
	private final EggTracker eggTracker;
	private final Host host;
	private Kill lootKill;
	private int lastKillTick = -100;
	private int lastCorpseClickTick = -100;
	private boolean lootReceived;
	private int fallbackEndTick = -1;
	private final Map<Integer, Long> fallbackGains = new HashMap<>();
	private final KillLootSources lootSources = new KillLootSources();
	/**
	 * A corpse choice clicked before its kill exists (the kill-count message was missed); the loot event's kill
	 * takes it.
	 */
	private String pendingChoice;
	private int pendingChoiceTick = -100;
	private Long bossSpawnedAt;
	private Long bossDiedAt;
	/**
	 * When the boss you're fighting spawned, for the live kill timer; null between kills. The game's Fight
	 * duration is the time from the spawn to the kill-count message (checked against the diagnostic logs).
	 */
	private Long fightStartedAt;

	private final List<GroundEntry> groundItems = new ArrayList<>();
	private final List<GroundEntry> recentDespawns = new ArrayList<>();

	LootRecorder(TripSession s, HistoryKeeper keeper, Client client, PriceService prices, EggTracker eggTracker, Host host)
	{
		this.s = s;
		this.keeper = keeper;
		this.client = client;
		this.prices = prices;
		this.eggTracker = eggTracker;
		this.host = host;
	}

	/**
	 * The kill loot goes to; null between trips.
	 */
	Kill getLootKill()
	{
		return lootKill;
	}

	/**
	 * When the fight in progress started, for the live kill timer; null between kills.
	 */
	Long getFightStartedAt()
	{
		return fightStartedAt;
	}

	void fightEnded()
	{
		fightStartedAt = null;
	}

	/**
	 * The trip in progress was deleted: its kill can't take loot any more.
	 */
	void forgetKill()
	{
		lootKill = null;
	}

	/**
	 * The boss spawned (or its fight clock starts): the kill timer counts from here.
	 */
	void fightStarts(long at)
	{
		fightStartedAt = at;
		bossSpawnedAt = at;
		bossDiedAt = null;
		s.viewDirty = true;
	}

	void bossDied(long at)
	{
		bossDiedAt = at;
	}

	/**
	 * Forgets the fight in progress, e.g. on logout.
	 */
	void resetFight()
	{
		fightStartedAt = null;
		bossSpawnedAt = null;
		bossDiedAt = null;
	}

	/**
	 * The game's "Fight duration", just after the kill-count message.
	 */
	void fightDuration(long duration, int tick)
	{
		if (lootKill != null && tick - lastKillTick <= 2)
		{
			lootKill.setDurationMs(duration);
			s.viewDirty = true;
		}
	}

	/**
	 * Open-stomach / Take-eggs on the corpse (or any click on a boss's loot NPC).
	 *
	 * @param choice the option's loot choice; null for a boss without choices
	 */
	void corpseClicked(LootChoice choice, int tick)
	{
		if (tick - lastCorpseClickTick <= CORPSE_WINDOW_TICKS)
		{
			// Repeated clicks on the same corpse
			return;
		}

		// Kills come from the kill-count message or a loot event, never a click: a corpse clicked again after an
		// interruption is the same kill
		if (choice != null)
		{
			Kill kill = unchosenKill();
			if (kill != null)
			{
				kill.setChoice(choice.getKey());
			}
			else if (lootKill == null)
			{
				pendingChoice = choice.getKey();
				pendingChoiceTick = tick;
			}
		}
		s.viewDirty = true;
		lastCorpseClickTick = tick;
		if (!lootReceived)
		{
			fallbackEndTick = tick + LOOT_FALLBACK_TICKS;
			fallbackGains.clear();
		}
	}

	/**
	 * The Loot Tracker reported this kill's loot.
	 */
	void lootEvent(Map<Integer, Long> items)
	{
		Kill kill = lootKillOrCreate();
		// Arrived after the inventory fallback recorded this kill's loot: the event replaces it
		Map<Integer, Long> replaced = lootSources.eventReceived(items);
		replaced.forEach((itemId, quantity) -> ItemEntries.removeLoot(kill.getLoot(), itemId, quantity));
		for (Map.Entry<Integer, Long> e : items.entrySet())
		{
			addLoot(kill, e.getKey(), e.getValue());
			if (!replaced.containsKey(e.getKey()))
			{
				host.alertForDrop(s.tripBoss, e.getKey(), e.getValue());
			}
		}
		lootReceived = true;
		fallbackEndTick = -1;
		fallbackGains.clear();
		s.viewDirty = true;
		keeper.requestSave();
	}

	/**
	 * Something of yours landed on the floor in the boss's area.
	 *
	 * @param ownDrop dropped by you (or thrown and usually picked back up, like blisterwood stakes)
	 * @param ownAmmo your own ammo, costed when fired
	 */
	void itemSpawned(int itemId, int quantity, WorldPoint location, boolean ownDrop, boolean ownAmmo, int tick)
	{
		GroundKind kind;
		Kill kill = null;
		if (ownDrop)
		{
			kind = GroundKind.OWN_DROP;
		}
		else if (ownAmmo)
		{
			kind = GroundKind.OWN_AMMO;
		}
		else if (s.tripBoss.isGroundOverflowLoot() && lootKill != null && tick - lastCorpseClickTick <= CORPSE_WINDOW_TICKS)
		{
			kind = GroundKind.LOOT_OVERFLOW;
			kill = lootKill;
			host.alertForDrop(s.tripBoss, itemId, quantity);
		}
		else
		{
			return;
		}
		groundItems.add(new GroundEntry(itemId, quantity, location, kind, kill, tick));
	}

	void itemDespawned(int itemId, int quantity, WorldPoint location, int tick)
	{
		for (Iterator<GroundEntry> it = groundItems.iterator(); it.hasNext(); )
		{
			GroundEntry entry = it.next();
			if (entry.itemId == itemId && entry.quantity == quantity && entry.location.equals(location))
			{
				it.remove();
				entry.tick = tick;
				recentDespawns.add(entry);
				return;
			}
		}
	}

	/**
	 * Items gained on a trip (not picked up from the floor, not polished): kept for the inventory fallback while no
	 * loot event has come, and as what reached the inventory after a corpse click.
	 */
	void inventoryGained(Map<Integer, Long> gained, int tick)
	{
		boolean afterCorpseClick = lootKill != null && tick - lastCorpseClickTick <= CORPSE_WINDOW_TICKS;
		if (!afterCorpseClick && fallbackEndTick < 0)
		{
			return;
		}
		for (Map.Entry<Integer, Long> e : gained.entrySet())
		{
			if (e.getKey() == ItemID.VIAL_EMPTY || prices.doseInfo(e.getKey()) != null)
			{
				continue;
			}
			if (fallbackEndTick >= 0 && !lootReceived)
			{
				fallbackGains.merge(e.getKey(), e.getValue(), Long::sum);
			}
			if (afterCorpseClick)
			{
				lootSources.inventoryGained(e.getKey(), e.getValue());
			}
		}
	}

	void prune(int tick)
	{
		recentDespawns.removeIf(d -> tick - d.tick > CLICK_MATCH_TICKS);
	}

	/**
	 * @param killCount the game's kill count, or null if the kill isn't on the boss's kill-count scale
	 */
	void recordKill(Integer killCount, String variant, int tick, long now)
	{
		Kill kill = new Kill();
		kill.setKillCount(killCount);
		kill.setVariant(variant);
		kill.setEndedAt(now);
		if (bossSpawnedAt != null)
		{
			long end = bossDiedAt != null ? bossDiedAt : now;
			kill.setDurationMs(Math.max(0, end - bossSpawnedAt));
		}
		s.currentTrip.getKills().add(kill);
		lootKill = kill;
		lastKillTick = tick;
		fightStartedAt = null;
		lootReceived = false;
		fallbackEndTick = -1;
		fallbackGains.clear();
		lootSources.clear();
		s.viewDirty = true;
		keeper.requestSave();
	}

	/**
	 * The kill that loot should be attached to. Creates one if the kill-count message was missed.
	 */
	private Kill lootKillOrCreate()
	{
		return lootKill != null ? lootKill : newKill();
	}

	/**
	 * A kill from a loot event whose kill-count message was missed, so the loot still has somewhere to go.
	 */
	private Kill newKill()
	{
		Kill kill = new Kill();
		kill.setEndedAt(System.currentTimeMillis());
		if (pendingChoice != null && client.getTickCount() - pendingChoiceTick <= CORPSE_WINDOW_TICKS)
		{
			kill.setChoice(pendingChoice);
		}
		pendingChoice = null;
		s.currentTrip.getKills().add(kill);
		lootKill = kill;
		lootReceived = false;
		fallbackEndTick = -1;
		fallbackGains.clear();
		lootSources.clear();
		return kill;
	}

	/**
	 * The most recent kill of this trip without a corpse choice yet, or null.
	 */
	private Kill unchosenKill()
	{
		List<Kill> kills = s.currentTrip.getKills();
		for (int i = kills.size() - 1; i >= 0; i--)
		{
			if (kills.get(i).getChoice() == null)
			{
				return kills.get(i);
			}
		}
		return null;
	}

	private void addLoot(Kill kill, int itemId, long quantity)
	{
		if (s.tripBoss.getTarnishedItems().contains(itemId))
		{
			// Real value is only known once polished; see PolishTracker
			for (long i = 0; i < quantity; i++)
			{
				ItemEntry pending = new ItemEntry(itemId, 1, 0);
				pending.setPending(true);
				pending.setPendingId(UUID.randomUUID().toString());
				kill.getLoot().add(pending);
			}
		}
		else
		{
			ItemEntries.merge(kill.getLoot(), itemId, quantity, prices.price(itemId), false);
		}
	}

	void applyLootFallback(int tick)
	{
		if (fallbackEndTick < 0 || tick <= fallbackEndTick)
		{
			return;
		}

		if (!lootReceived && lootKill != null && s.currentTrip != null && !fallbackGains.isEmpty())
		{
			log.debug("No Loot Tracker event for this kill; using inventory changes");
			for (Map.Entry<Integer, Long> e : fallbackGains.entrySet())
			{
				addLoot(lootKill, e.getKey(), e.getValue());
				host.alertForDrop(s.tripBoss, e.getKey(), e.getValue());
			}
			lootSources.fallbackUsed(fallbackGains);
			lootReceived = true;
			s.viewDirty = true;
			keeper.requestSave();
		}
		fallbackEndTick = -1;
		fallbackGains.clear();
	}

	void resetKillState()
	{
		lootKill = null;
		lootReceived = false;
		fallbackEndTick = -1;
		fallbackGains.clear();
		lootSources.clear();
		pendingChoice = null;
		lastCorpseClickTick = -100;
		groundItems.clear();
		recentDespawns.clear();
	}

	/**
	 * Pet messages are shared by every pet, so they only count right after a corpse or egg interaction.
	 */
	void petMessage(int tick)
	{
		// Right after the corpse click, or for bosses without a corpse, right after the kill
		int lootTick = s.currentTrip != null && s.tripBoss.getLootTriggerNpcs().isEmpty() ? lastKillTick : lastCorpseClickTick;
		if (s.inArea && s.currentTrip != null && lootKill != null && tick - lootTick <= CORPSE_WINDOW_TICKS)
		{
			lootKill.setPet(true);
			int petItem = s.tripBoss.getPet() == null ? -1 : s.tripBoss.getPet().getItemId();
			boolean listed = lootKill.getLoot().stream().anyMatch(e -> e.getItemId() == petItem);
			if (!listed && petItem > 0)
			{
				lootKill.getLoot().add(new ItemEntry(petItem, 1, 0));
			}
			host.alertPet(s.tripBoss.getDisplayName() + (s.tripBoss.getLootTriggerNpcs().isEmpty() ? " pet!" : " pet from the corpse!"));
			s.viewDirty = true;
			keeper.requestSave();
		}
		else if (eggTracker.recentlyClicked(tick))
		{
			eggTracker.petMessage(tick);
		}
	}

	/**
	 * Gains that match a ground item just picked up: loot overflow becomes loot, own drops are no longer lost, and
	 * your own ammo comes off the supply line it was costed on.
	 * Matched quantities are removed from {@code gained}.
	 */
	void matchPickups(Map<Integer, Long> gained, int tick)
	{
		for (Iterator<GroundEntry> it = recentDespawns.iterator(); it.hasNext(); )
		{
			GroundEntry entry = it.next();
			Long available = gained.get(entry.itemId);
			if (available == null || tick - entry.tick > CLICK_MATCH_TICKS)
			{
				continue;
			}

			long taken = Math.min(available, entry.quantity);
			if (entry.kind == GroundKind.LOOT_OVERFLOW)
			{
				// Not what this kill's loot event already listed
				long added = entry.kill == lootKill ? lootSources.overflowPickedUp(entry.itemId, taken) : taken;
				addLoot(entry.kill, entry.itemId, added);
				s.viewDirty = true;
				keeper.requestSave();
			}
			else if (entry.kind == GroundKind.OWN_AMMO)
			{
				host.ownAmmoPickedUp(entry.itemId, taken);
			}
			else
			{
				host.ownDropPickedUp(entry.itemId, taken);
			}

			if (available - taken > 0)
			{
				gained.put(entry.itemId, available - taken);
			}
			else
			{
				gained.remove(entry.itemId);
			}
			entry.quantity -= taken;
			if (entry.quantity <= 0)
			{
				it.remove();
			}
		}
	}
}
