package com.bosstriptracker.tracking;

import static org.junit.Assert.assertEquals;
import com.google.common.collect.ImmutableSet;
import com.bosstriptracker.model.ChargeType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.HitsplatID;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpotanimID;
import org.junit.Test;

public class ChargeCounterTest
{
	private static final Set<Integer> WEAPONS = ImmutableSet.of(
		ItemID.NIGHTMARE_STAFF_HARMONISED, ItemID.WILD_CAVE_WEBWEAVER_CHARGED, ItemID.ELDER_MAUL,
		ItemID.SCYTHE_OF_VITUR, ItemID.TUMEKENS_SHADOW, ItemID.CRIMSON_KISTEN, ItemID.HALLOWFELL, ItemID.BLISTERWOOD_STAKE,
		ItemID.EYE_OF_AYAK, ItemID.TOXIC_BLOWPIPE_LOADED, ItemID.TWISTED_BOW, ItemID.SULPHUR_BLADES, ItemID.DRAGON_CLAWS,
		ItemID.CRYSTAL_HALBERD, ItemID.VERZIK_SPECIAL_WEAPON);
	private static final Set<Integer> MELEE = ImmutableSet.of(ItemID.ELDER_MAUL, ItemID.SCYTHE_OF_VITUR, ItemID.HALLOWFELL,
		ItemID.CRIMSON_KISTEN, ItemID.SULPHUR_BLADES, ItemID.DRAGON_CLAWS, ItemID.CRYSTAL_HALBERD);
	private static final Set<Integer> AMULETS = ImmutableSet.of(ItemID.BLOOD_AMULET, 34428);

	private static final Pattern TICK = Pattern.compile("^tick=(\\d+) \\S+ \\S+ (\\w+) (.*)$");
	// Items are separated by ", "; names may contain parentheses, e.g. "Avernic treads (max) (31097) x1"
	private static final Pattern ITEM = Pattern.compile("(?:^|\\[|, )([+-]?)[^,\\[]*?\\((\\d+)\\) x\\d+");
	private static final Pattern SPOTANIMS = Pattern.compile("spotanims=\\[([\\d, ]*)\\]");
	private static final Pattern HITSPLAT = Pattern.compile("type=(\\d+) amount=(\\d+) target=npc");
	private static final Pattern MESSAGE = Pattern.compile("message=\"(.*)\"$");
	private static final Pattern ANIM = Pattern.compile("^id=(\\d+)");

	/**
	 * Diagnostic log of one kill (2026-09-27) with in-game Check messages before and after:
	 * blood fury 2,113 -> 2,096, Webweaver bow 591 -> 587, Tome of fire 6,590 -> 6,553.
	 */
	@Test
	public void replayedKillMatchesCheckMessages() throws Exception
	{
		Map<ChargeType, Integer> used = replay("charge-test-kill.log");

		assertEquals(Integer.valueOf(17), used.get(ChargeType.BLOOD_FURY));
		assertEquals(Integer.valueOf(4), used.get(ChargeType.WILDERNESS_WEAPON));
		assertEquals(Integer.valueOf(37), used.get(ChargeType.TOME_OF_FIRE));
	}

	/**
	 * Phosani's Nightmare between two in-game Check readings (2026-09-28): Scythe of Vitur 1,638 -> 1,602 and
	 * Tumeken's shadow 1,988 -> 1,942. Of 38 scythe attack animations, 2 missed with every hit, which uses no charge.
	 */
	@Test
	public void replayedPhosanisKillMatchesCheckMessages() throws Exception
	{
		Map<ChargeType, Integer> used = replay("charge-test-phosani-check.log");

		assertEquals(Integer.valueOf(36), used.get(ChargeType.SCYTHE_OF_VITUR));
		assertEquals(Integer.valueOf(46), used.get(ChargeType.TUMEKENS_SHADOW));
	}

