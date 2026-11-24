package com.bosstriptracker.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A region that may be shared (Phosani's dream and, perhaps, the regular Nightmare's arena) only starts a trip once
 * something has shown whose it is. The pool click to being inside took 9 and 11 ticks in the log of 2026-09-28.
 */
public class EntryEvidenceTest
{
	private static final String NIGHTMARE = "nightmare";

	private final EntryEvidence evidence = new EntryEvidence();

	@Test
	public void nothingSeenIsNoEvidence()
	{
		assertFalse(evidence.has(NIGHTMARE, 100));
	}

	@Test
	public void thePoolsMessageHoldsWhileTheDreamLoads()
	{
		evidence.seen(NIGHTMARE, 211);

		assertTrue(evidence.has(NIGHTMARE, 211));
		assertTrue(evidence.has(NIGHTMARE, 222));
		assertTrue(evidence.has(NIGHTMARE, 211 + EntryEvidence.TICKS));
	}

	@Test
	public void itDoesntCarryOverToALaterVisit()
	{
		evidence.seen(NIGHTMARE, 211);

		assertFalse(evidence.has(NIGHTMARE, 212 + EntryEvidence.TICKS));
	}

	@Test
	public void leavingClearsIt()
	{
		evidence.seen(NIGHTMARE, 211);
		evidence.clear();

		assertFalse(evidence.has(NIGHTMARE, 212));
	}

	@Test
	public void itIsOnlyForTheBossItWasSeenFor()
	{
		evidence.seen(NIGHTMARE, 211);

		assertFalse(evidence.has("maggot_king", 212));
	}
}
