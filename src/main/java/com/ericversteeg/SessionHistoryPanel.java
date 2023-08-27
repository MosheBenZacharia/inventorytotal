package com.ericversteeg;

import java.awt.event.KeyEvent;
import java.util.LinkedList;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.FlatTextField;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.*;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Locale;

public class SessionHistoryPanel extends JPanel
{
	private static final String HTML_LABEL_TEMPLATE = "<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";
	private static final String durationLabelPrefix = "Duration: ";
	private static final String gpPerHourLabelPrefix = "GP/hr: ";
	private static final String netTotalLabelPrefix = "Net Total: ";
	private static final String totalGainsLabelPrefix = "Gains: ";
	private static final String totalLossesLabelPrefix = "Losses: ";
	private static final String tripCountLabelPrefix = "Trip Count: ";
	private static final String avgTripDurationLabelPrefix = "Avg Trip Time: ";
	private static final Color borderColor = new Color(57, 57, 57);

	private final InventoryTotalConfig config;
	private final InventoryTotalPlugin plugin;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final SessionManager sessionManager;
	GridBagConstraints constraints = new GridBagConstraints();

	private final List<SessionHistoryPanelData> historyPanels = new LinkedList<>();
	private final IconTextField searchBar = new IconTextField();
	private final JPanel historyPanelContainer = new JPanel();
	private final JScrollPane resultsWrapper;

	SessionHistoryPanel(InventoryTotalPlugin plugin, InventoryTotalConfig config, ItemManager itemManager,
			ClientThread clientThread, SessionManager sessionManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.sessionManager = sessionManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		/* The main container, this holds the search bar and the center panel */
		JPanel container = new JPanel();
		container.setLayout(new BorderLayout(5, 15));
		container.setBorder(new EmptyBorder(15, 5, 5, 5));
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setPreferredSize(new Dimension(100, 30));
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.addKeyListener(new KeyListener()
		{
			@Override
			public void keyTyped(KeyEvent e)
			{
			}

			@Override
			public void keyPressed(KeyEvent e)
			{
			}

			@Override
			public void keyReleased(KeyEvent e)
			{
				redrawPanels(true);
			}
		});
		searchBar.addClearListener(() -> redrawPanels(true));

		historyPanelContainer.setLayout(new GridBagLayout());
		historyPanelContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 0, 10, 0); // Add vertical gap

		/* This panel wraps the results panel and guarantees the scrolling behaviour */
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(historyPanelContainer, BorderLayout.NORTH);

		/* The results wrapper, this scrolling panel wraps the results container */
		resultsWrapper = new JScrollPane(wrapper);
		resultsWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		resultsWrapper.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
		resultsWrapper.getVerticalScrollBar().setBorder(new EmptyBorder(0, 5, 0, 0));
		resultsWrapper.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		resultsWrapper.setVisible(true);

		container.add(searchBar, BorderLayout.NORTH);
		container.add(resultsWrapper, BorderLayout.CENTER);

