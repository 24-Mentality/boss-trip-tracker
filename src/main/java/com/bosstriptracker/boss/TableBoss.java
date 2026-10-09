package com.bosstriptracker.boss;

import com.bosstriptracker.model.ItemEntry;
import com.bosstriptracker.model.Kill;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A boss defined by a {@link BossData} entry. A boss without mechanics of its own needs nothing more; others extend
 * this and override what they do differently.
 */
public class TableBoss extends BossDefinition
{
	private final BossData data;
	private final List<ExpectedDrop> uniques = new ArrayList<>();
	private final TripStat profitCell;

	public TableBoss(BossData data)
	{
		this.data = data;
		for (ExpectedDrop drop : data.getDrops())
		{
			if (drop.getKind() == DropKind.UNIQUE)
			{
				uniques.add(drop);
			}
		}
		this.profitCell = data.getProfitCell() != null ? data.getProfitCell() : new TripStat("Uniques",
			"Uniques you received this trip.", trip ->
		{
			int count = 0;
			for (Kill kill : trip.getKills())
			{
				for (ItemEntry entry : kill.getLoot())
				{
					if (isUnique(entry.getItemId()))
					{
						count += entry.getQuantity();
					}
				}
			}
			return String.valueOf(count);
		});
	}

	/**
	 * The table entry this boss is made from.
	 */
	public BossData getData()
	{
		return data;
	}

	@Override
	public String getId()
	{
		return data.getId();
	}

	@Override
	public String getDisplayName()
	{
		return data.getDisplayName();
	}

	@Override
	public int getIconItemId()
	{
		return data.getIconItemId();
	}

	@Override
	public List<BossVariant> getVariants()
	{
		return data.getVariants();
	}

	@Override
	public Map<String, String> getKillNames()
	{
		return data.getKillNames();
	}

	@Override
	public TripModel getTripModel()
	{
		return data.getTripModel();
	}

	@Override
	public Set<Integer> getRegions()
	{
		return data.getRegions();
	}

	@Override
	public Set<Integer> getWaitingRegions()
	{
		return data.getWaitingRegions();
	}

	@Override
	public Set<Integer> getBossNpcIds()
	{
		return data.getBossNpcIds();
	}

	@Override
	public int getNameNpcId()
	{
		return data.getNameNpcId();
	}

	@Override
	public List<ExpectedDrop> getDrops()
	{
		return data.getDrops();
	}

	@Override
	public double anyUniqueChance(KillContext context)
	{
		return data.getDropModel().anyUniqueChance(uniques, context);
	}

	@Override
	protected Map<Integer, String> getLootCategoryMap()
	{
		return data.getLootCategories();
	}

	@Override
	public TripStat getProfitCell()
	{
		return profitCell;
	}

	@Override
	public List<AllTimeSource> getAllTimeSources()
	{
		return data.getAllTimeSources();
	}

	@Override
	public String getUnitNoun()
	{
		return data.getUnitNoun();
	}

	@Override
	public String getAreaNoun()
	{
		return data.getAreaNoun();
	}

	@Override
	public String getEmptyStateText()
	{
		return data.getEmptyStateText() != null ? data.getEmptyStateText() : super.getEmptyStateText();
	}
}
