package com.ericversteeg;

import java.util.LinkedList;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.ProgressBar;
import net.runelite.client.util.*;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import com.google.common.collect.Sets;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.val;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

@Slf4j
class SessionPanel extends PluginPanel
{
	private static final String HTML_LABEL_TEMPLATE = "<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";

	private final InventoryTotalConfig config;
	private final InventoryTotalPlugin plugin;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final SessionManager sessionManager;
	private final JPanel sidePanel;

	// Panels
	private final JPanel titlePanel;
	private final JPanel sessionInfoPanel;
	private final List<TripPanelData> tripPanels = new LinkedList<>();

	SessionPanel(InventoryTotalPlugin plugin, InventoryTotalConfig config, ItemManager itemManager,
			ClientThread clientThread, SessionManager sessionManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.sessionManager = sessionManager;
		this.sidePanel = new JPanel();
		this.titlePanel = new JPanel();
		this.sessionInfoPanel = new JPanel();
	}

	void sidePanelInitializer()
	{
		this.setLayout(new BorderLayout());
		this.setBorder(new EmptyBorder(10, 10, 10, 10));
		this.sidePanel.setLayout(new BoxLayout(this.sidePanel, BoxLayout.Y_AXIS));
		this.sidePanel.add(this.buildTitlePanel());
		this.sidePanel.add(Box.createRigidArea(new Dimension(0, 5)));
		this.sidePanel.add(buildSessionInfoPanel());

		JButton button = new JButton("Rebuild");
		button.addActionListener((o) ->
		{
			for (TripPanelData data : tripPanels)
			{
				this.sidePanel.remove(data.masterPanel);
			}
			this.tripPanels.clear();

			this.updateTrips(sessionManager.getActiveTrips());
		});
		this.sidePanel.add(button);

		this.add(sidePanel, "North");
	}

	private JPanel buildTitlePanel()
	{
		titlePanel.setBorder(
				new CompoundBorder(new EmptyBorder(5, 0, 0, 0), new MatteBorder(0, 0, 1, 0, new Color(37, 125, 141))));
		titlePanel.setLayout(new BorderLayout());
		PluginErrorPanel errorPanel = new PluginErrorPanel();
		errorPanel.setBorder(new EmptyBorder(2, 0, 3, 0));
		errorPanel.setContent("GP/hr", "Tracks your GP/hr over various trips.");
		titlePanel.add(errorPanel, "Center");
		return titlePanel;
	}

	// (editable) - inventory setups for reference
	private final JLabel sessionNameLabel = new JLabel("Unnamed Session");
	private final JLabel gpPerHourLabel = new JLabel(htmlLabel("GP/hr: ", "N/A"));
	private final JLabel netTotalLabel = new JLabel(htmlLabel("Net Total: ", "N/A"));
	private final JLabel totalGainsLabel = new JLabel(htmlLabel("Total Gains: ", "N/A"));
	private final JLabel totalLossesLabel = new JLabel(htmlLabel("Total Losses: ", "N/A"));
	private final JLabel sessionTimeLabel = new JLabel(htmlLabel("Session Time: ", "N/A"));
	private final JLabel tripCountLabel = new JLabel(htmlLabel("Trip Count: ", "N/A"));
	private final JLabel avgTripDurationLabel = new JLabel(htmlLabel("Avg Trip Duration: ", "N/A"));

	private JPanel buildSessionInfoPanel()
	{
		sessionInfoPanel.setLayout(new BorderLayout());
		sessionInfoPanel.setBorder(new EmptyBorder(0, 0, 4, 0));

		JPanel sessionInfoSection = new JPanel(new GridBagLayout());
		sessionInfoSection.setLayout(new GridLayout(8, 1, 0, 10));
		sessionInfoSection.setBorder(new EmptyBorder(10, 5, 3, 0));

		sessionNameLabel.setFont(FontManager.getRunescapeBoldFont());

		sessionInfoSection.add(sessionNameLabel);
		sessionInfoSection.add(gpPerHourLabel);
		sessionInfoSection.add(netTotalLabel);
		sessionInfoSection.add(totalGainsLabel);
		sessionInfoSection.add(totalLossesLabel);
		sessionInfoSection.add(sessionTimeLabel);
		sessionInfoSection.add(tripCountLabel);
		sessionInfoSection.add(avgTripDurationLabel);

		sessionInfoPanel.add(sessionInfoSection, "West");

		return sessionInfoPanel;
	}

