package com.ericversteeg;

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

@Slf4j
class ActiveSessionPanel extends PluginPanel
{
	private static final String HTML_LABEL_TEMPLATE = "<html><body style='color:%s'>%s<span style='color:white'>%s</span></body></html>";
	private static final String sessionNameLabelPlaceholder = "Session Stats";
	private static final String gpPerHourLabelPrefix = "GP/hr: ";
	private static final String netTotalLabelPrefix = "Net Total: ";
	private static final String totalGainsLabelPrefix = "Total Gains: ";
	private static final String totalLossesLabelPrefix = "Total Losses: ";
	private static final String sessionTimeLabelPrefix = "Session Time: ";
	private static final String tripCountLabelPrefix = "Trip Count: ";
	private static final String avgTripDurationLabelPrefix = "Avg Trip Duration: ";
	private static final Color tripActiveBorderColor = new Color(37, 107, 31);
	private static final Color redLootBackgroundColor = new Color(48, 15, 15);
	private static final Color greenLootBackgroundColor = new Color(21, 43, 16);
	private static final Color borderColor = new Color(57, 57, 57);
	private static final ImageIcon PAUSE_ICON;
	private static final ImageIcon PLAY_ICON;
	private static final ImageIcon SESSIONINFO_GEAR_ICON;
	private static final ImageIcon SESSIONINFO_REFRESH_ICON;
	private static final ImageIcon SESSIONINFO_WRENCH_ICON;
	private static final ImageIcon SESSIONINFO_STOP_ICON;
	private static final ImageIcon SESSIONINFO_PLAY_ICON;
	private static final ImageIcon SESSIONINFO_TRASH_ICON;
	private static final ImageIcon SESSIONINFO_SAVE_ICON;
	private static final ImageIcon SESSIONINFO_GEAR_HOVER_ICON;
	private static final ImageIcon SESSIONINFO_REFRESH_HOVER_ICON;
	private static final ImageIcon SESSIONINFO_WRENCH_HOVER_ICON;
	private static final ImageIcon SESSIONINFO_STOP_HOVER_ICON;
	private static final ImageIcon SESSIONINFO_PLAY_HOVER_ICON;
	private static final ImageIcon SESSIONINFO_TRASH_HOVER_ICON;
	private static final ImageIcon SESSIONINFO_SAVE_HOVER_ICON;
	private static final int ITEMS_PER_ROW = 5;
	private static final Dimension ITEM_SIZE = new Dimension(40, 40);

	private final InventoryTotalConfig config;
	private final InventoryTotalPlugin plugin;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final SessionManager sessionManager;
	private final JPanel tripsPanel = new JPanel();
	GridBagConstraints constraints = new GridBagConstraints();

	// Panels
	private final JPanel sessionInfoPanel;
	private final List<TripPanelData> tripPanels = new LinkedList<>();

	ActiveSessionPanel(InventoryTotalPlugin plugin, InventoryTotalConfig config, ItemManager itemManager,
			ClientThread clientThread, SessionManager sessionManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.sessionManager = sessionManager;
		this.sessionInfoPanel = new JPanel();
	}

	void sidePanelInitializer()
	{
		this.setLayout(new BorderLayout());

		/* The main container, this holds the session info and trips */
		JPanel container = new JPanel();
		container.setLayout(new BorderLayout(0, 0));
		container.setBorder(new EmptyBorder(0, 0, 0, 0));
		container.setBackground(ColorScheme.DARK_GRAY_COLOR);

		tripsPanel.setLayout(new GridBagLayout());
		tripsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		/* This panel wraps the trips panel and guarantees the scrolling behaviour */
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(tripsPanel, BorderLayout.NORTH);

		/* The trips wrapper, this scrolling panel wraps the results container */
		JScrollPane tripsWrapper = new JScrollPane(wrapper);
		tripsWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tripsWrapper.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
		tripsWrapper.getVerticalScrollBar().setBorder(new EmptyBorder(0, 5, 0, 0));
		tripsWrapper.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		container.add(buildSessionInfoPanel(), BorderLayout.NORTH);
		container.add(tripsWrapper, BorderLayout.CENTER);

		add(container, BorderLayout.CENTER);

	}

