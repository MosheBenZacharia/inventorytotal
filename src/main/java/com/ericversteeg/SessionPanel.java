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
	private final JPanel sidePanel;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SessionManager sessionManager;

	// Panels
	private final JPanel titlePanel;
	private final List<TripPanelData> tripPanels = new LinkedList<>();

	@Inject
	SessionPanel(InventoryTotalPlugin plugin, InventoryTotalConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		this.sidePanel = new JPanel();
		this.titlePanel = new JPanel();
	}

	void sidePanelInitializer()
	{
		this.setLayout(new BorderLayout());
		this.setBorder(new EmptyBorder(10, 10, 10, 10));
		this.sidePanel.setLayout(new BoxLayout(this.sidePanel, BoxLayout.Y_AXIS));
		this.sidePanel.add(this.buildTitlePanel());
		this.sidePanel.add(Box.createRigidArea(new Dimension(0, 5)));
		JButton button = new JButton("Rebuild");
		button.addActionListener((o)->
		{
			for(TripPanelData data : tripPanels)
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
		errorPanel.setContent("GP Per Hour", "Tracks your GP/hr over various trips.");
		titlePanel.add(errorPanel, "Center");
		return titlePanel;
	}

	void updateTrips(Map<String, InventoryTotalRunData> trips)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (tripPanels.size() < trips.size())
			{
				ensureTripPanelCount(trips.size());
			}
			clientThread.invokeLater(()->
			{
				List<InventoryTotalRunData> runDataSorted = trips.values().stream()
						.sorted(Comparator.comparingLong(o -> o.runStartTime)).collect(Collectors.toList());
	
				// TODO: try combinining neighboring runData if the quantityDifferences
				// match...?
				int tripIndex = 0;
				for (InventoryTotalRunData runData : runDataSorted)
				{
					// ensureTripPanelCount(tripIndex + 1);
					boolean validTrip = renderTrip(runData, tripIndex);
					if (!validTrip)
						continue;
	
					tripIndex++;
				}
				for (int i = tripIndex; i < tripPanels.size(); ++i)
				{
					tripPanels.get(i).masterPanel.setVisible(false);
				}
			});
		});
	}

	boolean renderTrip(InventoryTotalRunData runData, int tripIndex)
	{
		List<InventoryTotalLedgerItem> ledger = plugin.getProfitLossLedger(runData);

		// filter out anything with no change or change that will get rounded to 0
		ledger = ledger.stream().filter(item -> Math.abs(item.getQty()) >= InventoryTotalPlugin.roundAmount)
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
		log.info("trip " + tripIndex);
		log.info("net: " + tripStats.netTotal);

		TripPanelData tripPanelData = tripPanels.get(tripIndex);
		tripPanelData.masterPanel.setVisible(true);

		FontMetrics fontMetrics = getGraphics().getFontMetrics(FontManager.getRunescapeSmallFont());
		tripPanelData.bottomRightLabel
				.setText(htmlLabel("Losses: ", QuantityFormatter.quantityToStackSize(tripStats.totalLosses)));
		// tripPanelData.bottomLeftLabel.setText(htmlLabel("???: ",
		// doubleFormatNumber(convertToGpPerHour(kills,Kph))));
		tripPanelData.topLeftLabel
				.setText(htmlLabel("Net Total: ", QuantityFormatter.quantityToStackSize(tripStats.netTotal)));
		tripPanelData.topRightLabel
				.setText(htmlLabel("Gains: ", QuantityFormatter.quantityToStackSize(tripStats.totalGains)));
		tripPanelData.topRightLabel.setBorder(
				new EmptyBorder(0, 535 - fontMetrics.stringWidth(tripPanelData.topLeftLabel.getText()), 0, 0));

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
		JToggleButton lootHeaderButtonPanel = new JToggleButton();
		JToggleButton hideItemButton = new JToggleButton();
		JLabel topLeftLabel = new JLabel(htmlLabel("Net Total: ", "N/A"));
		JLabel bottomLeftLabel = new JLabel(htmlLabel("???: ", "N/A"));
		JLabel topRightLabel = new JLabel(htmlLabel("Gains: ", "N/A"));
		JLabel bottomRightLabel = new JLabel(htmlLabel("Losses: ", "N/A"));
		JPanel masterPanel = new JPanel();
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

		masterPanel.setLayout(new BorderLayout());
		masterPanel.setBorder(new EmptyBorder(5,0,0,0));

		JPanel bottomInfo = new JPanel();
		JPanel topInfo = new JPanel();

		PluginErrorPanel titlePanel = new PluginErrorPanel();
		titlePanel.setBorder(new EmptyBorder(2, 0, 3, 0));
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
		
		JPanel contentPanel = new JPanel();
		contentPanel.setPreferredSize(new Dimension(200, 80));
		contentPanel.add(titlePanel, "North");
		contentPanel.add(lootHeaderButtonPanel, "South");
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