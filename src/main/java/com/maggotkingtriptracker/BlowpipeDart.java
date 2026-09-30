package com.maggotkingtriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

/**
 * Which darts are in the Toxic blowpipe, for pricing the darts it uses. The plugin can't see them.
 */
@Getter
@RequiredArgsConstructor
public enum BlowpipeDart
{
	DRAGON("Dragon darts", ItemID.DRAGON_DART),
	AMETHYST("Amethyst darts", ItemID.AMETHYST_DART),
	RUNE("Rune darts", ItemID.RUNE_DART);

	private final String label;
	private final int itemId;

	@Override
	public String toString()
	{
		return label;
	}
}
