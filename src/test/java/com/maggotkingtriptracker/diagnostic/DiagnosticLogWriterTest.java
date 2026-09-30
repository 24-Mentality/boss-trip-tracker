package com.maggotkingtriptracker.diagnostic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;

public class DiagnosticLogWriterTest
{
	@Test
	public void rotationKeepsThreeOldFilesMovingOldestFirst()
	{
		List<String[]> moves = DiagnosticLogWriter.rotationMoves();

		// .3 is replaced by .2, then .1 moves up, then the log becomes .1: nothing is overwritten before it moves
		assertEquals(3, moves.size());
		assertArrayEquals(new String[]{"diagnostic.log.2", "diagnostic.log.3"}, moves.get(0));
		assertArrayEquals(new String[]{"diagnostic.log.1", "diagnostic.log.2"}, moves.get(1));
		assertArrayEquals(new String[]{"diagnostic.log", "diagnostic.log.1"}, moves.get(2));
	}

	@Test
	public void theatreOfBloodStateIsLogged()
	{
		assertTrue(DiagnosticRecorder.TOB_VARBITS.containsKey(VarbitID.TOB_CLIENT_PARTYSTATUS));
		for (int slot : new int[]{VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2,
			VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4})
		{
			assertTrue(DiagnosticRecorder.TOB_VARBITS.containsKey(slot));
		}
		assertTrue(DiagnosticRecorder.TOB_VARBITS.containsKey(VarbitID.TOB_MIDWAYCHEST_POINTS));
		assertTrue(DiagnosticRecorder.TOB_VARBITS.containsKey(VarbitID.TOB_TREASUREROOM_CHEST_0));
	}
}
