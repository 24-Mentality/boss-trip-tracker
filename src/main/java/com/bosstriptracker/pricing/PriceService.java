package com.bosstriptracker.pricing;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.http.api.item.ItemPrice;

/**
 * GE prices and potion dose handling. Item names always come from ItemManager at runtime.
 * Must be used on the client thread.
 */
public class PriceService
{
	private static final Pattern DOSE_NAME = Pattern.compile("^(.+)\\((\\d)\\)$");

	private final ItemManager itemManager;
	private final Map<Integer, DoseInfo> doseInfoCache = new HashMap<>();
	private final Map<String, FullDose> fullDoseCache = new HashMap<>();
	private final Map<Integer, Boolean> meleeWeaponCache = new HashMap<>();
	private final Map<Integer, Boolean> foodCache = new HashMap<>();
	private final Map<Integer, Boolean> equipableCache = new HashMap<>();

	public PriceService(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	public long price(int itemId)
	{
		return itemManager.getItemPrice(itemId);
	}

	public String name(int itemId)
	{
		return itemManager.getItemComposition(itemId).getName();
	}

	/**
	 * Food is anything with an Eat option.
	 */
	public boolean isFood(int itemId)
	{
		return foodCache.computeIfAbsent(itemId, id ->
		{
			for (String action : itemManager.getItemComposition(id).getInventoryActions())
			{
				if ("Eat".equalsIgnoreCase(action))
				{
					return true;
				}
			}
			return false;
		});
	}

	/**
	 * A weapon counts as melee when its best melee attack bonus is at least its ranged and magic bonus.
	 * No weapon (unarmed) is melee.
	 */
	public boolean isMeleeWeapon(int itemId)
	{
		if (itemId <= 0)
		{
			return true;
		}
		return meleeWeaponCache.computeIfAbsent(itemId, id ->
		{
			ItemStats stats = itemManager.getItemStats(id);
			ItemEquipmentStats equipment = stats != null ? stats.getEquipment() : null;
			if (equipment == null)
			{
				return false;
			}
			int melee = Math.max(equipment.getAstab(), Math.max(equipment.getAslash(), equipment.getAcrush()));
			return melee > 0 && melee >= equipment.getArange() && melee >= equipment.getAmagic();
		});
	}

	/**
	 * Gear that can be worn, such as a salve amulet dropped mid-raid (never a supply cost when dropped).
	 */
	public boolean isEquipable(int itemId)
	{
		return equipableCache.computeIfAbsent(itemId, id ->
		{
			ItemStats stats = itemManager.getItemStats(id);
			return stats != null && stats.isEquipable();
		});
	}

	/**
	 * @return the potion family and dose count for a dosed item such as "Prayer potion(3)", or null
	 */
	public DoseInfo doseInfo(int itemId)
	{
		return doseInfoCache.computeIfAbsent(itemId, id ->
		{
			Matcher m = DOSE_NAME.matcher(name(id));
			if (!m.matches())
			{
				return DoseInfo.NONE;
			}
			int doses = Integer.parseInt(m.group(2));
			return doses > 0 ? new DoseInfo(m.group(1), doses) : DoseInfo.NONE;
		}).orNull();
	}

	/**
	 * The highest-dose tradeable variant of a family, used for the icon and the per-dose price.
	 * Falls back to the variant that was seen if the family can't be found in the price list.
	 */
	public FullDose fullDose(String family, int seenItemId, int seenDoses)
	{
		FullDose cached = fullDoseCache.get(family);
		if (cached != null)
		{
			return cached;
		}

		FullDose best = null;
		for (ItemPrice candidate : itemManager.search(family))
		{
			Matcher m = DOSE_NAME.matcher(candidate.getName());
			if (!m.matches() || !m.group(1).equalsIgnoreCase(family))
			{
				continue;
			}
			int doses = Integer.parseInt(m.group(2));
			if (doses > 0 && (best == null || doses > best.getDoses()))
			{
				best = new FullDose(candidate.getId(), doses);
			}
		}

		if (best == null)
		{
			best = new FullDose(seenItemId, seenDoses);
		}
		fullDoseCache.put(family, best);
		return best;
	}

	public long pricePerDose(FullDose fullDose)
	{
		return Math.round((double) price(fullDose.getItemId()) / fullDose.getDoses());
	}

	@Value
	public static class DoseInfo
	{
		static final DoseInfo NONE = new DoseInfo(null, 0);

		String family;
		int doses;

		DoseInfo orNull()
		{
			return family == null ? null : this;
		}
	}

	@Value
	public static class FullDose
	{
		int itemId;
		int doses;
	}
}
