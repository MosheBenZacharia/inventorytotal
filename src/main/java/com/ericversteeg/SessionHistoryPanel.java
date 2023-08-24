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
import net.runelite.client.ui.components.IconTextField;
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
		container.setBorder(new EmptyBorder(10, 10, 10, 10));
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
				updateFilter(true);
			}
		});
		searchBar.addClearListener(() -> updateFilter(true));

		historyPanelContainer.setLayout(new GridBagLayout());
		historyPanelContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		/* This panel wraps the results panel and guarantees the scrolling behaviour */
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(historyPanelContainer, BorderLayout.NORTH);

		/* The results wrapper, this scrolling panel wraps the results container */
		resultsWrapper = new JScrollPane(wrapper);
		resultsWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		resultsWrapper.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
		resultsWrapper.getVerticalScrollBar().setBorder(new EmptyBorder(0, 5, 0, 0));
		resultsWrapper.setVisible(true);

		container.add(searchBar, BorderLayout.NORTH);
		container.add(resultsWrapper, BorderLayout.CENTER);

		add(container, BorderLayout.CENTER);
	}

	void updateFilter(boolean resetScroll)
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
		if(!searchBar.getText().isEmpty())
		{
			sessions = filterSessions(sessions, searchBar.getText());
		}
		int sessionIndex;
		for (sessionIndex = 0; sessionIndex < sessions.size(); ++sessionIndex)
		{
			ensurePanelCount(sessionIndex + 1);
			renderHistoryPanel(sessions.get(sessionIndex), historyPanels.get(sessionIndex));
		}
		for(int i = sessionIndex; i < historyPanels.size(); ++i)
		{
			historyPanels.get(i).masterPanel.setVisible(false);
		}
	}

	public List<SessionStats> filterSessions(List<SessionStats> sessionStats, String textToFilter)
	{
		final String textToFilterLower = textToFilter.toLowerCase();
		return sessionStats.stream()
			.filter(i -> i.getSessionName().toLowerCase().contains(textToFilterLower))
			.collect(Collectors.toList());
	}

	void renderHistoryPanel(SessionStats sessionStats, SessionHistoryPanelData panelData)
	{
		panelData.masterPanel.setVisible(true);
		panelData.title.setText(sessionStats.sessionName);
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
		JLabel title = new JLabel();

		SessionHistoryPanelData()
		{
			masterPanel.add(title);
		}
	}

	SessionHistoryPanelData buildHistoryPanel()
	{
		// error panel with name + date/time
		// net total gains
		// gp/hr losses
		// trip count avg trip duration
		// hover changes background color ( to indicate you can click )
		// clicking expands/hides loot grid

		return new SessionHistoryPanelData();
	}
}
