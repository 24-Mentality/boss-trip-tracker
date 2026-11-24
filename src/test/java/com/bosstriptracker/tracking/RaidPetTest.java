package com.bosstriptracker.tracking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * A raid's pet comes with its reward: in the raid, or at the chest outside.
 */
public class RaidPetTest
{
	private final RaidPet pet = new RaidPet();

	@Test
	public void inACompletedRaidThePetCountsAtOnce()
	{
		assertTrue(pet.inRaid(true));
		assertFalse(pet.takeWaiting());
	}

	@Test
	public void beforeTheCompletionIsRecordedItWaitsForIt()
	{
		assertFalse(pet.inRaid(false));

		assertTrue(pet.takeWaiting());
		// Only once
		assertFalse(pet.takeWaiting());
	}

	@Test
	public void aNewRaidDoesntInheritAWaitingPet()
	{
		pet.inRaid(false);
		pet.reset();

		assertFalse(pet.takeWaiting());
	}

	@Test
	public void outsideItCountsRightAfterTheRewardIsClaimed()
	{
		assertFalse(pet.claimedOutside(500));

		assertTrue(pet.outside(500 + RaidPet.AFTER_CLAIM_TICKS));
		// One pet per claim
		assertFalse(pet.outside(501 + RaidPet.AFTER_CLAIM_TICKS));
	}

	@Test
	public void outsideItCountsJustBeforeTheClaimsLootEvent()
	{
		assertFalse(pet.outside(500));

		assertTrue(pet.claimedOutside(500 + RaidPet.BEFORE_CLAIM_TICKS));
	}

	@Test
	public void aPetFromSomethingElseIsntTheRaids()
	{
		// A skilling pet long before the chest is opened
		assertFalse(pet.outside(100));
		assertFalse(pet.claimedOutside(500));
		// And another long after
		assertFalse(pet.outside(501 + RaidPet.AFTER_CLAIM_TICKS));
	}

	@Test
	public void loggingOutForgetsTheClaim()
	{
		pet.claimedOutside(500);
		pet.reset();

		assertFalse(pet.outside(501));
	}
}
