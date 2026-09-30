package com.bosstriptracker.tracking;

import com.google.common.collect.ImmutableSet;
import com.bosstriptracker.model.ChargeType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntPredicate;
import lombok.Value;
import net.runelite.api.HitsplatID;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpotanimID;

/**
 * Counts charges used from the local player's attacks, since the game does not update its charge varbits live.
 * <ul>
 * <li>Tome of fire: a fire spell casting graphic on the player while the tome is worn.</li>
 * <li>Revenant bows: a revenant bow launch graphic on the player (one ether per shot or special).</li>
 * <li>Scythe of Vitur: its attack animation while a charged scythe is worn (blocks use another animation), unless
 * every hit of that attack misses.</li>
 * <li>Tumeken's shadow: its casting graphic on the player while the shadow is worn.</li>
 * <li>Blood fury: each damaging hitsplat the player deals while wearing the amulet, if the player's last
 * attack before the hit was with a melee weapon. That excludes ranged and magic hits still in flight
 * after switching to melee. While a thrall is out, hits of up to a thrall's max hit that don't land the tick
 * after a melee attack are the thrall's: its hitsplats look like the player's own.</li>
 * <li>Toxic blowpipe: its attack animation (hitsplats can't be used: a thrall's look the same).</li>
 * <li>Crystal halberd: its attack and special attack animations.</li>
 * </ul>
 * Events are grouped by game tick and evaluated with the gear worn at the end of that tick, because gear
 * switches in the same tick can arrive after the attack. Validated against the game's Check messages.
 * Contains no client calls, so it can be tested by replaying a diagnostic log.
 */
class ChargeCounter
{
	static final Set<Integer> FIRE_CAST_SPOTANIMS = ImmutableSet.of(
		SpotanimID.FIRESTRIKE_CASTING,
		SpotanimID.FIREBOLT_CASTING,
		SpotanimID.FIREBLAST_CASTING,
		SpotanimID.FIREWAVE_CASTING,
		SpotanimID.FIRESURGE_CASTING,
		SpotanimID.FIRESURGE_CASTING_FAST
	);

	static final Set<Integer> REVENANT_BOW_SPOTANIMS = ImmutableSet.of(
		SpotanimID.WILD_CAVE_BOW_ARROW_LAUNCH,
		SpotanimID.WILD_CAVE_BOW_ARROW_LAUNCH02,
		SpotanimID.FX_WEBWEAVER01_LAUNCH_SPOTANIM
	);

	/**
	 * Damage the player dealt, in every colour: the Nightmare shows it in cyan during shield phases and in yellow on
	 * the totems.
	 */
	static final Set<Integer> DAMAGE_HITSPLATS = ImmutableSet.of(HitsplatID.DAMAGE_ME, HitsplatID.DAMAGE_MAX_ME,
		HitsplatID.DAMAGE_ME_CYAN, HitsplatID.DAMAGE_MAX_ME_CYAN, HitsplatID.DAMAGE_ME_ORANGE, HitsplatID.DAMAGE_MAX_ME_ORANGE,
		HitsplatID.DAMAGE_ME_YELLOW, HitsplatID.DAMAGE_MAX_ME_YELLOW, HitsplatID.DAMAGE_ME_WHITE, HitsplatID.DAMAGE_MAX_ME_WHITE,
		HitsplatID.DAMAGE_ME_POISE, HitsplatID.DAMAGE_MAX_ME_POISE);


