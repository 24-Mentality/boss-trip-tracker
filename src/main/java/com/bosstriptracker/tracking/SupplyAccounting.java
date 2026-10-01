package com.bosstriptracker.tracking;

import com.bosstriptracker.BlowpipeDart;
import com.bosstriptracker.TomePage;
import com.bosstriptracker.model.ChargeType;
import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.pricing.ItemInfo;
import com.bosstriptracker.pricing.PriceService;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Turns one tick's change to everything the player carries into supply lines: what was used up, dropped, converted
 * into something else or obtained. Holds no trip state, so it can be tested by replaying diagnostic logs.
 */
class SupplyAccounting
{
	/**
	 * Dropped items worth less than this each (empty vials are 2 gp) are junk, not a cost.
	 */
	static final long JUNK_PRICE = 100;

	static final String OPTION_DROP = "Drop";
	static final String OPTION_POLISH = PolishTracker.OPTION_POLISH;
	static final String OPTION_CAST = "Cast";
	static final String OPTION_EAT = "Eat";
	/**
	 * Menu options that put charges into an item. "Use" (item on item) is the game's option; "Load" and "Add" are
	 * unverified.
	 */
	static final Set<String> RECHARGE_OPTIONS = ImmutableSet.of("Use", "Load", "Add");
	/**
	 * What some foods leave behind when finished (a pie dish, a bowl).
	 */
	private static final Set<Integer> FOOD_CONTAINERS = ImmutableSet.of(ItemID.PIEDISH, ItemID.BOWL_EMPTY);

	/**
	 * Worn items that are used up by breaking into nothing, so they are a real cost (unverified in game).
	 */
	static final Set<Integer> BREAKABLE_JEWELLERY = ImmutableSet.of(ItemID.RING_OF_RECOIL, ItemID.BRACELET_OF_SLAUGHTER,
		ItemID.EXPEDITIOUS_BRACELET, ItemID.DODGY_NECKLACE, ItemID.JEWL_NECKLACE_OF_PHOENIX, ItemID.MAGIC_EMERALD_NECKLACE);

	/**
	 * Charged items, charged or not, that recharge materials can be put into.
	 */
	static final Set<Integer> RECHARGEABLE;
	/**
	 * Everything that recharges a charged item. Their charges are costed as they are used, so putting them in is
	 * a conversion.
	 */
	static final Set<Integer> RECHARGE_ITEMS;

	static
	{
		Set<Integer> rechargeable = new HashSet<>(ImmutableSet.of(
			ItemID.SCYTHE_OF_VITUR_UNCHARGED, ItemID.SCYTHE_OF_VITUR_OR, ItemID.SCYTHE_OF_VITUR_UNCHARGED_OR,
			ItemID.SCYTHE_OF_VITUR_BL, ItemID.SCYTHE_OF_VITUR_UNCHARGED_BL,
			ItemID.TOXIC_BLOWPIPE, ItemID.TOXIC_BLOWPIPE_ORNAMENT, ItemID.TOXIC_BLOWPIPE_LOADED_ORNAMENT,
			ItemID.SANGUINESTI_STAFF_UNCHARGED, ItemID.SANGUINESTI_STAFF_OR, ItemID.SANGUINESTI_STAFF_UNCHARGED_OR,
			ItemID.TOTS, ItemID.TOTS_UNCHARGED, ItemID.TOTS_I_CHARGED, ItemID.TOTS_I_UNCHARGED, ItemID.TOTS_CHARGED_ORN,
			ItemID.TOTS_ORN, ItemID.TOTS_UNCHARGED_ORN, ItemID.TOTS_I_CHARGED_ORN, ItemID.TOTS_I_UNCHARGED_ORN,
			ItemID.TOXIC_TOTS_UNCHARGED, ItemID.TOXIC_TOTS_I_CHARGED, ItemID.TOXIC_TOTS_I_UNCHARGED,
			ItemID.TOXIC_TOTS_CHARGED_ORN, ItemID.TOXIC_TOTS_UNCHARGED_ORN, ItemID.TOXIC_TOTS_I_CHARGED_ORN,
			ItemID.TOXIC_TOTS_I_UNCHARGED_ORN,
			ItemID.TUMEKENS_SHADOW_UNCHARGED, ItemID.EYE_OF_AYAK_UNCHARGED, ItemID.TOME_OF_FIRE_UNCHARGED,
			ItemID.WILD_CAVE_WEBWEAVER_UNCHARGED, ItemID.ENCHANTED_ONYX_AMULET));
		Set<Integer> materials = new HashSet<>();
		for (ChargeType type : ChargeType.values())
		{
			rechargeable.add(type.getSourceItemId());
			for (ChargeType.Component component : type.getComponents())
			{
				materials.add(component.getItemId());
			}
		}
		for (TomePage page : TomePage.values())
		{
			materials.add(page.getItemId());
		}
		for (BlowpipeDart dart : BlowpipeDart.values())
		{
			materials.add(dart.getItemId());
		}
		RECHARGEABLE = ImmutableSet.copyOf(rechargeable);
		RECHARGE_ITEMS = ImmutableSet.copyOf(materials);
	}

