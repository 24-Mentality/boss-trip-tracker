package com.bosstriptracker.tracking;

import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.pricing.ItemInfo;
import com.bosstriptracker.pricing.PriceService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replays the inventory, equipment and menu lines of a diagnostic log through {@link SupplyAccounting}, the way
 * TripTracker feeds it: one combined change of inventory and equipment per tick, with the menu clicks before it.
 * Rune pouch changes aren't in the replay.
 */
class SupplyReplay
{
	private static final Pattern LINE = Pattern.compile("^tick=(\\d+) region=(-?\\d+) \\S+ (\\w+) (.*)$");
	// Items are separated by ", "; names may contain parentheses, e.g. "Avernic treads (max) (31097) x1"
	private static final Pattern ITEM = Pattern.compile("(?:^|\\[|, )([+-]?)([^,\\[]*?) \\((\\d+)\\) x(\\d+)");
	private static final Pattern MENU = Pattern.compile("option=\"([^\"]*)\".* itemId=(-?\\d+)");
	private static final Pattern STACKABLE_NAME = Pattern.compile("(?i)( rune|arrow|bolts|dart|^coins|scales|chinchompa|knife|stake)$");
	private static final Pattern DOSE_NAME = Pattern.compile("^(.+)\\((\\d)\\)$");

	/**
	 * Items as the log names them, priced from {@link #prices} (1,000 gp otherwise).
	 */
	final Items items = new Items();
	final Map<Integer, Long> prices = new HashMap<>();

	/**
	 * Supplies used in the lair, merged as on a trip.
	 */
	final List<ItemEntry> used = new ArrayList<>();
	final Map<Integer, Long> dropped = new HashMap<>();
	final List<ItemEntry> acquired = new ArrayList<>();

	private final List<String> lines = new ArrayList<>();
	private final Set<Integer> regions;
	private final Set<Integer> convertedItems = new HashSet<>();
	private final Set<Integer> recoverableItems = new HashSet<>();
	private final SupplyAccounting accounting = new SupplyAccounting(items);
	private final RecentClicks clicks = new RecentClicks();
	private final Map<Integer, Long> inventory = new HashMap<>();
	private final Map<Integer, Long> worn = new HashMap<>();
	private Map<Integer, Long> before;
	private boolean deathEndsTrip = true;
	private boolean inArea;
	private boolean dead;
	private int ignoreUntilTick = -1;
	private Consumer<SupplyAccounting.Change> beforeConsumption;

