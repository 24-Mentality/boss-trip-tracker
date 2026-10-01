package com.bosstriptracker.diagnostic;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.boss.BossRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Nameable;
import net.runelite.api.NameableContainer;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.TileItem;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Filepath;

/**
 * Records raw game events around the tracked bosses to diagnostic.log so detection logic can be built
 * from real data. Only listens to events; it never draws anything or creates input.
 * <p>
 * Recording is active while diagnostic mode is enabled and the player is in a boss's area or the region just
 * outside it, or for {@link #CLICK_WINDOW_TICKS} ticks after clicking a loot source (e.g. the Maggot King's corpse),
 * an egg, a tarnished item, or an NPC that handles death recovery, and for {@link #DEATH_WINDOW_TICKS} ticks after
 * dying in a boss's area. With "log everywhere" on, it records everywhere, to find the regions, NPCs and messages of
 * bosses that aren't tracked yet. When enabled, and at each login, it also lists the saved Loot Tracker and Chat
 * Commands record keys of the bosses planned next.
 * <p>
 * Other players' names never reach the file: names seen nearby, in chat, on menu entries, and in the friends list,
 * friends chat, clan channel and Theatre of Blood party are passed to the log writer, which replaces them with
 * PLAYER1, PLAYER2 and so on. Only built in developer mode.
 */
public class DiagnosticRecorder
{
	private static final int CLICK_WINDOW_TICKS = 10;
	/**
	 * After dying in the lair, keep recording while the player respawns and recovers their gravestone.
	 */
	private static final int DEATH_WINDOW_TICKS = 200;
	/**
	 * How often the friends, friends chat, clan and party lists are read for names to replace.
	 */
	private static final int NAME_LIST_TICKS = 10;
	private static final int[] TOB_PARTY_NAMES = {VarClientID.TOB_CLIENT_NAME0, VarClientID.TOB_CLIENT_NAME1,
		VarClientID.TOB_CLIENT_NAME2, VarClientID.TOB_CLIENT_NAME3, VarClientID.TOB_CLIENT_NAME4};

	private static final Set<Integer> RUNE_POUCH_VARBITS = ImmutableSet.of(
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6,
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	);

	private static final Set<Integer> AUTOCAST_VARBITS = ImmutableSet.of(
		VarbitID.AUTOCAST_SET, VarbitID.AUTOCAST_SPELL
	);

	private final Client client;
	private final ItemManager itemManager;
	private final BossRegistry registry;
	private final Set<Integer> waitingRegions = new HashSet<>();
	private final Set<Integer> triggerNpcs = new HashSet<>();
	private final Set<Integer> triggerItems = new HashSet<>();
	private final Set<Integer> encounterNpcs = new HashSet<>();
	private final Set<Integer> bossNpcs = new HashSet<>();
	private final DiagnosticLogWriter writer;

	/**
	 * NPCs whose name contains one of these are logged everywhere while logging everywhere: the Nightmare,
	 * her totems, and the NPCs that return items after a death.
	 */
	private static final String[] EVERYWHERE_NPC_NAMES = {"Nightmare", "Totem", "Sister Senga", "Shura",
		// Theatre of Blood room bosses
		"Maiden of Sugadinti", "Pestilent Bloat", "Nylocas Vasilias", "Sotetseg", "Xarpus", "Verzik Vitur"};

	/**
	 * What players type or send (public, private, clan and friends chat, trade requests) isn't needed and carries
	 * other players' names and words, so it's never logged. Game messages still are.
	 */
	static final Set<ChatMessageType> PLAYER_CHAT = Sets.immutableEnumSet(ChatMessageType.PUBLICCHAT,
		ChatMessageType.MODCHAT, ChatMessageType.PRIVATECHAT, ChatMessageType.PRIVATECHATOUT,
		ChatMessageType.MODPRIVATECHAT, ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_CHAT,
		ChatMessageType.CLAN_MESSAGE, ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.CLAN_GUEST_MESSAGE,
		ChatMessageType.CLAN_GIM_CHAT, ChatMessageType.CLAN_GIM_MESSAGE, ChatMessageType.AUTOTYPER,
		ChatMessageType.MODAUTOTYPER, ChatMessageType.TRADEREQ, ChatMessageType.TRADE, ChatMessageType.TRADE_SENT,
		ChatMessageType.CHALREQ_TRADE, ChatMessageType.CHALREQ_FRIENDSCHAT, ChatMessageType.CHALREQ_CLANCHAT,
		ChatMessageType.LOGINLOGOUTNOTIFICATION, ChatMessageType.FRIENDNOTIFICATION);

