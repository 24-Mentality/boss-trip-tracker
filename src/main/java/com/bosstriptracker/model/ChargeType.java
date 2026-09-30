package com.bosstriptracker.model;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.gameval.ItemID;

/**
 * Charged items whose use is counted from attacks. The game's charge varbits are not updated live. Each is priced
 * from what recharges it: {@link #getComponents()} buy {@link #getChargesPerRecharge()} charges.
 */
@Getter
public enum ChargeType
{
	/**
	 * One charge per successful melee hit; a blood shard adds 10,000 charges.
	 */
	BLOOD_FURY(ItemID.BLOOD_AMULET, 10_000, new Component(ItemID.BLOOD_SHARD, 1)),
	/**
	 * One charge per fire spell; a burnt or searing page adds 20 charges. The page priced is chosen in config.
	 */
	TOME_OF_FIRE(ItemID.TOME_OF_FIRE, 20, new Component(ItemID.WINT_SEARING_PAGE, 1)),
	/**
	 * One revenant ether per shot (special attack included) for the revenant cave bows.
	 */
	WILDERNESS_WEAPON(ItemID.WILD_CAVE_WEBWEAVER_CHARGED, 1, new Component(ItemID.WILD_CAVE_SHARD, 1)),
	/**
	 * One charge per attack that does damage; a vial of blood and 200 blood runes add 100 charges (OSRS Wiki).
	 */
	SCYTHE_OF_VITUR(ItemID.SCYTHE_OF_VITUR, 100, new Component(ItemID.VIAL_BLOOD, 1), new Component(ItemID.BLOODRUNE, 200)),
	/**
	 * One charge per cast; each charge takes 2 soul runes and 5 chaos runes (OSRS Wiki).
	 */
	TUMEKENS_SHADOW(ItemID.TUMEKENS_SHADOW, 1, new Component(ItemID.SOULRUNE, 2), new Component(ItemID.CHAOSRUNE, 5)),
	/**
	 * One charge per cast; 2 blood runes a charge (OSRS Wiki). Counted from its casting graphic (unverified).
	 */
	SANGUINESTI_STAFF(ItemID.SANGUINESTI_STAFF, 1, new Component(ItemID.BLOODRUNE, 2)),
	/**
	 * One charge per cast; a death, a chaos, 5 fire runes and a Zulrah's scale a charge (OSRS Wiki). Counted from its
	 * casting graphic (unverified).
	 */
	TRIDENT_OF_THE_SWAMP(ItemID.TOXIC_TOTS_CHARGED, 1, new Component(ItemID.DEATHRUNE, 1), new Component(ItemID.CHAOSRUNE, 1),
		new Component(ItemID.FIRERUNE, 5), new Component(ItemID.SNAKEBOSS_SCALE, 1)),
	/**
	 * One charge per cast; a death, a chaos, 5 fire runes and 10 coins a charge (OSRS Wiki). Counted from its casting
	 * graphic (unverified).
	 */
	TRIDENT_OF_THE_SEAS(ItemID.TOTS_CHARGED, 1, new Component(ItemID.DEATHRUNE, 1), new Component(ItemID.CHAOSRUNE, 1),
		new Component(ItemID.FIRERUNE, 5), new Component(ItemID.COINS, 10)),
	/**
	 * One charge per cast, charged with a demon tear a charge (OSRS Wiki). Counted from its casting graphic (105 of
	 * 105 against a Check).
	 */
	EYE_OF_AYAK(ItemID.EYE_OF_AYAK, 1, new Component(ItemID.DEMON_TEAR, 1)),
	/**
	 * The Eye of Ayak charged with runes instead: 2 death runes and a chaos rune a charge (OSRS Wiki).
	 */
	EYE_OF_AYAK_RUNES(ItemID.EYE_OF_AYAK, 1, new Component(ItemID.DEATHRUNE, 2), new Component(ItemID.CHAOSRUNE, 1)),
	/**
	 * Zulrah's scales: a 1/3 chance per shot to use none, so 2 scales every 3 shots (OSRS Wiki). Counted from its attack
	 * animation, one per shot (5 of 5 against a Check).
	 */
	TOXIC_BLOWPIPE_SCALES(ItemID.TOXIC_BLOWPIPE_LOADED, 3, new Component(ItemID.SNAKEBOSS_SCALE, 2)),
	/**
	 * Darts lost per 25 shots, by the cape worn: an Ava's assembler or Dizana's quiver saves 80% (5 lost), an
	 * accumulator 72% (7), an attractor 60% (10), nothing saves none (25) (OSRS Wiki). The dart priced is chosen in
	 * config.
	 */
	TOXIC_BLOWPIPE_DARTS_80(ItemID.TOXIC_BLOWPIPE_LOADED, 25, new Component(ItemID.DRAGON_DART, 5)),
	TOXIC_BLOWPIPE_DARTS_72(ItemID.TOXIC_BLOWPIPE_LOADED, 25, new Component(ItemID.DRAGON_DART, 7)),
	TOXIC_BLOWPIPE_DARTS_60(ItemID.TOXIC_BLOWPIPE_LOADED, 25, new Component(ItemID.DRAGON_DART, 10)),
	TOXIC_BLOWPIPE_DARTS_0(ItemID.TOXIC_BLOWPIPE_LOADED, 25, new Component(ItemID.DRAGON_DART, 25)),
	/**
	 * One charge per attack (special attack included), recharged with crystal shards at 100 charges a shard (OSRS
	 * Wiki). Shards are untradeable, so it costs 0 gp but shows the count. Counted from its attack animations
	 * (unverified: no before-and-after Check yet).
	 */
	CRYSTAL_HALBERD(ItemID.CRYSTAL_HALBERD, 100, new Component(ItemID.PRIF_CRYSTAL_SHARD, 1));

