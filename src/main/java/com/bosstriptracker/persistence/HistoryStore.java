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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
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
	/**
	 * A write that fails is tried again at once this many times in all, for a file that was only briefly in use.
	 */
	static final int WRITE_ATTEMPTS = 3;

	private final Gson gson;
	private final Callable<Filepath> directorySupplier;
	private final ExecutorService executor;

	// Only touched on the executor thread
	private Filepath directory;
	private boolean saveFailing;

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
	 * @param written gets whether everything was written, once the write is done or has failed (false at once if the
	 *                plugin is shutting down). After a failure the caller still holds what wasn't saved and must save
	 *                it again.
	 */
	public void save(long accountHash, HistoryLayout.Snapshot snapshot, Consumer<Boolean> written)
	{
		if (!submit(() -> written.accept(write(accountHash, snapshot))))
		{
			written.accept(false);
		}
	}

	private boolean write(long accountHash, HistoryLayout.Snapshot snapshot)
	{
		Exception failure = null;
		for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++)
		{
			try
			{
				HistoryLayout.write(gson, directory().joinSegment(folderName(accountHash)), snapshot);
				if (saveFailing)
				{
					saveFailing = false;
					log.info("Trip history is being saved again");
				}
				return true;
			}
			catch (Exception e)
			{
				failure = e;
			}
		}
		if (saveFailing)
		{
			log.debug("Still unable to save trip history", failure);
		}
		else
		{
			// Logged once per run of failures: the caller keeps trying
			saveFailing = true;
			log.warn("Unable to save trip history; it will be tried again", failure);
		}
		return false;
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
			// A folder is read even without its account file (moved aside as unreadable, or never written): its trips
			// are the history, and a save would otherwise write over their months
			if (folder.isDirectory())
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
		if (history.getSchemaVersion() < AccountHistory.CURRENT_SCHEMA_VERSION)
		{
			backUpAccountFile(folder, history.getSchemaVersion());
		}
		// A folder from a newer plugin version is shown but never written to
		boolean newer = history.getSchemaVersion() > AccountHistory.CURRENT_SCHEMA_VERSION;
		return new LoadResult(history, newer, history.getSchemaVersion(), unreadable(loaded, alsoUnreadable));
	}

	private static String unreadable(HistoryLayout.Loaded loaded, String alsoUnreadable)
	{
		List<String> unreadable = new ArrayList<>(loaded.getUnreadable());
		if (alsoUnreadable != null)
		{
			unreadable.add(0, alsoUnreadable);
		}
		return unreadable.isEmpty() ? null : String.join(", ", unreadable);
	}

	/**
	 * Moves a single-file history (schema 5 and older) into the folder layout. The folder is read back and its trips
	 * checked against the file before the file is renamed to a backup; if anything fails, the file is left as it is
	 * and the history is shown read-only, to be tried again at the next login. A folder that is already there is
	 * never deleted: the file's trips are added to it ({@link #mergeIntoFolder}).
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
			return folder.isDirectory() ? readFolder(accountHash, folder, backup.getFileName())
				: new LoadResult(emptyHistory(accountHash), false, AccountHistory.CURRENT_SCHEMA_VERSION, backup.getFileName());
		}
		if (decoded == null)
		{
			// An empty file
			oldFile.delete();
			return folder.isDirectory() ? readFolder(accountHash, folder, null)
				: new LoadResult(emptyHistory(accountHash), false, AccountHistory.CURRENT_SCHEMA_VERSION, null);
		}

		AccountHistory history = decoded.getHistory();
		history.setAccountHash(accountHash);
		if (decoded.isNewer())
		{
			return new LoadResult(history, true, decoded.getSourceVersion(), null);
		}

		if (folder.isDirectory())
		{
			return mergeIntoFolder(accountHash, oldFile, folder, decoded);
		}

		try
		{
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
	 * A single-file history beside a folder: an older build of the plugin ran and started a file again, a backup was
	 * put back, or a move into the folder didn't finish. Either may hold trips the other lacks, so nothing in the
	 * folder is deleted: the file's trips that the folder doesn't have (by id) are added to it, and where the folder
	 * has no saved data for a boss (goal, egg pops, polish tallies) the file's is used. The folder is read back and
	 * checked before the file is renamed to a backup; if anything fails both are left as they are and the history is
	 * shown read-only, to be tried again at the next login. Backups aren't pruned here: the file may be a nearly
	 * empty one that would push out a real backup.
	 */
	private LoadResult mergeIntoFolder(long accountHash, Filepath oldFile, Filepath folder, HistoryCodec.Decoded decoded)
	{
		AccountHistory fromFile = decoded.getHistory();
		HistoryLayout.Loaded loaded;
		try
		{
			loaded = HistoryLayout.read(gson, folder);
		}
		catch (Exception e)
		{
			log.warn("Unable to read the history folder beside an older history file; both are kept (read-only this session)", e);
			return new LoadResult(fromFile, true, decoded.getSourceVersion(), null);
		}

		AccountHistory merged = loaded.getHistory();
		merged.setAccountHash(accountHash);
		int folderVersion = merged.getSchemaVersion();
		String unreadable = unreadable(loaded, null);
		if (folderVersion > AccountHistory.CURRENT_SCHEMA_VERSION)
		{
			// Written by a newer plugin version: shown, never written to, and the file is left alone
			return new LoadResult(merged, true, folderVersion, unreadable);
		}

		Set<String> added = HistoryMerge.addMissing(merged, fromFile, loaded.getAccountBosses());
		try
		{
			if (folderVersion < AccountHistory.CURRENT_SCHEMA_VERSION)
			{
				backUpAccountFile(folder, folderVersion);
			}
			HistoryLayout.write(gson, folder, HistoryLayout.Snapshot.of(merged));
			HistoryLayout.Loaded back = HistoryLayout.read(gson, folder);
			if (!back.getUnreadable().isEmpty() || !HistoryLayout.sameTrips(merged, back.getHistory()))
			{
				log.warn("The history folder didn't read back the same trips after adding the old file's; keeping both"
					+ " (read-only this session)");
				return new LoadResult(merged, true, folderVersion, unreadable);
			}
			Filepath backup = directory().joinSegment(fileName(accountHash) + ".v" + decoded.getSourceVersion()
				+ "-backup-" + BACKUP_STAMP.format(LocalDateTime.now()));
			oldFile.moveTo(backup);
			log.info("Found an older history file beside the history folder: added {} trips the folder didn't have;"
				+ " the file is kept as {}", added.size(), backup.getFileName());
		}
		catch (Exception e)
		{
			log.warn("Unable to add an older history file's trips to the history folder; keeping both (read-only this session)", e);
			return new LoadResult(merged, true, folderVersion, unreadable);
		}
		return new LoadResult(merged, false, folderVersion, unreadable, added, decoded.getSourceVersion());
	}

	/**
	 * A folder from an older schema is first written at the current one on the next save: its account file (the
	 * only file whose layout changes between folder schemas) is kept beside it as "account.json.vN-backup", once.
	 */
	private static void backUpAccountFile(Filepath folder, int version) throws IOException
	{
		Filepath account = folder.joinSegment(HistoryLayout.ACCOUNT_FILE);
		Filepath backup = folder.joinSegment(HistoryLayout.ACCOUNT_FILE + ".v" + version + "-backup");
		if (account.exists() && !backup.exists())
		{
			account.copyTo(backup);
			log.info("Trip history folder is at schema {}; its account file is kept as {} before it's written at schema {}",
				version, backup.getFileName(), AccountHistory.CURRENT_SCHEMA_VERSION);
		}
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
	@AllArgsConstructor
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
		/**
		 * Ids of trips added from an older single-file history found beside the folder; empty otherwise.
		 */
		Set<String> addedFromOldFile;
		/**
		 * Schema version of that file as read; 0 if there was none.
		 */
		int oldFileVersion;

		public LoadResult(AccountHistory history, boolean readOnly, int sourceVersion, String corruptBackup)
		{
			this(history, readOnly, sourceVersion, corruptBackup, Collections.emptySet(), 0);
		}
	}
}
