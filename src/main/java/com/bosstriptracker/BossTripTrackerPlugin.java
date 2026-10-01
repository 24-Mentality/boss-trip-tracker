package com.bosstriptracker;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.bosstriptracker.boss.BossRegistry;
import com.bosstriptracker.diagnostic.DiagnosticRecorder;
import com.bosstriptracker.model.AccountHistory;
import com.bosstriptracker.persistence.HistoryStore;
import com.bosstriptracker.pricing.PriceService;
import com.bosstriptracker.tracking.TripTracker;
import com.bosstriptracker.ui.PanelActions;
import com.bosstriptracker.ui.ShareCardExporter;
import com.bosstriptracker.ui.TrackerOverlay;
import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.ui.TrackerPanel;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Boss Trip Tracker",
	description = "Tracks loot, supplies and profit per boss trip (Maggot King, Phosani's Nightmare, Theatre of Blood) with per-account history",
	tags = {"maggot", "king", "vampyrium", "nightmare", "phosani", "tob", "theatre", "raids", "loot", "profit", "supplies", "trip", "boss", "tracker"},
	internalName = "boss-trip-tracker"
)
public class BossTripTrackerPlugin extends Plugin
{
	private static final DateTimeFormatter EXPORT_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Gson gson;

	@Inject
	private Notifier notifier;

	@Inject
	private BossTripTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ImageCapture imageCapture;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OverlayManager overlayManager;

	private DiagnosticRecorder diagnosticRecorder;
	private ScheduledExecutorService executor;
	private HistoryStore store;
	private TripTracker tripTracker;
	private TrackerPanel panel;
	private NavigationButton navigationButton;
	private ShareCardExporter shareCardExporter;
	private TrackerOverlay overlay;
	/**
	 * The latest panel state, for the overlays. Written and read on the client thread.
	 */
	private volatile PanelState latestState;