	/**
	 * Theatre of Blood state, for learning how raids, teams and loot look: party status and slots (team size),
	 * supply chest points, the vault chests, and whether loot is due. Logged by name when they change.
	 */
	static final Map<Integer, String> TOB_VARBITS = ImmutableMap.<Integer, String>builder()
		.put(VarbitID.TOB_CLIENT_PARTYSTATUS, "partystatus")
		.put(VarbitID.TOB_CLIENT_PARTYSLOT, "partyslot")
		.put(VarbitID.TOB_CLIENT_P0, "p0")
		.put(VarbitID.TOB_CLIENT_P1, "p1")
		.put(VarbitID.TOB_CLIENT_P2, "p2")
		.put(VarbitID.TOB_CLIENT_P3, "p3")
		.put(VarbitID.TOB_CLIENT_P4, "p4")
		.put(VarbitID.TOB_MIDWAYCHEST_POINTS, "supplychest_points")
		.put(VarbitID.TOB_TREASUREROOM_CHEST_0, "vaultchest0")
		.put(VarbitID.TOB_TREASUREROOM_CHEST_1, "vaultchest1")
		.put(VarbitID.TOB_TREASUREROOM_CHEST_2, "vaultchest2")
		.put(VarbitID.TOB_TREASUREROOM_CHEST_3, "vaultchest3")
		.put(VarbitID.TOB_TREASUREROOM_CHEST_4, "vaultchest4")
		.put(VarbitID.TOB_SHOULD_HAVE_LOOT, "should_have_loot")
		.put(VarbitID.TOB_DAMAGE_TAKEN, "damage_taken")
		.put(VarbitID.TOB_PROGRESS, "progress")
		.build();
	private static final String LOOT_TRACKER_GROUP = "loottracker";
	private static final String KILL_COUNT_GROUP = "killcount";

	private final ConfigManager configManager;
	private boolean enabled;
	private boolean everywhere;
	private boolean keysPending = true;
	private boolean inLair;
	private boolean nearLair;
	private int templateRegionId = -1;
	private int clickWindowEndTick = -1;
	private final Map<Integer, Map<Integer, Integer>> containerSnapshots = new HashMap<>();
	/**
	 * Names already passed to the writer, normalised.
	 */
	private final Set<String> learnedNames = new HashSet<>();
	/**
	 * Names seen before the local player's own name is known, so it isn't replaced by mistake.
	 */
	private final Set<String> deferredNames = new LinkedHashSet<>();

	public DiagnosticRecorder(Client client, ItemManager itemManager, BossRegistry registry, ConfigManager configManager,
		Callable<Filepath> directorySupplier)
	{
		this.client = client;
		this.configManager = configManager;
		this.itemManager = itemManager;
		this.registry = registry;
		for (BossDefinition boss : registry.all())
		{
			waitingRegions.addAll(boss.getWaitingRegions());
			triggerNpcs.addAll(boss.getLootTriggerNpcs());
			triggerNpcs.addAll(boss.getGraveHelperNpcs());
			triggerItems.addAll(boss.getEggPetRates().keySet());
			triggerItems.addAll(boss.getTarnishedItems());
			bossNpcs.addAll(boss.getBossNpcIds());
			encounterNpcs.addAll(boss.getBossNpcIds());
			encounterNpcs.addAll(boss.getLootTriggerNpcs());
		}
		this.writer = new DiagnosticLogWriter(directorySupplier);
	}

	public void shutDown()
	{
		if (enabled)
		{
			record("SESSION", "diagnostic recording stopped (plugin shut down)");
		}
		writer.shutDown();
	}

