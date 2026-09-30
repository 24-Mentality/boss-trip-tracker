package com.bosstriptracker.boss;

import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The bosses shown and tracked. Only finished bosses are listed.
 */
public final class BossRegistry
{
	private final List<BossDefinition> bosses;

	public BossRegistry(List<BossDefinition> bosses)
	{
		this.bosses = ImmutableList.copyOf(bosses);
	}

	public static BossRegistry standard()
	{
		return new BossRegistry(ImmutableList.of(new MaggotKingBoss(), new NightmareBoss(), new TheatreOfBloodBoss()));
	}

	public List<BossDefinition> all()
	{
		return bosses;
	}

	public BossDefinition first()
	{
		return bosses.get(0);
	}

	/**
	 * @return the boss with this id, or null if it isn't enabled
	 */
	public BossDefinition byId(String id)
	{
		for (BossDefinition boss : bosses)
		{
			if (boss.getId().equals(id))
			{
				return boss;
			}
		}
		return null;
	}

	/**
	 * @return the boss whose trip area contains this template region, or null
	 */
	public BossDefinition forRegion(int templateRegionId)
	{
		for (BossDefinition boss : bosses)
		{
			if (boss.getRegions().contains(templateRegionId))
			{
				return boss;
			}
		}
		return null;
	}

	/**
	 * @return the boss with this NPC id (any of its forms), or null
	 */
	public BossDefinition forBossNpc(int npcId)
	{
		for (BossDefinition boss : bosses)
		{
			if (boss.getBossNpcIds().contains(npcId))
			{
				return boss;
			}
		}
		return null;
	}

	/**
	 * @return the boss that drops this egg, or null
	 */
	public BossDefinition forEgg(int itemId)
	{
		for (BossDefinition boss : bosses)
		{
			if (boss.getEggPetRates().containsKey(itemId))
			{
				return boss;
			}
		}
		return null;
	}

	/**
	 * @return the boss that drops this tarnished item, or null
	 */
	public BossDefinition forTarnished(int itemId)
	{
		for (BossDefinition boss : bosses)
		{
			if (boss.getTarnishedItems().contains(itemId))
			{
				return boss;
			}
		}
		return null;
	}

	/**
	 * Regions any boss treats as its area or its waiting area, for diagnostic logging.
	 */
	public Set<Integer> allRegions()
	{
		Set<Integer> regions = new HashSet<>();
		for (BossDefinition boss : bosses)
		{
			regions.addAll(boss.getRegions());
		}
		return regions;
	}
}
