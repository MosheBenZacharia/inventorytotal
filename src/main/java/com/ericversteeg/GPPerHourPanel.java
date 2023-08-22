package com.ericversteeg;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.JPanel;
import javax.swing.border.*;

import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

class GPPerHourPanel extends PluginPanel
{
	private static final Color lineColor = ColorScheme.BRAND_ORANGE;

	// this panel will hold either the active session panel or the session history panel
	private final JPanel display = new JPanel();

	private final MaterialTabGroup tabGroup = new MaterialTabGroup(display);
	private final MaterialTab sessionHistoryTab;
	private final JPanel titlePanel = new JPanel();

	@Getter
	private final ActiveSessionPanel activeSessionPanel;
	@Getter
	private final SessionHistoryPanel sessionHistoryPanel;

	GPPerHourPanel(ActiveSessionPanel activeSessionPanel, SessionHistoryPanel sessionHistoryPanel)
	{
		super(false);

		this.activeSessionPanel = activeSessionPanel;
		this.sessionHistoryPanel = sessionHistoryPanel;

		buildTitlePanel();

		this.setLayout(new BorderLayout());
		this.setBackground(ColorScheme.DARK_GRAY_COLOR);
		this.setBorder(new EmptyBorder(10, 10, 10, 10));

		MaterialTab activeSessionTab = new MaterialTab("Active Session", tabGroup, activeSessionPanel);
		sessionHistoryTab = new MaterialTab("Session History", tabGroup, sessionHistoryPanel);

		tabGroup.setBorder(new EmptyBorder(5, 0, 0, 0));
		tabGroup.addTab(activeSessionTab);
		tabGroup.addTab(sessionHistoryTab);
		tabGroup.select(activeSessionTab); // selects the default selected tab

		JPanel centerPanel = new JPanel();
		centerPanel.add(tabGroup, BorderLayout.NORTH);
		centerPanel.add(display, BorderLayout.CENTER);

		add(titlePanel, BorderLayout.NORTH);
		add(centerPanel, BorderLayout.CENTER);
	}

	private JPanel buildTitlePanel()
	{
		titlePanel.setBorder(
			new CompoundBorder(new EmptyBorder(5, 0, 5, 0), new MatteBorder(0, 0, 1, 0, lineColor)));
		titlePanel.setLayout(new BorderLayout());
		PluginErrorPanel errorPanel = new PluginErrorPanel();
		errorPanel.setBorder(new EmptyBorder(2, 0, 10, 0));
		errorPanel.setContent("GP/hr", "Tracks your GP/hr over various trips.");
		titlePanel.add(errorPanel, "Center");
		return titlePanel;
	}

	boolean isShowingActiveSession()
	{
		return activeSessionPanel.isShowing();
	}

	void showActiveSession()
	{
		if (activeSessionPanel.isShowing())
		{
			return;
		}

		tabGroup.select(sessionHistoryTab);
		revalidate();
	}
}