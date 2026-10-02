package com.bosstriptracker.persistence;

import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.bosstriptracker.model.AccountHistory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Reads and writes one history-&lt;accountHash&gt; folder per account in the plugin data folder ({@link HistoryLayout}),
 * moving older single-file histories into it.
 * All disk IO runs on the supplied executor; callbacks are invoked on that executor thread.
 */
@Slf4j
public class HistoryStore
{
	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	static final int KEPT_BACKUPS = 3;

	private final Gson gson;
	private final Callable<Filepath> directorySupplier;
	private final ExecutorService executor;

	// Only touched on the executor thread
	private Filepath directory;

	public HistoryStore(Gson gson, Callable<Filepath> directorySupplier, ExecutorService executor)
	{
		this.gson = gson;
		this.directorySupplier = directorySupplier;
		this.executor = executor;
	}

	public void load(long accountHash, Consumer<LoadResult> callback)
	{
		submit(() -> callback.accept(readHistory(accountHash)));
	}

	/**
	 * Writes what changed: the months in the snapshot, the trips in progress and the account. The snapshot is turned
	 * into JSON here, on the IO thread.
	 *
	 * @param written runs once the write is done or has failed (at once if the plugin is shutting down)
	 */
	public void save(long accountHash, HistoryLayout.Snapshot snapshot, Runnable written)
	{
		if (!submit(() ->
		{
			try
			{
				HistoryLayout.write(gson, directory().joinSegment(folderName(accountHash)), snapshot);
			}
			catch (Exception e)
			{
				log.warn("Unable to save trip history", e);
			}
			finally
			{
				written.run();
			}
		}))
		{
			written.run();
		}
	}