	/**
	 * Phosani's Nightmare between two rounds of in-game Checks (2026-09-29), with a greater ghost thrall out:
	 * <ul>
	 * <li>Eye of Ayak 4,152 -> 4,047, and "4,100 charges remaining" at tick 2671, in the same tick as the cast that
	 * used the charge</li>
	 * <li>Toxic blowpipe 503 -> 498 dragon darts and 1,308 -> 1,303 scales, with no Ava's device: 5 shots. The
	 * thrall's hits on the sleepwalkers look like two more</li>
	 * <li>Scythe of Vitur 1,289 -> 1,215 (74); 75 attacks, 2 missing with every hit</li>
	 * <li>Blood fury 9,540 -> 9,525 (15)</li>
	 * </ul>
	 */
	@Test
	public void replayedPhosanisChecksWithAThrall() throws Exception
	{
		Map<ChargeType, Integer> used = replay("charge-test-check2.log");

		assertEquals(Integer.valueOf(105), used.get(ChargeType.EYE_OF_AYAK));
		assertEquals(Integer.valueOf(52), replay("charge-test-check2.log", 2671).get(ChargeType.EYE_OF_AYAK));
		assertEquals(Integer.valueOf(5), used.get(ChargeType.TOXIC_BLOWPIPE_SCALES));
		assertEquals(Integer.valueOf(5), used.get(ChargeType.TOXIC_BLOWPIPE_DARTS_0));
		// One short of the Check
		assertEquals(Integer.valueOf(73), used.get(ChargeType.SCYTHE_OF_VITUR));
		assertEquals(Integer.valueOf(14), used.get(ChargeType.BLOOD_FURY));
	}

	/**
	 * A Normal Theatre of Blood raid (2026-09-29) between the Checks after the Phosani's kill above and fresh Checks
	 * after the raid, with many switches between the scythe, twisted bow, sulphur blades, claws, halberd and the eye:
	 * <ul>
	 * <li>Scythe of Vitur 1,215 -> 1,066 (149), with "Your scythe has 1,200 / 1,100 charges remaining." at ticks 612
	 * and 2226, each in the tick of the attack that used the charge (its count is settled a tick later)</li>
	 * <li>Eye of Ayak 4,047 -> 4,041 (6)</li>
	 * <li>Toxic blowpipe 1,303 -> 1,302 scales and no darts: one shot with Ava's assembler worn</li>
	 * <li>Blood fury 9,525 -> "9,500 more hits" at tick 617 (25)</li>
	 * </ul>
	 */
	@Test
	public void replayedTheatreOfBloodMatchesCheckMessages() throws Exception
	{
		Map<ChargeType, Integer> used = replay("charge-test-tob.log");

		assertEquals(Integer.valueOf(15), replay("charge-test-tob.log", 613).get(ChargeType.SCYTHE_OF_VITUR));
		assertEquals(Integer.valueOf(115), replay("charge-test-tob.log", 2227).get(ChargeType.SCYTHE_OF_VITUR));
		assertEquals(Integer.valueOf(149), used.get(ChargeType.SCYTHE_OF_VITUR));
		assertEquals(Integer.valueOf(6), used.get(ChargeType.EYE_OF_AYAK));
		assertEquals(Integer.valueOf(1), used.get(ChargeType.TOXIC_BLOWPIPE_SCALES));
		assertEquals(Integer.valueOf(1), used.get(ChargeType.TOXIC_BLOWPIPE_DARTS_80));
		// Two short of the amulet's message
		assertEquals(Integer.valueOf(23), replay("charge-test-tob.log", 617).get(ChargeType.BLOOD_FURY));
		// Three special attacks with the crystal halberd (its Check has no earlier reading to compare with)
		assertEquals(Integer.valueOf(3), used.get(ChargeType.CRYSTAL_HALBERD));
	}

