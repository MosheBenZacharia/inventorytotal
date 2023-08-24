package com.ericversteeg;

import javax.swing.JPanel;
import java.util.LinkedList;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.*;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

public class SessionHistoryPanel extends JPanel
{

	private final InventoryTotalConfig config;
	private final InventoryTotalPlugin plugin;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final SessionManager sessionManager;
	GridBagConstraints constraints = new GridBagConstraints();
	private final JPanel historyPanelContainer = new JPanel();

	private final List<SessionHistoryPanelData> historyPanels = new LinkedList<>();


	SessionHistoryPanel(InventoryTotalPlugin plugin, InventoryTotalConfig config, ItemManager itemManager,
			ClientThread clientThread, SessionManager sessionManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.sessionManager = sessionManager;

		//TODO: setup gridbaglayout SAME EXACT WAY AS ACTIVE SESSION PANEL
	}

	void updateSessions()
	{
		//TODO: only update if something changed
		List<SessionStats> sessions = plugin.sessionHistory;
		for(int i=0;i<sessions.size();++i)
		{
			ensurePanelCount(i+1);
			renderHistoryPanel(sessions.get(i), historyPanels.get(i));
		}
	}

	void renderHistoryPanel(SessionStats sessionStats, SessionHistoryPanelData panelData)
	{

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
	}

	SessionHistoryPanelData buildHistoryPanel()
	{
		//error panel with name + date/time
		//net total 	gains
		//gp/hr			losses
		//trip count 	avg trip duration
		//hover changes background color ( to indicate you can click )
		//clicking expands/hides loot grid
		return new SessionHistoryPanelData();
	}
}