	@Override
	protected void startUp() throws Exception
	{
		migrateSettings();
		migrateOverlaySettings();
		BossRegistry registry = BossRegistry.standard();
		DiagnosticRecorder recorder = new DiagnosticRecorder(client, itemManager, registry, configManager,
			this::getPluginDirectory);
		diagnosticRecorder = recorder;
		eventBus.register(recorder);
		boolean diagnosticMode = config.diagnosticMode();
		boolean everywhere = config.diagnosticLogEverywhere();
		clientThread.invokeLater(() ->
		{
			recorder.setLogEverywhere(everywhere);
			recorder.setEnabled(diagnosticMode);
		});

		executor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread thread = new Thread(r, "boss-trip-tracker-io");
			thread.setDaemon(true);
			return thread;
		});

		TrackerPanel trackerPanel = new TrackerPanel(itemManager, new Actions(), registry.first());
		panel = trackerPanel;

		store = new HistoryStore(gson, this::getPluginDirectory, executor);
		shareCardExporter = new ShareCardExporter(client, itemManager, imageCapture, chatMessageManager, executor);
		TripTracker tracker = new TripTracker(client, clientThread, config, new PriceService(itemManager), store,
			gson, executor, state ->
			{
				latestState = state;
				SwingUtilities.invokeLater(() -> trackerPanel.update(state));
			},
			message -> notifier.notify(config.alertNotification(), message),
			this::lairEntered, configManager, registry);
		tripTracker = tracker;
		eventBus.register(tracker);
		clientThread.invokeLater(tracker::start);

		// Item icons load in the background and fill in once ready; ItemManager caches them
		overlay = new TrackerOverlay(this, config, () -> latestState, itemManager::getImage);
		overlayManager.add(overlay);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navigationButton = NavigationButton.builder()
			.tooltip("Boss Trip Tracker")
			.icon(icon)
			.priority(7)
			.panel(trackerPanel)
			.build();
		clientToolbar.addNavigation(navigationButton);

		log.debug("Boss Trip Tracker started");
	}

	/**
	 * Copies settings saved under the plugin's former config group (Maggot King Trip Tracker) to the current one,
	 * once, without overwriting anything already set here.
	 */
	private void migrateSettings()
	{
		String group = BossTripTrackerConfig.GROUP;
		if (configManager.getConfiguration(group, "settingsMigrated") != null)
		{
			return;
		}
		String legacy = BossTripTrackerConfig.LEGACY_GROUP;
		int copied = 0;
		for (String fullKey : configManager.getConfigurationKeys(legacy + "."))
		{
			String key = fullKey.substring(legacy.length() + 1);
			String value = configManager.getConfiguration(legacy, key);
			if (value != null && configManager.getConfiguration(group, key) == null)
			{
				configManager.setConfiguration(group, key, value);
				copied++;
			}
		}
		configManager.setConfiguration(group, "settingsMigrated", "true");
		if (copied > 0)
		{
			log.info("Copied {} settings from the {} config group", copied, legacy);
		}
	}

	/**
	 * Converts the overlay settings from before the three-row layout (a Show toggle and a stat per goal, trip and
	 * profit row), once: the rows that were shown keep their stats, in the same order.
	 */
	private void migrateOverlaySettings()
	{
		String group = BossTripTrackerConfig.GROUP;
		String[][] oldRows = {{"overlayShowGoal", "overlayGoalRow"}, {"overlayShowTrip", "overlayTripRow"}, {"overlayShowLoot", "overlayLootRow"}};
		boolean any = false;
		List<OverlayStat> shown = new ArrayList<>();
		for (String[] oldRow : oldRows)
		{
			String show = configManager.getConfiguration(group, oldRow[0]);
			String stat = configManager.getConfiguration(group, oldRow[1]);
			any |= show != null || stat != null;
			if (Boolean.parseBoolean(show))
			{
				try
				{
					shown.add(stat == null ? OverlayStat.valueOf(defaultOldStat(oldRow[1])) : OverlayStat.valueOf(stat));
				}
				catch (IllegalArgumentException e)
				{
					log.warn("Unknown overlay stat {} for {}", stat, oldRow[1]);
				}
			}
		}
		if (!any)
		{
			return;
		}
		if (!shown.isEmpty())
		{
			saveOverlayRows(new OverlayRows(true, shown.get(0),
				shown.size() > 1 ? OverlayOptionalStat.of(shown.get(1)) : OverlayOptionalStat.NOTHING,
				shown.size() > 2 ? OverlayOptionalStat.of(shown.get(2)) : OverlayOptionalStat.NOTHING));
		}
		for (String[] oldRow : oldRows)
		{
			configManager.unsetConfiguration(group, oldRow[0]);
			configManager.unsetConfiguration(group, oldRow[1]);
		}
		log.info("Converted the overlay settings to rows: {}", shown);
	}

	private static String defaultOldStat(String oldKey)
	{
		switch (oldKey)
		{
			case "overlayGoalRow":
				return "KILLS_PER_HOUR";
			case "overlayTripRow":
				return "CURRENT_KILL";
			default:
				return "NET_PROFIT";
		}
	}

	private void saveOverlayRows(OverlayRows rows)
	{
		String group = BossTripTrackerConfig.GROUP;
		configManager.setConfiguration(group, "overlayEnabled", rows.isEnabled());
		configManager.setConfiguration(group, "overlayRow1", rows.getRow1());
		configManager.setConfiguration(group, "overlayRow2", rows.getRow2());
		configManager.setConfiguration(group, "overlayRow3", rows.getRow3());
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		overlay = null;
		latestState = null;

		eventBus.unregister(tripTracker);
		tripTracker.shutDown();
		tripTracker = null;

		eventBus.unregister(diagnosticRecorder);
		diagnosticRecorder.shutDown();
		diagnosticRecorder = null;

		executor.shutdownNow();
		executor = null;
		store = null;
		shareCardExporter = null;

		clientToolbar.removeNavigation(navigationButton);
		navigationButton = null;
		panel.shutDown();
		panel = null;

		log.debug("Boss Trip Tracker stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (TripTracker.isAllTimeRecordGroup(event.getGroup()) && event.getKey() != null)
		{
			// The core plugins save from their own threads; the tracker lives on the client thread
			TripTracker tracker = tripTracker;
			String group = event.getGroup();
			String key = event.getKey();
			if (tracker != null)
			{
				clientThread.invokeLater(() -> tracker.allTimeRecordsChanged(group, key));
			}
			return;
		}
		if (!BossTripTrackerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("diagnosticMode".equals(event.getKey()))
		{
			boolean diagnosticMode = config.diagnosticMode();
			DiagnosticRecorder recorder = diagnosticRecorder;
			clientThread.invokeLater(() -> recorder.setEnabled(diagnosticMode));
		}
		else if ("diagnosticLogEverywhere".equals(event.getKey()))
		{
			boolean everywhere = config.diagnosticLogEverywhere();
			DiagnosticRecorder recorder = diagnosticRecorder;
			clientThread.invokeLater(() -> recorder.setLogEverywhere(everywhere));
		}
		else if ("showCurrentValue".equals(event.getKey()) || "luckCardStyle".equals(event.getKey()))
		{
			TripTracker tracker = tripTracker;
			clientThread.invokeLater(tracker::refreshView);
		}
	}

	/**
	 * Called on the client thread when the player enters a tracked boss's area.
	 */
	private void lairEntered()
	{
		if (!config.openPanelOnEntry())
		{
			return;
		}
		NavigationButton button = navigationButton;
		TrackerPanel trackerPanel = panel;
		SwingUtilities.invokeLater(() ->
		{
			if (button != null && trackerPanel != null)
			{
				clientToolbar.openPanel(button);
				trackerPanel.showTripTab();
			}
		});
	}

	/**
	 * Panel buttons. Runs on the Swing thread; file dialogs open here, IO runs on the executor and
	 * history access on the client thread.
	 */
	private class Actions implements PanelActions
	{
		@Override
		public void deleteTrip(String tripId)
		{
			TripTracker tracker = tripTracker;
			clientThread.invokeLater(() -> tracker.deleteTrip(tripId));
		}

		@Override
		public void clearHistory()
		{
			TripTracker tracker = tripTracker;
			clientThread.invokeLater(tracker::clearHistory);
		}

		@Override
		public void setGoal(int target, Long countFrom)
		{
			TripTracker tracker = tripTracker;
			clientThread.invokeLater(() -> tracker.setGoal(target, countFrom));
		}

		@Override
		public void restartGoalFrom(long countFrom)
		{
			TripTracker tracker = tripTracker;
			clientThread.invokeLater(() -> tracker.restartGoalFrom(countFrom));
		}

		@Override
		public void togglePause()
		{
			TripTracker tracker = tripTracker;
			clientThread.invokeLater(tracker::togglePause);
		}

		@Override
		public void selectBoss(String bossId)
		{
			TripTracker tracker = tripTracker;
			clientThread.invokeLater(() -> tracker.selectBoss(bossId));
		}

		@Override
		public void selectVariant(String variant)
		{
			TripTracker tracker = tripTracker;
			clientThread.invokeLater(() -> tracker.selectVariant(variant));
		}

		@Override
		public void setLastUniqueKc(Integer killCount)
		{
			TripTracker tracker = tripTracker;
			clientThread.invokeLater(() -> tracker.setLastUniqueKc(killCount));
		}

		@Override
		public boolean isOnCanvas(CanvasSection section)
		{
			return OverlayRows.of(config).shows(section);
		}

		@Override
		public void toggleCanvas(CanvasSection section)
		{
			OverlayRows rows = OverlayRows.of(config);
			saveOverlayRows(rows.shows(section) ? rows.without(section) : rows.with(section));
		}

		@Override
		public void shareCard()
		{
			TrackerPanel trackerPanel = panel;
			shareCardExporter.share(trackerPanel.getState(), config.shareShowName(),
				message -> trackerPanel.showMessage("Share card", message, false));
		}

		@Override
		public void exportCsv()
		{
			// Only the shown boss's trips
			export("Export trips", panel.getBoss().getFileSlug() + "-trips-" + fileStamp() + ".csv", "CSV files", "csv",
				TripTracker::exportCsv);
		}

		@Override
		public void exportJson()
		{
			// Every boss on this account
			export("Export history", "boss-trip-tracker-history-" + fileStamp() + ".json", "JSON files", "json",
				TripTracker::exportJson);
		}

		private String fileStamp()
		{
			return EXPORT_STAMP.format(LocalDateTime.now());
		}

		@Override
		public void importJson()
		{
			List<Filepath> chosen = new Filepath.Chooser()
				.setIsOpen()
				.setAcceptsFiles()
				.setDialogTitle("Import history")
				.addExtensionFilter("JSON files", "json")
				.showDialog(panel);
			// Null when the dialog is cancelled
			if (chosen == null || chosen.isEmpty())
			{
				return;
			}

			TripTracker tracker = tripTracker;
			TrackerPanel trackerPanel = panel;
			store.readHistoryFile(chosen.get(0), (decoded, error) ->
			{
				if (error != null)
				{
					SwingUtilities.invokeLater(() -> trackerPanel.showMessage("Import history",
						"That file couldn't be read as a Boss Trip Tracker export.", true));
					return;
				}
				AccountHistory imported = decoded.getHistory();
				clientThread.invokeLater(() ->
				{
					String description = tracker.describeImport(imported);
					SwingUtilities.invokeLater(() ->
					{
						if (description.startsWith("!"))
						{
							trackerPanel.showMessage("Import history", description.substring(1), true);
						}
						else if (trackerPanel.confirm("Import history", description))
						{
							clientThread.invokeLater(() -> tracker.importHistory(imported, decoded.getSourceVersion()));
						}
					});
				});
			});
		}

		private void export(String title, String fileName, String filterName, String extension,
			Function<TripTracker, String> content)
		{
			List<Filepath> chosen = new Filepath.Chooser()
				.setIsSave()
				.setDialogTitle(title)
				.setFileName(fileName)
				.addExtensionFilter(filterName, extension)
				.setDefaultExtension(extension)
				.showDialog(panel);
			// Null when the dialog is cancelled
			if (chosen == null || chosen.isEmpty())
			{
				return;
			}

			Filepath file = chosen.get(0);
			TripTracker tracker = tripTracker;
			TrackerPanel trackerPanel = panel;
			HistoryStore historyStore = store;
			clientThread.invokeLater(() ->
			{
				String data = content.apply(tracker);
				if (data == null)
				{
					SwingUtilities.invokeLater(() -> trackerPanel.showMessage(title, "Log in first.", true));
					return;
				}
				historyStore.writeFile(file, data, error -> SwingUtilities.invokeLater(() -> trackerPanel.showMessage(title,
					error == null ? "Saved to " + file.getFileName() : "Couldn't save the file: " + error.getMessage(),
					error != null)));
			});
		}
	}

	@Provides
	BossTripTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BossTripTrackerConfig.class);
	}
}
