package com.bosstriptracker.tracking;

import com.bosstriptracker.BossTripTrackerConfig;
import com.bosstriptracker.EyeOfAyakCharge;
import com.bosstriptracker.model.BossHistory;
import com.bosstriptracker.model.ChargeType;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Trip;
import com.bosstriptracker.pricing.PriceService;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;

/**
 * Supplies: what each tick's inventory change used up ({@link SupplyAccounting}), charges used, items dropped and left
 * behind, supplies used just before entering, your own ammo picked back up, and lines repriced once prices load.
 * Client thread.
 */
@Slf4j
class SupplyRecorder
{
	private static final int CLICK_MATCH_TICKS = RecentClicks.MATCH_TICKS;
	private static final long PRE_ENTRY_WINDOW_MS = 60_000;
	private static final long JUNK_PRICE = SupplyAccounting.JUNK_PRICE;
	private static final Set<String> CONSUME_OPTIONS = ImmutableSet.of("Eat", "Drink", "Cast");
	private static final String OPTION_POLISH = SupplyAccounting.OPTION_POLISH;

	@AllArgsConstructor
	private static class PreEntryUse
	{
		final long at;
		final List<ItemEntry> items;
	}

	private final TripSession s;
	private final HistoryKeeper keeper;
	private final BossTripTrackerConfig config;
	private final PriceService prices;
	private final RecentClicks recentClicks;
	private final EggTracker eggTracker;
	private final PolishTracker polishTracker;
	private final LootRecorder loot;
	private final RaidSession raids;
	private final Set<Integer> convertedItems;
	/**
	 * Takes the aranei scout's grave-move payment out of a tick's removed items.
	 */
	private final ObjIntConsumer<Map<Integer, Long>> gravePayments;
	private final SupplyAccounting accounting;
	private final Map<Integer, Long> pendingDrops = new HashMap<>();
	/**
	 * Worn ammo, and a worn weapon that stacks (knives, darts): your own lands on the floor and can be picked back up.
	 */
	private final Set<Integer> wornAmmo = new HashSet<>();
	private final Deque<PreEntryUse> preEntryUses = new ArrayDeque<>();
	private int lastBankTick = -100;
	/**
	 * Lines saved at 0 gp have been repriced since the price list last loaded.
	 */
	private boolean zeroPricesChecked;

	SupplyRecorder(TripSession s, HistoryKeeper keeper, BossTripTrackerConfig config, PriceService prices,
		RecentClicks recentClicks, EggTracker eggTracker, PolishTracker polishTracker, LootRecorder loot, RaidSession raids,
		Set<Integer> convertedItems, ObjIntConsumer<Map<Integer, Long>> gravePayments)
	{
		this.s = s;
		this.keeper = keeper;
		this.config = config;
		this.prices = prices;
		this.recentClicks = recentClicks;
		this.eggTracker = eggTracker;
		this.polishTracker = polishTracker;
		this.loot = loot;
		this.raids = raids;
		this.convertedItems = convertedItems;
		this.gravePayments = gravePayments;
		this.accounting = new SupplyAccounting(prices);
	}

	/**
	 * Forgets drops and pre-entry use, e.g. on logout.
	 */
	void reset()
	{
		pendingDrops.clear();
		preEntryUses.clear();
	}

	void bankOpened(int tick)
	{
		lastBankTick = tick;
	}

	boolean isWornAmmo(int itemId)
	{
		return wornAmmo.contains(itemId);
	}

	void ownDropPickedUp(int itemId, long quantity)
	{
		pendingDrops.computeIfPresent(itemId, (id, q) -> q - quantity > 0 ? q - quantity : null);
	}

	/**
	 * A trip starts or resumes: supplies used in the minute before count toward it.
	 */
	void entered(long now)
	{
		if (config.countPreEntrySupplies())
		{
			for (PreEntryUse use : preEntryUses)
			{
				if (now - use.at <= PRE_ENTRY_WINDOW_MS)
				{
					for (ItemEntry entry : use.items)
					{
						ItemEntries.merge(s.currentTrip.getSupplies(), entry);
					}
				}
			}
		}
		preEntryUses.clear();
	}

	void prune(long now)
	{
		while (!preEntryUses.isEmpty() && now - preEntryUses.peekFirst().at > PRE_ENTRY_WINDOW_MS)
		{
			preEntryUses.removeFirst();
		}
	}

