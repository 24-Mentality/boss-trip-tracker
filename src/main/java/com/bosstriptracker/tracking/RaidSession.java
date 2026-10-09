package com.bosstriptracker.tracking;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.BossRegistry;
import com.bosstriptracker.boss.DropKind;
import com.bosstriptracker.boss.ExpectedDrop;
import com.bosstriptracker.boss.RaidCompletion;
import com.bosstriptracker.boss.TripModel;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.DeathRecord;
import com.bosstriptracker.model.Kill;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.model.TripEndReason;
import com.bosstriptracker.pricing.PriceService;
import java.util.List;
import java.util.Locale;
import java.util.function.IntUnaryOperator;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;

/**
 * Raids (one raid per trip, the Theatre of Blood): the mode, rooms, deaths and completion from the raid's messages,
 * the team's purples (the item only, never who got it), the reward claimed in the raid or later from the chest
 * outside, and what was obtained inside (free). Client thread.
 */
class RaidSession
{
	private static final int CLICK_MATCH_TICKS = RecentClicks.MATCH_TICKS;

	/**
	 * What a raid needs from the rest of the tracker.
	 */
	interface Host
	{
		void recordKill(Integer killCount, String variant, int tick, long now);

		/**
		 * The kill loot is attached to; for a raid, its completion.
		 */
		Kill lootKill();

		void markDead(long now);

		void alertForDrop(BossDefinition boss, int itemId, long quantity);

		/**
		 * A kill count Chat Commands saved, or null.
		 */
		Integer killCount(String key);
	}

	private final TripSession s;
	private final HistoryKeeper keeper;
	private final BossRegistry registry;
	private final PriceService prices;
	private final Host host;
	private final RaidState raid = new RaidState();
	private final FreeSupplies freeSupplies = new FreeSupplies();
	/**
	 * The mode named by the entry message, which comes a tick before you're inside.
	 */
	private String enteredRaidMode;
	private int enteredRaidModeTick = -100;
	/**
	 * A completed raid whose reward hasn't been claimed yet: it can be claimed after leaving (the chest by the bank),
	 * until logging out.
	 */
	private Kill unclaimedRaid;
	private Trip unclaimedRaidTrip;
	private int unclaimedRaidIndex;
	private BossDefinition unclaimedRaidBoss;

	RaidSession(TripSession s, HistoryKeeper keeper, BossRegistry registry, PriceService prices, Host host)
	{
		this.s = s;
		this.keeper = keeper;
		this.registry = registry;
		this.prices = prices;
		this.host = host;
	}

	FreeSupplies freeSupplies()
	{
		return freeSupplies;
	}

	/**
	 * Forgets the entry message seen, e.g. on logout.
	 */
	void reset()
	{
		enteredRaidMode = null;
		enteredRaidModeTick = -100;
	}

	/**
	 * Unclaimed raid rewards are lost on logout.
	 */
	void loggedOut()
	{
		unclaimedRaid = null;
		unclaimedRaidTrip = null;
		unclaimedRaidBoss = null;
	}

	/**
	 * In a raid: the team's size from the health bars' varbits.
	 */
	void tick(IntUnaryOperator varbits)
	{
		int players = 0;
		for (int varbit : s.tripBoss.getTeamSlotVarbits())
		{
			if (varbits.applyAsInt(varbit) != 0)
			{
				players++;
			}
		}
		raid.teamSeen(players);
	}

	/**
	 * A raid's trip starts: with the mode from the entry message if it came just before.
	 */
	void entered(int tick)
	{
		raid.start(tick - enteredRaidModeTick <= CLICK_MATCH_TICKS ? enteredRaidMode : null);
		enteredRaidMode = null;
		freeSupplies.clear();
		// A new raid: an earlier one's reward can't be claimed any more
		loggedOut();
	}

