package com.maggotkingtriptracker.ui;

import static org.junit.Assert.assertTrue;
import com.maggotkingtriptracker.boss.DropKind;
import com.maggotkingtriptracker.boss.ExpectedDrop;
import com.maggotkingtriptracker.boss.KillContext;
import com.maggotkingtriptracker.boss.MaggotKingBoss;
import com.maggotkingtriptracker.boss.NightmareBoss;
import java.util.ArrayList;
import java.util.List;
import com.maggotkingtriptracker.view.DrynessView;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

/**
 * Renders the trial luck overview card with real numbers to build/luck-overview-preview.png, at twice the size so
 * it can be compared with the share card. Item icons are placeholders (no item cache in tests).
 */
public class LuckOverviewPreviewTest
{
	private static final int WIDTH = 242 - 17 - 10;

	@Test
	public void preview() throws Exception
	{
		render(dryness(), new MaggotKingBoss(), "build/luck-overview-preview.png");
	}

	@Test
	public void nightmarePreview() throws Exception
	{
		NightmareBoss nightmare = new NightmareBoss();
		List<DrynessView.Drop> uniques = new ArrayList<>();
		for (ExpectedDrop drop : nightmare.getDrops())
		{
			if (drop.getKind() == DropKind.UNIQUE)
			{
				uniques.add(new DrynessView.Drop(drop.getItemId(), "Unique", drop.chance(KillContext.DEFAULT), 0.2,
					uniques.size() == 4 ? 1 : 0, Collections.<Integer>emptyList()));
			}
		}
		DrynessView base = dryness();
		DrynessView dryness = base.toBuilder()
			.anyUniqueRate(nightmare.anyUniqueChance(KillContext.DEFAULT))
			.killsSinceUnique(37)
			.longestDryStreak(212)
			.allTime(base.getAllTime().toBuilder().lootKills(129).uniquesReceived(1).expectedUniques(1.16).uniques(uniques).build())
			.build();
		render(dryness, nightmare, "build/luck-overview-nightmare.png");
	}

	private static void render(DrynessView dryness, com.maggotkingtriptracker.boss.BossDefinition boss, String file)
		throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			LuckOverviewCard card = new LuckOverviewCard(null, () ->
			{
			}, () ->
			{
			});
			card.update(dryness, boss);
			placeholderIcons(card);
			card.setSize(WIDTH, card.getPreferredSize().height);
			layout(card);
			layout(card);

			BufferedImage image = new BufferedImage(WIDTH * 2, card.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			g.scale(2, 2);
			card.printAll(g);
			g.dispose();
			try
			{
				File out = new File(file);
				out.getParentFile().mkdirs();
				ImageIO.write(image, "PNG", out);
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
			assertTrue(card.getHeight() > 0);
		});
	}

	private static DrynessView dryness()
	{
		DrynessView.Drop pet = new DrynessView.Drop(ItemID.MAGGOTKINGPET, "Maggot marquess", 1 / 3500.0, 0.75, 0,
			Collections.<Integer>emptyList());
		return DrynessView.builder()
			.luckKills(171)
			.killsSinceUnique(4)
			.longestDryStreak(847)
			.anyUniqueRate(1 / 205.6)
			.uniques(Collections.<DrynessView.Drop>emptyList())
			.pet(pet)
			.eggTiers(Collections.<DrynessView.EggTier>emptyList())
			.allTime(DrynessView.AllTime.builder()
				.lootKills(2750)
				.killCount(2_752)
				.uniquesReceived(11)
				.expectedUniques(2750 / 205.6)
				.uniques(Arrays.asList(
					new DrynessView.Drop(ItemID.ELDER_VENATOR_FANG, "Elder venator fang", 1 / 340.0, 8.09, 6,
						Collections.<Integer>emptyList()),
					new DrynessView.Drop(ItemID.CRIMSON_KISTEN, "Crimson kisten", 1 / 520.0, 5.29, 5,
						Collections.<Integer>emptyList())))
				.pet(pet)
				.build())
			.build();
	}

	private static void placeholderIcons(Container container)
	{
		for (Component child : container.getComponents())
		{
			// Only the item icons: no text, no icon yet (the eye already has one)
			JLabel label = child instanceof JLabel ? (JLabel) child : null;
			if (label != null && label.getIcon() == null && (label.getText() == null || label.getText().isEmpty())
				&& label.getToolTipText() != null)
			{
				BufferedImage icon = new BufferedImage(27, 24, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = icon.createGraphics();
				g.setColor(new Color(150, 100, 70));
				g.fillOval(3, 1, 21, 21);
				g.dispose();
				label.setIcon(new ImageIcon(icon));
			}
			if (child instanceof Container)
			{
				placeholderIcons((Container) child);
			}
		}
	}

	private static void layout(Component component)
	{
		component.invalidate();
		component.doLayout();
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				layout(child);
			}
		}
	}
}