	/**
	 * Lines saved without prices are repriced once the price list has loaded.
	 */
	void pricesTick()
	{
		if (!prices.pricesLoaded())
		{
			zeroPricesChecked = false;
		}
		else if (!zeroPricesChecked && s.history != null && !s.readOnly)
		{
			repriceZeroPrices();
		}
	}

	/**
	 * A history was loaded: check it for lines saved without prices.
	 */
	void historyLoaded()
	{
		zeroPricesChecked = false;
	}

	/**
	 * Charges used in the lair are supplies, priced from the item that recharges them.
	 */
	void chargesUsed(ChargeType type, int used)
	{
		if (!s.inArea || s.currentTrip == null || s.dead)
		{
			return;
		}

		// Priced from everything one recharge takes (e.g. a vial of blood and 200 blood runes for 100 scythe charges)
		if (type == ChargeType.EYE_OF_AYAK && config.eyeOfAyakCharge() == EyeOfAyakCharge.RUNES)
		{
			type = ChargeType.EYE_OF_AYAK_RUNES;
		}
		int chargeItemId = type == ChargeType.TOME_OF_FIRE ? config.tomePage().getItemId()
			: type.isBlowpipeDarts() ? config.blowpipeDarts().getItemId()
			: type.getChargeItemId();
		ItemEntries.merge(s.currentTrip.getSupplies(), ItemEntry.charges(type.getSourceItemId(), used,
			chargeItemId, rechargePrice(type, chargeItemId), type.getChargesPerRecharge()));
		s.viewDirty = true;
		keeper.requestSave();
	}

	/**
	 * @param chargeItemId the first recharge item, as chosen (a tome page, a dart)
	 */
	private long rechargePrice(ChargeType type, int chargeItemId)
	{
		long rechargePrice = 0;
		for (ChargeType.Component component : type.getComponents())
		{
			int itemId = component == type.getComponents().get(0) ? chargeItemId : component.getItemId();
			rechargePrice += component.getQuantity() * prices.price(itemId);
		}
		return rechargePrice;
	}

	/**
	 * Lines saved at 0 gp while the price list wasn't loaded get today's price once it is.
	 */
	private void repriceZeroPrices()
	{
		zeroPricesChecked = true;
		List<Trip> trips = new ArrayList<>();
		for (BossHistory boss : s.history.getBosses().values())
		{
			trips.addAll(boss.getTrips());
		}
		int repriced = ZeroPrices.reprice(trips, keeper::editable, new ZeroPrices.Pricing()
		{
			@Override
			public boolean tradeable(ItemEntry entry)
			{
				return prices.isTradeable(entry.isCharges() ? entry.getChargeItemId() : entry.getItemId());
			}

			@Override
			public long price(ItemEntry entry)
			{
				if (entry.isCharges())
				{
					ChargeType type = ChargeType.forLine(entry.getItemId(), entry.getChargeItemId());
					return type == null ? 0 : rechargePrice(type, entry.getChargeItemId());
				}
				PriceService.DoseInfo dose = entry.isPerDose() ? prices.doseInfo(entry.getItemId()) : null;
				return dose == null ? prices.price(entry.getItemId())
					: Math.round((double) prices.price(entry.getItemId()) / dose.getDoses());
			}
		}, JUNK_PRICE);
		if (repriced > 0)
		{
			log.debug("Repriced {} lines saved while prices weren't loaded", repriced);
			s.historyChanged();
			keeper.requestSave();
		}
	}

	void wornChanged(ItemContainer worn)
	{
		wornAmmo.clear();
		if (worn == null)
		{
			return;
		}
		int ammo = wornId(worn, EquipmentInventorySlot.AMMO);
		if (ammo > 0)
		{
			wornAmmo.add(ammo);
		}
		int weapon = wornId(worn, EquipmentInventorySlot.WEAPON);
		if (weapon > 0 && prices.isStackable(weapon))
		{
			wornAmmo.add(weapon);
		}
	}

	static int wornId(ItemContainer worn, EquipmentInventorySlot slot)
	{
		Item item = worn.getItem(slot.getSlotIdx());
		return item == null ? -1 : item.getId();
	}