	private final ItemInfo items;

	SupplyAccounting(ItemInfo items)
	{
		this.items = items;
	}

	/**
	 * One tick's change, split into what left and what arrived. The steps below take matched items out of it.
	 */
	static final class Change
	{
		final Map<Integer, Long> removed = new HashMap<>();
		final Map<Integer, Long> gained = new HashMap<>();
		/**
		 * Used up as part of a conversion, priced at the difference (half of a split food).
		 */
		final List<ItemEntry> usedInConversions = new ArrayList<>();

		static Change of(Map<Integer, Long> delta)
		{
			Change change = new Change();
			for (Map.Entry<Integer, Long> e : delta.entrySet())
			{
				if (e.getValue() < 0)
				{
					change.removed.put(e.getKey(), -e.getValue());
				}
				else if (e.getValue() > 0)
				{
					change.gained.put(e.getKey(), e.getValue());
				}
			}
			return change;
		}
	}

	/**
	 * Takes items that left right after a Drop click on them out of the change.
	 *
	 * @return the dropped items and quantities
	 */
	Map<Integer, Long> takeDrops(Change change, RecentClicks clicks, int tick)
	{
		Map<Integer, Long> dropped = new HashMap<>();
		for (RecentClicks.Click click : clicks.all())
		{
			if (click.consumed || !OPTION_DROP.equals(click.option) || tick - click.tick > RecentClicks.MATCH_TICKS)
			{
				continue;
			}
			Long quantity = change.removed.remove(click.itemId);
			if (quantity != null)
			{
				click.consumed = true;
				dropped.merge(click.itemId, quantity, Long::sum);
			}
		}
		return dropped;
	}

	/**
	 * Takes items that were converted rather than used up out of the change:
	 * <ul>
	 * <li>popping eggs, polishing (tarnished items, dull ancient medals) and casting a spell on an item (e.g. High
	 * Level Alchemy)</li>
	 * <li>putting recharge materials into a charged item, whose charges are costed as they're used</li>
	 * <li>worn gear changing form: a charged weapon running dry, Barrows gear degrading, an amulet of fury becoming
	 * a blood fury. Gear that doesn't stack is never a supply; ammo is. Jewellery that breaks into nothing is a
	 * real cost.</li>
	 * <li>eating part of a split food (a summer pie becoming half a summer pie): only the part eaten is used</li>
	 * </ul>
	 * What a conversion produced is taken out of {@code gained}, so it's never loot or something obtained inside.
	 *
	 * @param convertedItems items some boss always converts, wherever it happens
	 */
	void removeConversions(Change change, RecentClicks clicks, int tick, Set<Integer> convertedItems)
	{
		change.removed.keySet().removeAll(convertedItems);
		boolean recharged = false;
		for (RecentClicks.Click click : clicks.all())
		{
			if (tick - click.tick > RecentClicks.MATCH_TICKS || click.itemId <= 0)
			{
				continue;
			}
			if (OPTION_POLISH.equals(click.option) || OPTION_CAST.equals(click.option))
			{
				change.removed.remove(click.itemId);
			}
			else if (RECHARGE_OPTIONS.contains(click.option)
				&& (RECHARGEABLE.contains(click.itemId) || RECHARGE_ITEMS.contains(click.itemId)))
			{
				recharged = true;
			}
		}
		if (recharged)
		{
			change.removed.keySet().removeAll(RECHARGE_ITEMS);
		}

		boolean gearChanged = false;
		for (Iterator<Integer> it = change.removed.keySet().iterator(); it.hasNext(); )
		{
			int itemId = it.next();
			if (isGear(itemId) && !BREAKABLE_JEWELLERY.contains(itemId))
			{
				it.remove();
				gearChanged = true;
			}
		}
		if (gearChanged || recharged)
		{
			// What it became (an uncharged scythe, a degraded helm, a loaded blowpipe)
			change.gained.keySet().removeIf(this::isGear);
		}

		splitFood(change, clicks, tick);
	}

	/**
	 * Worn items that don't stack. Ammo stacks, so it's still a supply.
	 */
	private boolean isGear(int itemId)
	{
		return items.isEquipable(itemId) && !items.isStackable(itemId);
	}

