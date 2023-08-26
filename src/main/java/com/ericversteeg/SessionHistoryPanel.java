package com.ericversteeg;

import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import java.util.LinkedList;
import lombok.extern.slf4j.Slf4j;
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
		container.setLayout(new BorderLayout(5, 5));
		container.setBorder(new EmptyBorder(5,5,5,5));
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
		// TODO: only update if something changed
		List<SessionStats> sessions = plugin.sessionHistory;
		if (!searchBar.getText().isEmpty())
		{
			sessions = filterSessions(sessions, searchBar.getText());
		}
		sessions = sessions.stream()
				.sorted(Comparator.comparingLong(o -> o.sessionSaveTime)).collect(Collectors.toList());
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
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy    h:mm a", Locale.US);
        String formattedDate = sdf.format(new Date(stats.sessionSaveTime));
		panelData.title.setText(formattedDate);
		panelData.gpPerHourLabel.setText(htmlLabel(gpPerHourLabelPrefix,
				UIHelper.formatGp(UIHelper.getGpPerHour(stats.getSessionRuntime(), stats.getNetTotal()),
				config.showExactGp()) + "/hr"));
		panelData.netTotalLabel
				.setText(htmlLabel(netTotalLabelPrefix, UIHelper.formatQuantity(stats.getNetTotal(), false)));
		panelData.totalGainsLabel.setText(
				htmlLabel(totalGainsLabelPrefix, UIHelper.formatGp(stats.getTotalGain(), config.showExactGp())));
		panelData.totalLossesLabel.setText(
				htmlLabel(totalLossesLabelPrefix, UIHelper.formatGp(stats.getTotalLoss(), config.showExactGp())));
		panelData.durationLabel
				.setText(htmlLabel(durationLabelPrefix, UIHelper.formatTime(stats.getSessionRuntime())));
		panelData.tripCountLabel.setText(htmlLabel(tripCountLabelPrefix, Integer.toString(stats.getTripCount())));
		panelData.avgTripDurationLabel
				.setText(htmlLabel(avgTripDurationLabelPrefix, UIHelper.formatTime(stats.getAvgTripDuration())));
		UIHelper.updateLootGrid(
				UIHelper.filterAndSortLedger(
						InventoryTotalPlugin.getProfitLossLedger(stats.getInitialQtys(), stats.getQtys())),
				panelData.sessionLootPanelData, itemManager, config);
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
		JPanel masterPanel = new JPanel();
		EditableNameField nameField;
		private final JLabel title = new JLabel("Title");
		private final JLabel durationLabel = new JLabel(htmlLabel(durationLabelPrefix, "N/A"));
		private final JLabel gpPerHourLabel = new JLabel(htmlLabel(gpPerHourLabelPrefix, "N/A"));
		private final JLabel netTotalLabel = new JLabel(htmlLabel(netTotalLabelPrefix, "N/A"));
		private final JLabel totalGainsLabel = new JLabel(htmlLabel(totalGainsLabelPrefix, "N/A"));
		private final JLabel totalLossesLabel = new JLabel(htmlLabel(totalLossesLabelPrefix, "N/A"));
		private final JLabel tripCountLabel = new JLabel(htmlLabel(tripCountLabelPrefix, "N/A"));
		private final JLabel avgTripDurationLabel = new JLabel(htmlLabel(avgTripDurationLabelPrefix, "N/A"));
		private final UIHelper.LootPanelData sessionLootPanelData = new UIHelper.LootPanelData();

		SessionHistoryPanelData(SessionHistoryPanel parentPanel)
		{
			masterPanel.setLayout(new BorderLayout(0,5));
			masterPanel.setBorder(new MatteBorder(1,1,1,1, borderColor));

			nameField = new EditableNameField(parentPanel, 50, ColorScheme.DARKER_GRAY_COLOR, null);

			JPanel infoLabels = new JPanel();
			infoLabels.setLayout(new GridLayout(7, 1, 0, 8));
			infoLabels.setBorder(new EmptyBorder(0, 0, 0, 0));

			infoLabels.add(durationLabel);
			infoLabels.add(gpPerHourLabel);
			infoLabels.add(netTotalLabel);
			infoLabels.add(totalGainsLabel);
			infoLabels.add(totalLossesLabel);
			infoLabels.add(tripCountLabel);
			infoLabels.add(avgTripDurationLabel);

			JPanel infoWrapper = new JPanel();
			infoWrapper.setLayout(new BorderLayout(0,10));
			infoWrapper.setBorder(new EmptyBorder(5, 5, 5, 5));

			title.setFont(FontManager.getRunescapeBoldFont());

			infoWrapper.add(title, BorderLayout.NORTH);
			infoWrapper.add(infoLabels, BorderLayout.CENTER);


			sessionLootPanelData.lootPanel.setLayout(new BorderLayout());
			// sessionLootPanelData.lootPanel.setBorder(new MatteBorder(1,1,1,1, borderColor));

			// ???: time/date + delete button
			masterPanel.add(nameField, BorderLayout.NORTH);
			masterPanel.add(infoWrapper, BorderLayout.CENTER);
			masterPanel.add(sessionLootPanelData.lootPanel, BorderLayout.SOUTH);

		}
	}

	SessionHistoryPanelData buildHistoryPanel()
	{
		// date/time duration
		// net total gains
		// gp/hr losses
		// trip count avg trip duration
		// hover changes background color ( to indicate you can click )
		// clicking expands/hides loot grid

		return new SessionHistoryPanelData(this);
	}

	static String htmlLabel(String key, String valueStr)
	{
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
	}
}