	public void setEnabled(boolean enabled)
	{
		if (this.enabled == enabled)
		{
			return;
		}

		if (!enabled)
		{
			record("SESSION", "diagnostic mode disabled");
		}
		this.enabled = enabled;
		if (enabled)
		{
			record("SESSION", "diagnostic mode enabled; boss template regions " + registry.allRegions()
				+ (everywhere ? "; logging everywhere" : ""));
			if (inLair || everywhere)
			{
				recordContainerSnapshots();
			}
			if (everywhere && client.getGameState() == GameState.LOGGED_IN)
			{
				recordTobSnapshot();
			}
			recordRecordKeys();
		}
	}

	/**
	 * Record everywhere, not only around tracked bosses. Only has an effect while diagnostic mode is on.
	 */
	public void setLogEverywhere(boolean everywhere)
	{
		if (this.everywhere == everywhere)
		{
			return;
		}
		this.everywhere = everywhere;
		if (enabled)
		{
			record("SESSION", everywhere ? "logging everywhere" : "logging only around tracked bosses");
			if (everywhere && client.getGameState() == GameState.LOGGED_IN)
			{
				recordContainerSnapshots();
				recordTobSnapshot();
			}
		}
	}

	/**
	 * The logged-in account's saved records for the bosses planned next, with their kill counts, so their exact
	 * keys are known before they are implemented.
	 */
	private void recordRecordKeys()
	{
		if (!enabled || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		String profile = configManager.getRSProfileKey();
		if (profile == null)
		{
			return;
		}
		int found = 0;
		for (String key : configManager.getRSProfileConfigurationKeys(LOOT_TRACKER_GROUP, profile, "drops_"))
		{
			if (RecordKeys.isPlannedBoss(key))
			{
				Integer kills = RecordKeys.lootTrackerKills(configManager.getRSProfileConfiguration(LOOT_TRACKER_GROUP, key));
				record("RECORDKEY", "group=" + LOOT_TRACKER_GROUP + " key=\"" + key + "\" kills=" + kills);
				found++;
			}
		}
		for (String key : configManager.getRSProfileConfigurationKeys(KILL_COUNT_GROUP, profile, ""))
		{
			if (RecordKeys.isPlannedBoss(key))
			{
				record("RECORDKEY", "group=" + KILL_COUNT_GROUP + " key=\"" + key + "\" value="
					+ configManager.getRSProfileConfiguration(KILL_COUNT_GROUP, key));
				found++;
			}
		}
		record("RECORDKEY", found + " record keys found for the Nightmare, Nex and Theatre of Blood");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}

		if (enabled)
		{
			learnNames(player);
		}

		LocalPoint localPoint = player.getLocalLocation();
		int region = WorldPoint.fromLocalInstance(client, localPoint).getRegionID();
		if (region != templateRegionId)
		{
			int previous = templateRegionId;
			templateRegionId = region;
			if (isRecording() || registry.forRegion(region) != null)
			{
				record("REGION", "template region " + previous + " -> " + region
					+ " instance=" + client.getTopLevelWorldView().isInstance()
					+ " actual region=" + WorldPoint.fromLocal(client, localPoint).getRegionID());
			}
		}

		boolean nowNearLair = waitingRegions.contains(region);
		if (nowNearLair != nearLair)
		{
			nearLair = nowNearLair;
			record("ENTRANCE", nearLair ? "entered region outside the lair" : "left region outside the lair");
		}

		boolean nowInLair = registry.forRegion(region) != null;
		if (nowInLair != inLair)
		{
			inLair = nowInLair;
			record("LAIR", inLair ? "entered" : "left");
			if (inLair)
			{
				recordContainerSnapshots();
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (isRecording())
		{
			record("GAMESTATE", state.name());
		}
		// The game reports LOGGED_IN again after every loading screen; list the keys once per login
		if (state == GameState.LOGGED_IN && keysPending)
		{
			keysPending = false;
			recordRecordKeys();
			if (enabled && everywhere)
			{
				// Turned on before logging in: note what's carried so later changes can be followed
				recordContainerSnapshots();
				recordTobSnapshot();
			}
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			keysPending = true;
		}

		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			if (inLair)
			{
				record("LAIR", "left (" + state.name() + ")");
			}
			inLair = false;
			nearLair = false;
			templateRegionId = -1;
			clickWindowEndTick = -1;
			containerSnapshots.clear();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		NPC npc = entry.getNpc();
		int itemId = event.getItemId();

		if (enabled && entry.getPlayer() != null)
		{
			learnName(entry.getPlayer().getName());
		}

		boolean trigger = (npc != null && triggerNpcs.contains(npc.getId())) || triggerItems.contains(itemId);
		if (trigger)
		{
			clickWindowEndTick = client.getTickCount() + CLICK_WINDOW_TICKS;
		}

		if (!isRecording())
		{
			return;
		}

		StringBuilder sb = new StringBuilder()
			.append("option=\"").append(event.getMenuOption()).append('"')
			.append(" target=\"").append(event.getMenuTarget()).append('"')
			.append(" action=").append(event.getMenuAction())
			.append(" id=").append(event.getId())
			.append(" itemId=").append(itemId)
			.append(" itemOp=").append(event.getItemOp())
			.append(" param0=").append(event.getParam0())
			.append(" param1=").append(event.getParam1());
		if (npc != null)
		{
			sb.append(" npcId=").append(npc.getId()).append(" npcName=\"").append(npc.getName()).append('"');
		}
		if (trigger)
		{
			sb.append(" [opens ").append(CLICK_WINDOW_TICKS).append("-tick window]");
		}
		record("MENU", sb.toString());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!isRecording() || PLAYER_CHAT.contains(event.getType()))
		{
			return;
		}

		learnName(event.getName());
		learnName(event.getSender());
		record("CHAT", "type=" + event.getType()
			+ " name=\"" + event.getName() + '"'
			+ " sender=\"" + event.getSender() + '"'
			+ " message=\"" + event.getMessage() + '"');

	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (containerId != InventoryID.INV && containerId != InventoryID.WORN)
		{
			return;
		}

		Map<Integer, Integer> current = toQuantities(event.getItemContainer());
		Map<Integer, Integer> previous = containerSnapshots.put(containerId, current);
		if (!isRecording() || previous == null)
		{
			return;
		}

		String diff = describeDiff(previous, current);
		if (!diff.isEmpty())
		{
			record(containerName(containerId), diff);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!isRecording())
		{
			return;
		}

		if (RUNE_POUCH_VARBITS.contains(event.getVarbitId()))
		{
			record("RUNEPOUCH", "varbit=" + event.getVarbitId() + " value=" + event.getValue());
			return;
		}
		if (AUTOCAST_VARBITS.contains(event.getVarbitId()))
		{
			record("AUTOCAST", "varbit=" + event.getVarbitId() + " value=" + event.getValue());
			return;
		}
		if (event.getVarpId() == VarPlayerID.SA_ENERGY)
		{
			record("SPEC", "energy=" + event.getValue());
			return;
		}
		String tob = TOB_VARBITS.get(event.getVarbitId());
		if (tob != null)
		{
			record("TOB", tob + "=" + event.getValue());
		}

	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		recordGroundItem("spawned", event.getItem(), event.getTile().getLocalLocation());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		recordGroundItem("despawned", event.getItem(), event.getTile().getLocalLocation());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		recordEncounterNpc("spawned", event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		recordEncounterNpc("despawned", event.getNpc());
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!isRecording())
		{
			return;
		}

		Actor actor = event.getActor();
		if (actor == client.getLocalPlayer())
		{
			record("DEATH", "local player died; recording for " + DEATH_WINDOW_TICKS + " ticks");
			clickWindowEndTick = Math.max(clickWindowEndTick, client.getTickCount() + DEATH_WINDOW_TICKS);
		}
		else if (actor instanceof NPC && bossNpcs.contains(((NPC) actor).getId()))
		{
			record("DEATH", "boss died");
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (!isRecording() || actor != client.getLocalPlayer() || actor.getAnimation() == -1)
		{
			return;
		}

		Actor target = actor.getInteracting();
		record("ANIM", "id=" + actor.getAnimation()
			+ " target=" + describeActor(target)
			+ ' ' + describeGear()
			+ " autocast=" + client.getVarbitValue(VarbitID.AUTOCAST_SPELL)
			+ " spec=" + client.getVarpValue(VarPlayerID.SA_ENERGY));
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Hitsplat hitsplat = event.getHitsplat();
		if (!isRecording() || !hitsplat.isMine() || event.getActor() == client.getLocalPlayer())
		{
			return;
		}

		record("HITSPLAT", "type=" + hitsplat.getHitsplatType()
			+ " amount=" + hitsplat.getAmount()
			+ " target=" + describeActor(event.getActor())
			+ ' ' + describeGear());
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();
		if (!isRecording() || actor != client.getLocalPlayer())
		{
			return;
		}

		StringBuilder sb = new StringBuilder("spotanims=[");
		boolean first = true;
		for (ActorSpotAnim spotAnim : actor.getSpotAnims())
		{
			if (!first)
			{
				sb.append(", ");
			}
			first = false;
			sb.append(spotAnim.getId());
		}
		record("GRAPHIC", sb.append(']').toString());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (!isRecording())
		{
			return;
		}

		StringBuilder sb = new StringBuilder()
			.append("name=\"").append(event.getName()).append('"')
			.append(" type=").append(event.getType())
			.append(" combatLevel=").append(event.getCombatLevel())
			.append(" amount=").append(event.getAmount())
			.append(" items=[");
		boolean first = true;
		for (ItemStack stack : event.getItems())
		{
			if (!first)
			{
				sb.append(", ");
			}
			first = false;
			sb.append(describeItem(stack.getId(), stack.getQuantity()));
		}
		sb.append(']');
		record("LOOT", sb.toString());
	}

	private String describeGear()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		return "weapon=" + wornId(worn, EquipmentInventorySlot.WEAPON)
			+ " shield=" + wornId(worn, EquipmentInventorySlot.SHIELD)
			+ " amulet=" + wornId(worn, EquipmentInventorySlot.AMULET);
	}

	private static int wornId(ItemContainer worn, EquipmentInventorySlot slot)
	{
		if (worn == null)
		{
			return -1;
		}
		Item item = worn.getItem(slot.getSlotIdx());
		return item == null ? -1 : item.getId();
	}

	private static String describeActor(Actor actor)
	{
		if (actor == null)
		{
			return "none";
		}
		if (actor instanceof NPC)
		{
			return "npc:" + ((NPC) actor).getId();
		}
		return "player";
	}

	private void recordGroundItem(String what, TileItem item, LocalPoint localPoint)
	{
		if (!enabled || !(inLair || nearLair || everywhere))
		{
			return;
		}

		WorldPoint location = WorldPoint.fromLocalInstance(client, localPoint);
		record("GROUND", what + " " + describeItem(item.getId(), item.getQuantity())
			+ " ownership=" + item.getOwnership()
			+ " at=" + location.getX() + "," + location.getY() + "," + location.getPlane());
	}

	private void recordEncounterNpc(String what, NPC npc)
	{
		int id = npc.getId();
		if (isRecording() && (encounterNpcs.contains(id) || (everywhere && hasEverywhereName(npc))))
		{
			record("NPC", what + " id=" + id + " name=\"" + npc.getName() + "\" index=" + npc.getIndex());
		}
	}

	/**
	 * The Theatre of Blood varbits as they are now, so later changes have a starting point.
	 */
	private void recordTobSnapshot()
	{
		StringBuilder sb = new StringBuilder("snapshot");
		for (Map.Entry<Integer, String> e : TOB_VARBITS.entrySet())
		{
			sb.append(' ').append(e.getValue()).append('=').append(client.getVarbitValue(e.getKey()));
		}
		record("TOB", sb.toString());
	}

	private boolean isRecording()
	{
		return enabled && (everywhere || inLair || nearLair || client.getTickCount() <= clickWindowEndTick);
	}

	private static boolean hasEverywhereName(NPC npc)
	{
		String name = npc.getName();
		if (name == null)
		{
			return false;
		}
		for (String part : EVERYWHERE_NPC_NAMES)
		{
			if (name.contains(part))
			{
				return true;
			}
		}
		return false;
	}

	private void recordContainerSnapshots()
	{
		if (!enabled)
		{
			return;
		}

		for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(containerId);
			Map<Integer, Integer> quantities = toQuantities(container);
			containerSnapshots.put(containerId, quantities);
			StringBuilder sb = new StringBuilder("snapshot [");
			boolean first = true;
			for (Map.Entry<Integer, Integer> e : quantities.entrySet())
			{
				if (!first)
				{
					sb.append(", ");
				}
				first = false;
				sb.append(describeItem(e.getKey(), e.getValue()));
			}
			record(containerName(containerId), sb.append(']').toString());
		}
	}