	void updateTrips(Map<String, InventoryTotalRunData> trips)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (tripPanels.size() < trips.size())
			{
				ensureTripPanelCount(trips.size());
			}
			clientThread.invokeLater(() ->
			{
				List<InventoryTotalRunData> runDataSorted = trips.values().stream()
						.sorted(Comparator.comparingLong(o -> o.runStartTime)).collect(Collectors.toList());

				// TODO: try combinining neighboring runData if the quantityDifferences
				// match...?
				int tripIndex = 0;
				for (InventoryTotalRunData runData : runDataSorted)
				{
					boolean validTrip = renderTrip(runData, tripIndex);
					if (!validTrip)
						continue;

					tripIndex++;
				}
				for (int i = tripIndex; i < tripPanels.size(); ++i)
				{
					tripPanels.get(i).masterPanel.setVisible(false);
				}

				SessionStats stats = sessionManager.getActiveSessionStats();
				if (stats == null)
				{
					gpPerHourLabel.setText(htmlLabel("GP/hr: ", "N/A"));
					netTotalLabel.setText(htmlLabel("Net Total: ", "N/A"));
					totalGainsLabel.setText(htmlLabel("Total Gains: ", "N/A"));
					totalLossesLabel.setText(htmlLabel("Total Losses: ", "N/A"));
					sessionTimeLabel.setText(htmlLabel("Session Time: ", "N/A"));
					tripCountLabel.setText(htmlLabel("Trip Count: ", "N/A"));
					avgTripDurationLabel.setText(htmlLabel("Avg Trip Duration: ", "N/A"));

				} else
				{
					long runtime = stats.getSessionEndTime() - stats.getSessionStartTime();
					sessionNameLabel.setText("Unnamed Session");
					gpPerHourLabel.setText(htmlLabel("GP/hr: ",
							UIHelper.formatGp(UIHelper.getGpPerHour(runtime, stats.getNetTotal()), config.showExactGp())
									+ "/hr"));
					netTotalLabel.setText(
							htmlLabel("Net Total: ", UIHelper.formatGp(stats.getNetTotal(), config.showExactGp())));
					totalGainsLabel.setText(
							htmlLabel("Total Gains: ", UIHelper.formatGp(stats.getTotalGain(), config.showExactGp())));
					totalLossesLabel.setText(
							htmlLabel("Total Losses: ", UIHelper.formatGp(stats.getTotalLoss(), config.showExactGp())));
					sessionTimeLabel.setText(htmlLabel("Session Time: ", UIHelper.formatTime(runtime)));
					tripCountLabel.setText(htmlLabel("Trip Count: ", Integer.toString(stats.getTripCount())));
					avgTripDurationLabel.setText(htmlLabel("Avg Trip Duration: ", UIHelper.formatTime(stats.getAvgTripDuration())));
				}
			});
		});
	}

	boolean renderTrip(InventoryTotalRunData runData, int tripIndex)
	{
		List<InventoryTotalLedgerItem> ledger = plugin.getProfitLossLedger(runData);

		// filter out anything with no change or change that will get rounded to 0
		ledger = ledger.stream().filter(item -> Math.abs(item.getQty()) > (InventoryTotalPlugin.roundAmount/2f))
				.collect(Collectors.toList());

		// if there's nothing worthwhile that happened in this trip we don't need to
		// render it (unless it's our current trip which we always want to render)
		if (ledger.isEmpty() && !runData.isInProgress())
		{
			return false;
		}

		// sort by profit descending
		ledger = ledger.stream().sorted(Comparator.comparingLong(o -> -(o.getCombinedValue())))
				.collect(Collectors.toList());

		TripStats tripStats = getTripStats(ledger);

		TripPanelData tpData = tripPanels.get(tripIndex);
		tpData.masterPanel.setVisible(true);
		long runtime = (runData.runEndTime == null ? Instant.now().toEpochMilli() : runData.runEndTime) - runData.runStartTime;

		FontMetrics fontMetrics;
		try
		{
			fontMetrics = getGraphics().getFontMetrics(FontManager.getRunescapeSmallFont());
		}
		catch(Exception e)
		{
			log.error("getGraphics: " + getGraphics(), e);
			return false;
		}
		tpData.bottomRightLabel
				.setText(htmlLabel("Losses: ", QuantityFormatter.quantityToStackSize(tripStats.totalLosses)));
		tpData.bottomLeftLabel.setText(htmlLabel("GP/hr: ", UIHelper.formatGp(UIHelper.getGpPerHour(runtime, tripStats.getNetTotal()), config.showExactGp()) + "/hr"));
		tpData.topLeftLabel
				.setText(htmlLabel("Net Total: ", QuantityFormatter.quantityToStackSize(tripStats.netTotal)));
		tpData.topRightLabel.setText(htmlLabel("Gains: ", QuantityFormatter.quantityToStackSize(tripStats.totalGains)));
		tpData.topRightLabel
				.setBorder(new EmptyBorder(0, 535 - fontMetrics.stringWidth(tpData.topLeftLabel.getText()), 0, 0));
		tpData.titlePanel.setContent("Trip " + (tripIndex + 1), "Started " + UIHelper.getTimeAgo(runData.runStartTime));
		// buttons
		UIHelper.clearListeners(tpData.buttonLeft);
		tpData.buttonLeft.setEnabled(!sessionManager.getActiveSessionStartId().equals(runData.identifier));
		tpData.buttonLeft.setText("Set Start");
		tpData.buttonLeft.addActionListener((event) ->
		{
			sessionManager.setSessionStart(runData.identifier);
		});
		UIHelper.clearListeners(tpData.buttonMiddle);
		tpData.buttonMiddle.setEnabled((sessionManager.getActiveSessionEndId() == null) ?
			!runData.isInProgress() :
			!sessionManager.getActiveSessionEndId().equals(runData.identifier));
		tpData.buttonMiddle.setText("Set End");
		tpData.buttonMiddle.addActionListener((event) ->
		{
			sessionManager.setSessionEnd(runData.identifier);
		});
		UIHelper.clearListeners(tpData.buttonRight);
		tpData.buttonRight.setEnabled(!runData.isInProgress());
		tpData.buttonRight.setText("Delete");
		tpData.buttonRight.addActionListener((event) ->
		{
			int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this trip?",
					"Warning", JOptionPane.OK_CANCEL_OPTION);

			if (confirm == 0)
			{
				sessionManager.deleteSession(runData.identifier);
			}
		});

		return true;
	}

	// build out the pool
	void ensureTripPanelCount(int size)
	{
		while (tripPanels.size() < size)
		{
			TripPanelData data = buildTripPanel();
			this.sidePanel.add(data.masterPanel);
			tripPanels.add(data);
		}
	}

	TripStats getTripStats(List<InventoryTotalLedgerItem> ledger)
	{
		long gains = 0;
		long losses = 0;
		for (InventoryTotalLedgerItem item : ledger)
		{
			long value = item.getCombinedValue();
			if (value > 0)
			{
				gains += value;
			} else
			{
				losses += value;
			}
		}
		long net = gains + losses;
		return new TripStats(gains, losses, net);
	}

	private class TripPanelData
	{
		PluginErrorPanel titlePanel = new PluginErrorPanel();
		JToggleButton lootHeaderButtonPanel = new JToggleButton();
		JToggleButton hideItemButton = new JToggleButton();
		JLabel topLeftLabel = new JLabel(htmlLabel("Net Total: ", "N/A"));
		JLabel bottomLeftLabel = new JLabel(htmlLabel("GP/hr: ", "N/A"));
		JLabel topRightLabel = new JLabel(htmlLabel("Gains: ", "N/A"));
		JLabel bottomRightLabel = new JLabel(htmlLabel("Losses: ", "N/A"));
		JPanel masterPanel = new JPanel();
		JButton buttonLeft = new JButton("Left");
		JButton buttonMiddle = new JButton("Middle");
		JButton buttonRight = new JButton("Right");
	}

	private TripPanelData buildTripPanel()
	{
		TripPanelData data = new TripPanelData();
		JToggleButton lootHeaderButtonPanel = data.lootHeaderButtonPanel;
		JToggleButton hideItemButton = data.hideItemButton;
		JLabel bottomLeftLabel = data.bottomLeftLabel;
		JLabel topLeftLabel = data.topLeftLabel;
		JLabel bottomRightLabel = data.bottomRightLabel;
		JLabel topRightLabel = data.topRightLabel;
		JPanel masterPanel = data.masterPanel;
		PluginErrorPanel titlePanel = data.titlePanel;
		JButton buttonLeft = data.buttonLeft;
		JButton buttonMiddle = data.buttonMiddle;
		JButton buttonRight = data.buttonRight;

		masterPanel.setLayout(new BorderLayout());
		masterPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

		JPanel bottomInfo = new JPanel();
		JPanel topInfo = new JPanel();

		titlePanel.setBorder(new EmptyBorder(10, 0, 3, 0));
		titlePanel.setContent("Trip 1", "Repeated 4 times...");

		lootHeaderButtonPanel.setLayout(new GridLayout(2, 0, 0, 0));
		bottomInfo.setLayout(new GridLayout(0, 2, 0, 0));
		topInfo.setLayout(new BorderLayout());

		lootHeaderButtonPanel.setPreferredSize(new Dimension(200, 35));

		lootHeaderButtonPanel.setBorder(new EmptyBorder(4, 5, 0, 5));

		// hideItemButton.setIcon(INVISABLE_ICON);
		// hideItemButton.setSelectedIcon(VISIBLE_ICON);

		bottomLeftLabel.setFont(FontManager.getRunescapeSmallFont());
		topLeftLabel.setFont(FontManager.getRunescapeSmallFont());
		bottomRightLabel.setFont(FontManager.getRunescapeSmallFont());
		topRightLabel.setFont(FontManager.getRunescapeSmallFont());

		SwingUtil.removeButtonDecorations(hideItemButton);
		SwingUtil.removeButtonDecorations(lootHeaderButtonPanel);
		lootHeaderButtonPanel.setRolloverEnabled(false);

		hideItemButton.setPreferredSize(new Dimension(20, 18));

		// hideItemButton.addActionListener(e -> clientThread.invoke(() ->
		// updateLootGrid(lootDisplayMap())));

		// lootHeaderButtonPanel.addActionListener(e -> collapseLoot());

		topLeftLabel.setForeground(Color.WHITE);

		topInfo.setBorder(new EmptyBorder(0, 0, 0, 0));

		topInfo.add(topLeftLabel, "West");
		topInfo.add(hideItemButton, "East");

		topRightLabel.setBorder(new EmptyBorder(0, 48, 0, 0));

		topInfo.add(topRightLabel, "Center");
		bottomInfo.add(bottomLeftLabel);
		bottomInfo.add(bottomRightLabel);
		topInfo.setBackground(new Color(30, 30, 30));
		bottomInfo.setBackground(new Color(30, 30, 30));

		lootHeaderButtonPanel.add(topInfo, "North");
		lootHeaderButtonPanel.add(bottomInfo, "South");

		float fontSize = 16f;
		EmptyBorder buttonBorder = new EmptyBorder(2, 2, 2, 2);
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(0, 3, 0, 0));
		buttonLeft.setBorder(buttonBorder);
		buttonLeft.setFont(buttonLeft.getFont().deriveFont(fontSize));
		buttonPanel.add(buttonLeft);
		buttonMiddle.setFont(buttonMiddle.getFont().deriveFont(fontSize));
		buttonPanel.add(buttonMiddle);
		buttonRight.setFont(buttonRight.getFont().deriveFont(fontSize));
		buttonPanel.add(buttonRight);

		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new GridLayout(3, 0, 0, 0));
		// contentPanel.setPreferredSize(new Dimension(200, 400));
		contentPanel.add(titlePanel);
		contentPanel.add(lootHeaderButtonPanel);
		contentPanel.add(buttonPanel);
		contentPanel.setBackground(new Color(30, 30, 30));
		contentPanel.setBorder(new MatteBorder(1, 1, 1, 1, new Color(57, 57, 57)));

		masterPanel.add(contentPanel, "North");

		return data;
	}

	static String htmlLabel(String key, String valueStr)
	{
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
	}

	@RequiredArgsConstructor
	@Data
	private class TripStats
	{
		private final long totalGains;
		private final long totalLosses;
		private final long netTotal;
	}
}