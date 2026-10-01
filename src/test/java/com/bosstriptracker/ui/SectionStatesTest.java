package com.bosstriptracker.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SectionStatesTest
{
	@Test
	public void missingEntriesUseTheBoxDefault()
	{
		SectionStates states = new SectionStates("", saved -> { });

		assertFalse(states.isOpen("trip.loot", false));
		assertTrue(states.isOpen("lifetime.loot", true));
	}

	@Test
	public void savedEntriesWin()
	{
		SectionStates states = new SectionStates("trip.loot=open,lifetime.loot=closed", saved -> { });

		assertTrue(states.isOpen("trip.loot", false));
		assertFalse(states.isOpen("lifetime.loot", true));
	}

	@Test
	public void togglingSavesEveryEntry()
	{
		List<String> saves = new ArrayList<>();
		SectionStates states = new SectionStates("trip.loot=open", saves::add);

		states.setOpen("history.supplies", true);
		states.setOpen("trip.loot", false);
		// No change, no save
		states.setOpen("trip.loot", false);

		assertEquals(2, saves.size());
		assertEquals("trip.loot=closed,history.supplies=open", saves.get(1));
		assertEquals(SectionStates.parse(saves.get(1)), SectionStates.parse("history.supplies=open,trip.loot=closed"));
	}

	@Test
	public void listenersHearEveryChange()
	{
		int[] calls = {0};
		SectionStates states = new SectionStates(null, saved -> { });
		Runnable listener = () -> calls[0]++;
		states.addListener(listener);

		states.setOpen("history.loot", true);
		states.removeListener(listener);
		states.setOpen("history.loot", false);

		assertEquals(1, calls[0]);
	}

	@Test
	public void badInputIsIgnored()
	{
		Map<String, Boolean> parsed = SectionStates.parse(" ,=open,trip.loot,trip.supplies=maybe, history.loot = open ,,x=closed=");

		assertEquals(1, parsed.size());
		assertEquals(Boolean.TRUE, parsed.get("history.loot"));
		assertTrue(SectionStates.parse(null).isEmpty());
	}
}