	static final Set<Integer> SANGUINESTI_STAVES = ImmutableSet.of(ItemID.SANGUINESTI_STAFF, ItemID.SANGUINESTI_STAFF_OR);
	static final Set<Integer> SANGUINESTI_SPOTANIMS = ImmutableSet.of(
		SpotanimID.SANGUINESTI_STAFF_CASTING, SpotanimID.SANGUINESTI_STAFF_CASTING_JUSTICIAR);
	static final Set<Integer> SWAMP_TRIDENTS = ImmutableSet.of(
		ItemID.TOXIC_TOTS_CHARGED, ItemID.TOXIC_TOTS_I_CHARGED, ItemID.TOXIC_TOTS_CHARGED_ORN);
	static final Set<Integer> SEAS_TRIDENTS = ImmutableSet.of(ItemID.TOTS, ItemID.TOTS_CHARGED, ItemID.TOTS_I_CHARGED);
	static final Set<Integer> AYAK_SPOTANIMS = ImmutableSet.of(
		SpotanimID.VFX_AYAK_PLAYER_NORMAL_SPOTANIM, SpotanimID.VFX_AYAK_PLAYER_SPECIAL_SPOTANIM);
	static final Set<Integer> BLOWPIPES = ImmutableSet.of(ItemID.TOXIC_BLOWPIPE_LOADED, ItemID.TOXIC_BLOWPIPE_LOADED_ORNAMENT);
	static final Set<Integer> BLOWPIPE_ANIMATIONS = ImmutableSet.of(AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK,
		AnimationID.SNAKEBOSS_BLOWPIPE_ATTACK_ORNAMENT, AnimationID.TOXIC_BLOWPIPE_SPECIAL_UPDATED);
	/**
	 * Capes that save 80% of blowpipe darts: Ava's assembler, Dizana's quiver and their max capes.
	 */
	static final Set<Integer> DART_SAVE_80 = ImmutableSet.of(
		ItemID.AVAS_ASSEMBLER, ItemID.AVAS_ASSEMBLER_TROUVER, ItemID.AVAS_ASSEMBLER_MASORI, ItemID.AVAS_ASSEMBLER_MASORI_TROUVER,
		ItemID.SKILLCAPE_MAX_ASSEMBLER, ItemID.SKILLCAPE_MAX_ASSEMBLER_TROUVER, ItemID.SKILLCAPE_MAX_ASSEMBLER_MASORI,
		ItemID.SKILLCAPE_MAX_ASSEMBLER_MASORI_TROUVER, ItemID.DIZANAS_QUIVER_CHARGED, ItemID.DIZANAS_QUIVER_CHARGED_TROUVER,
		ItemID.DIZANAS_QUIVER_INFINITE, ItemID.DIZANAS_QUIVER_INFINITE_TROUVER, ItemID.SKILLCAPE_MAX_DIZANAS,
		ItemID.SKILLCAPE_MAX_DIZANAS_TROUVER);
	/**
	 * Ava's accumulator (and Ava's max cape) saves 72%; Ava's attractor 60%.
	 */
	static final Set<Integer> DART_SAVE_72 = ImmutableSet.of(ItemID.ANMA_50_REWARD, ItemID.SKILLCAPE_MAX_ANMA);
	static final Set<Integer> DART_SAVE_60 = ImmutableSet.of(ItemID.ANMA_30_REWARD);

	/**
	 * A greater thrall's max hit (OSRS Wiki); lesser and superior thralls hit less.
	 */
	static final int THRALL_MAX_HIT = 3;
	/**
	 * A thrall lasts 0.6 seconds per Magic level (OSRS Wiki), so no more than this many ticks even with a boost.
	 * Covers a thrall whose "returns to the grave" message was missed.
	 */
	static final int THRALL_MAX_TICKS = 130;

	/**
	 * The crystal halberd's stab, slash and special attack (Sweep). Its spec was seen as 1203 in the Theatre of
	 * Blood log; the others are the usual halberd animations (unverified).
	 */
	static final Set<Integer> HALBERD_ANIMATIONS = ImmutableSet.of(AnimationID.HUMAN_SPEAR_SPIKE,
		AnimationID.HUMAN_SCYTHE_SWEEP, AnimationID.DRAGON_HALBERD_SPECIAL_ATTACK);

	/**
	 * Charged scythes, ornamented ones included.
	 */
	static final Set<Integer> SCYTHES = ImmutableSet.of(
		ItemID.SCYTHE_OF_VITUR, ItemID.SCYTHE_OF_VITUR_OR, ItemID.SCYTHE_OF_VITUR_BL);

	interface Listener
	{
		void chargesUsed(ChargeType type, int charges);
	}

	@Value
	static class Gear
	{
		static final Gear NONE = new Gear(-1, -1, -1, -1);

		int weapon;
		int shield;
		int amulet;
		int cape;

		Gear(int weapon, int shield, int amulet)
		{
			this(weapon, shield, amulet, -1);
		}

		Gear(int weapon, int shield, int amulet, int cape)
		{
			this.weapon = weapon;
			this.shield = shield;
			this.amulet = amulet;
			this.cape = cape;
		}
	}

	private static class TickEvents
	{
		final Set<Integer> spotAnims = new HashSet<>();
		final Set<Integer> animations = new HashSet<>();
		boolean animation;
		int damagingHits;
		/**
		 * Damaging hits no bigger than a thrall's max hit.
		 */
		int smallDamagingHits;
		/**
		 * Set when a thrall was raised (true) or returned to the grave (false) this tick.
		 */
		Boolean thrall;
	}