	/**
	 * Leaving the raid: how it ended, the wipe fee, and an unclaimed reward kept for the chest outside.
	 *
	 * @param reason how the trip ended going by where you are
	 * @return how the raid ended, if known, else {@code reason}
	 */
	TripEndReason left(TripEndReason reason, Kill completion)
	{
		TripEndReason raidEnd = raid.endReason();
		if (raidEnd != null)
		{
			reason = raidEnd;
		}
		if (raidEnd == TripEndReason.WIPED)
		{
			// Items are reclaimed from the chest for a fee (no payment has been logged yet, so it's added here)
			List<DeathRecord> deaths = s.currentTrip.getDeaths();
			DeathRecord last = deaths.get(deaths.size() - 1);
			last.setReclaimFee(last.getReclaimFee() + s.tripBoss.getWipeFee());
		}
		else if (raidEnd == TripEndReason.COMPLETED && completion != null && completion.getLoot().isEmpty())
		{
			// Left without claiming: the reward waits in the chest outside until you log out
			unclaimedRaid = completion;
			unclaimedRaidTrip = s.currentTrip;
			unclaimedRaidIndex = s.currentTrip.getKills().indexOf(completion);
			unclaimedRaidBoss = s.tripBoss;
		}
		return reason;
	}

	/**
	 * @return whether this death in a room counts (one per death; the game repeats the message)
	 */
	boolean died(int tick)
	{
		return raid.died(tick);
	}

	/**
	 * A friends chat notification in a raid: only the team's purple broadcast is read (unverified as this type).
	 */
	void friendsChat(String text)
	{
		if (s.inArea && s.currentTrip != null && isRaid(s.tripBoss))
		{
			String uniqueName = s.tripBoss.teamUniqueName(text);
			if (uniqueName != null)
			{
				teamUnique(s.tripBoss, uniqueName);
			}
		}
	}

	/**
	 * Raid progress messages: the mode, rooms cleared, your deaths, the raid's time and completion, purples and the
	 * game's dry streak.
	 *
	 * @return whether the message was one of them
	 */
	boolean message(String text, int tick, long now)
	{
		if (!s.inArea)
		{
			// The entry message comes a tick before you're inside
			for (BossDefinition boss : registry.all())
			{
				String mode = isRaid(boss) ? boss.raidMode(text) : null;
				if (mode != null)
				{
					enteredRaidMode = mode;
					enteredRaidModeTick = tick;
					return true;
				}
			}
			return false;
		}
		if (s.currentTrip == null || !isRaid(s.tripBoss))
		{
			return false;
		}

		BossDefinition boss = s.tripBoss;
		String mode = boss.raidMode(text);
		if (mode != null)
		{
			raid.modeSeen(mode);
			if (boss.isRaidRoomComplete(text))
			{
				raid.roomCleared();
			}
			return true;
		}
		if (boss.isOwnRaidDeath(text))
		{
			host.markDead(now);
			return true;
		}
		Long raidTime = boss.raidTimeMs(text);
		if (raidTime != null)
		{
			raid.raidTime(raidTime);
			return true;
		}
		RaidCompletion completion = boss.raidCompletion(text);
		if (completion != null)
		{
			recordRaid(completion, tick, now);
			return true;
		}
		String uniqueName = boss.teamUniqueName(text);
		if (uniqueName != null)
		{
			teamUnique(boss, uniqueName);
			return true;
		}
		Integer dryStreak = boss.gameDryStreak(text);
		if (dryStreak != null)
		{
			BossHistory bossHistory = s.writableHistory(boss);
			Integer killCount = host.lootKill() != null && raid.isCompleted() ? host.lootKill().getKillCount() : null;
			if (bossHistory != null && killCount != null)
			{
				bossHistory.setGameDryStreak(dryStreak);
				bossHistory.setGameDryStreakKc(killCount);
				s.historyChanged();
				keeper.requestSave();
			}
			return true;
		}
		Integer teamDryStreak = boss.gameTeamDryStreak(text);
		if (teamDryStreak != null)
		{
			BossHistory bossHistory = s.writableHistory(boss);
			if (bossHistory != null && host.lootKill() != null && raid.isCompleted())
			{
				bossHistory.setGameTeamDryStreak(teamDryStreak);
				bossHistory.setGameTeamDryStreakAt(host.lootKill().getEndedAt());
				s.historyChanged();
				keeper.requestSave();
			}
			return true;
		}
		return false;
	}

