package com.bosstriptracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SettingsRefreshTest
{
	@Test
	public void settingsThatChangeTheNumbersRefreshThePanel()
	{
		assertTrue(BossTripTrackerPlugin.refreshesView("tobPastTeamSize"));
		assertTrue(BossTripTrackerPlugin.refreshesView("showCurrentValue"));
		assertTrue(BossTripTrackerPlugin.refreshesView("idlePauseSeconds"));
	}

	@Test
	public void panelStateAndDiagnosticSwitchesDoNot()
	{
		assertFalse(BossTripTrackerPlugin.refreshesView("selectedBoss"));
		assertFalse(BossTripTrackerPlugin.refreshesView("sectionStates"));
		assertFalse(BossTripTrackerPlugin.refreshesView("diagnosticMode"));
		assertFalse(BossTripTrackerPlugin.refreshesView("diagnosticLogEverywhere"));
		assertFalse(BossTripTrackerPlugin.refreshesView(null));
	}
}
