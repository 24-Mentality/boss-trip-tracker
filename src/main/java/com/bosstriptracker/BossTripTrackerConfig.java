package com.bosstriptracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BossTripTrackerConfig.GROUP)
public interface BossTripTrackerConfig extends Config
{
	/**
	 * Kept from when the plugin only tracked the Maggot King, so saved settings carry over.
	 */
	String GROUP = "bosstriptracker";
	/**
	 * The config group before the plugin became the Boss Trip Tracker; settings under it are copied over once
	 * (see BossTripTrackerPlugin#migrateSettings).
	 */
	String LEGACY_GROUP = "maggotkingtriptracker";

	@ConfigSection(
		name = "Trips",
		description = "When trips start and end",
		position = 0
	)
	String tripsSection = "trips";

	@ConfigSection(
		name = "Charges",
		description = "Charged item costs",
		position = 1
	)
	String chargesSection = "charges";

	@ConfigSection(
		name = "Loot alerts",
		description = "Notifications for good drops",
		position = 2
	)
	String alertsSection = "alerts";

	@ConfigSection(
		name = "Display",
		description = "Side panel options",
		position = 3
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Overlay",
		description = "An optional box on the game screen with your kill goal, trip times and profit",
		position = 4
	)
	String overlaySection = "overlay";

	@ConfigSection(
		name = "Developer",
		description = "Settings used to collect data for plugin development",
		position = 100,
		closedByDefault = true
	)
	String developerSection = "developer";

