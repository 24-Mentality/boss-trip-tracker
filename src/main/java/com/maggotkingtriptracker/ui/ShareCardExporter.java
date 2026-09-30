package com.maggotkingtriptracker.ui;

import com.maggotkingtriptracker.view.PanelState;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageCapture;

/**
 * Makes a share card: renders it, copies it to the clipboard and saves it with RuneLite's screenshot utility (to
 * the screenshots folder, under "Boss Trip Tracker"), then confirms in the game chat. Nothing is uploaded.
 */
@Slf4j
public class ShareCardExporter
{
	private static final String SCREENSHOT_FOLDER = "Boss Trip Tracker";
	/**
	 * Item icons normally load at once; don't wait longer than this for one that doesn't.
	 */
	private static final int ICON_WAIT_MS = 3000;

	private final Client client;
	private final ItemManager itemManager;
	private final ImageCapture imageCapture;
	private final ChatMessageManager chatMessageManager;
	private final ExecutorService executor;
	private final ShareCardRenderer renderer = new ShareCardRenderer(ZoneId.systemDefault());

	public ShareCardExporter(Client client, ItemManager itemManager, ImageCapture imageCapture,
		ChatMessageManager chatMessageManager, ExecutorService executor)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.imageCapture = imageCapture;
		this.chatMessageManager = chatMessageManager;
		this.executor = executor;
	}

	/**
	 * Call on the Swing thread.
	 *
	 * @param onMessage shows a message in the panel when the game chat can't (not logged in, or nothing to share)
	 */
	public void share(PanelState state, boolean showName, Consumer<String> onMessage)
	{
		ShareCard card = state == null ? null : ShareCard.from(state, showName, System.currentTimeMillis());
		if (card == null)
		{
			onMessage.accept("Log in first so there is something to share.");
			return;
		}

		Set<Integer> ids = new LinkedHashSet<>();
		ids.add(card.getIconItemId());
		for (ShareCard.Drop drop : card.getDrops())
		{
			ids.add(drop.getItemId());
		}

		// Icons load asynchronously; render once all are in, or after a short wait
		Map<Integer, Image> icons = new HashMap<>();
		AtomicInteger pending = new AtomicInteger(ids.size());
		AtomicBoolean done = new AtomicBoolean();
		Runnable finish = () ->
		{
			if (done.compareAndSet(false, true))
			{
				export(card, icons, onMessage);
			}
		};
		for (int id : ids)
		{
			AsyncBufferedImage image = itemManager.getImage(id);
			icons.put(id, image);
			image.onLoaded(() ->
			{
				if (pending.decrementAndGet() == 0)
				{
					SwingUtilities.invokeLater(finish);
				}
			});
		}
		Timer fallback = new Timer(ICON_WAIT_MS, e -> finish.run());
		fallback.setRepeats(false);
		fallback.start();
	}

	private void export(ShareCard card, Map<Integer, Image> icons, Consumer<String> onMessage)
	{
		BufferedImage image = renderer.render(card, icons::get);
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(image), null);
		}
		catch (IllegalStateException e)
		{
			log.warn("Clipboard unavailable for the share card", e);
			onMessage.accept("The clipboard is busy; try the camera button again.");
			return;
		}

		String fileName = card.getBossName() + " share card";
		try
		{
			executor.execute(() ->
			{
				// The screenshot utility refuses on the login screen, and the chat isn't visible there either
				if (client.getGameState() != GameState.LOGGED_IN)
				{
					SwingUtilities.invokeLater(() -> onMessage.accept("Share card copied to the clipboard. Log in to also"
						+ " save it to your screenshots folder."));
					return;
				}
				imageCapture.saveScreenshot(image, fileName, SCREENSHOT_FOLDER, false, false);
				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.GAMEMESSAGE)
					.runeLiteFormattedMessage(new ChatMessageBuilder()
						.append(ChatColorType.HIGHLIGHT)
						.append(card.getBossName() + " share card")
						.append(ChatColorType.NORMAL)
						.append(" copied to your clipboard and saved to your screenshots folder (" + SCREENSHOT_FOLDER + ").")
						.build())
					.build());
			});
		}
		catch (RejectedExecutionException e)
		{
			// Plugin is shutting down; the clipboard copy still happened
		}
	}

	/**
	 * An image on the clipboard.
	 */
	private static class ImageSelection implements Transferable
	{
		private final Image image;

		ImageSelection(Image image)
		{
			this.image = image;
		}

		@Override
		public DataFlavor[] getTransferDataFlavors()
		{
			return new DataFlavor[]{DataFlavor.imageFlavor};
		}

		@Override
		public boolean isDataFlavorSupported(DataFlavor flavor)
		{
			return DataFlavor.imageFlavor.equals(flavor);
		}

		@Override
		public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
		{
			if (!isDataFlavorSupported(flavor))
			{
				throw new UnsupportedFlavorException(flavor);
			}
			return image;
		}
	}
}