	/**
	 * Blood fury at Phosani's Nightmare with a thrall out, against the amulet's Check: 9,989 -> 9,899 (90) in
	 * charge-test-phosani-check.log, then 9,826 -> 9,774 (52) -> 9,644 (130) in charge-test-blood-fury.log
	 * (2026-09-28). Counting every damaging hit gave 114, 57 and 164: the thrall's small hits look like the
	 * player's own.
	 */
	@Test
	public void replayedBloodFuryLeavesOutTheThrall() throws Exception
	{
		assertEquals(Integer.valueOf(95), replay("charge-test-phosani-check.log").get(ChargeType.BLOOD_FURY));
		int toSecondCheck = replay("charge-test-blood-fury.log", 1750).get(ChargeType.BLOOD_FURY);
		assertEquals(51, toSecondCheck);
		assertEquals(137, replay("charge-test-blood-fury.log").get(ChargeType.BLOOD_FURY) - toSecondCheck);
	}

	/**
	 * Two Phosani's Nightmare kills and a death (2026-09-28), switching between the scythe, Tumeken's shadow and
	 * other weapons: no attack is lost to the switches. 132 scythe attacks, 7 of them missing with every hit (one more
	 * only hit a totem, shown as a yellow hitsplat).
	 */
	@Test
	public void replayedPhosanisTripSurvivesWeaponSwitches() throws Exception
	{
		Map<ChargeType, Integer> used = replay("charge-test-phosani.log");

		assertEquals(Integer.valueOf(125), used.get(ChargeType.SCYTHE_OF_VITUR));
		assertEquals(Integer.valueOf(135), used.get(ChargeType.TUMEKENS_SHADOW));
	}

	@Test
	public void scytheBlocksMissesAndUnchargedScythesUseNoCharge()
	{
		Map<ChargeType, Integer> used = new EnumMap<>(ChargeType.class);
		ChargeCounter counter = new ChargeCounter(id -> true);

		counter.gearChanged(1, new ChargeCounter.Gear(ItemID.SCYTHE_OF_VITUR, -1, -1));
		counter.animation(2, AnimationID.SCYTHE_OF_VITUR_ATTACK);
		counter.hitsplat(3, HitsplatID.DAMAGE_ME_CYAN, 12);
		counter.animation(3, AnimationID.HUMAN_SCYTHE_BLOCK);
		// Switched to the scythe in the same tick as the attack
		counter.gearChanged(5, new ChargeCounter.Gear(ItemID.TUMEKENS_SHADOW, -1, -1));
		counter.animation(6, AnimationID.SCYTHE_OF_VITUR_ATTACK);
		counter.gearChanged(6, new ChargeCounter.Gear(ItemID.SCYTHE_OF_VITUR, -1, -1));
		counter.hitsplat(7, HitsplatID.DAMAGE_ME, 30);
		// Every hit missed
		counter.animation(10, AnimationID.SCYTHE_OF_VITUR_ATTACK);
		counter.hitsplat(11, HitsplatID.BLOCK_ME, 0);
		counter.hitsplat(11, HitsplatID.BLOCK_ME, 0);
		counter.gearChanged(13, new ChargeCounter.Gear(ItemID.SCYTHE_OF_VITUR_UNCHARGED, -1, -1));
		counter.animation(14, AnimationID.SCYTHE_OF_VITUR_ATTACK);
		counter.hitsplat(15, HitsplatID.DAMAGE_ME, 5);
		counter.process(20, (type, n) -> used.merge(type, n, Integer::sum));

		assertEquals(Integer.valueOf(2), used.get(ChargeType.SCYTHE_OF_VITUR));
	}

	@Test
	public void shadowCastsOnlyCountWithTheShadowWorn()
	{
		Map<ChargeType, Integer> used = new EnumMap<>(ChargeType.class);
		ChargeCounter counter = new ChargeCounter(id -> false);

		counter.gearChanged(1, new ChargeCounter.Gear(ItemID.TUMEKENS_SHADOW, -1, -1));
		counter.spotAnimsChanged(2, ImmutableSet.of(SpotanimID.TUMEKENS_SHADOW_CASTING));
		// The graphic re-applied in the same tick is still one cast
		counter.spotAnimsChanged(2, ImmutableSet.of(SpotanimID.TUMEKENS_SHADOW_CASTING));
		counter.gearChanged(3, new ChargeCounter.Gear(ItemID.TUMEKENS_SHADOW_UNCHARGED, -1, -1));
		counter.spotAnimsChanged(5, ImmutableSet.of(SpotanimID.TUMEKENS_SHADOW_CASTING));
		counter.process(10, (type, n) -> used.merge(type, n, Integer::sum));

		assertEquals(Integer.valueOf(1), used.get(ChargeType.TUMEKENS_SHADOW));
	}

