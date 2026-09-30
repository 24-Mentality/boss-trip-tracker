package com.maggotkingtriptracker.persistence;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class Fixtures
{
	private Fixtures()
	{
	}

	/**
	 * A schema 1 history file shaped like a real one (from before multi-boss support), with made-up values.
	 */
	public static String historyV1() throws IOException
	{
		return Resources.toString(Resources.getResource(Fixtures.class, "history-v1.json"), StandardCharsets.UTF_8);
	}
}
