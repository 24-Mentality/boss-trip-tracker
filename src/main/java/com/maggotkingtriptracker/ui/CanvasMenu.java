package com.maggotkingtriptracker.ui;

import com.maggotkingtriptracker.CanvasSection;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/**
 * The right-click "Add to canvas" / "Remove from canvas" menu on a panel card, like RuneLite's XP tracker panel. It
 * shows or hides that card's row on the overlay.
 */
final class CanvasMenu
{
	private CanvasMenu()
	{
	}

	static void attach(JComponent card, CanvasSection section, PanelActions actions)
	{
		JMenuItem item = new JMenuItem();
		item.addActionListener(e -> actions.toggleCanvas(section));
		JPopupMenu menu = new JPopupMenu();
		menu.add(item);
		menu.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent e)
			{
				// Read when opened, so it's right after changes in the config panel too
				item.setText(actions.isOnCanvas(section) ? "Remove from canvas" : "Add to canvas");
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent e)
			{
			}
		});
		card.setComponentPopupMenu(menu);
		LuckCard.inheritPopupMenu(card);
	}
}