	@Test
	public void wikiPhosanisMagicWeaponsCountOneChargePerCast()
	{
		Map<ChargeType, Integer> used = new EnumMap<>(ChargeType.class);
		ChargeCounter counter = new ChargeCounter(id -> false);

		counter.gearChanged(1, new ChargeCounter.Gear(ItemID.SANGUINESTI_STAFF, -1, -1));
		counter.spotAnimsChanged(2, ImmutableSet.of(SpotanimID.SANGUINESTI_STAFF_CASTING));
		counter.gearChanged(3, new ChargeCounter.Gear(ItemID.TOXIC_TOTS_CHARGED, -1, -1));
		counter.spotAnimsChanged(4, ImmutableSet.of(SpotanimID.TOXIC_TOTS_CASTING));
		counter.gearChanged(5, new ChargeCounter.Gear(ItemID.TOTS_CHARGED, -1, -1));
		counter.spotAnimsChanged(6, ImmutableSet.of(SpotanimID.SLAYER_TOTS_CASTING));
		counter.gearChanged(7, new ChargeCounter.Gear(ItemID.EYE_OF_AYAK, -1, -1));
		counter.spotAnimsChanged(8, ImmutableSet.of(SpotanimID.VFX_AYAK_PLAYER_NORMAL_SPOTANIM));
		counter.spotAnimsChanged(11, ImmutableSet.of(SpotanimID.VFX_AYAK_PLAYER_SPECIAL_SPOTANIM));
		// A sanguinesti graphic with the eye worn isn't a sanguinesti cast
		counter.spotAnimsChanged(14, ImmutableSet.of(SpotanimID.SANGUINESTI_STAFF_CASTING));
		counter.process(20, (type, n) -> used.merge(type, n, Integer::sum));

		assertEquals(Integer.valueOf(1), used.get(ChargeType.SANGUINESTI_STAFF));
		assertEquals(Integer.valueOf(1), used.get(ChargeType.TRIDENT_OF_THE_SWAMP));
		assertEquals(Integer.valueOf(1), used.get(ChargeType.TRIDENT_OF_THE_SEAS));
		assertEquals(Integer.valueOf(2), used.get(ChargeType.EYE_OF_AYAK));
	}

	@Test
	public void blowpipeShotsUseScalesAndDartsByCape()
	{
		Map<ChargeType, Integer> used = new EnumMap<>(ChargeType.class);
		ChargeCounter counter = new ChargeCounter(id -> false);

		// One shot per attack animation; hitsplats don't count (a thrall's look the same)
		counter.gearChanged(1, new ChargeCounter.Gear(ItemID.TOXIC_BLOWPIPE_LOADED, -1, -1, ItemID.DIZANAS_QUIVER_INFINITE));
		counter.animation(1, AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK);
		counter.hitsplat(2, HitsplatID.DAMAGE_ME, 10);
		counter.hitsplat(4, HitsplatID.DAMAGE_ME, 2);
		counter.animation(3, AnimationID.TOXIC_BLOWPIPE_SPECIAL_UPDATED);
		counter.gearChanged(5, new ChargeCounter.Gear(ItemID.TOXIC_BLOWPIPE_LOADED, -1, -1, ItemID.ANMA_50_REWARD));
		counter.animation(6, AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK);
		counter.gearChanged(7, new ChargeCounter.Gear(ItemID.TOXIC_BLOWPIPE_LOADED, -1, -1, ItemID.INFERNAL_CAPE));
		counter.animation(8, AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK);
		// Switched away in the same tick as the shot still counts; the animation with another weapon doesn't
		counter.animation(10, AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK);
		counter.gearChanged(10, new ChargeCounter.Gear(ItemID.EYE_OF_AYAK, -1, -1, ItemID.INFERNAL_CAPE));
		counter.animation(12, AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK);
		counter.process(20, (type, n) -> used.merge(type, n, Integer::sum));

		assertEquals(Integer.valueOf(5), used.get(ChargeType.TOXIC_BLOWPIPE_SCALES));
		assertEquals(Integer.valueOf(2), used.get(ChargeType.TOXIC_BLOWPIPE_DARTS_80));
		assertEquals(Integer.valueOf(1), used.get(ChargeType.TOXIC_BLOWPIPE_DARTS_72));
		assertEquals(Integer.valueOf(2), used.get(ChargeType.TOXIC_BLOWPIPE_DARTS_0));
	}

