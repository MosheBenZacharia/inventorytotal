/*
 * Copyright (c) 2023, Moshe Ben-Zacharia <https://github.com/MosheBenZacharia>, Eric Versteeg <https://github.com/erversteeg>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.ericversteeg;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;

import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import javax.inject.Inject;
import javax.swing.SwingUtilities;

import com.ericversteeg.itemcharges.ChargedItemManager;
import com.ericversteeg.weaponcharges.WeaponChargesManager;
import com.google.gson.Gson;
import com.google.inject.Provides;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "GP Per Hour",
	description = "Tracks your gp/hr over various trips.",
	tags = {
		"inventory",
		"total",
		"profit",
		"tracker",
		"loss",
		"gp",
		"per",
		"hour",
		"prices",
		"gold farming",
		"gp/hr"
	}
)

@Slf4j
//Should probably be renamed to GPPerHourPlugin...
public class InventoryTotalPlugin extends Plugin
{
	static final int COINS = ItemID.COINS_995;
	static final int NO_PROFIT_LOSS_TIME = -1;
	static final int RUNEPOUCH_ITEM_ID = 12791;
	static final int DIVINE_RUNEPOUCH_ITEM_ID = 27281;
	public static final float roundAmount = 0.05f;

    // static item prices so that when ItemManager updates, the Profit / Loss value doesn't all of a sudden change
    // this is cleared and repopulated at the start of each new run (after bank) and whenever new items hit the inventory
    private static final Map<Integer, Float> itemPrices = new HashMap<>();
	//so we can do name lookups on the swing thread
	private static final Map<Integer, String> itemNames = new HashMap<>();

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private InventoryTotalOverlay overlay;

	@Inject
	private WeaponChargesManager weaponChargesManager;

	@Inject
	private ChargedItemManager chargedItemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	@Getter
	private Client client;

	@Inject
	private InventoryTotalConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private LootingBagManager lootingBagManager;
	
	@Inject
	private EventBus eventBus;
	
	@Inject
	private ConfigManager configManager;
	
	@Inject
	private Gson gson;
	
    @Inject
    private ClientToolbar clientToolbar;

	@Getter
	private SessionManager sessionManager;

	private GPPerHourPanel gpPerHourPanel;
	private ActiveSessionPanel activeSessionPanel;
	private SessionHistoryPanel sessionHistoryPanel;

	@Getter
	private InventoryTotalRunData runData;
	private InventoryTotalGoldDrops goldDropsObject;

	@Getter
	private InventoryTotalMode mode = InventoryTotalMode.TOTAL;

	@Getter
	private InventoryTotalState state = InventoryTotalState.NONE;
	@Getter
	private InventoryTotalState previousState = InventoryTotalState.NONE;

	@Getter @Setter
	private long totalGp = 0;
	private Long previousTotalGp = null;

	private long initialGp = 0;
	
    private BufferedImage icon;
    private NavigationButton navButton;
	private Map<Integer, Float> inventoryQtyMap = new HashMap<>();
	private Map<Integer, Float> equipmentQtyMap = new HashMap<>();
	private HashSet<String> ignoredItems = new HashSet<>();

	@Getter
	private Widget inventoryWidget;
	private ItemContainer inventoryItemContainer;
	private ItemContainer equipmentItemContainer;
	private boolean postNewRun = false;
	private long newRunTick = 0;

	// from ClueScrollPlugin
	private static final int[] RUNEPOUCH_AMOUNT_VARBITS = {
			Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2, Varbits.RUNE_POUCH_AMOUNT3, Varbits.RUNE_POUCH_AMOUNT4
	};
	private static final int[] RUNEPOUCH_RUNE_VARBITS = {
			Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3, Varbits.RUNE_POUCH_RUNE4
	};

	@Override
	protected void startUp() throws Exception
	{
		if (config.showTripOverlay())
			overlayManager.add(overlay);

		goldDropsObject = new InventoryTotalGoldDrops(client, itemManager, config, configManager);
		eventBus.register(lootingBagManager);
		eventBus.register(weaponChargesManager);
		eventBus.register(chargedItemManager);
		weaponChargesManager.startUp();
		
		sessionManager = new SessionManager(this, config, executor, gson);
		buildSidePanel();
		updatePanels();
		refreshIgnoredItems();
		checkLoadingState(true);
		lastTickTime = null;
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		eventBus.unregister(lootingBagManager);
		eventBus.unregister(weaponChargesManager);
		eventBus.unregister(chargedItemManager);
		weaponChargesManager.shutDown();
		clientToolbar.removeNavigation(navButton);
		if (this.currentProfileKey != null)
		{
			writeSavedData(this.currentProfileKey);
		}
	}

	private String currentProfileKey;

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		lastTickTime = null;
		checkLoadingState(false);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		lastTickTime = null;
	}

	private void checkLoadingState(boolean isStartingUp)
	{
		String profileKey = configManager.getRSProfileKey();

		if (profileKey != null)
		{
			//getting profile for first time
			if (this.currentProfileKey == null || isStartingUp)
			{
				loadData();
			}
			//profile switched
			else if (!profileKey.equals(this.currentProfileKey))
			{
				writeSavedData(this.currentProfileKey);
				loadData();
			}
		}
		//lost profile somehow
		else if (this.currentProfileKey != null)
		{
			writeSavedData(this.currentProfileKey);
		}

		this.currentProfileKey = profileKey;
	}

	private void loadData()
	{
		lootingBagManager.loadConfigData();
		chargedItemManager.loadConfigData();
		sessionManager.reloadSessions();
		sessionManager.deleteAllTrips();
		sessionManager.stopTracking();
		runData = getSavedData();
		sessionManager.startTracking();
		previousTotalGp = null;
	}

	void updatePanels()
	{
		if (navButton.isSelected() && gpPerHourPanel.isShowingActiveSession())
		{
			SessionStats sessionStats = sessionManager.getActiveSessionStats();
			//ensure we load these after a restart
			if (sessionStats != null)
			{
				ensureSessionNameAndPriceLoaded(sessionStats);
			}
			SwingUtilities.invokeLater(() -> activeSessionPanel.updateTrips());
		}
		if (navButton.isSelected() && gpPerHourPanel.isShowingSessionHistory() && sessionManager.sessionHistoryDirty)
		{
			//ensure we load these after a restart
			for (SessionStats sessionStats : sessionManager.sessionHistory)
			{
				ensureSessionNameAndPriceLoaded(sessionStats);
			}
			SwingUtilities.invokeLater(() -> sessionHistoryPanel.updateSessions());
			sessionManager.sessionHistoryDirty = false;
		}
	}

	void ensureSessionNameAndPriceLoaded(SessionStats sessionStats)
	{
		for(Integer intialItemId : sessionStats.getInitialQtys().keySet())
		{
			ensureNameAndPriceLoaded(intialItemId);
		}
		for(Integer itemId : sessionStats.getQtys().keySet())
		{
			ensureNameAndPriceLoaded(itemId);
		}
	}

	void ensureNameAndPriceLoaded(Integer itemId)
	{
		if (!InventoryTotalPlugin.itemNames.containsKey(itemId))
		{
			ItemComposition composition = itemManager.getItemComposition(itemId);
			itemNames.put(itemId, composition.getName());
		}
		if (!InventoryTotalPlugin.itemPrices.containsKey(itemId))
		{
			itemPrices.put(itemId, getPrice(itemId));
		}
	}

    private void buildSidePanel()
    {
        activeSessionPanel = new ActiveSessionPanel(this, config, itemManager, clientThread, sessionManager);
        activeSessionPanel.sidePanelInitializer();

		sessionHistoryPanel = new SessionHistoryPanel(this, config, itemManager, clientThread, sessionManager);

		gpPerHourPanel = new GPPerHourPanel(activeSessionPanel, sessionHistoryPanel);

        icon = ImageUtil.loadImageResource(getClass(), "/gpperhour-icon.png");
        navButton = buildNavButton();
		if (config.enableSessionPanel())
			clientToolbar.addNavigation(navButton);
    }

	@Subscribe(priority = -1)//run after xpdrop plugin to overwrite their colors
	public void onScriptPreFired(ScriptPreFired scriptPreFired)
	{
		goldDropsObject.onScriptPreFired(scriptPreFired);
	}

	private Long lastTickTime = null;
	
    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
		if (runData == null)
			return;
		updatePluginState(false);
		updatePanels();
		updateChargeableItemsNeedingCheck();
		
		if (this.state == InventoryTotalState.RUN && !runData.isPaused && lastTickTime != null)
		{
			runData.runtime += Instant.now().toEpochMilli() - lastTickTime;
		}
		lastTickTime = Instant.now().toEpochMilli();

		checkTickProfit();
    }

	// If profit total changed generate gold drop (nice animation for showing gold earn or loss)
	void checkTickProfit()
	{
		boolean isRun = this.state == InventoryTotalState.RUN;

		if (!isRun)
			return;
		if (runData.isBankDelay)
			return;

		if (previousTotalGp == null)
		{
			previousTotalGp = Long.valueOf(totalGp);
			return;
		}
        long tickProfit = (totalGp - previousTotalGp);
		previousTotalGp = Long.valueOf(totalGp);
		//avoid rounding issues with charges
		if (Math.abs(tickProfit) <= 1l)
			return;

		//unpause run automatically
		if (runData.isPaused && config.autoResumeTrip())
		{
			runData.isPaused = false;
		}
		if (config.goldDrops())
		{
			goldDropsObject.requestGoldDrop(tickProfit);
		}
	}

	@Provides
	InventoryTotalConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InventoryTotalConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(InventoryTotalConfig.GROUP))
		{
			if (event.getKey().equals(InventoryTotalConfig.showTripOverlayKeyName))
			{
				if (config.showTripOverlay())
					overlayManager.add(overlay);
				else
					overlayManager.remove(overlay);
			}
			else if (event.getKey().equals(InventoryTotalConfig.enableSessionPanelKeyName))
			{
				if (config.enableSessionPanel())
					clientToolbar.addNavigation(navButton);
				else
					clientToolbar.removeNavigation(navButton);

				sessionManager.refreshSessionTracking();
			}
			else if (event.getKey().equals(InventoryTotalConfig.enableSessionTrackingKeyName))
			{
				sessionManager.refreshSessionTracking();
			}
			else if (event.getKey().equals(InventoryTotalConfig.sidePanelPositionKeyName))
			{
				clientToolbar.removeNavigation(navButton);
				//need to rebuild it for some reason (i think its a bug in core)
				navButton = buildNavButton();
				if (config.enableSessionPanel())
				{
					clientToolbar.addNavigation(navButton);
				}
			}
			else if (event.getKey().equals(InventoryTotalConfig.ignoredItemsKey))
			{
				refreshIgnoredItems();
			}
			else if (event.getKey().startsWith("tokkul"))
			{
				refreshPrice(ItemID.TOKKUL);
			}
			else if (event.getKey().startsWith("crystalShard"))
			{
				refreshPrice(ItemID.CRYSTAL_SHARD);
			}
			else if (event.getKey().startsWith("crystalDust"))
			{
				refreshPrice(ItemID.CRYSTAL_DUST_23964);
			}
			else if (event.getKey().startsWith("mermaidsTear"))
			{
				refreshPrice(ItemID.MERMAIDS_TEAR);
			}
			else if (event.getKey().startsWith("stardust"))
			{
				refreshPrice(ItemID.STARDUST);
			}
			else if (event.getKey().startsWith("unidentifiedMinerals"))
			{
				refreshPrice(ItemID.UNIDENTIFIED_MINERALS);
			}
			else if (event.getKey().startsWith("goldenNugget"))
			{
				refreshPrice(ItemID.GOLDEN_NUGGET);
			}
			else if (event.getKey().startsWith("hallowedMark"))
			{
				refreshPrice(ItemID.HALLOWED_MARK);
			}
			else if (event.getKey().startsWith("abyssalPearls"))
			{
				refreshPrice(ItemID.ABYSSAL_PEARLS);
			}
		}
	}

	private void refreshIgnoredItems()
	{
		ignoredItems.clear();

		String[] items = config.ignoredItems().split(",");
		for (int i=0;i<items.length;++i)
		{
			ignoredItems.add(items[i].trim().toLowerCase());
		}
	}

	private void refreshPrice(int itemID)
	{
		clientThread.invoke(()->{

			if (itemPrices.remove(itemID) != null)
			{
				getPrice(itemID);
			}
		});
	}

	private NavigationButton buildNavButton()
	{
		return NavigationButton.builder().tooltip("GP Per Hour").icon(icon).priority(config.sidePanelPosition()).panel(gpPerHourPanel).build();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		updatePluginState(false);
	}

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getId() == ObjectID.BANK_DEPOSIT_BOX 
			|| event.getId() == ObjectID.DEPOSIT_POOL
			|| event.getId() == ObjectID.DEPOSIT_POT)
		{
			updatePluginState(true);
		}
	}

	boolean isBanking()
	{
		//Collect on bank
		//Don't want it to appear there but have it count as bank still
		Widget collectOnBank = client.getWidget(402, 2);
		if (collectOnBank != null && !collectOnBank.isHidden())
		{
			return true;
		}
		//Grand exchange can be open while inventory widget is closed, same functionality as above
		Widget grandExchange = client.getWidget(WidgetInfo.GRAND_EXCHANGE_WINDOW_CONTAINER);
		if (grandExchange != null && !grandExchange.isHidden())
		{
			return true;
		}

		if (inventoryWidget == null || inventoryWidget.getCanvasLocation().getX() < 0 || inventoryWidget.isHidden())
		{
			Widget [] altInventoryWidgets = new Widget[]
			{
				//Bank
				client.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER),
				//GE
				client.getWidget(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER),
				//Bank with equipment view open
				client.getWidget(WidgetID.BANK_INVENTORY_GROUP_ID, 4),
				//Bank with looting bag open
				client.getWidget(WidgetID.BANK_INVENTORY_GROUP_ID, 5),
				//Deposit box open
				client.getWidget(268, 0),
				//COX storage open
				client.getWidget(WidgetID.CHAMBERS_OF_XERIC_STORAGE_UNIT_INVENTORY_GROUP_ID, 1)
			};

			for (Widget altInventoryWidget: altInventoryWidgets)
			{
				inventoryWidget = altInventoryWidget;
				if (inventoryWidget != null && !inventoryWidget.isHidden())
				{
					return true;
				}
			}
		}
		return false;
	}
	
	void updatePluginState(boolean forceBanking)
	{
		if (runData == null)
			return;
		inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);

		inventoryItemContainer = client.getItemContainer(InventoryID.INVENTORY);
		equipmentItemContainer = client.getItemContainer(InventoryID.EQUIPMENT);

		if (config.enableProfitLoss())
		{
			setMode(InventoryTotalMode.PROFIT_LOSS);
		}
		else
		{
			setMode(InventoryTotalMode.TOTAL);
		}

		boolean isBank = runData.isFirstRun || forceBanking || isBanking();

		if (isBank)
		{
			setState(InventoryTotalState.BANK);
		}
		else
		{
			setState(InventoryTotalState.RUN);
		}

		boolean newRun = getPreviousState() == InventoryTotalState.BANK && getState() == InventoryTotalState.RUN;
		
		getRunData().itemQtys.clear();
		long inventoryTotal = getInventoryTotal(false);
		long equipmentTotal = getEquipmentTotal(false);


		long totalGp = inventoryTotal;
		if (getState() == InventoryTotalState.RUN && getMode() == InventoryTotalMode.PROFIT_LOSS)
		{
			totalGp += equipmentTotal;
		}

		setTotalGp(totalGp);

		if (newRun)
		{
			onNewRun();

			postNewRun = true;
			newRunTick = client.getTickCount();
		}
		else if (getPreviousState() != InventoryTotalState.BANK && getState() == InventoryTotalState.BANK)
		{
			onBank();
		}

		// check post new run, need to wait one tick because if you withdraw something and close the bank right after it shows up one tick later
		if (postNewRun && (client.getTickCount() - newRunTick) > 0)
		{
			//make sure user didn't open the bank back up in those two ticks
			if (getState() == InventoryTotalState.RUN)
			{
				postNewRun();
			}
			else
			{
				getRunData().isBankDelay = false;
			}
			postNewRun = false;
		}
	}

	void onNewRun()
	{
		runData.isBankDelay = true;
		runData.runStartTime = Instant.now().toEpochMilli();
		runData.ignoredItems = getIgnoredItems();

		previousTotalGp = null;
	}

	// to handle same tick bank closing
	void postNewRun()
	{
		runData.initialItemQtys.clear();
		runData.itemQtys.clear();

		long inventoryTotal = getInventoryTotal(true);
		long equipmentTotal = getEquipmentTotal(true);

		runData.profitLossInitialGp = inventoryTotal + equipmentTotal;

		if (mode == InventoryTotalMode.PROFIT_LOSS)
		{
			initialGp = runData.profitLossInitialGp;
		}
		else
		{
			initialGp = 0;
		}

		runData.isBankDelay = false;
		writeSavedData(this.currentProfileKey);

		sessionManager.onTripStarted(runData);
	}

	void onBank()
	{
		runData.runEndTime = Instant.now().toEpochMilli();
		if (!runData.isFirstRun)
		{
			sessionManager.onTripCompleted(runData);
		}
		runData = createRunData();
		initialGp = 0;
	}

	long getInventoryTotal(boolean isNewRun)
	{
		if (inventoryItemContainer == null)
		{
			return 0l;
		}

		long totalGp = 0;
		refreshQtyMap(inventoryQtyMap, inventoryItemContainer);

		for (Integer itemId: inventoryQtyMap.keySet())
		{
			long totalPrice;
			float gePrice = getPrice(itemId);
			float itemQty = inventoryQtyMap.get(itemId);

			if (itemId == COINS)
			{
				totalPrice = (long) itemQty;
			}
			else
			{
				totalPrice = (long) (itemQty * gePrice);
			}

			totalGp += totalPrice;
			updateRunData(isNewRun, itemId, itemQty, gePrice);
		}

		return totalGp;
	}
	
    public void openConfiguration() {
		// We don't have access to the ConfigPlugin so let's just emulate an overlay click
		this.eventBus.post(new OverlayMenuClicked(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, null, null), this.overlay));
    }

	public void refreshPrices()
	{
		List<Integer> itemIds = new LinkedList<>(itemPrices.keySet());
		itemPrices.clear();
		for(Integer itemId : itemIds)
		{
			itemPrices.put(itemId, getPrice(itemId));
		}
	}

	private void addChargedWeaponComponents(Map<Integer, Float> qtyMap)
	{
		Map<Integer, Float> chargedWeaponComponents = getChargedWeaponComponentQtyMap(qtyMap.keySet());
		for (int itemId: chargedWeaponComponents.keySet()) {
			qtyMap.merge(itemId, chargedWeaponComponents.get(itemId), Float::sum);
		}
	}

	private void addChargedItemComponents(Map<Integer, Float> qtyMap)
	{
		Map<Integer, Float> chargedItemComponents = getChargedItemQtyMap(qtyMap.keySet());
		for (int itemId: chargedItemComponents.keySet()) {
			qtyMap.merge(itemId, chargedItemComponents.get(itemId), Float::sum);
		}
	}

	long getEquipmentTotal(boolean isNewRun)
	{
		refreshQtyMap(equipmentQtyMap, equipmentItemContainer);

		long eTotal = 0;
		for (int itemId: equipmentQtyMap.keySet())
		{
			float qty = equipmentQtyMap.get(itemId);
			float gePrice = getPrice(itemId);
			if (itemId == COINS)
			{
				gePrice = 1;
			}
			long totalPrice = (long) (qty * gePrice);

			eTotal += totalPrice;
			updateRunData(isNewRun, itemId, qty, gePrice);
		}

		return eTotal;
	}

	//avoid GC
	private final Map<Integer, Float> chargedWeaponComponentQtyMap = new HashMap<>();
	private Map<Integer, Float> getChargedWeaponComponentQtyMap(Set<Integer> itemIdsToCheck)
	{
		chargedWeaponComponentQtyMap.clear();
		for (int itemId: itemIdsToCheck) {
			if (weaponChargesManager.isChargeableWeapon(itemId) && weaponChargesManager.hasChargeData(itemId))
			{
				Map<Integer, Float> chargeComponents = weaponChargesManager.getChargeComponents(itemId);
				for (Integer chargeComponentItemId: chargeComponents.keySet())
				{
					chargedWeaponComponentQtyMap.merge(chargeComponentItemId, chargeComponents.get(chargeComponentItemId), Float::sum);
				}
			}
		}
		return chargedWeaponComponentQtyMap;
	}

	//avoid GC
	private final Map<Integer, Float> chargedItemQtyMap = new HashMap<>();
	private Map<Integer, Float> getChargedItemQtyMap(Set<Integer> itemIdsToCheck)
	{
		chargedItemQtyMap.clear();
		for (int itemId: itemIdsToCheck) {
			if (chargedItemManager.isChargeableItem(itemId) && chargedItemManager.hasChargeData(itemId))
			{
				Map<Integer, Float> itemContents = chargedItemManager.getItemQuantities(itemId);
				for (Integer itemContentId: itemContents.keySet())
				{
					chargedItemQtyMap.merge(itemContentId, itemContents.get(itemContentId), Float::sum);
				}
			}
		}
		return chargedItemQtyMap;
	}

	List<InventoryTotalLedgerItem> getInventoryLedger()
	{
		List<InventoryTotalLedgerItem> ledgerItems = new LinkedList<>();

		if (inventoryItemContainer == null)
		{
			return new LinkedList<>();
		}

		refreshQtyMap(inventoryQtyMap, inventoryItemContainer);

		for (Integer itemId: inventoryQtyMap.keySet())
		{
			final ItemComposition itemComposition = itemManager.getItemComposition(itemId);

			String itemName = itemComposition.getName();

			Float qty = inventoryQtyMap.get(itemId);

			Float price = itemPrices.get(itemId);
			if (itemId == COINS || price == null)
			{
				price = 1f;
			}

			ledgerItems.add(new InventoryTotalLedgerItem(itemName, qty, price, itemId));
		}

		return ledgerItems;
	}


	void refreshQtyMap(Map<Integer,Float> qtyMap, ItemContainer container)
	{
		qtyMap.clear();
		if (container==null)
		{
			return;
		}

		final Item[] containerItems = container.getItems();
		for (int i = 0; i < containerItems.length; ++i)
		{
			int itemId = containerItems[i].getId();
			if (itemId == -1)
				continue;

			ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			String itemName = itemComposition.getName();
			boolean ignore = ignoredItems.contains(itemName.toLowerCase());
			if (ignore) { continue; }
				
			qtyMap.merge(itemId, (float) containerItems[i].getQuantity(), Float::sum);

			if (itemId == RUNEPOUCH_ITEM_ID || itemId == DIVINE_RUNEPOUCH_ITEM_ID)
			{
				addRunepouchContents(qtyMap);
			}
			else if(itemId == ItemID.LOOTING_BAG || itemId == ItemID.LOOTING_BAG_22586)
			{
				lootingBagManager.addLootingBagContents(qtyMap);
			}
		}
		addChargedWeaponComponents(qtyMap);
		addChargedItemComponents(qtyMap);
		FractionalRemapper.Remap(qtyMap);
	}

	@Getter
	private final HashSet<String> chargeableItemsNeedingCheck = new HashSet<>();

	void updateChargeableItemsNeedingCheck()
	{
		chargeableItemsNeedingCheck.clear();
		if (this.state == InventoryTotalState.BANK)
			return;

		checkQtyMapForCheck(inventoryQtyMap.keySet());
		checkQtyMapForCheck(equipmentQtyMap.keySet());
	}

	void checkQtyMapForCheck(Set<Integer> keySet)
	{
		for (Integer itemId : keySet)
		{
			if ((itemId == ItemID.LOOTING_BAG || itemId == ItemID.LOOTING_BAG_22586) && lootingBagManager.needsCheck())
			{
				chargeableItemsNeedingCheck.add("looting bag");
			}
			else if (weaponChargesManager.isChargeableWeapon(itemId) && !weaponChargesManager.hasChargeData(itemId))
			{
				chargeableItemsNeedingCheck.add(itemManager.getItemComposition(itemId).getName().toLowerCase());
			} 
			else if (chargedItemManager.isChargeableItem(itemId) && !chargedItemManager.hasChargeData(itemId))
			{
				chargeableItemsNeedingCheck.add(itemManager.getItemComposition(itemId).getName().toLowerCase());
			} 
		}
	}

	static List<InventoryTotalLedgerItem> getProfitLossLedger(Map<Integer, Float> initialQtys, Map<Integer, Float> qtys)
	{
		Map<Integer, Float> qtyDifferences = new HashMap<>();

		HashSet<Integer> combinedQtyKeys = new HashSet<>();
		combinedQtyKeys.addAll(qtys.keySet());
		combinedQtyKeys.addAll(initialQtys.keySet());

		for (Integer itemId: combinedQtyKeys)
		{
			Float initialQty = initialQtys.get(itemId);
			Float qty = qtys.get(itemId);

			if (initialQty == null)
			{
				initialQty = 0f;
			}

			if (qty == null)
			{
				qty = 0f;
			}

			qtyDifferences.put(itemId, qty - initialQty);
		}

		Map<String, InventoryTotalLedgerItem> ledgerItems  = new HashMap<>();

		for (Integer itemId: qtyDifferences.keySet())
		{
			String name = itemNames.get(itemId);
			if (name == null)
			{
				continue;
			}
			Float price = itemPrices.get(itemId);
			if (price == null)
			{
				price = 1f;
			}

			Float qtyDifference = qtyDifferences.get(itemId);

			if (ledgerItems.containsKey(name))
			{
				ledgerItems.get(name).addQuantityDifference(qtyDifference);
			}
			else
			{
				if (price != 0)
				{
					ledgerItems.put(name, new InventoryTotalLedgerItem(name, qtyDifference, price, itemId));
				}
			}
		}

		//filter out quantities less than round amount here!
		List<InventoryTotalLedgerItem> ledgerItemsFiltered = new LinkedList<>();
		for (InventoryTotalLedgerItem item : ledgerItems.values())
		{
			if (Math.abs(item.getQty()) > (roundAmount/2f))
			{
				ledgerItemsFiltered.add(item);
			}
		}

		return ledgerItemsFiltered;
	}

	// from ClueScrollPlugin
	private void addRunepouchContents(Map<Integer, Float> qtyMap)
	{
		EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		for (int i = 0; i < RUNEPOUCH_AMOUNT_VARBITS.length; i++)
		{
			int amount = client.getVarbitValue(RUNEPOUCH_AMOUNT_VARBITS[i]);
			if (amount <= 0)
			{
				continue;
			}

			int runeId = client.getVarbitValue(RUNEPOUCH_RUNE_VARBITS[i]);
			if (runeId == 0)
			{
				continue;
			}

			qtyMap.merge(runepouchEnum.getIntValue(runeId), (float) amount, Float::sum);
		}
	}

	void updateRunData(boolean isNewRun, int itemId, float itemQty, float gePrice)
	{
		if (itemId != COINS && !itemPrices.containsKey(itemId))
		{
			itemPrices.put(itemId, gePrice);
		}

		itemNames.put(itemId, itemManager.getItemComposition(itemId).getName());

		if (isNewRun)
		{
			if (runData.initialItemQtys.containsKey(itemId))
			{
				runData.initialItemQtys.put(itemId, runData.initialItemQtys.get(itemId) + itemQty);
			}
			else
			{
				runData.initialItemQtys.put(itemId, itemQty);
			}
		}

		if (runData.itemQtys.containsKey(itemId))
		{
			runData.itemQtys.put(itemId, runData.itemQtys.get(itemId) + itemQty);
		}
		else
		{
			runData.itemQtys.put(itemId, itemQty);
		}
	}

	float getPrice(int itemId)
	{
		if (itemPrices.containsKey(itemId))
		{
			return itemPrices.get(itemId);
		}
		else
		{
			Float remappedValue = ValueRemapper.remapPrice(itemId, this, config);
			if (remappedValue != null)
			{
				return remappedValue;
			}
			else
			{
				return itemManager.getItemPrice(itemId);
			}
		}
	}

	void writeSavedData(String profileKey)
	{
		executor.execute(() ->
		{
			String json = gson.toJson(runData);
			configManager.setConfiguration(InventoryTotalConfig.GROUP, profileKey, "inventory_total_data", json);
		});
	}

	private InventoryTotalRunData getSavedData()
	{
		String json = readData( "inventory_total_data");
		InventoryTotalRunData savedData = gson.fromJson(json, InventoryTotalRunData.class);

		if (savedData == null)
		{
			InventoryTotalRunData runData = createRunData();
			runData.isFirstRun = true;
			return runData;
		}
		return savedData;
	}

	private InventoryTotalRunData createRunData()
	{
		InventoryTotalRunData data = new InventoryTotalRunData();
		data.identifier = UUID.randomUUID().toString();
		return data;
	}

	private LinkedList<String> getIgnoredItems() {
		return new LinkedList<>(
			Arrays.asList(
				config.ignoredItems().split("\\s*,\\s*")
			)
		);
	}

	long elapsedRunTime()
	{
		if (runData.runStartTime == 0)
		{
			return NO_PROFIT_LOSS_TIME;
		}

		return runData.getRuntime();
	}

	void setMode(InventoryTotalMode mode)
	{
		this.mode = mode;

		switch(mode)
		{
			case TOTAL:
				initialGp = 0;
				break;
			case PROFIT_LOSS:
				initialGp = runData.profitLossInitialGp;
				break;
		}
	}

	void setState(InventoryTotalState state)
	{
		this.previousState = this.state;
		this.state = state;
	}

	public long getProfitGp()
	{
		return totalGp - initialGp;
	}

	void saveData(String key, String data)
	{
		configManager.setRSProfileConfiguration(InventoryTotalConfig.GROUP, key, data);
	}

	String readData(String key)
	{
		return configManager.getRSProfileConfiguration(InventoryTotalConfig.GROUP, key);
	}

	<T> void saveData(String key, T data)
	{
		configManager.setRSProfileConfiguration(InventoryTotalConfig.GROUP, key, data);
	}

	void deleteData(String key)
	{
		configManager.unsetRSProfileConfiguration(InventoryTotalConfig.GROUP, key);
	}
}