	/**
	 * A completed raid is its trip's one kill. Its reward is claimed from a chest, maybe after leaving.
	 */
	private void recordRaid(RaidCompletion completion, int tick, long now)
	{
		host.recordKill(s.tripBoss.raidKillCount(completion, host::killCount), completion.getVariant(), tick, now);
		Kill kill = host.lootKill();
		kill.setPartySize(raid.getTeamSize());
		if (raid.getRaidTimeMs() != null)
		{
			kill.setDurationMs(raid.getRaidTimeMs());
		}
		kill.getTeamUniques().addAll(raid.getTeamUniques());
		raid.getTeamUniques().clear();
		raid.completed();
	}

	/**
	 * A purple for anyone in the raid. Only the item is kept, never who got it.
	 */
	private void teamUnique(BossDefinition boss, String name)
	{
		int itemId = uniqueNamed(boss, name);
		if (itemId < 0)
		{
			return;
		}
		if (raid.isCompleted() && host.lootKill() != null)
		{
			host.lootKill().getTeamUniques().add(itemId);
			s.viewDirty = true;
			keeper.requestSave();
		}
		else
		{
			raid.getTeamUniques().add(itemId);
		}
	}

	/**
	 * @return the boss's unique with this name ("(uncharged)" or not), or -1
	 */
	private int uniqueNamed(BossDefinition boss, String name)
	{
		String wanted = baseName(name);
		for (ExpectedDrop drop : boss.getDrops())
		{
			if (drop.getKind() == DropKind.UNIQUE && baseName(prices.name(drop.getItemId())).equals(wanted))
			{
				return drop.getItemId();
			}
		}
		return -1;
	}

	private static String baseName(String name)
	{
		return name.toLowerCase(Locale.ROOT).replace("(uncharged)", "").replaceAll("[.!]+$", "").trim();
	}

	static boolean isRaid(BossDefinition boss)
	{
		return boss != null && boss.getTripModel() == TripModel.ONE_RAID;
	}

	/**
	 * A raid's reward, claimed in the raid or later from the chest outside (until logging out).
	 *
	 * @return whether the event was a raid reward
	 */
	boolean loot(LootReceived event)
	{
		boolean inRaid = s.currentTrip != null && isRaid(s.tripBoss);
		BossDefinition boss = inRaid ? s.tripBoss : unclaimedRaidBoss;
		if (boss == null || !boss.isRaidLootEvent(event.getName(), event.getType()))
		{
			return false;
		}
		Kill kill = inRaid ? (raid.isCompleted() ? host.lootKill() : null) : unclaimedRaidForChange();
		unclaimedRaid = null;
		unclaimedRaidTrip = null;
		unclaimedRaidBoss = null;
		if (kill == null)
		{
			// The reward of a raid this plugin didn't see completed
			return true;
		}
		for (ItemStack stack : event.getItems())
		{
			ItemEntries.merge(kill.getLoot(), stack.getId(), stack.getQuantity(), prices.price(stack.getId()), false);
			host.alertForDrop(boss, stack.getId(), stack.getQuantity());
		}
		s.historyChanged();
		keeper.requestSave();
		return true;
	}

	/**
	 * The completed raid whose reward is claimed from the chest outside, in its (ended) trip.
	 */
	private Kill unclaimedRaidForChange()
	{
		Trip trip = keeper.editable(unclaimedRaidTrip);
		if (trip == null || unclaimedRaidIndex < 0 || unclaimedRaidIndex >= trip.getKills().size())
		{
			return null;
		}
		return trip.getKills().get(unclaimedRaidIndex);
	}
}
