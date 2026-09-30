package com.maggotkingtriptracker.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class Trip
{
	private String id;
	private long startedAt;
	/**
	 * Null while the trip is in progress (or paused by a logout within the grace period).
	 */
	private Long endedAt;
	private TripEndReason endReason;
	/**
	 * Time spent inside the lair, excluding the current segment.
	 */
	private long activeMs;
	/**
	 * Start of the current in-lair segment, or null when outside the lair.
	 */
	private Long segmentStartedAt;
	/**
	 * Last time the trip was known to be active; used to close trips left open by a client exit.
	 */
	private long lastActiveAt;
	private List<Kill> kills = new ArrayList<>();
	private List<ItemEntry> supplies = new ArrayList<>();
	/**
	 * Items dropped in the lair and not picked back up.
	 */
	private List<ItemEntry> dropped = new ArrayList<>();
	private List<DeathRecord> deaths = new ArrayList<>();

	public boolean isOpen()
	{
		return endedAt == null;
	}

	public long activeMsAt(long now)
	{
		return activeMs + (segmentStartedAt != null ? Math.max(0, now - segmentStartedAt) : 0);
	}
}
