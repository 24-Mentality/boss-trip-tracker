package com.maggotkingtriptracker.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.maggotkingtriptracker.boss.BossDefinition;
import com.maggotkingtriptracker.boss.DropKind;
import com.maggotkingtriptracker.boss.ExpectedDrop;
import com.maggotkingtriptracker.boss.KillContext;
import com.maggotkingtriptracker.boss.MaggotKingBoss;
import com.maggotkingtriptracker.boss.NightmareBoss;
import com.maggotkingtriptracker.boss.TheatreOfBloodBoss;
import com.maggotkingtriptracker.model.LuckTier;
import com.maggotkingtriptracker.view.DrynessView;
import com.maggotkingtriptracker.view.LifetimeView;
import com.maggotkingtriptracker.view.PanelState;
import com.maggotkingtriptracker.view.PolishView;
import com.maggotkingtriptracker.view.StatView;
import com.maggotkingtriptracker.view.TripView;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.ui.FontManager;
import org.junit.Test;

public class ShareCardTest
{
	private static final BossDefinition BOSS = new MaggotKingBoss();
	private static final long NOW = 1_790_640_000_000L;

	@Test
	public void cardTakesTheAllTimeLuckAndLastFiveTrips()
	{
		ShareCard card = ShareCard.from(state(8), true, NOW);

		assertEquals("Maggot King", card.getBossName());
		assertEquals("Example Name", card.getPlayerName());
		assertEquals(Integer.valueOf(2_752), card.getKillCount());
		assertEquals(11, card.getUniquesReceived());
		assertEquals(2630 / 205.6, card.getUniquesExpected(), 1e-9);
		assertEquals(LuckTier.of(com.maggotkingtriptracker.model.DropOdds.luckPercentile(11, 2630 / 205.6)), card.getTier());
		assertTrue(card.isAllTime());
		assertEquals(832, card.getDryKills());
		assertEquals(847, card.getLongestDryStreak());
		// 206 kills on average between uniques, so 626 past it
		assertEquals(-626, card.getDueInKills());
		assertEquals(172, card.getTrackedKills());
		assertEquals(Integer.valueOf(2_565), card.getTrackedFromKc());
		// Fang, kisten, then the pet
		assertEquals(3, card.getDrops().size());
		assertEquals(6, card.getDrops().get(0).getCount());
		assertEquals(5, card.getDrops().get(1).getCount());
		assertEquals(ItemID.MAGGOTKINGPET, card.getDrops().get(2).getItemId());
		assertEquals(ShareCard.RECENT_TRIPS, card.getRecentTrips().size());
		// Drop chances from the all-time record: any unique, fang, kisten, pet
		assertEquals(4, card.getChances().size());
		assertEquals(11, card.getChances().get(0).getReceived());
		assertEquals(2630 / 205.6, card.getChances().get(0).getExpected(), 1e-9);
		assertEquals(6, card.getChances().get(1).getReceived());
		assertEquals("All-time · 2,630 kills · KC 2,752", card.getChancesSource());
		assertEquals(9_620_000, card.getCosts());
		assertEquals(-5_350_000, card.getNet());
	}

	@Test
	public void nameCanBeLeftOff()
	{
		assertNull(ShareCard.from(state(2), false, NOW).getPlayerName());
		assertNull(ShareCard.from(PanelState.builder().boss(BOSS).build(), true, NOW));
	}

	@Test
	public void rendersUnder800PxWide() throws Exception
	{
		ShareCardRenderer renderer = new ShareCardRenderer(ZoneId.of("UTC"));
		BufferedImage image = renderer.render(ShareCard.from(state(8), true, NOW), ShareCardTest::placeholderIcon);
		assertEquals(760, image.getWidth());
		// Header, luck, drop chances (any unique, fang, kisten, pet), totals and five trips
		assertTrue(image.getHeight() < 1_100);

		BufferedImage few = renderer.render(ShareCard.from(state(0), false, NOW), id -> null);
		assertTrue(few.getHeight() < image.getHeight());

		// For looking at the layout: build/share-card-preview.png
		File out = new File("build/share-card-preview.png");
		out.getParentFile().mkdirs();
		ImageIO.write(image, "PNG", out);
	}