	/**
	 * @param regions the boss area's template regions; changes elsewhere aren't accounted
	 */
	SupplyReplay(String resource, Integer... regions) throws Exception
	{
		this.regions = new HashSet<>(java.util.Arrays.asList(regions));
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			getClass().getResourceAsStream(resource), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				lines.add(line);
				learnItems(line);
			}
		}
	}

	SupplyReplay price(int itemId, long price)
	{
		prices.put(itemId, price);
		return this;
	}

	/**
	 * Items any boss converts rather than uses up (eggs, tarnished items).
	 */
	SupplyReplay converted(Integer... itemIds)
	{
		convertedItems.addAll(java.util.Arrays.asList(itemIds));
		return this;
	}

	/**
	 * Items thrown and picked back up (blisterwood stakes): like drops, never a supply here.
	 */
	SupplyReplay recoverable(Integer... itemIds)
	{
		recoverableItems.addAll(java.util.Arrays.asList(itemIds));
		return this;
	}

	/**
	 * Dying keeps your items and carries on (a Theatre of Blood room).
	 */
	SupplyReplay deathKeepsItems()
	{
		deathEndsTrip = false;
		return this;
	}

	SupplyReplay run()
	{
		return run(change -> { });
	}

	/**
	 * @param beforeConsumption runs on each tick's change after drops and conversions are taken out
	 */
	SupplyReplay run(Consumer<SupplyAccounting.Change> beforeConsumption)
	{
		this.beforeConsumption = beforeConsumption;
		int tick = -1;
		for (String line : lines)
		{
			Matcher m = LINE.matcher(line);
			if (!m.matches())
			{
				continue;
			}
			int lineTick = Integer.parseInt(m.group(1));
			if (lineTick != tick)
			{
				account(tick);
				clicks.prune(lineTick);
				tick = lineTick;
			}
			String category = m.group(3);
			String details = m.group(4);
			boolean inArea = regions.contains(Integer.parseInt(m.group(2)));
			switch (category)
			{
				case "INVENTORY":
				case "EQUIPMENT":
					apply(category.equals("INVENTORY") ? inventory : worn, details);
					if (dead && !inArea)
					{
						// As TripTracker: after a death, changes until shortly after respawning are the death itself
						dead = false;
						ignoreUntilTick = lineTick + 5;
					}
					this.inArea = inArea;
					break;
				case "MENU":
					Matcher menu = MENU.matcher(details);
					if (menu.find())
					{
						clicks.add(lineTick, menu.group(1), Integer.parseInt(menu.group(2)));
					}
					break;
				case "DEATH":
					if (deathEndsTrip && details.startsWith("local player died"))
					{
						dead = true;
					}
					break;
				default:
					break;
			}
		}
		account(tick);
		return this;
	}

	private void account(int tick)
	{
		Map<Integer, Long> now = new HashMap<>(inventory);
		worn.forEach((id, q) -> now.merge(id, q, Long::sum));
		Map<Integer, Long> previous = before;
		before = now;
		if (previous == null)
		{
			return;
		}

		Map<Integer, Long> delta = new HashMap<>();
		now.forEach((id, q) -> delta.put(id, q - previous.getOrDefault(id, 0L)));
		previous.forEach((id, q) -> delta.putIfAbsent(id, -q));
		delta.values().removeIf(q -> q == 0);
		if (delta.isEmpty() || !inArea || dead || tick <= ignoreUntilTick)
		{
			return;
		}

		SupplyAccounting.Change change = SupplyAccounting.Change.of(delta);
		accounting.takeDrops(change, clicks, tick).forEach((id, q) -> dropped.merge(id, q, Long::sum));
		for (int itemId : recoverableItems)
		{
			change.removed.remove(itemId);
		}
		accounting.removeConversions(change, clicks, tick, convertedItems);
		beforeConsumption.accept(change);
		for (ItemEntry entry : accounting.consumption(change))
		{
			ItemEntries.merge(used, entry);
		}
		for (ItemEntry entry : accounting.acquisitions(change))
		{
			ItemEntries.merge(acquired, entry);
		}
	}

	/**
	 * Applies a container line. Counts may go below zero until the log's first snapshot, since the replay starts
	 * without knowing what was carried; a snapshot sets the contents without counting as a change.
	 */
	private void apply(Map<Integer, Long> container, String details)
	{
		Map<Integer, Long> parsed = new HashMap<>();
		Matcher item = ITEM.matcher(details);
		while (item.find())
		{
			long quantity = Long.parseLong(item.group(4));
			parsed.merge(Integer.parseInt(item.group(3)), "-".equals(item.group(1)) ? -quantity : quantity, Long::sum);
		}

		if (details.startsWith("snapshot"))
		{
			Map<Integer, Long> shift = new HashMap<>(parsed);
			container.forEach((id, q) -> shift.merge(id, -q, Long::sum));
			container.clear();
			container.putAll(parsed);
			if (before != null)
			{
				shift.forEach((id, q) -> before.merge(id, q, Long::sum));
			}
			return;
		}
		parsed.forEach((id, q) -> container.merge(id, q, Long::sum));
		container.values().removeIf(q -> q == 0);
	}

	private void learnItems(String line)
	{
		Matcher m = LINE.matcher(line);
		if (!m.matches() || !(m.group(3).equals("INVENTORY") || m.group(3).equals("EQUIPMENT")
			|| m.group(3).equals("GROUND") || m.group(3).equals("LOOT")))
		{
			return;
		}
		Matcher item = ITEM.matcher(m.group(4));
		while (item.find())
		{
			int id = Integer.parseInt(item.group(3));
			items.names.put(id, item.group(2).replaceFirst("^(snapshot|spawned|despawned) ", ""));
			if (STACKABLE_NAME.matcher(items.names.get(id)).find())
			{
				items.stackable.add(id);
			}
			if (m.group(3).equals("EQUIPMENT"))
			{
				items.equipable.add(id);
			}
		}
	}

	/**
	 * Supply line for an item (per dose or not), or null.
	 */
	ItemEntry line(int itemId)
	{
		for (ItemEntry entry : used)
		{
			if (entry.getItemId() == itemId)
			{
				return entry;
			}
		}
		return null;
	}

	long quantity(int itemId)
	{
		ItemEntry entry = line(itemId);
		return entry == null ? 0 : entry.getQuantity();
	}

	/**
	 * Item facts learnt from the log: names from its lines, stackable by name (runes, ammo, coins), equipable if
	 * ever worn. Unknown items cost 1,000 gp.
	 */
	class Items implements ItemInfo
	{
		final Map<Integer, String> names = new HashMap<>();
		final Set<Integer> stackable = new HashSet<>();
		final Set<Integer> equipable = new HashSet<>();

		@Override
		public long price(int itemId)
		{
			return prices.getOrDefault(itemId, 1_000L);
		}

		@Override
		public String name(int itemId)
		{
			return names.getOrDefault(itemId, "Item " + itemId);
		}

		@Override
		public PriceService.DoseInfo doseInfo(int itemId)
		{
			Matcher m = DOSE_NAME.matcher(name(itemId));
			return m.matches() ? new PriceService.DoseInfo(m.group(1), Integer.parseInt(m.group(2))) : null;
		}

		@Override
		public PriceService.FullDose fullDose(String family, int seenItemId, int seenDoses)
		{
			PriceService.FullDose best = new PriceService.FullDose(seenItemId, seenDoses);
			for (Map.Entry<Integer, String> e : names.entrySet())
			{
				PriceService.DoseInfo dose = doseInfo(e.getKey());
				if (dose != null && dose.getFamily().equals(family) && dose.getDoses() > best.getDoses())
				{
					best = new PriceService.FullDose(e.getKey(), dose.getDoses());
				}
			}
			return best;
		}

		@Override
		public boolean isEquipable(int itemId)
		{
			return equipable.contains(itemId);
		}

		@Override
		public boolean isStackable(int itemId)
		{
			return stackable.contains(itemId);
		}
	}
}
