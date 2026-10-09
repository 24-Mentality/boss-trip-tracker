package com.bosstriptracker.boss;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * How a boss gives out uniques: your chance of any unique on one kill, given the boss's uniques and the kill's
 * context. Each unique's own chance is in its {@link ExpectedDrop}.
 */
@FunctionalInterface
public interface DropModel
{
	/**
	 * @param uniques the boss's uniques
	 */
	double anyUniqueChance(List<ExpectedDrop> uniques, KillContext context);

	/**
	 * Every unique is its own roll on one table, so the chance of any is their sum: most solo bosses.
	 */
	DropModel SUM_OF_UNIQUES = (uniques, context) ->
	{
		double any = 0;
		for (ExpectedDrop unique : uniques)
		{
			any += unique.chance(context);
		}
		return any;
	};

	/**
	 * The published chance of any unique (e.g. the OSRS Wiki's "any unique 1/205.6"), whatever the context.
	 */
	static DropModel anyUniqueRate(double rate)
	{
		return (uniques, context) -> rate;
	}

	/**
	 * A team rolls once and the unique goes to one player: your chance is the team's (by mode) divided by the team
	 * size at the start of the kill, assuming everyone contributes equally.
	 *
	 * @param teamChance  the team's chance of any unique for a variant (null for the default)
	 * @param teamSize    the kill's team size, or a typical one when it isn't known
	 */
	static DropModel teamShare(ToDoubleFunction<String> teamChance, ToDoubleFunction<KillContext> teamSize)
	{
		return (uniques, context) -> teamChance.applyAsDouble(context.getVariant()) / teamSize.applyAsDouble(context);
	}
}
