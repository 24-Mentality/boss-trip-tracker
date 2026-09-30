package com.bosstriptracker.model;

import net.runelite.api.gameval.ItemID;

/**
 * One-off fixes to supplies saved with a wrong price, applied when loading history from an older schema.
 */
public final class SupplyCorrections
{
	/**
	 * Schema 2 priced Scythe of Vitur charges at a vial of blood and 300 blood runes per 100 charges; it's 200
	 * (OSRS Wiki). Every scythe line saved before schema 3 used the wrong recipe.
	 */
	public static final int SCYTHE_FIXED_IN_SCHEMA = 3;
	private static final int EXTRA_BLOOD_RUNES = 100;
	private static final int SCYTHE_CHARGES_PER_RECHARGE = 100;

	private SupplyCorrections()
	{
	}

	/**
	 * Takes the extra 100 blood runes out of each scythe charge line's recharge price.
	 *
	 * @param bloodRunePrice the blood rune price to remove, per rune (today's, as the price at the time isn't saved)
	 * @return the number of lines corrected
	 */
	public static int repriceScythe(AccountHistory history, long bloodRunePrice)
	{
		int fixed = 0;
		for (BossHistory boss : history.getBosses().values())
		{
			for (Trip trip : boss.getTrips())
			{
				for (ItemEntry entry : trip.getSupplies())
				{
					if (entry.getItemId() == ItemID.SCYTHE_OF_VITUR && entry.getChargeItemId() == ItemID.VIAL_BLOOD
						&& entry.getChargesPerItem() == SCYTHE_CHARGES_PER_RECHARGE)
					{
						entry.setPriceEach(Math.max(0, entry.getPriceEach() - EXTRA_BLOOD_RUNES * bloodRunePrice));
						fixed++;
					}
				}
			}
		}
		return fixed;
	}
}