	/**
	 * Some of an item that recharges a charged item.
	 */
	@Value
	public static class Component
	{
		int itemId;
		int quantity;
	}

	/**
	 * The charged item, used for the icon and name in the panel.
	 */
	private final int sourceItemId;
	/**
	 * Charges that {@link #getComponents()} buy.
	 */
	private final int chargesPerRecharge;
	/**
	 * What recharges it, e.g. a blood shard, or a vial of blood and blood runes. The first is the one saved on the
	 * supply line.
	 */
	private final List<Component> components;

	ChargeType(int sourceItemId, int chargesPerRecharge, Component... components)
	{
		this.sourceItemId = sourceItemId;
		this.chargesPerRecharge = chargesPerRecharge;
		this.components = ImmutableList.copyOf(components);
	}

	/**
	 * The first recharge item; for the Tome of fire, the default page (the config picks the one priced).
	 */
	public int getChargeItemId()
	{
		return components.get(0).getItemId();
	}

	/**
	 * Blowpipe dart lines, whose recharge item (the dart) is chosen in config.
	 */
	public boolean isBlowpipeDarts()
	{
		return this == TOXIC_BLOWPIPE_DARTS_80 || this == TOXIC_BLOWPIPE_DARTS_72 || this == TOXIC_BLOWPIPE_DARTS_60
			|| this == TOXIC_BLOWPIPE_DARTS_0;
	}

	/**
	 * @return the first charge type whose charged item this is, or null
	 */
	public static ChargeType forSourceItem(int itemId)
	{
		for (ChargeType type : values())
		{
			if (type.sourceItemId == itemId)
			{
				return type;
			}
		}
		return null;
	}

	/**
	 * The charge type of a saved supply line: several share a charged item (the blowpipe's scales and darts, the
	 * Eye of Ayak's tears or runes), told apart by the saved recharge item.
	 *
	 * @return the type, or null if none matches
	 */
	public static ChargeType forLine(int sourceItemId, int chargeItemId)
	{
		ChargeType fallback = null;
		for (ChargeType type : values())
		{
			if (type.sourceItemId != sourceItemId)
			{
				continue;
			}
			if (type.getChargeItemId() == chargeItemId)
			{
				return type;
			}
			// Configurable recharge items (tome pages, darts) are saved as the item chosen
			if (fallback == null && (type == TOME_OF_FIRE || type.isBlowpipeDarts()))
			{
				fallback = type;
			}
		}
		return fallback;
	}
}
