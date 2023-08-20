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
	private final InventoryTotalConfig config;
	private final InventoryTotalPlugin plugin;
	private final JPanel sidePanel;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SessionManager sessionManager;

	@Inject
	SessionPanel(InventoryTotalPlugin plugin, InventoryTotalConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		this.sidePanel = new JPanel();
	}

	void sidePanelInitializer()
	{
		this.setLayout(new BorderLayout());
		this.setBorder(new EmptyBorder(10, 10, 10, 10));
		this.sidePanel.setLayout(new BoxLayout(this.sidePanel, BoxLayout.Y_AXIS));

		this.add(sidePanel, "North");
	}

	void updateTrips(Map<String, InventoryTotalRunData> trips)
	{
		//Map<String, InventoryTotalRunData> trips = sessionManager.getActiveTrips();
		List<InventoryTotalRunData> runDataSorted = trips.values().stream().sorted(Comparator.comparingLong(o -> o.runStartTime))
			.collect(Collectors.toList());

		//TODO: try combinining neighboring runData if the quantityDifferences match...?
		int tripIndex = 0;
		for (InventoryTotalRunData runData : runDataSorted)
		{
			ensureTripBlockSize(tripIndex + 1);
			boolean validTrip = renderTrip(runData, tripIndex);
			if (!validTrip)
				continue;

			tripIndex++;
		}
		// for(int i = tripIndex; i < tripBlock.size(); ++i)
		// {
		// 	tripBlock[i].setVisible(false);
		// }
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
		log.info("net: "+tripStats.netTotal);

		//tripblock[i].setVisible(true);

		return true;
	}

	//build out the pool 
	void ensureTripBlockSize(int size)
	{

	}

	TripStats getTripStats(List<InventoryTotalLedgerItem> ledger)
	{
		long gains = 0;
		long losses = 0;
		for(InventoryTotalLedgerItem item : ledger)
		{
			long value = item.getCombinedValue();
			if(value > 0)
			{
				gains += value;
			}
			else
			{
				losses += value;
			}
		}
		long net = gains + losses;
		return new TripStats(gains, losses, net);
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