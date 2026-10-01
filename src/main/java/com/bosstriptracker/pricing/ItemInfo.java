package com.bosstriptracker.pricing;

/**
 * What supply accounting needs to know about an item. {@link PriceService} answers from ItemManager; tests use a
 * fixed table.
 */
public interface ItemInfo
{
	long price(int itemId);

	String name(int itemId);

	/**
	 * @return the potion family and dose count for a dosed item such as "Prayer potion(3)", or null
	 */
	PriceService.DoseInfo doseInfo(int itemId);

	/**
	 * The highest-dose variant of a potion family, for the icon and the per-dose price.
	 */
	PriceService.FullDose fullDose(String family, int seenItemId, int seenDoses);

	default long pricePerDose(PriceService.FullDose fullDose)
	{
		return Math.round((double) price(fullDose.getItemId()) / fullDose.getDoses());
	}

	boolean isEquipable(int itemId);

	boolean isStackable(int itemId);
}
