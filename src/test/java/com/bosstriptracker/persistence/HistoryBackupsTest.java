package com.bosstriptracker.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class HistoryBackupsTest
{
	private static final String FILE = "history-42.json";

	@Test
	public void keepsTheNewestThreeMigrationBackups()
	{
		List<String> names = ImmutableList.of(
			FILE,
			FILE + ".v1-backup-20260928-163105",
			FILE + ".v3-backup-20260929-030754",
			FILE + ".v2-backup-20260929-000240",
			FILE + ".v3-backup-20260929-024814",
			FILE + ".v4-backup-20261001-101500",
			// Never touched: older backups, a file moved aside as unreadable, and another account's backup
			FILE + ".backup-20260927-052835",
			FILE + ".corrupt-1790000000000",
			"history-7.json.v1-backup-20200101-000000");

		assertEquals(ImmutableList.of(FILE + ".v2-backup-20260929-000240", FILE + ".v1-backup-20260928-163105"),
			HistoryStore.backupsToDelete(FILE, names));
	}

	@Test
	public void threeOrFewerBackupsAreAllKept()
	{
		assertTrue(HistoryStore.backupsToDelete(FILE, ImmutableList.of(FILE + ".v1-backup-20260928-163105")).isEmpty());
	}
}