	void processDelta(Map<Integer, Long> delta, int tick, long now)
	{
		SupplyAccounting.Change change = SupplyAccounting.Change.of(delta);
		Map<Integer, Long> removed = change.removed;
		Map<Integer, Long> gained = change.gained;

		gravePayments.accept(removed, tick);

		boolean trackingTrip = s.inArea && s.currentTrip != null && !s.dead;
		Map<Integer, Long> dropped = accounting.takeDrops(change, recentClicks, tick);
		if (trackingTrip)
		{
			dropped.forEach((itemId, quantity) -> pendingDrops.merge(itemId, quantity, Long::sum));
			// Thrown and usually picked back up: like a drop, only what's left behind counts
			for (int itemId : s.tripBoss.getRecoverableItems())
			{
				Long quantity = removed.remove(itemId);
				if (quantity != null)
				{
					pendingDrops.merge(itemId, quantity, Long::sum);
				}
			}
		}

		eggTracker.itemsRemoved(removed, tick, now);
		polishTracker.gained(gained, tick);

		accounting.removeConversions(change, recentClicks, tick, convertedItems);

		if (trackingTrip)
		{
			loot.matchPickups(gained, tick);
		}

		List<ItemEntry> used = tick <= s.ignoreDeltasUntilTick ? Collections.emptyList() : accounting.consumption(change);
		if (trackingTrip && s.tripBoss.isAcquiredInsideFree())
		{
			// Supply chest purchases and items picked up inside cost nothing: only use beyond them is paid for
			for (ItemEntry entry : accounting.acquisitions(change))
			{
				long refund = raids.freeSupplies().acquired(entry);
				if (refund > 0)
				{
					// Makes up for use already charged this raid (e.g. arrows picked back up)
					ItemEntries.reduce(s.currentTrip.getSupplies(), entry.getItemId(), entry.isPerDose(), refund);
					s.viewDirty = true;
					keeper.requestSave();
				}
			}
			used = raids.freeSupplies().paidFor(used);
		}

		if (trackingTrip && !recentClicks.has(OPTION_POLISH, -1, tick))
		{
			loot.inventoryGained(gained, tick);
		}

		if (used.isEmpty())
		{
			return;
		}

		if (trackingTrip)
		{
			for (ItemEntry entry : used)
			{
				ItemEntries.merge(s.currentTrip.getSupplies(), entry);
			}
			s.viewDirty = true;
			keeper.requestSave();
		}
		else if (!s.inArea && !s.dead && s.currentTrip != null && s.suspendedOutside
			&& tick - lastBankTick > CLICK_MATCH_TICKS && recentClicks.has(-1, tick, CONSUME_OPTIONS::contains))
		{
			// Waiting just outside the lair: the trip is still open
			for (ItemEntry entry : used)
			{
				ItemEntries.merge(s.currentTrip.getSupplies(), entry);
			}
			s.viewDirty = true;
			keeper.requestSave();
		}
		else if (!s.inArea && !s.dead && config.countPreEntrySupplies()
			&& tick - lastBankTick > CLICK_MATCH_TICKS && recentClicks.has(-1, tick, CONSUME_OPTIONS::contains))
		{
			preEntryUses.addLast(new PreEntryUse(now, used));
		}
	}

	void finalizeDrops()
	{
		if (s.tripBoss.isDroppedSupplyUsed())
		{
			dropsAsUsed();
			pendingDrops.clear();
			return;
		}
		for (Map.Entry<Integer, Long> e : pendingDrops.entrySet())
		{
			long price = prices.price(e.getKey());
			// Without prices nothing can be told to be junk: it's repriced, or removed as junk, later (ZeroPrices)
			if ((price >= JUNK_PRICE || !prices.pricesLoaded()) && e.getValue() > 0)
			{
				ItemEntries.merge(s.currentTrip.getDropped(), e.getKey(), e.getValue(), price, false);
			}
		}
		pendingDrops.clear();
	}

	/**
	 * Dropped supplies left behind count as used (per dose for potions, less anything obtained inside).
	 */
	private void dropsAsUsed()
	{
		List<ItemEntry> used = accounting.droppedAsUsed(pendingDrops);
		if (s.tripBoss.isAcquiredInsideFree())
		{
			used = raids.freeSupplies().paidFor(used);
		}
		for (ItemEntry entry : used)
		{
			ItemEntries.merge(s.currentTrip.getSupplies(), entry);
		}
	}
}