	@Test
	public void luckTitleRowFitsEveryTier()
	{
		Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
		FontMetrics bold = g.getFontMetrics(FontManager.getRunescapeBoldFont());
		FontMetrics small = g.getFontMetrics(FontManager.getRunescapeSmallFont());
		int left = ShareCardRenderer.PAD + ShareCardRenderer.INNER;
		int right = ShareCardRenderer.WIDTH - ShareCardRenderer.PAD - ShareCardRenderer.INNER;
		// Same positions as drawLuck: label, 6 px, tier ... source note right-aligned
		int sourceLeft = right - small.stringWidth("All-time (Loot Tracker)");
		for (LuckTier tier : LuckTier.values())
		{
			int tierRight = left + bold.stringWidth("Luck Status:") + 6 + bold.stringWidth(tier.getLabel());
			assertTrue(tier.getLabel() + " ends at " + tierRight + ", source note starts at " + sourceLeft,
				tierRight + 6 <= sourceLeft);
		}
		g.dispose();
	}

	@Test
	public void manyUniquesWrapOntoMoreRows() throws Exception
	{
		NightmareBoss nightmare = new NightmareBoss();
		List<DrynessView.Drop> uniques = new ArrayList<>();
		for (ExpectedDrop drop : nightmare.getDrops())
		{
			if (drop.getKind() == DropKind.UNIQUE)
			{
				uniques.add(new DrynessView.Drop(drop.getItemId(), "Unique", drop.chance(KillContext.DEFAULT), 0.2, 0,
					Collections.<Integer>emptyList()));
			}
		}
		PanelState base = state(5);
		DrynessView dryness = base.getLifetime().getDryness().toBuilder()
			.anyUniqueRate(nightmare.anyUniqueChance(KillContext.DEFAULT))
			.allTime(base.getLifetime().getDryness().getAllTime().toBuilder().uniques(uniques).build())
			.build();
		PanelState state = base.toBuilder()
			.boss(nightmare)
			.lifetime(base.getLifetime().toBuilder().dryness(dryness).build())
			.build();

		ShareCardRenderer renderer = new ShareCardRenderer(ZoneId.of("UTC"));
		BufferedImage maggotKing = renderer.render(ShareCard.from(base, true, NOW), ShareCardTest::placeholderIcon);
		BufferedImage image = renderer.render(ShareCard.from(state, true, NOW), ShareCardTest::placeholderIcon);
		// Eight uniques and the pet: two rows of icons instead of one, and six more drop chances rows
		assertEquals(maggotKing.getHeight() + (26 + 6 * ShareCardRenderer.CHANCE_ROW) * ShareCardRenderer.SCALE, image.getHeight());
		ImageIO.write(image, "PNG", new File("build/share-card-nightmare.png"));
	}

	@Test
	public void theatreCardShowsTheTeamDryStreak() throws Exception
	{
		PanelState base = state(5);
		assertNull(ShareCard.from(base, true, NOW).getTeamDryStreak());

		DrynessView dryness = base.getLifetime().getDryness().toBuilder().teamDryStreak(12_345).build();
		PanelState state = base.toBuilder()
			.boss(new TheatreOfBloodBoss())
			.lifetime(base.getLifetime().toBuilder().dryness(dryness).build())
			.build();
		List<TripView> raids = new ArrayList<>();
		for (TripView trip : base.getHistory())
		{
			raids.add(TripView.builder().id(trip.getId()).startedAt(trip.getStartedAt()).kills(1).detail("Normal · team of 4")
				.bossStat(trip.getBossStat()).netProfit(trip.getNetProfit()).loot(trip.getLoot()).supplies(trip.getSupplies())
				.dropped(trip.getDropped()).supplyCategories(trip.getSupplyCategories()).build());
		}
		state = state.toBuilder().history(raids).build();
		ShareCard card = ShareCard.from(state, true, NOW);
		assertEquals(Integer.valueOf(12_345), card.getTeamDryStreak());
		// A raid's line shows its mode and team rather than "1 kill"
		assertEquals("Normal · team of 4", card.getRecentTrips().get(0).getDetail());

		// Beside the longest dry streak, so the card keeps its height
		ShareCardRenderer renderer = new ShareCardRenderer(ZoneId.of("UTC"));
		BufferedImage image = renderer.render(card, ShareCardTest::placeholderIcon);
		assertEquals(renderer.render(ShareCard.from(base, true, NOW), ShareCardTest::placeholderIcon).getHeight(),
			image.getHeight());
		ImageIO.write(image, "PNG", new File("build/share-card-tob.png"));
	}