	private void record(String category, String details)
	{
		if (!enabled)
		{
			return;
		}

		String prefix = "tick=" + client.getTickCount()
			+ " region=" + templateRegionId
			+ " inLair=" + inLair
			+ ' ' + category;
		writer.append(prefix, details.replace("\r", "\\r").replace("\n", "\\n"));
	}

	/**
	 * Passes the names of players nearby to the writer every tick, and those in the friends list, friends chat, clan
	 * channel and Theatre of Blood party every few ticks.
	 */
	private void learnNames(Player localPlayer)
	{
		if (localPlayer.getName() != null && !deferredNames.isEmpty())
		{
			List<String> deferred = new ArrayList<>(deferredNames);
			deferredNames.clear();
			deferred.forEach(this::learnName);
		}

		for (Player player : client.getTopLevelWorldView().players())
		{
			learnName(player.getName());
		}

		if (client.getTickCount() % NAME_LIST_TICKS != 0)
		{
			return;
		}
		learnNames(client.getFriendContainer());
		FriendsChatManager friendsChat = client.getFriendsChatManager();
		learnNames(friendsChat);
		if (friendsChat != null)
		{
			learnName(friendsChat.getOwner());
		}
		for (ClanChannel channel : new ClanChannel[]{client.getClanChannel(), client.getGuestClanChannel()})
		{
			if (channel != null)
			{
				for (ClanChannelMember member : channel.getMembers())
				{
					learnName(member.getName());
				}
			}
		}
		for (int varc : TOB_PARTY_NAMES)
		{
			learnName(client.getVarcStrValue(varc));
		}
	}