	@ConfigItem(
		keyName = "logoutGraceMinutes",
		name = "Logout grace period",
		description = "After logging out during a trip, the trip continues if you are back at the boss within this time",
		section = tripsSection,
		position = 0
	)
	@Range(max = 60)
	@Units(Units.MINUTES)
	default int logoutGraceMinutes()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "mergeReentries",
		name = "Merge re-entries",
		description = "Count leaving and re-entering a boss's area within the merge window as one trip",
		section = tripsSection,
		position = 1
	)
	default boolean mergeReentries()
	{
		return false;
	}

	@ConfigItem(
		keyName = "mergeWindowMinutes",
		name = "Merge window",
		description = "How soon you must re-enter for it to count as the same trip",
		section = tripsSection,
		position = 2
	)
	@Range(min = 1, max = 60)
	@Units(Units.MINUTES)
	default int mergeWindowMinutes()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "countPreEntrySupplies",
		name = "Count supplies used before entry",
		description = "Add food, potions and spells used in the 60 seconds before entering a boss's area to the trip",
		section = tripsSection,
		position = 3
	)
	default boolean countPreEntrySupplies()
	{
		return true;
	}

	@ConfigItem(
		keyName = "idlePauseSeconds",
		name = "Idle pause after",
		description = "During a trip, stop the trip and goal clocks after this long without dealing damage (the idle"
			+ " time isn't counted). They restart on your next hit. 0 turns it off. Raids never pause: the time"
			+ " between rooms is part of the raid.",
		section = tripsSection,
		position = 4
	)
	@Range(max = 600)
	@Units(Units.SECONDS)
	default int idlePauseSeconds()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "outsideGraceMinutes",
		name = "Outside grace period",
		description = "After walking out (e.g. out of the Maggot King's lair), the trip stays open (paused) while you stay just outside for"
			+ " this long. Going back in continues it. 0 ends the trip when you walk out.",
		section = tripsSection,
		position = 5
	)
	@Range(max = 30)
	@Units(Units.MINUTES)
	default int outsideGraceMinutes()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "autoResumeOnAttack",
		name = "Auto-resume when I attack",
		description = "After pressing Pause, resume as soon as you damage the boss",
		section = tripsSection,
		position = 6
	)
	default boolean autoResumeOnAttack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tobPastTeamSize",
		name = "Typical team size for past raids",
		description = "Theatre of Blood raids from RuneLite's all-time records don't say the team size; luck assumes"
			+ " this one. Raids this plugin tracks use their own team size.",
		section = tripsSection,
		position = 7
	)
	@Range(min = 1, max = 5)
	default int tobPastTeamSize()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "tomePage",
		name = "Tome of fire pages",
		description = "Which page's GE price is used for Tome of fire charges (20 charges per page)",
		section = chargesSection,
		position = 0
	)
	default TomePage tomePage()
	{
		return TomePage.SEARING;
	}

	@ConfigItem(
		keyName = "eyeOfAyakCharge",
		name = "Eye of Ayak charged with",
		description = "What your Eye of Ayak is charged with, for pricing its charges (one per cast)",
		section = chargesSection,
		position = 1
	)
	default EyeOfAyakCharge eyeOfAyakCharge()
	{
		return EyeOfAyakCharge.DEMON_TEARS;
	}

	@ConfigItem(
		keyName = "blowpipeDarts",
		name = "Blowpipe darts",
		description = "Which darts are in your Toxic blowpipe, for pricing the darts it uses (an Ava's device or Dizana's"
			+ " quiver saves most of them)",
		section = chargesSection,
		position = 2
	)
	default BlowpipeDart blowpipeDarts()
	{
		return BlowpipeDart.DRAGON;
	}

	@ConfigItem(
		keyName = "alertUniques",
		name = "Unique drops",
		description = "Notify when you get a unique drop",
		section = alertsSection,
		position = 0
	)
	default boolean alertUniques()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alertPet",
		name = "Pet",
		description = "Notify when you get a boss pet (for the Maggot King, from a kill or an egg)",
		section = alertsSection,
		position = 1
	)
	default boolean alertPet()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alertValue",
		name = "Drops worth at least",
		description = "Notify for any boss drop (including polished tarnished items) worth at least this"
			+ " many gp. 0 turns it off.",
		section = alertsSection,
		position = 2
	)
	default int alertValue()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "alertNotification",
		name = "Notification",
		description = "How loot alerts are shown",
		section = alertsSection,
		position = 3
	)
	default Notification alertNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = "openPanelOnEntry",
		name = "Open panel on entry",
		description = "Open this plugin's side panel on the Trip tab when you enter a tracked boss's area",
		section = displaySection,
		position = 0
	)
	default boolean openPanelOnEntry()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCurrentValue",
		name = "Show today's value",
		description = "On the Lifetime tab, also show all loot valued at today's GE prices",
		section = displaySection,
		position = 1
	)
	default boolean showCurrentValue()
	{
		return false;
	}

	@ConfigItem(
		keyName = "luckCardStyle",
		name = "Luck card",
		description = "Overview is laid out like the share card; Classic is the original card with the dry streak"
			+ " progress bar",
		section = displaySection,
		position = 2
	)
	default LuckCardStyle luckCardStyle()
	{
		return LuckCardStyle.OVERVIEW;
	}

	@ConfigItem(
		keyName = "shareShowName",
		name = "Show my name on share cards",
		description = "Put your display name on share cards made with the camera button",
		section = displaySection,
		position = 3
	)
	default boolean shareShowName()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayEnabled",
		name = "Show overlay",
		description = "A small box on the game screen like RuneLite's XP tracker box, with the boss icon and up to three"
			+ " rows picked below. Also: right-click the goal, trip time or profit card, Add to canvas. Alt+drag the box"
			+ " to move it.",
		section = overlaySection,
		position = 0
	)
	default boolean overlayEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "overlayRow1",
		name = "Row 1",
		description = "The first row. Goal stats are hidden while no goal is set, trip stats while no trip is shown."
			+ " Current kill counts from the boss spawning, like the game's Fight duration, and shows the last kill's"
			+ " time between kills. Luck status is the tier from the Luck card.",
		section = overlaySection,
		position = 1
	)
	default OverlayStat overlayRow1()
	{
		return OverlayStat.KILLS_PER_HOUR;
	}

	@ConfigItem(
		keyName = "overlayRow2",
		name = "Row 2",
		description = "The second row, or nothing",
		section = overlaySection,
		position = 2
	)
	default OverlayOptionalStat overlayRow2()
	{
		return OverlayOptionalStat.CURRENT_KILL;
	}

	@ConfigItem(
		keyName = "overlayRow3",
		name = "Row 3",
		description = "The third row, or nothing",
		section = overlaySection,
		position = 3
	)
	default OverlayOptionalStat overlayRow3()
	{
		return OverlayOptionalStat.NET_PROFIT;
	}

	@ConfigItem(
		keyName = "overlayProgressBar",
		name = "Goal progress bar",
		description = "With a goal set, a progress bar at the bottom with kills done, the percentage and your goal",
		section = overlaySection,
		position = 4
	)
	default boolean overlayProgressBar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayOnlyOnTrip",
		name = "Only during a trip",
		description = "Only show the overlay while a trip is in progress (including paused or waiting just outside)."
			+ " Off shows it whenever you're logged in, with your last trip.",
		section = overlaySection,
		position = 5
	)
	default boolean overlayOnlyOnTrip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "diagnosticMode",
		name = "Diagnostic mode",
		description = "Append boss related game events (chat, clicks, inventory changes, loot) to diagnostic.log"
			+ " in this plugin's data folder. Leave off unless you are collecting data for development.",
		section = developerSection,
		position = 0
	)
	default boolean diagnosticMode()
	{
		return false;
	}

	@ConfigItem(
		keyName = "diagnosticLogEverywhere",
		name = "Log everywhere",
		description = "With diagnostic mode on, record everywhere instead of only around tracked bosses, to collect"
			+ " data for bosses that aren't supported yet. The log grows quickly; turn it off after the run.",
		section = developerSection,
		position = 1
	)
	default boolean diagnosticLogEverywhere()
	{
		return false;
	}

	@ConfigItem(
		keyName = "selectedBoss",
		name = "",
		description = "The boss shown in the side panel",
		hidden = true
	)
	default String selectedBoss()
	{
		return "";
	}

	@ConfigItem(
		keyName = "selectedBoss",
		name = "",
		description = "",
		hidden = true
	)
	void setSelectedBoss(String bossId);

	@ConfigItem(
		keyName = "sectionStates",
		name = "",
		description = "Which item boxes in the side panel are open, e.g. trip.loot=open,history.supplies=closed",
		hidden = true
	)
	default String sectionStates()
	{
		return "";
	}

	@ConfigItem(
		keyName = "sectionStates",
		name = "",
		description = "",
		hidden = true
	)
	void setSectionStates(String states);
}