	private static Image placeholderIcon(int itemId)
	{
		BufferedImage icon = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setColor(new Color(0x80 + (itemId * 37) % 0x7f, 0x60, 0x40));
		g.fillOval(4, 2, 28, 28);
		g.dispose();
		return icon;
	}

	private static PanelState state(int trips)
	{
		List<TripView> history = new ArrayList<>();
		for (int i = 0; i < trips; i++)
		{
			history.add(TripView.builder()
				.id("t" + i)
				.startedAt(NOW - (i + 1) * 5_400_000L)
				.kills(20 + i)
				.bossStat(new StatView("Stom / Eggs", "20 / 0", null))
				.netProfit(i == 0 ? 1_250_000 : -400_000L * i)
				.loot(Collections.emptyList())
				.supplies(Collections.emptyList())
				.dropped(Collections.emptyList())
				.supplyCategories(Collections.emptyList())
				.build());
		}

		List<DrynessView.Drop> tracked = Arrays.asList(
			new DrynessView.Drop(ItemID.ELDER_VENATOR_FANG, "Elder venator fang", 1 / 340.0, 0.5, 0, Collections.<Integer>emptyList()),
			new DrynessView.Drop(ItemID.CRIMSON_KISTEN, "Crimson kisten", 1 / 520.0, 0.33, 0, Collections.<Integer>emptyList()));
		List<DrynessView.Drop> allTime = Arrays.asList(
			new DrynessView.Drop(ItemID.ELDER_VENATOR_FANG, "Elder venator fang", 1 / 340.0, 2630 / 340.0, 6, Collections.<Integer>emptyList()),
			new DrynessView.Drop(ItemID.CRIMSON_KISTEN, "Crimson kisten", 1 / 520.0, 2630 / 520.0, 5, Collections.<Integer>emptyList()));
		DrynessView.Drop pet = new DrynessView.Drop(ItemID.MAGGOTKINGPET, "Maggot marquess", 1 / 3500.0, 0.75, 0,
			Collections.<Integer>emptyList());

		DrynessView dryness = DrynessView.builder()
			.luckKills(171)
			.killsSinceUnique(832)
			.longestDryStreak(847)
			.anyUniqueRate(1 / 205.6)
			.expectedUniques(171 / 205.6)
			.uniques(tracked)
			.pet(pet)
			.eggTiers(Collections.<DrynessView.EggTier>emptyList())
			.currentKc(2_752)
			.firstTrackedKc(2_565)
			.allTime(DrynessView.AllTime.builder()
				.lootKills(2630)
				.killCount(2_752)
				.uniquesReceived(11)
				.expectedUniques(2630 / 205.6)
				.uniques(allTime)
				.pet(pet)
				.build())
			.build();

		LifetimeView lifetime = LifetimeView.builder()
			.trips(trips + 1)
			.kills(172)
			.choiceSummary("")
			.activeMs(26_362_000L)
			.lootValue(4_270_000)
			.supplyCost(9_000_000)
			.droppedCost(20_000)
			.deathCost(600_000)
			.netProfit(-5_350_000)
			.netPerTrip(Collections.<Long>emptyList())
			.dryness(dryness)
			.polish(Collections.<PolishView>emptyList())
			.build();

		return PanelState.builder()
			.boss(BOSS)
			.history(history)
			.lifetime(lifetime)
			.playerName("Example Name")
			.build();
	}
}
