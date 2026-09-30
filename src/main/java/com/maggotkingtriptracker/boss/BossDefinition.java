package com.maggotkingtriptracker.boss;

import com.maggotkingtriptracker.model.ChargeType;
import com.maggotkingtriptracker.model.Kill;
import com.maggotkingtriptracker.model.Trip;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Everything the tracker needs to know about one boss. Defaults cover a boss without special features, so a new
 * boss only overrides what is different. Definitions are immutable and safe to read from any thread.
 */
public abstract class BossDefinition
{
	// ---- Identity ----

	/**
	 * Stable id used as the key in saved history. Never change it.
	 */
	public abstract String getId();

	public abstract String getDisplayName();

	/**
	 * Item whose icon represents the boss in the panel.
	 */
	public abstract int getIconItemId();

	public List<BossVariant> getVariants()
	{
		return Collections.emptyList();
	}

	// ---- Detection ----

	/**
	 * Template region ids (from WorldPoint.fromLocalInstance) where a trip is running.
	 */
	public abstract Set<Integer> getRegions();

	/**
	 * Regions just outside where an open trip waits (paused) for a while before it ends.
	 */
	public Set<Integer> getWaitingRegions()
	{
		return Collections.emptySet();
	}

	public TripModel getTripModel()
	{
		return TripModel.INSTANCE_KILLS;
	}

	public abstract Set<Integer> getBossNpcIds();

	/**
	 * NPC whose name is used in the kill-count message and by the Loot Tracker, unless {@link #getKillNames()} lists
	 * the names.
	 */
	public abstract int getNameNpcId();

	/**
	 * Names in the kill-count message ("Your &lt;name&gt; kill count is"), each to the variant its kills belong to
	 * (null for none). Empty means the name of {@link #getNameNpcId()}, with no variant.
	 */
	public Map<String, String> getKillNames()
	{
		return Collections.emptyMap();
	}

	/**
	 * The game's fight clock starts when the boss spawns (Maggot King). Otherwise see {@link #fightStartDelayMs}.
	 */
	public boolean isFightStartOnSpawn()
	{
		return true;
	}

	/**
	 * @param text a game message without colour tags
	 * @return how long after this message the game's fight clock starts, or null if it doesn't start a fight
	 */
	public Long fightStartDelayMs(String text)
	{
		return null;
	}

	/**
	 * @param text a game message without colour tags, seen shortly after clicking a grave helper
	 * @return the fee paid to get items back after a death, or null if the message isn't a payment
	 */
	public Long reclaimFee(String text)
	{
		return null;
	}

	// ---- Loot ----

	/**
	 * Whether a Loot Tracker event is this boss's loot. {@code bossName} is the name of {@link #getNameNpcId()}.
	 */
	public boolean isLootEvent(String name, LootRecordType type, String bossName)
	{
		return type != LootRecordType.EVENT && bossName.equalsIgnoreCase(name);
	}

	/**
	 * NPCs (e.g. a corpse) whose loot options start the loot window for a kill.
	 */
	public Set<Integer> getLootTriggerNpcs()
	{
		return Collections.emptySet();
	}

	/**
	 * Options on the loot trigger NPC. Empty when every kill drops loot the same way.
	 */
	public List<LootChoice> getLootChoices()
	{
		return Collections.emptyList();
	}

	/**
	 * Items that land on the floor next to you after a loot click are loot (full inventory overflow).
	 */
	public boolean isGroundOverflowLoot()
	{
		return false;
	}

	public LootChoice choiceForOption(String option)
	{
		for (LootChoice choice : getLootChoices())
		{
			if (choice.getOption().equalsIgnoreCase(option))
			{
				return choice;
			}
		}
		return null;
	}

	// ---- Odds ----

	/**
	 * Uniques first, then the pet, then tertiaries, in the order the panel shows them.
	 */
	public abstract List<ExpectedDrop> getDrops();

	/**
	 * The player's chance of any unique per loot kill.
	 */
	public abstract double anyUniqueChance(KillContext context);

