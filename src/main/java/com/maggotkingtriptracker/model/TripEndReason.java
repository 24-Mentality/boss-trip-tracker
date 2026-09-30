package com.maggotkingtriptracker.model;

public enum TripEndReason
{
	WALKED_OUT,
	TELEPORT,
	DEATH,
	LOGOUT,
	/**
	 * A raid finished (Theatre of Blood). Added in schema 4.
	 */
	COMPLETED,
	/**
	 * The whole team died and the raid ended (Theatre of Blood). Added in schema 4.
	 */
	WIPED,
}
