package com.maggotkingtriptracker.persistence;

import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.maggotkingtriptracker.model.AccountHistory;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Reads and writes one history-&lt;accountHash&gt;.json per account in the plugin data folder.
 * All disk IO runs on the supplied executor; callbacks are invoked on that executor thread.
 */
@Slf4j
public class HistoryStore
{
	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

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
	 * @param json the history already serialized on the client thread
	 */
	public void save(long accountHash, String json)
	{
		submit(() -> writeHistory(accountHash, json));
	}

	/**
	 * Writes a user-chosen export file. The callback gets null on success, or the error.
	 */
	public void writeFile(Filepath file, String content, Consumer<Exception> callback)
	{
		submit(() ->
		{
			try
			{
				file.write(content.getBytes(StandardCharsets.UTF_8),
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
	 * Reads a user-chosen history export. The callback gets the parsed history, or null and the error.
	 */
	public void readHistoryFile(Filepath file, BiConsumer<HistoryCodec.Decoded, Exception> callback)
	{
		submit(() ->
		{
			try
			{
				HistoryCodec.Decoded decoded = HistoryCodec.decode(gson, readString(file));
				if (decoded == null)
				{
					throw new IOException("Not a Boss Trip Tracker export");
				}
				callback.accept(decoded, null);
			}
			catch (Exception e)
			{
				log.warn("Unable to read import", e);
				callback.accept(null, e);
			}
		});
	}

	private void submit(Runnable task)
	{
		try
		{
			executor.execute(task);
		}
		catch (RejectedExecutionException e)
		{
			log.debug("Plugin shutting down; skipped history IO");
		}
	}

	private LoadResult readHistory(long accountHash)
	{
		try
		{
			Filepath file = directory().joinSegment(fileName(accountHash));
			if (!file.exists())
			{
				return new LoadResult(emptyHistory(accountHash), false, AccountHistory.CURRENT_SCHEMA_VERSION);
			}

			HistoryCodec.Decoded decoded;
			try
			{
				decoded = HistoryCodec.decode(gson, readString(file));
			}
			catch (JsonParseException e)
			{
				Filepath backup = directory().joinSegment(fileName(accountHash) + ".corrupt-" + System.currentTimeMillis());
				log.warn("Trip history for this account is unreadable; moving it to {}", backup.getFileName(), e);
				file.moveTo(backup);
				return new LoadResult(emptyHistory(accountHash), false, AccountHistory.CURRENT_SCHEMA_VERSION);
			}

			if (decoded == null)
			{
				return new LoadResult(emptyHistory(accountHash), false, AccountHistory.CURRENT_SCHEMA_VERSION);
			}
			if (decoded.isMigrated())
			{
				// Keep the old file as it was before it's rewritten in the new format
				Filepath backup = directory().joinSegment(fileName(accountHash) + ".v" + decoded.getSourceVersion()
					+ "-backup-" + BACKUP_STAMP.format(LocalDateTime.now()));
				if (!backup.exists())
				{
					file.copyTo(backup);
				}
				log.info("Upgraded trip history from schema {} to {}; the old file is kept as {}",
					decoded.getSourceVersion(), AccountHistory.CURRENT_SCHEMA_VERSION, backup.getFileName());
			}

			AccountHistory history = decoded.getHistory();
			history.setAccountHash(accountHash);
			// A file from a newer plugin version is shown but never overwritten
			return new LoadResult(history, decoded.isNewer(), decoded.getSourceVersion());
		}
		catch (Exception e)
		{
			log.warn("Unable to load trip history", e);
			return new LoadResult(emptyHistory(accountHash), true, AccountHistory.CURRENT_SCHEMA_VERSION);
		}
	}

	private static String readString(Filepath file) throws IOException
	{
		try (InputStream in = file.openInputStream())
		{
			return new String(ByteStreams.toByteArray(in), StandardCharsets.UTF_8);
		}
	}

	private void writeHistory(long accountHash, String json)
	{
		try
		{
			Filepath target = directory().joinSegment(fileName(accountHash));
			Filepath temp = directory().joinSegment(fileName(accountHash) + ".tmp");
			temp.write(json.getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			try
			{
				temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (IOException e)
			{
				temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (Exception e)
		{
			log.warn("Unable to save trip history", e);
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
	}
}
