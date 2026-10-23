package com.bosstriptracker.boss;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableMap;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Which Collection Log page is whose, from the pages drawn on 2026-10-23.
 */
public class CollectionLogPagesTest
{
	private final BossRegistry registry = BossRegistry.standard();

	@Test
	public void everyTrackedBossHasExactlyOnePage()
	{
		String[] pages = {"Vorkath", "Vardorvis", "Maggot King", "The Leviathan", "Duke Sucellus", "Doom of Mokhaiotl",
			"The Nightmare", "Phantom Muspah", "The Whisperer", "The Fractured Archive", "Theatre of Blood"};
		Set<String> claimed = new HashSet<>();
		for (String page : pages)
		{
			BossDefinition owner = null;
			for (BossDefinition boss : registry.all())
			{
				if (boss.isCollectionLogPage(page))
				{
					assertNull(page + " is claimed twice", owner);
					owner = boss;
				}
			}
			assertTrue(page + " has no boss", owner != null);
			assertTrue(claimed.add(owner.getId()));
		}
		assertEquals(registry.all().size(), claimed.size());
		// Pages of bosses the plugin doesn't track
		for (String page : new String[]{"Nex", "Abyssal Sire", "Chambers of Xeric", "Tombs of Amascut"})
		{
			for (BossDefinition boss : registry.all())
			{
				assertFalse(page, boss.isCollectionLogPage(page));
			}
		}
		assertSame(registry.byId(ForgottenFourBoss.LEVIATHAN_ID), owner("The Leviathan"));
		assertSame(registry.byId(NightmareBoss.ID), owner("The Nightmare"));
		assertSame(registry.byId(FracturedArchiveBoss.ID), owner("The Fractured Archive"));
	}

	@Test
	public void theDefaultKillCountIsThePagesOneKillsLine()
	{
		BossDefinition vorkath = registry.byId(VorkathBoss.ID);
		assertEquals(Integer.valueOf(4_340), vorkath.collectionLogKills(ImmutableMap.of("Vorkath kills", 4_340), null));
		assertEquals(Integer.valueOf(0), registry.byId(FracturedArchiveBoss.ID)
			.collectionLogKills(ImmutableMap.of("Fractured Archive completions", 0), null));
		// Two lines and none: nothing to go on
		assertNull(vorkath.collectionLogKills(ImmutableMap.of("A kills", 1, "B kills", 2), null));
		assertNull(vorkath.collectionLogKills(ImmutableMap.of("Deepest delve", 7), null));
		assertEquals(Integer.valueOf(1_446), registry.byId(DoomOfMokhaiotlBoss.ID)
			.collectionLogKills(ImmutableMap.of("Deepest delve", 7, "Deep delves", 0, "Total delves", 1_446), null));
		assertNull(registry.byId(MaggotKingBoss.ID).collectionLogKills(ImmutableMap.of("Maggot King kills", 1_007), null));
	}

	private BossDefinition owner(String page)
	{
		for (BossDefinition boss : registry.all())
		{
			if (boss.isCollectionLogPage(page))
			{
				return boss;
			}
		}
		return null;
	}
}