	/**
	 * Whether a kill can roll uniques, so it counts toward luck and the dry streak.
	 */
	public boolean countsForLuck(Kill kill)
	{
		if (getLootChoices().isEmpty())
		{
			return true;
		}
		LootChoice choice = choiceForKey(kill.getChoice());
		return choice != null && choice.isCountsForLuck();
	}

	/**
	 * What luck is counted over, e.g. "Open-stomach kills"; used in tooltips.
	 */
	public String getLuckKillsName()
	{
		return "kills";
	}

	public ExpectedDrop getPet()
	{
		for (ExpectedDrop drop : getDrops())
		{
			if (drop.getKind() == DropKind.PET)
			{
				return drop;
			}
		}
		return null;
	}

	/**
	 * Uniques and the pet: highlighted with a gold border and alerted on.
	 */
	public Set<Integer> getHighlightedItems()
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (ExpectedDrop drop : getDrops())
		{
			if (drop.getKind() != DropKind.TERTIARY)
			{
				ids.add(drop.getItemId());
			}
		}
		return ids;
	}

	public boolean isUnique(int itemId)
	{
		for (ExpectedDrop drop : getDrops())
		{
			if (drop.getKind() == DropKind.UNIQUE && drop.getItemId() == itemId)
			{
				return true;
			}
		}
		return false;
	}

	// ---- Boss-specific features ----

	/**
	 * Eggs that can be popped for a pet, lowest tier first, to their pet chance. Empty turns egg tracking off.
	 */
	public Map<Integer, Double> getEggPetRates()
	{
		return Collections.emptyMap();
	}

	/**
	 * Drops whose value is only known once polished. Empty turns polish tracking off.
	 */
	public Set<Integer> getTarnishedItems()
	{
		return Collections.emptySet();
	}

	/**
	 * Items thrown from your equipment onto the floor and usually picked back up (the Nightmare's blisterwood
	 * stakes): handled like dropped items, so only the ones left behind count, as "Dropped".
	 */
	public Set<Integer> getRecoverableItems()
	{
		return Collections.emptySet();
	}

	/**
	 * Items that leave the inventory by being converted into something else, never as a supply.
	 */
	public Set<Integer> getConvertedItems()
	{
		return Collections.emptySet();
	}

	/**
	 * @return a caveat for this charge type's count at this boss, shown in the supply tooltip; null if none
	 */
	public String getChargeNote(ChargeType type)
	{
		return null;
	}

	// ---- Deaths and supplies ----

	/**
	 * NPCs that move or return your gravestone for a payment after dying here.
	 */
	public Set<Integer> getGraveHelperNpcs()
	{
		return Collections.emptySet();
	}

	/**
	 * What the grave helpers accept as payment.
	 */
	public Set<Integer> getGravePaymentItems()
	{
		return Collections.emptySet();
	}

	/**
	 * Supplies obtained inside (Theatre of Blood supply chest purchases, items picked up) are free: only use beyond
	 * them is paid for, per item and per dose.
	 */
	public boolean isAcquiredInsideFree()
	{
		return false;
	}

	/**
	 * Dropped items left behind are used supplies (per dose for potions), except equipment, which is never a cost
	 * (the salve amulet after Bloat). Otherwise they are a "Dropped" cost at their item price.
	 */
	public boolean isDroppedSupplyUsed()
	{
		return false;
	}

	/**
	 * Dying ends the trip (items go to a grave). False where you keep your items and carry on (Theatre of Blood
	 * rooms: you rejoin when the team clears the room).
	 */
	public boolean isDeathEndsTrip()
	{
		return true;
	}

	// ---- Raids (TripModel.ONE_RAID) ----

	/**
	 * @param text a game message without colour tags
	 * @return the variant a message names for the raid you're in (entering it, completing a room), or null
	 */
	public String raidMode(String text)
	{
		return null;
	}

	/**
	 * A room of the raid was cleared ("Wave '...' complete!").
	 */
	public boolean isRaidRoomComplete(String text)
	{
		return false;
	}

	/**
	 * @return the completion-count message's mode and count, or null
	 */
	public RaidCompletion raidCompletion(String text)
	{
		return null;
	}

	/**
	 * The kill count stored on a completed raid, on the same scale as {@link #getAllTimeSources()} kill counts.
	 *
	 * @param killCounts reads a Chat Commands kill count by key; null if unknown
	 * @return the kill count, or null if this raid isn't counted on that scale
	 */
	public Integer raidKillCount(RaidCompletion completion, Function<String, Integer> killCounts)
	{
		return completion.getCount();
	}

	/**
	 * @return the raid's total time from its completion-time message, or null
	 */
	public Long raidTimeMs(String text)
	{
		return null;
	}

	/**
	 * Your own death in the raid (not a teammate's).
	 */
	public boolean isOwnRaidDeath(String text)
	{
		return false;
	}

	/**
	 * @return the item name from a broadcast of a unique anyone in the raid received, or null
	 */
	public String teamUniqueName(String text)
	{
		return null;
	}

	/**
	 * @return the game's own count of raids since your last unique, or null
	 */
	public Integer gameDryStreak(String text)
	{
		return null;
	}

	/**
	 * @return the game's own count of raids since you last saw a unique for anyone in the team, or null
	 */
	public Integer gameTeamDryStreak(String text)
	{
		return null;
	}

	/**
	 * Show a team dry streak (raids since anyone in the team got a unique) next to your own.
	 */
	public boolean hasTeamDryStreak()
	{
		return false;
	}

	/**
	 * Whether a Loot Tracker event is this raid's reward, which can be claimed after the raid has ended.
	 */
	public boolean isRaidLootEvent(String name, LootRecordType type)
	{
		return false;
	}

	/**
	 * What a wipe costs (the reclaim fee), added when a raid ends in one.
	 */
	public long getWipeFee()
	{
		return 0;
	}

	/**
	 * Varbits that are set for each player in the team (the Theatre of Blood's health bar slots), to count the
	 * team size at the start of a raid. Empty for solo bosses.
	 */
	public List<Integer> getTeamSlotVarbits()
	{
		return Collections.emptyList();
	}

	/**
	 * Whether a kill is on the kill-count scale used for the dry streak (Entry Mode raids have their own count).
	 */
	public boolean countsTowardKillCount(Kill kill)
	{
		return true;
	}

	/**
	 * The chance context for past kills from the all-time records, which don't say the mode or team size.
	 *
	 * @param variant the mode, or null for the boss's usual one
	 * @param pastTeamSize the "Typical team size for past raids" setting
	 */
	public KillContext pastKillContext(String variant, int pastTeamSize)
	{
		return KillContext.DEFAULT;
	}

	/**
	 * A short description of a trip for the History card (e.g. "Normal · team of 4"); null to show the
	 * kill count.
	 */
	public String tripDetail(Trip trip)
	{
		return null;
	}

	// ---- Panel ----

	/**
	 * The third cell of the profit card.
	 */
	public abstract TripStat getProfitCell();

	/**
	 * Extra CSV columns, written after the kills column.
	 */
	public List<TripStat> getCsvColumns()
	{
		return Collections.emptyList();
	}

	public String getEmptyStateText()
	{
		return "No trips yet. Go to " + getDisplayName() + " to start one.";
	}

	/**
	 * What the trip area is called, e.g. "lair", as in "Trip paused (outside the lair)".
	 */
	public String getAreaNoun()
	{
		return "area";
	}

	/**
	 * Why uniques only come from some kills, e.g. "Uniques and the kill pet only come from Open-stomach."; empty
	 * when every kill counts.
	 */
	public String getLuckNote()
	{
		return "";
	}

	/**
	 * Short label for the kills luck is counted over, e.g. "Stomach kills".
	 */
	public String getLuckKillsLabel()
	{
		return "Kills";
	}

	// ---- All-time records ----

	public abstract List<AllTimeSource> getAllTimeSources();

	/**
	 * File name part for exports, e.g. "maggot-king".
	 */
	public String getFileSlug()
	{
		return getId().replace('_', '-');
	}

	private LootChoice choiceForKey(String key)
	{
		for (LootChoice choice : getLootChoices())
		{
			if (choice.getKey().equals(key))
			{
				return choice;
			}
		}
		return null;
	}
}