	/**
	 * Eating a split food leaves its remainder (half a pie, then a pie dish): only the price difference is used, and
	 * the remainder is costed when it's eaten in turn. A remainder is a food sharing a word with the one eaten, or a
	 * pie dish or bowl.
	 */
	private void splitFood(Change change, RecentClicks clicks, int tick)
	{
		for (RecentClicks.Click click : clicks.all())
		{
			if (tick - click.tick > RecentClicks.MATCH_TICKS || !OPTION_EAT.equals(click.option)
				|| change.removed.getOrDefault(click.itemId, 0L) <= 0)
			{
				continue;
			}
			Integer remainder = null;
			for (Map.Entry<Integer, Long> e : change.gained.entrySet())
			{
				if (e.getValue() == 1 && (FOOD_CONTAINERS.contains(e.getKey())
					|| (items.isFood(e.getKey()) && sharesWord(items.name(click.itemId), items.name(e.getKey())))))
				{
					remainder = e.getKey();
					break;
				}
			}
			if (remainder == null)
			{
				continue;
			}
			change.gained.remove(remainder);
			change.removed.computeIfPresent(click.itemId, (id, q) -> q > 1 ? q - 1 : null);
			long eaten = Math.max(0, items.price(click.itemId) - items.price(remainder));
			change.usedInConversions.add(new ItemEntry(click.itemId, 1, eaten));
		}
	}

	/**
	 * Whether two item names share a word of 3 letters or more ("Summer pie" and "Half a summer pie").
	 */
	static boolean sharesWord(String a, String b)
	{
		Set<String> words = new HashSet<>();
		for (String word : a.toLowerCase(Locale.ROOT).split("[^a-z]+"))
		{
			if (word.length() >= 3)
			{
				words.add(word);
			}
		}
		for (String word : b.toLowerCase(Locale.ROOT).split("[^a-z]+"))
		{
			if (words.contains(word))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * What was used up: the items that left. Potions are counted in doses: a Prayer potion(4) becoming a Prayer
	 * potion(3) is one dose, priced from the highest-dose variant.
	 */
	List<ItemEntry> consumption(Change change)
	{
		List<ItemEntry> used = net(change.removed, change.gained);
		used.addAll(change.usedInConversions);
		return used;
	}

	/**
	 * Items gained that weren't carried before (not a potion going down a dose), counted the way
	 * {@link #consumption} counts supplies: per dose for potions.
	 */
	List<ItemEntry> acquisitions(Change change)
	{
		return net(change.gained, change.removed);
	}

	/**
	 * Dropped supplies left behind, counted as used (per dose for potions). Dropped equipment and junk never are.
	 */
	List<ItemEntry> droppedAsUsed(Map<Integer, Long> dropped)
	{
		Change change = new Change();
		for (Map.Entry<Integer, Long> e : dropped.entrySet())
		{
			int itemId = e.getKey();
			// Without prices nothing can be told to be junk: it's repriced later (ZeroPrices)
			if (e.getValue() <= 0 || items.isEquipable(itemId)
				|| (items.pricesLoaded() && items.doseInfo(itemId) == null && items.price(itemId) < JUNK_PRICE))
			{
				continue;
			}
			change.removed.put(itemId, e.getValue());
		}
		return consumption(change);
	}

	/**
	 * Lines for {@code moved}, with potions netted per dose against {@code against} (doses that went the other way).
	 */
	private List<ItemEntry> net(Map<Integer, Long> moved, Map<Integer, Long> against)
	{
		List<ItemEntry> lines = new ArrayList<>();
		// family -> [net doses, a variant id seen, its dose count]
		Map<String, long[]> doseFamilies = new HashMap<>();

		for (Map.Entry<Integer, Long> e : moved.entrySet())
		{
			int itemId = e.getKey();
			PriceService.DoseInfo dose = items.doseInfo(itemId);
			if (dose != null)
			{
				long[] family = doseFamilies.computeIfAbsent(dose.getFamily(), f -> new long[]{0, itemId, dose.getDoses()});
				family[0] += e.getValue() * dose.getDoses();
			}
			else
			{
				lines.add(new ItemEntry(itemId, e.getValue(), items.price(itemId)));
			}
		}

		for (Map.Entry<Integer, Long> e : against.entrySet())
		{
			PriceService.DoseInfo dose = items.doseInfo(e.getKey());
			if (dose != null && doseFamilies.containsKey(dose.getFamily()))
			{
				doseFamilies.get(dose.getFamily())[0] -= e.getValue() * dose.getDoses();
			}
		}

		for (Map.Entry<String, long[]> e : doseFamilies.entrySet())
		{
			long[] family = e.getValue();
			if (family[0] > 0)
			{
				PriceService.FullDose full = items.fullDose(e.getKey(), (int) family[1], (int) family[2]);
				ItemEntry entry = new ItemEntry(full.getItemId(), family[0], items.pricePerDose(full));
				entry.setPerDose(true);
				lines.add(entry);
			}
		}
		return lines;
	}
}