		add(container, BorderLayout.CENTER);
	}

	void redrawPanels(boolean resetScroll)
	{
		updateSessions();
		if (resetScroll)
		{
			resultsWrapper.getVerticalScrollBar().setValue(0);
		}
	}

	void updateSessions()
	{
		List<SessionStats> sessions = plugin.sessionHistory;
		if (!searchBar.getText().isEmpty())
		{
			sessions = filterSessions(sessions, searchBar.getText());
		}
		sessions = sessions.stream().sorted(Comparator.comparingLong(o -> o.sessionSaveTime))
				.collect(Collectors.toList());
		int sessionIndex;
		for (sessionIndex = 0; sessionIndex < sessions.size(); ++sessionIndex)
		{
			ensurePanelCount(sessionIndex + 1);
			renderHistoryPanel(sessions.get(sessionIndex), historyPanels.get(sessionIndex));
		}
		for (int i = sessionIndex; i < historyPanels.size(); ++i)
		{
			historyPanels.get(i).masterPanel.setVisible(false);
		}

		repaint();
		revalidate();
	}

	public List<SessionStats> filterSessions(List<SessionStats> sessionStats, String textToFilter)
	{
		final String textToFilterLower = textToFilter.toLowerCase();
		return sessionStats.stream().filter(i -> i.getSessionName().toLowerCase().contains(textToFilterLower))
				.collect(Collectors.toList());
	}

	void renderHistoryPanel(SessionStats stats, SessionHistoryPanelData panelData)
	{
		panelData.masterPanel.setVisible(true);
		panelData.nameField.setData(stats.sessionName, (String newName) ->
		{
			stats.sessionName = newName;
			plugin.overwriteSession(stats);
		});
		SimpleDateFormat sdfLeft = new SimpleDateFormat("MMM dd, yyyy   h:mm a", Locale.US);
		Date date = new Date(stats.sessionSaveTime);
		String formattedDateLeft = sdfLeft.format(date);
		panelData.subtitleLeft.setText(formattedDateLeft);

		String gpPerHour = UI.formatGp(UI.getGpPerHour(stats.getSessionRuntime(), stats.getNetTotal()),
				config.showExactGp()) + "/hr";

		panelData.gpPerHourTabLabel.setText(gpPerHour);

		panelData.detailsPanel.setVisible(stats.showDetails);

		if (stats.showDetails)
		{
			panelData.gpPerHourLabel.setText(htmlLabel(gpPerHourLabelPrefix, gpPerHour));
			panelData.netTotalLabel.setText(htmlLabel(netTotalLabelPrefix, UI.formatQuantity(stats.getNetTotal(), false)));
			panelData.totalGainsLabel
					.setText(htmlLabel(totalGainsLabelPrefix, UI.formatGp(stats.getTotalGain(), config.showExactGp())));
			panelData.totalLossesLabel
					.setText(htmlLabel(totalLossesLabelPrefix, UI.formatGp(stats.getTotalLoss(), config.showExactGp())));
			panelData.durationLabel.setText(htmlLabel(durationLabelPrefix, UI.formatTime(stats.getSessionRuntime())));
			panelData.tripCountLabel.setText(htmlLabel(tripCountLabelPrefix, Integer.toString(stats.getTripCount())));
			panelData.avgTripDurationLabel
					.setText(htmlLabel(avgTripDurationLabelPrefix, UI.formatTime(stats.getAvgTripDuration())));
			UI.updateLootGrid(
					UI.filterAndSortLedger(
							InventoryTotalPlugin.getProfitLossLedger(stats.getInitialQtys(), stats.getQtys())),
					panelData.sessionLootPanelData, itemManager, config);
		}

		panelData.onDetailsPressed = () ->
		{
			stats.showDetails = !stats.showDetails;
			redrawPanels(false);
		};
		panelData.onDeletePressed = () ->
		{
			clientThread.invokeLater(()-> plugin.deleteSession(stats));
		};
	}

	void ensurePanelCount(int size)
	{
		while (historyPanels.size() < size)
		{
			constraints.gridy = historyPanels.size();
			SessionHistoryPanelData data = buildHistoryPanel();
			this.historyPanelContainer.add(data.masterPanel, constraints);
			historyPanels.add(data);
		}
	}

	private class SessionHistoryPanelData
	{
		final JPanel detailsPanel;
		final JLabel gpPerHourTabLabel;
		final JPanel masterPanel = new JPanel();
		final EditableNameField nameField;
		private final JLabel subtitleLeft = new JLabel("Left");
		private final JLabel durationLabel = new JLabel(htmlLabel(durationLabelPrefix, "N/A"));
		private final JLabel gpPerHourLabel = new JLabel(htmlLabel(gpPerHourLabelPrefix, "N/A"));
		private final JLabel netTotalLabel = new JLabel(htmlLabel(netTotalLabelPrefix, "N/A"));
		private final JLabel totalGainsLabel = new JLabel(htmlLabel(totalGainsLabelPrefix, "N/A"));
		private final JLabel totalLossesLabel = new JLabel(htmlLabel(totalLossesLabelPrefix, "N/A"));
		private final JLabel tripCountLabel = new JLabel(htmlLabel(tripCountLabelPrefix, "N/A"));
		private final JLabel avgTripDurationLabel = new JLabel(htmlLabel(avgTripDurationLabelPrefix, "N/A"));
		private final UI.LootPanelData sessionLootPanelData = new UI.LootPanelData();
		Runnable onDetailsPressed;
		Runnable onDeletePressed;

		SessionHistoryPanelData(SessionHistoryPanel parentPanel)
		{
			masterPanel.setLayout(new BorderLayout(0, 0));

			JLabel coinsLabel = new JLabel();
			parentPanel.getCoinsImage(1000, (BufferedImage image) ->
			{
				coinsLabel.setIcon(new ImageIcon(image));
			});

			gpPerHourTabLabel = new JLabel();
			gpPerHourTabLabel.setText("xxx/hr");
			gpPerHourTabLabel.setFont(FontManager.getRunescapeBoldFont());

			RoundedPanel gpPerHourPanel = new RoundedPanel();
			gpPerHourPanel.setLayout(new BorderLayout(5, 0));
			gpPerHourPanel.setBorder(new EmptyBorder(3, 10, 3, 10));
			gpPerHourPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			gpPerHourPanel.add(coinsLabel, BorderLayout.WEST);
			gpPerHourPanel.add(gpPerHourTabLabel, BorderLayout.CENTER);

			JPanel gpPerHourWrapperPanel = new JPanel();
			gpPerHourWrapperPanel.setLayout(new BorderLayout());
			gpPerHourWrapperPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
			gpPerHourWrapperPanel.add(gpPerHourPanel, BorderLayout.WEST);

			nameField = new EditableNameField(parentPanel, 50, ColorScheme.DARKER_GRAY_COLOR, null);

			JLabel detailsButton = UI.createIconButton(UI.SESSIONINFO_INFO_ICON, UI.SESSIONINFO_INFO_HOVER_ICON,
					"Show Details", () ->
					{
						onDetailsPressed.run();
					});
			JLabel deleteButton = UI.createIconButton(UI.SESSIONINFO_TRASH_ICON, UI.SESSIONINFO_TRASH_HOVER_ICON,
					"Delete Session", () ->
					{
						onDeletePressed.run();
					});

			JPanel subtitlePanel = new JPanel();
			subtitlePanel.setLayout(new BorderLayout());
			subtitlePanel.setBorder(new EmptyBorder(5, 10, 5, 10));
			subtitlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			subtitlePanel.add(subtitleLeft, BorderLayout.WEST);
			subtitlePanel.add(detailsButton, BorderLayout.CENTER);
			subtitlePanel.add(deleteButton, BorderLayout.EAST);

			// Always visible header area
			JPanel headerPanel = new JPanel();
			headerPanel.setLayout(new BorderLayout());
			headerPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
			headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			headerPanel.add(gpPerHourWrapperPanel, BorderLayout.NORTH);
			headerPanel.add(nameField, BorderLayout.CENTER);
			headerPanel.add(subtitlePanel, BorderLayout.SOUTH);

			JPanel infoLabels = new JPanel();
			infoLabels.setLayout(new GridLayout(7, 1, 0, 8));
			infoLabels.setBorder(new EmptyBorder(8, 10, 8, 10));
			infoLabels.setBackground(ColorScheme.DARKER_GRAY_COLOR);

			infoLabels.add(gpPerHourLabel);
			infoLabels.add(netTotalLabel);
			infoLabels.add(totalGainsLabel);
			infoLabels.add(totalLossesLabel);
			infoLabels.add(tripCountLabel);
			infoLabels.add(avgTripDurationLabel);
			infoLabels.add(durationLabel);

			sessionLootPanelData.lootPanel.setLayout(new BorderLayout());

			detailsPanel = new JPanel();
			detailsPanel.setLayout(new BorderLayout());
			detailsPanel.setBorder(new MatteBorder(1,0,0,0,borderColor));
			detailsPanel.add(infoLabels, BorderLayout.NORTH);
			detailsPanel.add(sessionLootPanelData.lootPanel, BorderLayout.SOUTH);

			masterPanel.add(headerPanel, BorderLayout.NORTH);
			masterPanel.add(detailsPanel, BorderLayout.CENTER);
		}
	}

	SessionHistoryPanelData buildHistoryPanel()
	{
		return new SessionHistoryPanelData(this);
	}

	static String htmlLabel(String key, String valueStr)
	{
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
	}

	private static BufferedImage coinsImage;
	private static int lastCoinValue;

	void getCoinsImage(int quantity, Consumer<BufferedImage> consumer)
	{
		// TODO: this needs a lot of work to be useful (each session will call this with
		// differnet values)
		if (coinsImage == null || quantity != lastCoinValue)
		{
			AsyncBufferedImage asyncImage = itemManager.getImage(ItemID.COINS_995, quantity, false);
			asyncImage.onLoaded(() ->
			{
				coinsImage = ImageUtil.resizeImage(asyncImage, 24, 24);
				consumer.accept(coinsImage);
				lastCoinValue = quantity;
			});
		} else
		{
			consumer.accept(coinsImage);
		}
	}
}
