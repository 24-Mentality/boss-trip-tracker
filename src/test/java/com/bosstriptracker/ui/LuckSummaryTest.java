package com.bosstriptracker.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.bosstriptracker.model.LuckTier;
import com.bosstriptracker.view.DrynessView;
import com.bosstriptracker.view.Words;
import java.util.Collections;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class LuckSummaryTest
{
	/**
	 * The Fractured Archive at release: no published rates, so no tier and nothing expected, but uniques count.
	 */
	@Test
	public void unpublishedRatesGiveNoTier()
	{
		DrynessView dryness = DrynessView.builder()
			.words(new Words("raid", "Archive"))
			.ratesPending(true)
			.luckKills(3)
			.uniquesReceived(1)
			.expectedUniques(0)
			.anyUniqueRate(0)
			.uniques(Collections.singletonList(new DrynessView.Drop(ItemID.RONDACHE, "Rondache", 0, 0, 1,
				Collections.singletonList(1))))
			.eggTiers(Collections.emptyList())
			.build();

		LuckSummary luck = LuckSummary.of(dryness);
		assertEquals(LuckTier.NO_RATES, luck.getTier());
		assertTrue(luck.isRatesPending());
		assertEquals(1, luck.getReceived());
		assertEquals(0, luck.getExpected(), 0);
		assertEquals(0, luck.getRate(), 0);
		assertTrue(luck.tierHelp().contains("drop rates"));

		// Without raids there is no tier at all, as for other bosses
		assertNull(LuckSummary.of(dryness.toBuilder().luckKills(0).uniquesReceived(0)
			.uniques(Collections.emptyList()).build()).getTier());
	}

	@Test
	public void publishedRatesStillUseTheTails()
	{
		DrynessView dryness = DrynessView.builder()
			.luckKills(100)
			.uniquesReceived(0)
			.expectedUniques(100 / 205.6)
			.anyUniqueRate(1 / 205.6)
			.uniques(Collections.emptyList())
			.eggTiers(Collections.emptyList())
			.build();
		assertEquals(LuckTier.TOO_EARLY, LuckSummary.of(dryness).getTier());
	}
}
