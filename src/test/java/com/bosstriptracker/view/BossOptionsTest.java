package com.bosstriptracker.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.boss.BossRegistry;
import com.bosstriptracker.boss.FracturedArchiveBoss;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class BossOptionsTest
{
	@Test
	public void bossesAlphabeticallyThenRaids()
	{
		List<BossOption> options = BossOptions.of(BossRegistry.standard().all(),
			boss -> boss.getId().equals(FracturedArchiveBoss.ID));
		List<String> names = options.stream().map(BossOption::getName).collect(Collectors.toList());
		assertEquals(List.of("Doom of Mokhaiotl", "Duke Sucellus", "Leviathan", "Maggot King", "Nightmare", "Phantom Muspah",
			"Vardorvis", "Vorkath", "Whisperer", "Fractured Archive", "Theatre of Blood"), names);
		for (BossOption option : options)
		{
			assertEquals(option.getName(), option.getName().equals("Fractured Archive") || option.getName().equals("Theatre of Blood"),
				option.isRaid());
			assertEquals(option.getName(), option.getName().equals("Fractured Archive"), option.isLive());
			assertFalse(option.isHeader());
		}
	}

	@Test
	public void headersAreNotBosses()
	{
		BossOption header = BossOption.header("Raids", true);
		assertTrue(header.isHeader());
		assertTrue(header.isRaid());
		assertEquals("Raids", header.getName());
	}
}