	private void learnNames(NameableContainer<? extends Nameable> container)
	{
		if (container == null)
		{
			return;
		}
		for (Nameable member : container.getMembers())
		{
			if (member != null)
			{
				learnName(member.getName());
			}
		}
	}

	private void learnName(String name)
	{
		String key = PlayerNameScrubber.normalize(name);
		if (key.isEmpty() || learnedNames.contains(key))
		{
			return;
		}
		Player localPlayer = client.getLocalPlayer();
		String localName = localPlayer == null ? null : localPlayer.getName();
		if (localName == null)
		{
			deferredNames.add(name);
			return;
		}
		if (key.equals(PlayerNameScrubber.normalize(localName)))
		{
			return;
		}
		learnedNames.add(key);
		writer.learnName(name);
	}

	private String describeDiff(Map<Integer, Integer> previous, Map<Integer, Integer> current)
	{
		Set<Integer> ids = new TreeSet<>(previous.keySet());
		ids.addAll(current.keySet());

		StringBuilder sb = new StringBuilder();
		for (int id : ids)
		{
			int delta = current.getOrDefault(id, 0) - previous.getOrDefault(id, 0);
			if (delta == 0)
			{
				continue;
			}
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			sb.append(delta > 0 ? '+' : '-').append(describeItem(id, Math.abs(delta)));
		}
		return sb.toString();
	}

	private String describeItem(int itemId, int quantity)
	{
		return itemManager.getItemComposition(itemId).getName() + " (" + itemId + ") x" + quantity;
	}

	private static Map<Integer, Integer> toQuantities(ItemContainer container)
	{
		Map<Integer, Integer> quantities = new HashMap<>();
		if (container == null)
		{
			return quantities;
		}

		for (Item item : container.getItems())
		{
			if (item.getId() >= 0)
			{
				quantities.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return quantities;
	}

	private static String containerName(int containerId)
	{
		return containerId == InventoryID.INV ? "INVENTORY" : "EQUIPMENT";
	}
}