	/**
	 * Writes a user-chosen export file. The callback gets null on success, or the error.
	 *
	 * @param content made here, on the IO thread (an export of the whole history is turned into JSON here)
	 */
	public void writeFile(Filepath file, Supplier<String> content, Consumer<Exception> callback)
	{
		submit(() ->
		{
			try
			{
				file.write(content.get().getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
				callback.accept(null);
			}
			catch (Exception e)
			{
				log.warn("Unable to write export", e);
				callback.accept(e);
			}
		});
	}

	/**
	 * Reads a user-chosen history export, checked and cleaned by {@link ImportSanitizer}.
	 *
	 * @param knownBossIds bosses this version tracks; others in the file are left out
	 */
	public void readHistoryFile(Filepath file, Set<String> knownBossIds, ImportCallback callback)
	{
		submit(() ->
		{
			try
			{
				if (file.size() > ImportSanitizer.MAX_FILE_BYTES)
				{
					callback.done(null, 0, "That file is over 50 MB, too large to be a Boss Trip Tracker export.");
					return;
				}
				String json = readString(file);
				HistoryCodec.Decoded decoded = ImportSanitizer.isHistory(gson.fromJson(json, JsonObject.class))
					? HistoryCodec.decode(gson, json) : null;
				if (decoded == null)
				{
					callback.done(null, 0, "That file isn't a Boss Trip Tracker export.");
					return;
				}
				int leftOut = ImportSanitizer.sanitize(decoded.getHistory(), knownBossIds);
				callback.done(decoded, leftOut, null);
			}
			catch (Exception e)
			{
				log.warn("Unable to read import", e);
				callback.done(null, 0, "That file couldn't be read as a Boss Trip Tracker export.");
			}
		});
	}

	public interface ImportCallback
	{
		/**
		 * @param decoded the cleaned history, or null if the file was rejected
		 * @param leftOut invalid or unknown entries left out of it
		 * @param error   why the file was rejected, for the panel; null if it was read
		 */
		void done(HistoryCodec.Decoded decoded, int leftOut, String error);
	}

	/**
	 * @return whether the task was queued (false while the plugin shuts down)
	 */
	private boolean submit(Runnable task)
	{
		try
		{
			executor.execute(task);
			return true;
		}
		catch (RejectedExecutionException e)
		{
			log.debug("Plugin shutting down; skipped history IO");
			return false;
		}
	}

	private LoadResult readHistory(long accountHash)
	{
		try
		{
			Filepath oldFile = directory().joinSegment(fileName(accountHash));
			Filepath folder = directory().joinSegment(folderName(accountHash));
			if (oldFile.exists())
			{
				// Until it's been moved to a backup, a single-file history is the one to use
				return migrate(accountHash, oldFile, folder);
			}
			if (HistoryLayout.exists(folder))
			{
				return readFolder(accountHash, folder, null);
			}
			return new LoadResult(emptyHistory(accountHash), false, AccountHistory.CURRENT_SCHEMA_VERSION, null);
		}
		catch (Exception e)
		{
			log.warn("Unable to load trip history", e);
			return new LoadResult(emptyHistory(accountHash), true, AccountHistory.CURRENT_SCHEMA_VERSION, null);
		}
	}

	private LoadResult readFolder(long accountHash, Filepath folder, String alsoUnreadable) throws IOException
	{
		HistoryLayout.Loaded loaded = HistoryLayout.read(gson, folder);
		AccountHistory history = loaded.getHistory();
		history.setAccountHash(accountHash);
		List<String> unreadable = new java.util.ArrayList<>(loaded.getUnreadable());
		if (alsoUnreadable != null)
		{
			unreadable.add(0, alsoUnreadable);
		}
		// A folder from a newer plugin version is shown but never written to
		boolean newer = history.getSchemaVersion() > AccountHistory.CURRENT_SCHEMA_VERSION;
		return new LoadResult(history, newer, history.getSchemaVersion(),
			unreadable.isEmpty() ? null : String.join(", ", unreadable));
	}

	/**
	 * Moves a single-file history (schema 5 and older) into the folder layout. The folder is read back and its trips
	 * checked against the file before the file is renamed to a backup; if anything fails, the file is left as it is
	 * and the history is shown read-only, to be tried again at the next login.
	 */
	private LoadResult migrate(long accountHash, Filepath oldFile, Filepath folder) throws Exception
	{
		HistoryCodec.Decoded decoded;
		try
		{
			decoded = HistoryCodec.decode(gson, readString(oldFile));
		}
		catch (JsonParseException e)
		{
			Filepath backup = directory().joinSegment(fileName(accountHash) + ".corrupt-" + System.currentTimeMillis());
			log.warn("Trip history for this account is unreadable; moving it to {}", backup.getFileName(), e);
			oldFile.moveTo(backup);
			return HistoryLayout.exists(folder) ? readFolder(accountHash, folder, backup.getFileName())
				: new LoadResult(emptyHistory(accountHash), false, AccountHistory.CURRENT_SCHEMA_VERSION, backup.getFileName());
		}
		if (decoded == null)
		{
			// An empty file
			oldFile.delete();
			return HistoryLayout.exists(folder) ? readFolder(accountHash, folder, null)
				: new LoadResult(emptyHistory(accountHash), false, AccountHistory.CURRENT_SCHEMA_VERSION, null);
		}

		AccountHistory history = decoded.getHistory();
		history.setAccountHash(accountHash);
		if (decoded.isNewer())
		{
			return new LoadResult(history, true, decoded.getSourceVersion(), null);
		}

		try
		{
			// What's there is from a migration that didn't finish (nothing is saved to it until the file is gone)
			if (folder.exists())
			{
				folder.deleteRecursively();
			}
			HistoryLayout.write(gson, folder, HistoryLayout.Snapshot.of(history));
			HistoryLayout.Loaded back = HistoryLayout.read(gson, folder);
			if (!back.getUnreadable().isEmpty() || !HistoryLayout.sameTrips(history, back.getHistory()))
			{
				log.warn("The new history folder didn't read back the same trips; keeping the old file (read-only this session)");
				return new LoadResult(history, true, decoded.getSourceVersion(), null);
			}
			Filepath backup = directory().joinSegment(fileName(accountHash) + ".v" + decoded.getSourceVersion()
				+ "-backup-" + BACKUP_STAMP.format(LocalDateTime.now()));
			oldFile.moveTo(backup);
			pruneBackups(accountHash);
			log.info("Moved trip history from schema {} to the folder layout (schema {}); the old file is kept as {}",
				decoded.getSourceVersion(), AccountHistory.CURRENT_SCHEMA_VERSION, backup.getFileName());
		}
		catch (Exception e)
		{
			log.warn("Unable to move trip history to the folder layout; keeping the old file (read-only this session)", e);
			return new LoadResult(history, true, decoded.getSourceVersion(), null);
		}
		return new LoadResult(history, false, decoded.getSourceVersion(), null);
	}

	/**
	 * Keeps the newest {@link #KEPT_BACKUPS} migration backups of this account's history (".vN-backup-"); other
	 * files, such as unreadable histories moved aside, are never touched.
	 */
	private void pruneBackups(long accountHash) throws Exception
	{
		List<Filepath> backups;
		try (Stream<Filepath> files = directory().walk(1))
		{
			backups = files.filter(f -> isMigrationBackup(fileName(accountHash), f.getFileName()))
				.collect(Collectors.toList());
		}
		for (String name : backupsToDelete(fileName(accountHash), backups.stream().map(Filepath::getFileName)
			.collect(Collectors.toList())))
		{
			directory().joinSegment(name).deleteIfExists();
			log.debug("Deleted old history backup {}", name);
		}
	}

	static boolean isMigrationBackup(String historyFile, String name)
	{
		return name.startsWith(historyFile + ".v") && name.contains("-backup-");
	}

	/**
	 * @return the migration backups beyond the newest {@link #KEPT_BACKUPS}, by the date and time in their names
	 */
	static List<String> backupsToDelete(String historyFile, List<String> names)
	{
		List<String> backups = names.stream()
			.filter(n -> isMigrationBackup(historyFile, n))
			.sorted(Comparator.comparing((String n) -> n.substring(n.indexOf("-backup-"))).reversed())
			.collect(Collectors.toList());
		return backups.size() <= KEPT_BACKUPS ? Collections.emptyList() : backups.subList(KEPT_BACKUPS, backups.size());
	}

	private static String readString(Filepath file) throws IOException
	{
		try (InputStream in = file.openInputStream())
		{
			return new String(ByteStreams.toByteArray(in), StandardCharsets.UTF_8);
		}
	}

	private Filepath directory() throws Exception
	{
		if (directory == null)
		{
			Filepath resolved = directorySupplier.call();
			resolved.createDirectories();
			directory = resolved;
		}
		return directory;
	}

	private static String fileName(long accountHash)
	{
		return "history-" + accountHash + ".json";
	}

	private static String folderName(long accountHash)
	{
		return "history-" + accountHash;
	}

	private static AccountHistory emptyHistory(long accountHash)
	{
		AccountHistory history = new AccountHistory();
		history.setAccountHash(accountHash);
		return history;
	}

	@Value
	public static class LoadResult
	{
		AccountHistory history;
		/**
		 * True if the file must not be overwritten (newer schema, or it could not be read).
		 */
		boolean readOnly;
		/**
		 * Schema version of the file as read; the current version for a new history.
		 */
		int sourceVersion;
		/**
		 * The names unreadable history files were moved to (their readable trips were kept), or null.
		 */
		String corruptBackup;
	}
}