	private final IntPredicate isMeleeWeapon;
	/**
	 * A scythe attack waiting for its hits: it only uses a charge if one of them does damage.
	 */
	private Integer pendingScytheTick;
	private final TreeMap<Integer, Gear> gearByTick = new TreeMap<>();
	private final TreeMap<Integer, TickEvents> pending = new TreeMap<>();
	private Boolean lastAttackMelee;
	private Integer lastMeleeAttackTick;
	/**
	 * The tick the current thrall was raised, or null with no thrall out.
	 */
	private Integer thrallRaisedTick;

	ChargeCounter(IntPredicate isMeleeWeapon)
	{
		this.isMeleeWeapon = isMeleeWeapon;
	}

	void reset()
	{
		gearByTick.clear();
		pending.clear();
		lastAttackMelee = null;
		lastMeleeAttackTick = null;
		pendingScytheTick = null;
		thrallRaisedTick = null;
	}

	/**
	 * Gear after a change during this tick; later calls in the same tick replace earlier ones.
	 */
	void gearChanged(int tick, Gear gear)
	{
		gearByTick.put(tick, gear);
	}

	/**
	 * The player's spot animations after a change. The game re-applies a casting or launch graphic on every
	 * attack, so each one counts once per tick.
	 */
	void spotAnimsChanged(int tick, Set<Integer> spotAnims)
	{
		events(tick).spotAnims.addAll(spotAnims);
	}

	void animation(int tick, int animationId)
	{
		TickEvents events = events(tick);
		events.animation = true;
		events.animations.add(animationId);
	}

	/**
	 * A hitsplat the local player dealt to an NPC.
	 */
	void hitsplat(int tick, int type, int amount)
	{
		if (amount > 0 && DAMAGE_HITSPLATS.contains(type))
		{
			TickEvents events = events(tick);
			events.damagingHits++;
			if (amount <= THRALL_MAX_HIT)
			{
				events.smallDamagingHits++;
			}
		}
	}

	/**
	 * A thrall was raised ({@code out}) or returned to the grave, from the game message.
	 */
	void thrallChanged(int tick, boolean out)
	{
		events(tick).thrall = out;
	}

	/**
	 * Evaluates every tick up to and including {@code lastCompleteTick}.
	 */
	void process(int lastCompleteTick, Listener listener)
	{
		while (!pending.isEmpty() && pending.firstKey() <= lastCompleteTick)
		{
			Map.Entry<Integer, TickEvents> entry = pending.pollFirstEntry();
			evaluate(entry.getKey(), entry.getValue(), listener);
		}

		// Keep only the gear in effect at the last complete tick, plus anything newer
		Integer floor = gearByTick.floorKey(lastCompleteTick);
		if (floor != null)
		{
			gearByTick.headMap(floor, false).clear();
		}
	}