	@Test
	public void thrallHitsUseNoBloodFuryCharge()
	{
		Map<ChargeType, Integer> used = new EnumMap<>(ChargeType.class);
		ChargeCounter counter = new ChargeCounter(id -> id == ItemID.SCYTHE_OF_VITUR);

		counter.gearChanged(1, new ChargeCounter.Gear(ItemID.SCYTHE_OF_VITUR, -1, ItemID.BLOOD_AMULET));
		counter.thrallChanged(1, true);
		counter.animation(2, AnimationID.SCYTHE_OF_VITUR_ATTACK);
		// The scythe's hits land the next tick, small ones included
		counter.hitsplat(3, HitsplatID.DAMAGE_ME, 30);
		counter.hitsplat(3, HitsplatID.DAMAGE_ME_CYAN, 2);
		// A thrall hit, and a big hit from the same attack a few ticks later
		counter.hitsplat(5, HitsplatID.DAMAGE_ME, 3);
		counter.hitsplat(6, HitsplatID.DAMAGE_ME, 25);
		// Without the thrall, small hits count again
		counter.thrallChanged(7, false);
		counter.hitsplat(8, HitsplatID.DAMAGE_ME, 3);
		// A thrall that outlives its longest duration is gone even if its message was missed
		counter.thrallChanged(10, true);
		counter.hitsplat(11, HitsplatID.DAMAGE_ME, 3);
		counter.hitsplat(10 + ChargeCounter.THRALL_MAX_TICKS + 1, HitsplatID.DAMAGE_ME, 3);
		counter.process(500, (type, n) -> used.merge(type, n, Integer::sum));

		assertEquals(Integer.valueOf(5), used.get(ChargeType.BLOOD_FURY));
	}

	@Test
	public void rangedHitAfterSwitchingToMeleeUsesNoBloodFuryCharge()
	{
		Map<ChargeType, Integer> used = new EnumMap<>(ChargeType.class);
		ChargeCounter counter = new ChargeCounter(id -> id == ItemID.ELDER_MAUL);

		counter.gearChanged(10, new ChargeCounter.Gear(ItemID.WILD_CAVE_WEBWEAVER_CHARGED, -1, ItemID.BLOOD_AMULET));
		counter.animation(10, 0);
		counter.spotAnimsChanged(10, ImmutableSet.of(SpotanimID.WILD_CAVE_BOW_ARROW_LAUNCH02));
		counter.gearChanged(11, new ChargeCounter.Gear(ItemID.ELDER_MAUL, -1, ItemID.BLOOD_AMULET));
		counter.hitsplat(13, HitsplatID.DAMAGE_ME, 30);
		counter.animation(14, 0);
		counter.hitsplat(15, HitsplatID.DAMAGE_MAX_ME, 75);
		counter.hitsplat(16, HitsplatID.BLOCK_ME, 0);
		counter.process(20, (type, n) -> used.merge(type, n, Integer::sum));

		assertEquals(Integer.valueOf(1), used.get(ChargeType.WILDERNESS_WEAPON));
		assertEquals(Integer.valueOf(1), used.get(ChargeType.BLOOD_FURY));
	}

