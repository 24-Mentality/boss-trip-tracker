package com.maggotkingtriptracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What the Eye of Ayak is charged with, for pricing its charges. The plugin can't see it.
 */
@Getter
@RequiredArgsConstructor
public enum EyeOfAyakCharge
{
	DEMON_TEARS("Demon tears"),
	RUNES("Death and chaos runes");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