	private JLabel startTrackingButton;
	private JLabel stopTrackingButton;
	private final JLabel sessionNameLabel = new JLabel(sessionNameLabelPlaceholder);
	private final JLabel gpPerHourLabel = new JLabel(htmlLabel(gpPerHourLabelPrefix, "N/A"));
	private final JLabel netTotalLabel = new JLabel(htmlLabel(netTotalLabelPrefix, "N/A"));
	private final JLabel totalGainsLabel = new JLabel(htmlLabel(totalGainsLabelPrefix, "N/A"));
	private final JLabel totalLossesLabel = new JLabel(htmlLabel(totalLossesLabelPrefix, "N/A"));
	private final JLabel sessionTimeLabel = new JLabel(htmlLabel(sessionTimeLabelPrefix, "N/A"));
	private final JLabel tripCountLabel = new JLabel(htmlLabel(tripCountLabelPrefix, "N/A"));
	private final JLabel avgTripDurationLabel = new JLabel(htmlLabel(avgTripDurationLabelPrefix, "N/A"));
	private final LootPanelData sessionLootPanelData = new LootPanelData();

	private JPanel buildSessionInfoPanel()
	{
		sessionInfoPanel.setLayout(new BorderLayout(0, 5));
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

		//icon buttons
		startTrackingButton = UIHelper.createIconButton(SESSIONINFO_PLAY_ICON, SESSIONINFO_PLAY_HOVER_ICON, "Start tracking new trips", ()-> { 
			sessionManager.startTracking(); 
			updateStopStartVisibility();
		});
		stopTrackingButton = UIHelper.createIconButton(SESSIONINFO_STOP_ICON, SESSIONINFO_STOP_HOVER_ICON, "Stop tracking new trips", ()-> { 
			sessionManager.stopTracking();
			updateStopStartVisibility();
		});
		updateStopStartVisibility();
		JLabel refreshPricesButton = UIHelper.createIconButton(SESSIONINFO_REFRESH_ICON, SESSIONINFO_REFRESH_HOVER_ICON, "Refresh prices", ()-> { clientThread.invokeLater(() -> {plugin.refreshPrices();});});
		JLabel deleteTripsButton = UIHelper.createIconButton(SESSIONINFO_TRASH_ICON, SESSIONINFO_TRASH_HOVER_ICON, "Delete all trips", ()-> { 

			int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete all trips?", "Warning",
						JOptionPane.OK_CANCEL_OPTION);
			if (confirm == 0)
			{
				sessionManager.deleteAllTrips();
				this.updateTrips();
			}
		});
		JLabel settingsButton = UIHelper.createIconButton(SESSIONINFO_GEAR_ICON, SESSIONINFO_GEAR_HOVER_ICON, "Open configuration", ()-> { clientThread.invokeLater(() -> {plugin.openConfiguration();});});
		JLabel saveButton = UIHelper.createIconButton(SESSIONINFO_SAVE_ICON, SESSIONINFO_SAVE_HOVER_ICON, "Save session", ()-> { 

			String name = JOptionPane.showInputDialog(this,
				"Enter the name of this session.",
				"Save Session",
				JOptionPane.PLAIN_MESSAGE);

			// cancel button was clicked
			if (name == null)
			{
				return;
			}
			if (name.isEmpty())
			{
				name = "Unnamed Session";
			}
			plugin.saveSession(name);
		});
		JLabel debugButton = UIHelper.createIconButton(SESSIONINFO_WRENCH_ICON, SESSIONINFO_WRENCH_HOVER_ICON, "Rebuild all trip panels", ()->
		{
			for (TripPanelData data : tripPanels)
			{
				this.tripsPanel.remove(data.masterPanel);
			}
			this.tripPanels.clear();
			this.updateTrips();
		});

		JPanel iconButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		iconButtons.add(startTrackingButton);
		iconButtons.add(stopTrackingButton);
		iconButtons.add(refreshPricesButton);
		iconButtons.add(deleteTripsButton);
		iconButtons.add(settingsButton);
		iconButtons.add(saveButton);
		iconButtons.add(debugButton);

		sessionLootPanelData.lootPanel.setLayout(new BorderLayout());
		sessionLootPanelData.lootPanel.setBorder(new MatteBorder(1,1,1,1,borderColor));
		sessionInfoPanel.add(sessionInfoSection, BorderLayout.NORTH);
		sessionInfoPanel.add(sessionLootPanelData.lootPanel, BorderLayout.CENTER);
		sessionInfoPanel.add(iconButtons, BorderLayout.SOUTH);