	@Test
	public void fireSpellWithoutTomeUsesNoCharge()
	{
		Map<ChargeType, Integer> used = new EnumMap<>(ChargeType.class);
		ChargeCounter counter = new ChargeCounter(id -> false);

		counter.gearChanged(1, new ChargeCounter.Gear(ItemID.NIGHTMARE_STAFF_HARMONISED, -1, -1));
		counter.spotAnimsChanged(2, ImmutableSet.of(SpotanimID.FIRESURGE_CASTING));
		// Tome equipped in the same tick as the cast still counts
		counter.spotAnimsChanged(6, ImmutableSet.of(SpotanimID.FIRESURGE_CASTING));
		counter.gearChanged(6, new ChargeCounter.Gear(ItemID.NIGHTMARE_STAFF_HARMONISED, ItemID.TOME_OF_FIRE, -1));
		counter.process(10, (type, n) -> used.merge(type, n, Integer::sum));

		assertEquals(Integer.valueOf(1), used.get(ChargeType.TOME_OF_FIRE));
	}

	private Map<ChargeType, Integer> replay(String resource) throws Exception
	{
		return replay(resource, Integer.MAX_VALUE);
	}

	/**
	 * Replays a diagnostic log up to and including {@code lastTick}.
	 */
	private Map<ChargeType, Integer> replay(String resource, int lastTick) throws Exception
	{
		Map<ChargeType, Integer> used = new EnumMap<>(ChargeType.class);
		ChargeCounter counter = new ChargeCounter(MELEE::contains);
		Set<Integer> worn = new HashSet<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			getClass().getResourceAsStream(resource), StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				Matcher m = TICK.matcher(line);
				if (!m.matches())
				{
					continue;
				}
				int tick = Integer.parseInt(m.group(1));
				if (tick > lastTick)
				{
					break;
				}
				String category = m.group(2);
				String details = m.group(3);

				switch (category)
				{
					case "EQUIPMENT":
						if (details.startsWith("snapshot"))
						{
							worn.clear();
						}
						Matcher item = ITEM.matcher(details);
						while (item.find())
						{
							int id = Integer.parseInt(item.group(2));
							if ("-".equals(item.group(1)))
							{
								worn.remove(id);
							}
							else
							{
								worn.add(id);
							}
						}
						counter.gearChanged(tick, gear(worn));
						break;
					case "ANIM":
						Matcher a = ANIM.matcher(details);
						counter.animation(tick, a.find() ? Integer.parseInt(a.group(1)) : -1);
						break;
					case "GRAPHIC":
						Matcher s = SPOTANIMS.matcher(details);
						Set<Integer> ids = new HashSet<>();
						if (s.find() && !s.group(1).isEmpty())
						{
							for (String id : s.group(1).split(", "))
							{
								ids.add(Integer.parseInt(id));
							}
						}
						counter.spotAnimsChanged(tick, ids);
						break;
					case "CHAT":
						Matcher c = MESSAGE.matcher(details);
						Boolean thrall = c.find() ? ChatPatterns.thrall(c.group(1)) : null;
						if (thrall != null)
						{
							counter.thrallChanged(tick, thrall);
						}
						break;
					case "HITSPLAT":
						Matcher h = HITSPLAT.matcher(details);
						if (h.find())
						{
							counter.hitsplat(tick, Integer.parseInt(h.group(1)), Integer.parseInt(h.group(2)));
						}
						break;
					default:
						break;
				}
			}
		}

		counter.process(Integer.MAX_VALUE, (type, n) -> used.merge(type, n, Integer::sum));
		return used;
	}

	private static ChargeCounter.Gear gear(Set<Integer> worn)
	{
		int weapon = worn.stream().filter(WEAPONS::contains).findFirst().orElse(-1);
		int shield = worn.contains(ItemID.TOME_OF_FIRE) ? ItemID.TOME_OF_FIRE : -1;
		int amulet = worn.stream().filter(AMULETS::contains).findFirst().orElse(-1);
		int cape = worn.stream().filter(id -> ChargeCounter.DART_SAVE_80.contains(id) || ChargeCounter.DART_SAVE_72.contains(id)
			|| ChargeCounter.DART_SAVE_60.contains(id)).findFirst().orElse(-1);
		return new ChargeCounter.Gear(weapon, shield, amulet, cape);
	}
}
