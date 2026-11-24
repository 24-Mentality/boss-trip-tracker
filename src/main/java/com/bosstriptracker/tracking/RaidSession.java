package com.bosstriptracker.tracking;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.BossRegistry;
import com.bosstriptracker.boss.DropKind;
import com.bosstriptracker.boss.ExpectedDrop;
import com.bosstriptracker.boss.RaidCompletion;
import com.bosstriptracker.boss.TripModel;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.DeathRecord;
import com.bosstriptracker.model.ItemEntry;
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
 * Raids (one raid per trip: the Theatre of Blood, the Fractured Archive): the mode, rooms, deaths, wipes and
 * completion from the raid's messages (or from its reward, when no completion message was recognised), the team's
 * purples (the item only, never who got it), the reward claimed in the raid or later from the chest outside, the pet,
 * and what was obtained inside (free). Client thread.
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

		/**
		 * The game ended the raid with the whole team dead: your items are in a gravestone outside, so container
		 * changes until you're back out are the wipe, not supplies.
		 */
		void wiped(long now);

		/**
		 * The wipe's reclaim fee is paid later, at the gravestone outside: watch for the payment on this death.
		 */
		void expectReclaimFee(DeathRecord death);

		void alertForDrop(BossDefinition boss, int itemId, long quantity);

		void alertPet(String message);

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
	 * The raid just left, until the next raid or logging out: its (ended) trip, and where its completion is among the
	 * trip's kills (-1 if no completion was seen).
	 */
	private Trip leftRaidTrip;
	private BossDefinition leftRaidBoss;
	private int leftRaidIndex = -1;
	/**
	 * Its reward hasn't been claimed yet: it can be claimed after leaving (the chest by the bank), until logging out.
	 */
	private boolean leftRaidUnclaimed;
	private final RaidPet pet = new RaidPet();

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
		leftRaidTrip = null;
		leftRaidBoss = null;
		leftRaidIndex = -1;
		leftRaidUnclaimed = false;
		pet.reset();
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
	TripEndReason left(TripEndReason reason, Kill completion, long now)
	{
		TripEndReason raidEnd = raid.endReason();
		if (raidEnd != null)
		{
			reason = raidEnd;
		}
		if (raidEnd == TripEndReason.WIPED)
		{
			List<DeathRecord> deaths = s.currentTrip.getDeaths();
			if (deaths.isEmpty())
			{
				// A wipe means you died too; the message must have been missed
				DeathRecord death = new DeathRecord();
				death.setAt(now);
				deaths.add(death);
			}
			DeathRecord last = deaths.get(deaths.size() - 1);
			if (s.tripBoss.getWipeFee() > 0)
			{
				// Items are reclaimed from a chest for a fixed fee (the Theatre of Blood; no payment has been logged yet)
				last.setReclaimFee(last.getReclaimFee() + s.tripBoss.getWipeFee());
			}
			else if (!s.tripBoss.getGraveHelperNpcs().isEmpty())
			{
				// Items are in a gravestone outside and the fee is read from the bank payment (the Fractured Archive)
				host.expectReclaimFee(last);
			}
		}
		else if (raidEnd == TripEndReason.COMPLETED && completion != null)
		{
			// Left without claiming: the reward waits in the chest outside until you log out
			leftRaidTrip = s.currentTrip;
			leftRaidBoss = s.tripBoss;
			leftRaidIndex = s.currentTrip.getKills().indexOf(completion);
			leftRaidUnclaimed = completion.getLoot().isEmpty();
		}
		else if (raidEnd == null && raid.rewardCompletes())
		{
			// Left with no ending seen. If the raid was completed with a message that wasn't recognised, a reward
			// from the chest outside will show it
			leftRaidTrip = s.currentTrip;
			leftRaidBoss = s.tripBoss;
			leftRaidIndex = -1;
			leftRaidUnclaimed = true;
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
		}
		// The Fractured Archive's challenge messages carry the raid's running total, so a completed raid has a time
		// even if the total-time message is missed
		Long raidTime = boss.raidTimeMs(text);
		if (raidTime != null)
		{
			raid.raidTime(raidTime);
		}
		if (boss.isRaidRoomComplete(text))
		{
			raid.roomCleared();
			return true;
		}
		if (mode != null || raidTime != null)
		{
			return true;
		}
		if (boss.isOwnRaidDeath(text))
		{
			host.markDead(now);
			return true;
		}
		if (boss.isRaidFailed(text))
		{
			raid.wiped();
			host.wiped(now);
			return true;
		}
		if (boss.isRaidAbandoned(text))
		{
			raid.abandoned();
			return true;
		}
		RaidCompletion completion = boss.raidCompletion(text);
		if (completion != null)
		{
			Integer killCount = boss.raidKillCount(completion, host::killCount);
			Kill recorded = raid.isCompleted() ? host.lootKill() : null;
			if (!raid.isCompleted())
			{
				recordRaid(killCount, completion.getVariant(), tick, now);
			}
			else if (recorded != null && recorded.getKillCount() == null)
			{
				// The reward came first and recorded the raid: this is its count
				recorded.setKillCount(killCount);
				if (completion.getVariant() != null)
				{
					recorded.setVariant(completion.getVariant());
				}
				s.viewDirty = true;
				keeper.requestSave();
			}
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
	private void recordRaid(Integer killCount, String variant, int tick, long now)
	{
		host.recordKill(killCount, variant, tick, now);
		Kill kill = host.lootKill();
		completed(kill);
		raid.completed();
		if (pet.takeWaiting())
		{
			markPet(kill, s.tripBoss);
		}
	}

	/**
	 * What the raid's messages said, onto its completion.
	 */
	private void completed(Kill kill)
	{
		kill.setPartySize(raid.getTeamSize());
		if (raid.getRaidTimeMs() != null)
		{
			kill.setDurationMs(raid.getRaidTimeMs());
		}
		kill.getTeamUniques().addAll(raid.getTeamUniques());
		raid.getTeamUniques().clear();
	}

	/**
	 * A pet message. Inside a raid it's the raid's (it goes onto the completion once there is one); after a raid, only
	 * around its reward being claimed from the chest outside. Unverified: no raid pet has been logged.
	 *
	 * @return whether the raid took it
	 */
	boolean petMessage(int tick)
	{
		if (s.inArea && s.currentTrip != null && isRaid(s.tripBoss))
		{
			Kill kill = raid.isCompleted() ? host.lootKill() : null;
			if (pet.inRaid(kill != null))
			{
				markPet(kill, s.tripBoss);
				s.viewDirty = true;
				keeper.requestSave();
			}
			return true;
		}
		if (leftRaidBoss == null || !pet.outside(tick))
		{
			return false;
		}
		Kill kill = leftRaidKill(false, 0);
		if (kill != null)
		{
			markPet(kill, leftRaidBoss);
			s.historyChanged();
			keeper.requestSave();
		}
		return true;
	}

	private void markPet(Kill kill, BossDefinition boss)
	{
		kill.setPet(true);
		ExpectedDrop petDrop = boss.getPet();
		if (petDrop != null && kill.getLoot().stream().noneMatch(e -> e.getItemId() == petDrop.getItemId()))
		{
			kill.getLoot().add(new ItemEntry(petDrop.getItemId(), 1, 0));
		}
		host.alertPet(boss.getDisplayName() + " pet!");
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
	 * A raid's reward, claimed in the raid or later from the chest outside (until logging out). A reward only exists
	 * for a completed raid, so one that arrives with no completion recorded records it (the Fractured Archive's
	 * completion message has never been seen, and may not be worded as expected).
	 *
	 * @return whether the event was a raid reward
	 */
	boolean loot(LootReceived event, int tick, long now)
	{
		boolean inRaid = s.currentTrip != null && isRaid(s.tripBoss);
		BossDefinition boss = inRaid ? s.tripBoss : leftRaidUnclaimed ? leftRaidBoss : null;
		if (boss == null || !boss.isRaidLootEvent(event.getName(), event.getType()))
		{
			return false;
		}
		Kill kill;
		boolean petBefore = false;
		if (inRaid)
		{
			if (raid.rewardCompletes())
			{
				// The kill count isn't known; the completion message fills it in if it still comes
				recordRaid(null, raid.getMode(), tick, now);
			}
			kill = raid.isCompleted() ? host.lootKill() : null;
		}
		else
		{
			kill = leftRaidKill(true, now);
			leftRaidUnclaimed = false;
			petBefore = pet.claimedOutside(tick);
		}
		if (kill == null)
		{
			// Not a raid this plugin can put it on (its trip is gone, or the game ended it another way)
			return true;
		}
		ExpectedDrop petDrop = boss.getPet();
		boolean petListed = false;
		for (ItemStack stack : event.getItems())
		{
			ItemEntries.merge(kill.getLoot(), stack.getId(), stack.getQuantity(), prices.price(stack.getId()), false);
			host.alertForDrop(boss, stack.getId(), stack.getQuantity());
			petListed |= petDrop != null && petDrop.getItemId() == stack.getId();
		}
		if (petListed || petBefore)
		{
			markPet(kill, boss);
		}
		s.historyChanged();
		keeper.requestSave();
		return true;
	}

	/**
	 * The completion of the raid just left, in its (ended) trip, to change.
	 *
	 * @param fromReward the reward is being claimed: a raid left with no completion seen gets one now, and its trip
	 *                   is put back if it was dropped as empty when it ended
	 * @return the completion, or null if there is none
	 */
	private Kill leftRaidKill(boolean fromReward, long now)
	{
		if (leftRaidTrip == null || s.history == null)
		{
			return null;
		}
		Trip trip = keeper.editable(leftRaidTrip);
		if (leftRaidIndex >= 0)
		{
			return trip == null || leftRaidIndex >= trip.getKills().size() ? null : trip.getKills().get(leftRaidIndex);
		}
		if (!fromReward)
		{
			return null;
		}
		if (trip == null)
		{
			trip = leftRaidTrip.copy();
			s.history.boss(leftRaidBoss.getId()).getTrips().add(trip);
			keeper.monthChanged(leftRaidBoss.getId(), trip);
		}
		Kill kill = new Kill();
		kill.setVariant(raid.getMode());
		kill.setEndedAt(trip.getEndedAt() != null ? trip.getEndedAt() : now);
		completed(kill);
		trip.getKills().add(kill);
		trip.setEndReason(TripEndReason.COMPLETED);
		leftRaidTrip = trip;
		leftRaidIndex = trip.getKills().size() - 1;
		return kill;
	}
}