		return sessionInfoPanel;
	}

	void updateStopStartVisibility()
	{
		startTrackingButton.setVisible(!sessionManager.isTracking());
		stopTrackingButton.setVisible(sessionManager.isTracking());
	}

	// avoid GC
	private final List<InventoryTotalLedgerItem> emptyLedger = new LinkedList<>();

	void updateTrips()
	{
		Graphics graphics = getGraphics();
		if (graphics == null)
		{
			return;
		}
		Map<String, InventoryTotalRunData> trips = sessionManager.getActiveTrips();

		List<InventoryTotalRunData> runDataSorted = trips.values().stream()
				.sorted(Comparator.comparingLong(o -> o.runStartTime)).collect(Collectors.toList());

		int tripIndex = 0;
		previousLedger = null;
		previousRunData = null;
		repeatCount = 0;
		consecutiveRepeatCount = 0;
		for (InventoryTotalRunData runData : runDataSorted)
		{
			boolean validTrip = renderTrip(runData, tripIndex);
			if (!validTrip)
			{
				continue;
			}

			tripIndex++;
		}
		for (int i = (tripIndex - repeatCount); i < tripPanels.size(); ++i)
		{
			getPanelData(i).masterPanel.setVisible(false);
		}

		SessionStats stats = sessionManager.getActiveSessionStats();
		if (stats == null)
		{
			sessionNameLabel.setText(sessionNameLabelPlaceholder);
			gpPerHourLabel.setText(htmlLabel(gpPerHourLabelPrefix, "N/A"));
			netTotalLabel.setText(htmlLabel(netTotalLabelPrefix, "N/A"));
			totalGainsLabel.setText(htmlLabel(totalGainsLabelPrefix, "N/A"));
			totalLossesLabel.setText(htmlLabel(totalLossesLabelPrefix, "N/A"));
			sessionTimeLabel.setText(htmlLabel(sessionTimeLabelPrefix, "N/A"));
			tripCountLabel.setText(htmlLabel(tripCountLabelPrefix, "N/A"));
			avgTripDurationLabel.setText(htmlLabel(avgTripDurationLabelPrefix, "N/A"));
			lootGrid(emptyLedger, sessionLootPanelData);

		} else
		{
			sessionNameLabel.setText(sessionNameLabelPlaceholder);
			gpPerHourLabel.setText(htmlLabel(gpPerHourLabelPrefix,
					UIHelper.formatGp(UIHelper.getGpPerHour(stats.getSessionRuntime(), stats.getNetTotal()),
							config.showExactGp()) + "/hr"));
			netTotalLabel.setText(
					htmlLabel(netTotalLabelPrefix, UIHelper.formatGp(stats.getNetTotal(), config.showExactGp())));
			totalGainsLabel.setText(
					htmlLabel(totalGainsLabelPrefix, UIHelper.formatGp(stats.getTotalGain(), config.showExactGp())));
			totalLossesLabel.setText(
					htmlLabel(totalLossesLabelPrefix, UIHelper.formatGp(stats.getTotalLoss(), config.showExactGp())));
			sessionTimeLabel.setText(htmlLabel(sessionTimeLabelPrefix, UIHelper.formatTime(stats.getSessionRuntime())));
			tripCountLabel.setText(htmlLabel(tripCountLabelPrefix, Integer.toString(stats.getTripCount())));
			avgTripDurationLabel
					.setText(htmlLabel(avgTripDurationLabelPrefix, UIHelper.formatTime(stats.getAvgTripDuration())));
			lootGrid(InventoryTotalPlugin.getProfitLossLedger(stats.getInitialQtys(), stats.getQtys()),
					sessionLootPanelData);
		}
	}

	InventoryTotalRunData previousRunData = null;
	List<InventoryTotalLedgerItem> previousLedger = null;
	int repeatCount = 0;
	int consecutiveRepeatCount = 0;
	long previousGpPerHour = 0;

	boolean renderTrip(InventoryTotalRunData runData, int tripIndex)
	{
		List<InventoryTotalLedgerItem> ledger = InventoryTotalPlugin.getProfitLossLedger(runData.initialItemQtys,
				runData.itemQtys);

		// filter out anything with no change or change that will get rounded to 0
		ledger = ledger.stream().filter(item -> Math.abs(item.getQty()) > (InventoryTotalPlugin.roundAmount / 2f))
				.collect(Collectors.toList());

		// sort by profit descending
		ledger = ledger.stream().sorted(Comparator.comparingLong(o -> -(o.getCombinedValue())))
				.collect(Collectors.toList());

		if (!runData.isInProgress() && UIHelper.ledgersMatch(ledger, previousLedger))
		{
			consecutiveRepeatCount++;
			repeatCount++;
			TripPanelData tpData = getPanelData(tripIndex - repeatCount);
			TripStats tripStats = getTripStats(ledger);
			int startIndex = tripIndex - consecutiveRepeatCount;
			int endIndex = tripIndex;
			long runtime =  runData.getRuntime();
			tpData.titlePanel.setContent("Trips " + (startIndex + 1) + "-" + (endIndex + 1) + " (Identical)",
					"Started " + UIHelper.getTimeAgo(previousRunData.runStartTime));
			long gpPerHour = UIHelper.getGpPerHour(runtime, tripStats.getNetTotal());
			//average it into previous
			gpPerHour = (long) (((previousGpPerHour * consecutiveRepeatCount) + gpPerHour) / ((float) consecutiveRepeatCount + 1));
			tpData.bottomLeftLabel
					.setText(htmlLabel("GP/hr: ", UIHelper.formatGp(gpPerHour, config.showExactGp()) + "/hr"));
			updateButtonMiddle(tpData, runData);
			updateButtonRight(tpData, runData, false);
			updateButtonPause(tpData, runData);
			previousGpPerHour = gpPerHour;
			return true;
		}

		TripPanelData tpData = getPanelData(tripIndex - repeatCount);
		TripStats tripStats = getTripStats(ledger);
		tpData.setContentPanelBorder(
				sessionManager.isTimeInActiveSession(runData.runStartTime) ? tripActiveBorderColor : null);

		tpData.masterPanel.setVisible(true);
		long runtime = runData.getRuntime();

		FontMetrics fontMetrics = getGraphics().getFontMetrics(FontManager.getRunescapeSmallFont());
		tpData.bottomRightLabel
				.setText(htmlLabel("Losses: ", QuantityFormatter.quantityToStackSize(tripStats.totalLosses)));
		long gpPerHour = UIHelper.getGpPerHour(runtime, tripStats.getNetTotal());
		tpData.bottomLeftLabel
				.setText(htmlLabel("GP/hr: ", UIHelper.formatGp(gpPerHour, config.showExactGp()) + "/hr"));
		tpData.topLeftLabel
				.setText(htmlLabel("Net Total: ", QuantityFormatter.quantityToStackSize(tripStats.netTotal)));
		tpData.topRightLabel.setText(htmlLabel("Gains: ", QuantityFormatter.quantityToStackSize(tripStats.totalGains)));
		tpData.topRightLabel
				.setBorder(new EmptyBorder(0, 535 - fontMetrics.stringWidth(tpData.topLeftLabel.getText()), 0, 0));
		String title = "Trip " + (tripIndex + 1);
		if (runData.isInProgress())
		{
			title += " (Active)";
		}
		tpData.titlePanel.setContent(title, "Started " + UIHelper.getTimeAgo(runData.runStartTime));
		// buttons
		UIHelper.clearListeners(tpData.buttonLeft);
		tpData.buttonLeft.setEnabled(!sessionManager.getActiveSessionStartId().equals(runData.identifier));
		tpData.buttonLeft.setText("Set Start");
		tpData.buttonLeft.setToolTipText("Set this trip as the start of the active session.");
		tpData.buttonLeft.addActionListener((event) ->
		{
			sessionManager.setSessionStart(runData.identifier);
			this.updateTrips();
		});
		updateButtonMiddle(tpData, runData);
		UIHelper.clearListeners(tpData.buttonRight);
		updateButtonRight(tpData, runData, true);
		updateButtonPause(tpData, runData);

		lootGrid(ledger, tpData.lootPanelData);

		consecutiveRepeatCount = 0;
		previousLedger = ledger;
		previousRunData = runData;
		previousGpPerHour = gpPerHour;

		return true;
	}

	void updateButtonMiddle(TripPanelData tpData, InventoryTotalRunData runData)
	{
		UIHelper.clearListeners(tpData.buttonMiddle);
		tpData.buttonMiddle.setEnabled((sessionManager.getActiveSessionEndId() == null) ? !runData.isInProgress()
				: !sessionManager.getActiveSessionEndId().equals(runData.identifier));
		if (runData.isInProgress())
		{
			tpData.buttonMiddle.setText("Continue");
			tpData.buttonMiddle.setToolTipText("Include new trips in the active session.");
			tpData.buttonMiddle.addActionListener((event) ->
			{
				sessionManager.setSessionEnd(null);
				this.updateTrips();
			});
		}
		else
		{
			tpData.buttonMiddle.setText("Set End");
			tpData.buttonMiddle.setToolTipText("Set this trip as the end of the active session.");
			tpData.buttonMiddle.addActionListener((event) ->
			{
				sessionManager.setSessionEnd(runData.identifier);
				this.updateTrips();
			});
		}
	}

	void updateButtonRight(TripPanelData tpData, InventoryTotalRunData runData, boolean includeConfirmation)
	{
		tpData.buttonRight.setEnabled(!runData.isInProgress());
		tpData.buttonRight.setText("Delete");
		tpData.buttonRight.setToolTipText("Delete this trip.");
		tpData.buttonRight.addActionListener((event) ->
		{
			int confirm = 0;
			if (includeConfirmation)
			{
				confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this trip?", "Warning",
						JOptionPane.OK_CANCEL_OPTION);
			}

			if (confirm == 0)
			{
				sessionManager.deleteTrip(runData.identifier);
				this.updateTrips();
			}
		});
	}

	void updateButtonPause(TripPanelData tpData, InventoryTotalRunData runData)
	{
		tpData.pauseButton.setVisible(runData.isInProgress());
		tpData.pauseButton.setSelected(runData.isPaused);
		tpData.pauseButton.addActionListener((event) ->
		{
			runData.isPaused = tpData.pauseButton.isSelected();
			tpData.pauseButton.setSelected(runData.isPaused);
		});
	}

	TripPanelData getPanelData(int index)
	{
		ensureTripPanelCount(index + 1);
		return tripPanels.get((tripPanels.size() - 1) - index);
	}

	// build out the pool
	void ensureTripPanelCount(int size)
	{
		while (tripPanels.size() < size)
		{
			constraints.gridy = tripPanels.size();
			TripPanelData data = buildTripPanel();
			this.tripsPanel.add(data.masterPanel, constraints);
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

	private class LootPanelData
	{
		JPanel lootPanel = new JPanel();
		JPanel containerPanel = new JPanel();
		List<InventoryTotalLedgerItem> previousLedger = new LinkedList<InventoryTotalLedgerItem>();
	}

	private class TripPanelData
	{
		PluginErrorPanel titlePanel = new PluginErrorPanel();
		JToggleButton lootHeaderButtonPanel = new JToggleButton();
		JToggleButton pauseButton = new JToggleButton();
		JLabel topLeftLabel = new JLabel(htmlLabel("Net Total: ", "N/A"));
		JLabel bottomLeftLabel = new JLabel(htmlLabel("GP/hr: ", "N/A"));
		JLabel topRightLabel = new JLabel(htmlLabel("Gains: ", "N/A"));
		JLabel bottomRightLabel = new JLabel(htmlLabel("Losses: ", "N/A"));
		JPanel masterPanel = new JPanel();
		JButton buttonLeft = new JButton("Left");
		JButton buttonMiddle = new JButton("Middle");
		JButton buttonRight = new JButton("Right");
		JPanel contentPanel = new JPanel();
		LootPanelData lootPanelData = new LootPanelData();

		void setContentPanelBorder(Color color)
		{
			if (color == null)
			{
				color = borderColor;
			}
			contentPanel.setBorder(new MatteBorder(1, 1, 1, 1, color));
		}
	}

	private TripPanelData buildTripPanel()
	{
		TripPanelData data = new TripPanelData();
		JToggleButton lootHeaderButtonPanel = data.lootHeaderButtonPanel;
		JToggleButton pauseButton = data.pauseButton;
		JLabel bottomLeftLabel = data.bottomLeftLabel;
		JLabel topLeftLabel = data.topLeftLabel;
		JLabel bottomRightLabel = data.bottomRightLabel;
		JLabel topRightLabel = data.topRightLabel;
		JPanel masterPanel = data.masterPanel;
		PluginErrorPanel titlePanel = data.titlePanel;
		JButton buttonLeft = data.buttonLeft;
		JButton buttonMiddle = data.buttonMiddle;
		JButton buttonRight = data.buttonRight;
		JPanel contentPanel = data.contentPanel;
		JPanel lootPanel = data.lootPanelData.lootPanel;

		masterPanel.setLayout(new BorderLayout());
		masterPanel.setBorder(new EmptyBorder(5, 0, 0, 0));

		JPanel bottomInfo = new JPanel();
		JPanel topInfo = new JPanel();

		titlePanel.setBorder(new EmptyBorder(10, 10, 3, 10));
		titlePanel.setContent("Title", "Subtitle");

		lootHeaderButtonPanel.setLayout(new GridLayout(2, 0, 0, 0));
		bottomInfo.setLayout(new GridLayout(0, 2, 0, 0));
		topInfo.setLayout(new BorderLayout());

		lootHeaderButtonPanel.setPreferredSize(new Dimension(200, 35));

		lootHeaderButtonPanel.setBorder(new EmptyBorder(4, 5, 0, 5));

		pauseButton.setIcon(PAUSE_ICON);
		pauseButton.setSelectedIcon(PLAY_ICON);
		pauseButton.setToolTipText("Pause time tracking for this trip.");

		bottomLeftLabel.setFont(FontManager.getRunescapeSmallFont());
		topLeftLabel.setFont(FontManager.getRunescapeSmallFont());
		bottomRightLabel.setFont(FontManager.getRunescapeSmallFont());
		topRightLabel.setFont(FontManager.getRunescapeSmallFont());

		SwingUtil.removeButtonDecorations(pauseButton);
		SwingUtil.removeButtonDecorations(lootHeaderButtonPanel);
		lootHeaderButtonPanel.setRolloverEnabled(false);

		pauseButton.setPreferredSize(new Dimension(20, 20));
		// lootHeaderButtonPanel.addActionListener(e -> collapseLoot());

		topLeftLabel.setForeground(Color.WHITE);

		topInfo.setBorder(new EmptyBorder(0, 0, 0, 0));

		topInfo.add(topLeftLabel, "West");
		titlePanel.add(pauseButton, "East");

		topRightLabel.setBorder(new EmptyBorder(0, 48, 0, 0));

		topInfo.add(topRightLabel, "Center");
		bottomInfo.add(bottomLeftLabel);
		bottomInfo.add(bottomRightLabel);
		topInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		bottomInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);

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
		buttonMiddle.setBorder(buttonBorder);
		buttonPanel.add(buttonMiddle);
		buttonRight.setFont(buttonRight.getFont().deriveFont(fontSize));
		buttonPanel.add(buttonRight);
		buttonPanel.setPreferredSize(new Dimension(0, 30));

		lootPanel.setLayout(new BorderLayout());

		JPanel topPanel = new JPanel();
		topPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		topPanel.setLayout(new BorderLayout());
		topPanel.add(titlePanel, BorderLayout.NORTH);
		topPanel.add(lootHeaderButtonPanel, BorderLayout.SOUTH);

		contentPanel.setLayout(new BorderLayout());
		contentPanel.add(topPanel, BorderLayout.NORTH);
		contentPanel.add(lootPanel, BorderLayout.CENTER);
		contentPanel.add(buttonPanel, BorderLayout.SOUTH);
		contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		data.setContentPanelBorder(null);

		masterPanel.add(contentPanel, "North");

		return data;
	}

	public void lootGrid(List<InventoryTotalLedgerItem> ledger, LootPanelData lootPanelData)
	{
		if (UIHelper.ledgersMatch(ledger, lootPanelData.previousLedger))
		{
			return;
		}
		lootPanelData.previousLedger = ledger;
		JPanel containerCurrent = new JPanel();
		int totalItems = ledger.size();

		// tpData.containerPanel.setBorder(new EmptyBorder(2, 2, 5, 2));
		// containerCurrent.setBorder(new EmptyBorder(2, 2, 5, 2));

		// Calculates how many rows need to be display to fit all items
		final int rowSize = ((totalItems % ITEMS_PER_ROW == 0) ? 0 : 1) + totalItems / ITEMS_PER_ROW;
		lootPanelData.containerPanel.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));
		containerCurrent.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));

		// Create stacked items from the item list, calculates total price and then
		// displays all the items in the UI.
		for (InventoryTotalLedgerItem ledgerItem : ledger)
		{
			final JPanel slot = new JPanel();
			boolean wasGain = ledgerItem.getQty() > 0;
			slot.setLayout(new GridLayout(1, 1, 0, 0));
			slot.setBackground(wasGain ? greenLootBackgroundColor : redLootBackgroundColor);
			slot.setPreferredSize(ITEM_SIZE);

			final JLabel itemLabel = new JLabel();

			itemLabel.setToolTipText(UIHelper.buildToolTip(ledgerItem.getDescription(),
					UIHelper.formatQuantity(ledgerItem.getQty(), false),
					UIHelper.formatGp(ledgerItem.getPrice(), config.showExactGp()),
					UIHelper.formatGp(ledgerItem.getCombinedValue(), config.showExactGp())));
			itemLabel.setVerticalAlignment(SwingConstants.CENTER);
			itemLabel.setHorizontalAlignment(SwingConstants.CENTER);

			AsyncBufferedImage itemImage = itemManager.getImage(ledgerItem.getItemId(),
					(int) Math.ceil(Math.abs(ledgerItem.getQty())), Math.ceil(Math.abs(ledgerItem.getQty())) > 1);
			itemImage.addTo(itemLabel);

			slot.add(itemLabel);
			containerCurrent.add(slot);
		}
		if (totalItems < ITEMS_PER_ROW || totalItems % ITEMS_PER_ROW != 0)
		{
			int extraBoxes;
			if (totalItems % ITEMS_PER_ROW != 0 && totalItems >= ITEMS_PER_ROW)
			{
				int i = totalItems;
				while (i % ITEMS_PER_ROW != 0)
				{
					i++;
				}
				extraBoxes = i - totalItems;
			} else
			{
				extraBoxes = ITEMS_PER_ROW - totalItems;
			}
			for (int i = 0; i < extraBoxes; i++)
			{
				final JPanel slot = new JPanel();
				slot.setLayout(new GridLayout(1, 1, 0, 0));
				slot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				slot.setPreferredSize(ITEM_SIZE);

				containerCurrent.add(slot);
			}
		}

		lootPanelData.lootPanel.remove(lootPanelData.containerPanel);
		lootPanelData.containerPanel = containerCurrent;
		lootPanelData.lootPanel.add(lootPanelData.containerPanel);
		lootPanelData.lootPanel.revalidate();
		lootPanelData.lootPanel.repaint();
	}

	static String htmlLabel(String key, String valueStr)
	{
		return String.format(HTML_LABEL_TEMPLATE, ColorUtil.toHexColor(ColorScheme.LIGHT_GRAY_COLOR), key, valueStr);
	}

	static
	{
		BufferedImage pausePNG = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-pause.png");
		BufferedImage playPNG = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-play.png");

		PAUSE_ICON = new ImageIcon(pausePNG);
		PLAY_ICON = new ImageIcon(playPNG);

		//Session info tray
		final float hoverAlphaOffset = .53f;
		final BufferedImage importIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-gear.png");
		SESSIONINFO_GEAR_ICON = new ImageIcon(importIcon);
		SESSIONINFO_GEAR_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(importIcon, hoverAlphaOffset));
		final BufferedImage refreshIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-refresh.png");
		SESSIONINFO_REFRESH_ICON = new ImageIcon(refreshIcon);
		SESSIONINFO_REFRESH_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(refreshIcon, hoverAlphaOffset));
		final BufferedImage wrenchIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-wrench.png");
		SESSIONINFO_WRENCH_ICON = new ImageIcon(wrenchIcon);
		SESSIONINFO_WRENCH_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(wrenchIcon, hoverAlphaOffset));
		final BufferedImage stopIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-stop.png");
		SESSIONINFO_STOP_ICON = new ImageIcon(stopIcon);
		SESSIONINFO_STOP_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(stopIcon, hoverAlphaOffset));
		final BufferedImage playIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-play.png");
		SESSIONINFO_PLAY_ICON = new ImageIcon(playIcon);
		SESSIONINFO_PLAY_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(playIcon, hoverAlphaOffset));
		final BufferedImage trashIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-trash.png");
		SESSIONINFO_TRASH_ICON = new ImageIcon(trashIcon);
		SESSIONINFO_TRASH_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(trashIcon, hoverAlphaOffset));
		final BufferedImage saveIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-save.png");
		SESSIONINFO_SAVE_ICON = new ImageIcon(saveIcon);
		SESSIONINFO_SAVE_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(saveIcon, hoverAlphaOffset));
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