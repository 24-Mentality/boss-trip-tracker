package com.bosstriptracker.ui;

import com.bosstriptracker.boss.BossDefinition;
import com.bosstriptracker.view.PanelState;
import com.bosstriptracker.view.TripView;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Side panel with the boss dropdown and the Trip, History and Lifetime tabs pinned at the top, and the selected
 * tab scrolling below.
 * All methods run on the Swing thread.
 */
public class TrackerPanel extends PluginPanel
{
	private final CurrentTripPanel currentTab;
	private final HistoryPanel historyTab;
	private final LifetimePanel lifetimeTab;
	private final JLabel readOnlyWarning = new JLabel();
	private final BossSelector bossSelector;
	private final VariantChips variantChips;
	private BossDefinition boss;
	private final JLabel shareButton = new JLabel(new CameraIcon(ColorScheme.LIGHT_GRAY_COLOR));
	private PanelState state;
	private final Timer timer;
	private MaterialTabGroup tabGroup;
	private MaterialTab tripTab;

	public TrackerPanel(ItemManager itemManager, SectionStates sectionStates, PanelActions actions, BossDefinition initialBoss)
	{
		// Not wrapped in PluginPanel's scroll pane, so the tabs stay visible while content scrolls
		super(false);
		boss = initialBoss;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		currentTab = new CurrentTripPanel(itemManager, sectionStates, actions, () -> promptGoal(actions), actions::togglePause,
			() -> promptRestartGoal(actions), () -> promptLastUniqueKc(actions), () -> actions.setLastUniqueKc(null));
		historyTab = new HistoryPanel(itemManager, sectionStates, trip -> confirmDelete(trip, actions::deleteTrip));
		lifetimeTab = new LifetimePanel(itemManager, sectionStates, actions, () -> confirmClear(actions::clearHistory));

		ScrollableContent display = new ScrollableContent();
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		display.setBorder(BorderFactory.createEmptyBorder(4, 5, 5, 5));

		MaterialTabGroup tabs = new MaterialTabGroup(display);
		// The default wrapping row hides the third tab at sidebar width; equal columns always fit
		tabs.setLayout(new GridLayout(1, 0));
		tabs.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
		MaterialTab current = new MaterialTab("Trip", tabs, currentTab);
		MaterialTab history = new MaterialTab("History", tabs, historyTab);
		MaterialTab lifetime = new MaterialTab("Lifetime", tabs, lifetimeTab);
		for (MaterialTab tab : new MaterialTab[]{current, history, lifetime})
		{
			tab.setHorizontalAlignment(SwingConstants.CENTER);
			tabs.addTab(tab);
		}
		tabs.select(current);
		tabGroup = tabs;
		tripTab = current;

		readOnlyWarning.setFont(FontManager.getRunescapeSmallFont());
		readOnlyWarning.setForeground(UiFormat.LOSS);
		readOnlyWarning.setText("<html>History could not be loaded or is from a newer version. Changes will not be saved.</html>");
		readOnlyWarning.setVisible(false);
		readOnlyWarning.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));

		bossSelector = new BossSelector(itemManager, actions::selectBoss);
		variantChips = new VariantChips(actions::selectVariant);
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(6, 5, 0, 5));
		shareButton.setDisabledIcon(new CameraIcon(UiFormat.MUTED_TEXT.darker()));
		shareButton.setToolTipText("Share card: copies an image of this boss's stats to your clipboard, ready to paste"
			+ " into Discord, and saves it to your screenshots folder");
		shareButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		shareButton.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 2));
		shareButton.setEnabled(false);
		shareButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (shareButton.isEnabled() && e.getButton() == MouseEvent.BUTTON1)
				{
					actions.shareCard();
				}
			}
		});
		header.add(bossSelector, BorderLayout.CENTER);
		header.add(shareButton, BorderLayout.EAST);
		header.add(variantChips, BorderLayout.SOUTH);

		JPanel north = new JPanel(new BorderLayout());
		north.setOpaque(false);
		north.add(header, BorderLayout.NORTH);
		north.add(tabs, BorderLayout.CENTER);
		north.add(readOnlyWarning, BorderLayout.SOUTH);

		JScrollPane scroll = new JScrollPane(display);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(north, BorderLayout.NORTH);
		add(scroll, BorderLayout.CENTER);

		timer = new Timer(1000, e -> currentTab.tick(System.currentTimeMillis()));
		timer.start();
	}

	public void update(PanelState state)
	{
		this.state = state;
		boss = state.getBoss();
		shareButton.setEnabled(state.getLifetime() != null);
		bossSelector.update(state.getBosses(), boss.getId());
		variantChips.update(boss, state.getVariant());
		readOnlyWarning.setVisible(state.isReadOnly());
		currentTab.update(state, System.currentTimeMillis());
		historyTab.update(state.getHistory(), state.getLifetime());
		lifetimeTab.update(state.getLifetime(), state.isReadOnly(), boss);
	}

	/**
	 * The latest state shown, for the share card; null before the first update.
	 */
	public PanelState getState()
	{
		return state;
	}

	/**
	 * The boss shown in the tabs.
	 */
	public BossDefinition getBoss()
	{
		return boss;
	}

	public void showTripTab()
	{
		tabGroup.select(tripTab);
	}

	/**
	 * Selects a tab by position (0 Trip, 1 History, 2 Lifetime).
	 */
	void selectTab(int index)
	{
		tabGroup.select(tabGroup.getTab(index));
	}

	public void shutDown()
	{
		timer.stop();
	}

	/**
	 * Developer mode only: adds the diagnostic log switches to the Lifetime tab.
	 */
	public void showDeveloperTools(boolean diagnosticMode, boolean logEverywhere, Consumer<Boolean> setDiagnosticMode,
		Consumer<Boolean> setLogEverywhere)
	{
		lifetimeTab.addDeveloperTools(diagnosticMode, logEverywhere, setDiagnosticMode, setLogEverywhere);
	}

	public void showMessage(String title, String message, boolean error)
	{
		JOptionPane.showMessageDialog(this, message, title,
			error ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
	}

	public boolean confirm(String title, String message)
	{
		return JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION,
			JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
	}

	private void confirmDelete(TripView trip, Consumer<String> onDeleteTrip)
	{
		int choice = JOptionPane.showConfirmDialog(this,
			"Delete the trip from " + UiFormat.dateTime(trip.getStartedAt()) + "? This can't be undone.",
			"Delete trip", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION)
		{
			onDeleteTrip.accept(trip.getId());
		}
	}

	private void promptGoal(PanelActions actions)
	{
		boolean goalRunning = state != null && state.getGoal() != null;
		JTextField target = new JTextField(goalRunning ? String.valueOf(state.getGoal().getTarget()) : "", 8);
		JComboBox<GoalStart> from = goalStartBox(goalRunning);
		JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
		form.add(new JLabel("How many " + boss.getDisplayName() + " kills is your goal? (0 removes it)"));
		form.add(target);
		form.add(new JLabel("Count kills from:"));
		form.add(from);
		if (JOptionPane.showConfirmDialog(this, form, "Set kill goal", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION)
		{
			return;
		}
		try
		{
			int kills = Integer.parseInt(target.getText().trim().replace(",", ""));
			if (kills < 0 || kills > 1_000_000)
			{
				throw new NumberFormatException();
			}
			actions.setGoal(kills, startedAt((GoalStart) from.getSelectedItem()));
		}
		catch (NumberFormatException e)
		{
			showMessage("Set kill goal", "Enter a whole number of kills, like 100.", true);
		}
	}

	/**
	 * The Reset button and the goal card's "Count from" menu: restart the goal's count from a chosen point.
	 */
	private void promptRestartGoal(PanelActions actions)
	{
		if (state == null || state.getGoal() == null)
		{
			return;
		}
		JComboBox<GoalStart> from = goalStartBox(false);
		JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
		form.add(new JLabel("Count the goal's kills again from:"));
		form.add(from);
		if (JOptionPane.showConfirmDialog(this, form, "Reset kill goal", JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION)
		{
			Long startedAt = startedAt((GoalStart) from.getSelectedItem());
			actions.restartGoalFrom(startedAt != null ? startedAt : System.currentTimeMillis());
		}
	}

	private JComboBox<GoalStart> goalStartBox(boolean goalRunning)
	{
		JComboBox<GoalStart> box = new JComboBox<>();
		for (GoalStart option : GoalStart.options(state, System.currentTimeMillis(), goalRunning))
		{
			box.addItem(option);
		}
		return box;
	}

	/**
	 * @return the chosen start, or null to keep a running goal's count (for a new goal, the tracker's default)
	 */
	private static Long startedAt(GoalStart choice)
	{
		switch (choice.getKind())
		{
			case NOW:
				return System.currentTimeMillis();
			case TRIP:
				return choice.getStartedAt();
			default:
				return null;
		}
	}

	private void promptLastUniqueKc(PanelActions actions)
	{
		String input = JOptionPane.showInputDialog(this,
			"At what " + boss.getDisplayName() + " kill count did you get your last unique?\n"
				+ "Your dry streak counts from there unless the plugin tracks a newer unique.",
			"Last unique", JOptionPane.QUESTION_MESSAGE);
		if (input == null)
		{
			return;
		}
		try
		{
			int killCount = Integer.parseInt(input.trim().replace(",", ""));
			if (killCount < 0 || killCount > 10_000_000)
			{
				throw new NumberFormatException();
			}
			actions.setLastUniqueKc(killCount);
		}
		catch (NumberFormatException e)
		{
			showMessage("Last unique", "Enter a kill count, like 1,234.", true);
		}
	}

	private void confirmClear(Runnable onClearHistory)
	{
		int choice = JOptionPane.showConfirmDialog(this,
			"Delete all " + boss.getDisplayName() + " trip history for this account? This can't be undone.",
			"Clear all history", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.YES_OPTION)
		{
			onClearHistory.run();
		}
	}

	/**
	 * Tab content that fills the scroll pane's width and scrolls vertically.
	 */
	private static class ScrollableContent extends JPanel implements Scrollable
	{
		ScrollableContent()
		{
			super(new BorderLayout());
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
		{
			return Math.max(16, visibleRect.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}
}
