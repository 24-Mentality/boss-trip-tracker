package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class LeaveDelayTest
{
	private final LeaveDelay delay = new LeaveDelay();

	@Test
	public void leavesAfterThreeTicksOutsideFromTheFirst()
	{
		assertNull(delay.outside(1_000, false));
		assertNull(delay.outside(1_600, false));
		assertEquals(Long.valueOf(1_000), delay.outside(2_200, false));
	}

	@Test
	public void oneTickOutsideBetweenRoomsDoesNotLeave()
	{
		assertNull(delay.outside(1_000, false));
		delay.inside();
		assertNull(delay.outside(5_000, false));
		assertNull(delay.outside(5_600, false));
		assertEquals(Long.valueOf(5_000), delay.outside(6_200, false));
	}

	@Test
	public void deathLeavesAtOnce()
	{
		assertEquals(Long.valueOf(1_000), delay.outside(1_000, true));
	}
}