	private void evaluate(int tick, TickEvents events, Listener listener)
	{
		Map.Entry<Integer, Gear> gearEntry = gearByTick.floorEntry(tick);
		Gear gear = gearEntry != null ? gearEntry.getValue() : Gear.NONE;

		if (events.thrall != null)
		{
			thrallRaisedTick = events.thrall ? tick : null;
		}
		if (thrallRaisedTick != null && tick - thrallRaisedTick > THRALL_MAX_TICKS)
		{
			thrallRaisedTick = null;
		}

		boolean fireCast = events.spotAnims.stream().anyMatch(FIRE_CAST_SPOTANIMS::contains);
		if (fireCast && gear.getShield() == ItemID.TOME_OF_FIRE)
		{
			listener.chargesUsed(ChargeType.TOME_OF_FIRE, 1);
		}

		if (events.spotAnims.stream().anyMatch(REVENANT_BOW_SPOTANIMS::contains))
		{
			listener.chargesUsed(ChargeType.WILDERNESS_WEAPON, 1);
		}

		// A scythe attack whose hits all miss uses no charge (checked against the game's Check messages), so it's
		// decided when the hits land, the tick after the attack
		// Its hits land the next tick; damage a tick later is something else (e.g. a bleed)
		if (pendingScytheTick != null && tick > pendingScytheTick)
		{
			if (tick == pendingScytheTick + 1 && events.damagingHits > 0)
			{
				listener.chargesUsed(ChargeType.SCYTHE_OF_VITUR, 1);
			}
			pendingScytheTick = null;
		}

		// The weapon may be switched in the same tick, before or after the attack: either counts
		Map.Entry<Integer, Gear> before = gearByTick.lowerEntry(tick);
		int previousWeapon = before != null ? before.getValue().getWeapon() : -1;
		if (events.animations.contains(AnimationID.SCYTHE_OF_VITUR_ATTACK)
			&& (SCYTHES.contains(gear.getWeapon()) || SCYTHES.contains(previousWeapon)))
		{
			pendingScytheTick = tick;
		}

		if (events.spotAnims.contains(SpotanimID.TUMEKENS_SHADOW_CASTING)
			&& (gear.getWeapon() == ItemID.TUMEKENS_SHADOW || previousWeapon == ItemID.TUMEKENS_SHADOW))
		{
			listener.chargesUsed(ChargeType.TUMEKENS_SHADOW, 1);
		}

		// From the wiki's Phosani's gear. The Eye of Ayak's cast graphic matched a Check exactly (52 of 52); its
		// attack animation doesn't restart on every cast, so it's not used. The others aren't in a log yet
		if (events.spotAnims.stream().anyMatch(SANGUINESTI_SPOTANIMS::contains) && worn(SANGUINESTI_STAVES, gear, previousWeapon))
		{
			listener.chargesUsed(ChargeType.SANGUINESTI_STAFF, 1);
		}
		if (events.spotAnims.contains(SpotanimID.TOXIC_TOTS_CASTING) && worn(SWAMP_TRIDENTS, gear, previousWeapon))
		{
			listener.chargesUsed(ChargeType.TRIDENT_OF_THE_SWAMP, 1);
		}
		if (events.spotAnims.contains(SpotanimID.SLAYER_TOTS_CASTING) && worn(SEAS_TRIDENTS, gear, previousWeapon))
		{
			listener.chargesUsed(ChargeType.TRIDENT_OF_THE_SEAS, 1);
		}
		if (events.spotAnims.stream().anyMatch(AYAK_SPOTANIMS::contains)
			&& (gear.getWeapon() == ItemID.EYE_OF_AYAK || previousWeapon == ItemID.EYE_OF_AYAK))
		{
			listener.chargesUsed(ChargeType.EYE_OF_AYAK, 1);
		}
		// One animation per shot: 5 animations for the 5 darts and 5 scales a pair of Checks showed (2026-09-29). Two
		// more hitsplats in that log were the thrall's
		if (events.animations.stream().anyMatch(BLOWPIPE_ANIMATIONS::contains) && worn(BLOWPIPES, gear, previousWeapon))
		{
			listener.chargesUsed(ChargeType.TOXIC_BLOWPIPE_SCALES, 1);
			listener.chargesUsed(DART_SAVE_80.contains(gear.getCape()) ? ChargeType.TOXIC_BLOWPIPE_DARTS_80
				: DART_SAVE_72.contains(gear.getCape()) ? ChargeType.TOXIC_BLOWPIPE_DARTS_72
				: DART_SAVE_60.contains(gear.getCape()) ? ChargeType.TOXIC_BLOWPIPE_DARTS_60
				: ChargeType.TOXIC_BLOWPIPE_DARTS_0, 1);
		}

		if (events.animations.stream().anyMatch(HALBERD_ANIMATIONS::contains)
			&& (gear.getWeapon() == ItemID.CRYSTAL_HALBERD || previousWeapon == ItemID.CRYSTAL_HALBERD))
		{
			listener.chargesUsed(ChargeType.CRYSTAL_HALBERD, 1);
		}

		// Hits land at least a tick after the attack, so judge them by attacks from earlier ticks. Some attacks hit
		// more than once over several ticks (the elder maul at the Maggot King), so later hits count too, except
		// small ones while a thrall is out. Checked against the Maggot King (exact) and five Nightmare readings
		// (within 3 charges each)
		int furyHits = events.damagingHits;
		boolean hitTickOfMeleeAttack = lastMeleeAttackTick != null && tick == lastMeleeAttackTick + 1;
		if (thrallRaisedTick != null && !hitTickOfMeleeAttack)
		{
			furyHits -= events.smallDamagingHits;
		}
		if (furyHits > 0 && Boolean.TRUE.equals(lastAttackMelee) && gear.getAmulet() == ItemID.BLOOD_AMULET)
		{
			listener.chargesUsed(ChargeType.BLOOD_FURY, furyHits);
		}

		if (events.animation)
		{
			lastAttackMelee = isMeleeWeapon.test(gear.getWeapon());
			if (lastAttackMelee)
			{
				lastMeleeAttackTick = tick;
			}
		}
	}

	private static boolean worn(Set<Integer> items, Gear gear, int previousWeapon)
	{
		return items.contains(gear.getWeapon()) || items.contains(previousWeapon);
	}

	private TickEvents events(int tick)
	{
		return pending.computeIfAbsent(tick, t -> new TickEvents());
	}
}
