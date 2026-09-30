package com.maggotkingtriptracker.boss;

import java.util.function.ToDoubleFunction;
import lombok.Value;

/**
 * A drop the Expected / Received card and the luck numbers follow, with the player's chance per kill.
 */
@Value
public class ExpectedDrop
{
	int itemId;
	DropKind kind;
	/**
	 * The player's chance per loot kill. A function because some bosses depend on mode and team size.
	 */
	ToDoubleFunction<KillContext> chance;

	public static ExpectedDrop fixed(int itemId, DropKind kind, double rate)
	{
		return new ExpectedDrop(itemId, kind, context -> rate);
	}

	public double chance(KillContext context)
	{
		return chance.applyAsDouble(context);
	}
}
