package com.ericversteeg;

import com.ericversteeg.itemcharges.ChargedItem;
import com.ericversteeg.itemcharges.ChargedItemManager;
import com.ericversteeg.weaponcharges.WeaponChargesManager;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Inventory Total",
	description = "Totals item prices in your inventory."
)

@Slf4j
public class InventoryTotalPlugin extends Plugin
{
	static final int COINS = ItemID.COINS_995;
	static final int NO_PROFIT_LOSS_TIME = -1;
	static final int RUNEPOUCH_ITEM_ID = 12791;
	static final int DIVINE_RUNEPOUCH_ITEM_ID = 27281;
	public static final float roundAmount = 0.1f;

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
    @Getter
    private SessionPanel panel;

	private String profileKey = "";

	private InventoryTotalRunData runData;
	private InventoryTotalGoldDrops goldDropsObject;

	private InventoryTotalMode mode = InventoryTotalMode.TOTAL;

	private InventoryTotalState state = InventoryTotalState.NONE;
	private InventoryTotalState prevState = InventoryTotalState.NONE;

	private long totalGp = 0;
	private Long previousTotalGp = null;

	private long initialGp = 0;

	private long lastWriteSaveTime = 0;
	
    private BufferedImage icon;
    private NavigationButton navButton;

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
		overlayManager.add(overlay);

		goldDropsObject = new InventoryTotalGoldDrops(client, itemManager);
		eventBus.register(lootingBagManager);
		eventBus.register(weaponChargesManager);
		eventBus.register(chargedItemManager);
		weaponChargesManager.startUp();
		chargedItemManager.startUp();
		lootingBagManager.startUp();

		runData = getSavedData();
		sessionManager = new SessionManager(this);
		sessionManager.startUp();
		sessionManager.onTripStarted(runData);
		buildSidePanel();
		panel.updateTrips(sessionManager.getActiveTrips());
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		eventBus.unregister(lootingBagManager);
		eventBus.unregister(weaponChargesManager);
		eventBus.unregister(chargedItemManager);
		weaponChargesManager.shutDown();
		chargedItemManager.shutDown();
		sessionManager.shutDown();
	}

    private void buildSidePanel()
    {
        panel = new SessionPanel(this, config, itemManager, clientThread, sessionManager);
        panel.sidePanelInitializer();
        icon = ImageUtil.loadImageResource(getClass(), "/gpperhour-icon.png");
        navButton = NavigationButton.builder().tooltip("GP Per Hour").icon(icon).priority(config.sidePanelPosition()).panel(panel).build();
        clientToolbar.addNavigation(navButton);
    }

	@Subscribe
	public void onScriptPreFired(ScriptPreFired scriptPreFired)
	{
		goldDropsObject.onScriptPreFired(scriptPreFired);
	}
    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
		//TODO: REMOVE (JUST HERE TO MAKE TESTING EASIER)
		panel.updateTrips(sessionManager.getActiveTrips());
		
        //1. If profit total changed generate gold drop (nice animation for showing gold earn or loss)
		boolean isRun = this.state == InventoryTotalState.RUN;
		if (!isRun)
			return;
		if (previousTotalGp == null)
		{
			previousTotalGp = Long.valueOf(totalGp);
			return;
		}
        long tickProfit = (totalGp - previousTotalGp);
		previousTotalGp = Long.valueOf(totalGp);
		if(tickProfit == 0)
			return;

		panel.updateTrips(sessionManager.getActiveTrips());

		// generate gold drop
		if (config.goldDrops() && config.enableProfitLoss() && tickProfit != 0)
		{
			goldDropsObject.requestGoldDrop(tickProfit);
		}
    }

	@Provides
	InventoryTotalConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(InventoryTotalConfig.class);
	}

	void onNewRun()
	{
		runData.showInterstitial = true;
		runData.runStartTime = Instant.now().toEpochMilli();
		runData.ignoredItems = getIgnoredItems();

		previousTotalGp = null;
	}

	// to handle same tick bank closing
	void postNewRun()
	{
		runData.initialItemQtys.clear();

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

		runData.showInterstitial = false;
		writeSavedData();

		sessionManager.onTripStarted(runData);
	}

	void onBank()
	{
		runData.runEndTime = Instant.now().toEpochMilli();
		sessionManager.onTripCompleted(runData);
		runData = createRunData();
		initialGp = 0;

		writeSavedData();
	}

	long getInventoryTotal(boolean isNewRun)
	{
		if (overlay.getInventoryItemContainer() == null)
		{
			return 0l;
		}

		long totalGp = 0;
		Map<Integer, Float> qtyMap = getInventoryQtyMap();

		for (Integer itemId: qtyMap.keySet())
		{
			long totalPrice;
			int gePrice = getPrice(itemId);
			float itemQty = qtyMap.get(itemId);

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

	//no GC
	private Map<Integer, Float> equipmentQtyMap = new HashMap<>();
	private Map<Integer, Float> getEquipmentQtyMap()
	{
		equipmentQtyMap.clear();

		ItemContainer itemContainer = overlay.getEquipmentItemContainer();

		if (itemContainer == null)
		{
			return equipmentQtyMap;
		}

		Item ring = itemContainer.getItem(EquipmentInventorySlot.RING.getSlotIdx());
		Item ammo = itemContainer.getItem(EquipmentInventorySlot.AMMO.getSlotIdx());

		Player player = client.getLocalPlayer();

		int [] ids = player.getPlayerComposition().getEquipmentIds();

		for (int id: ids)
		{
			if (id < 512)
			{
				continue;
			}

			equipmentQtyMap.put(id - 512, 1f);
		}

		if (ring != null)
		{
			equipmentQtyMap.put(ring.getId(), 1f);
		}

		if (ammo != null)
		{
			equipmentQtyMap.put(ammo.getId(), (float) ammo.getQuantity());
		}

		addChargedWeaponComponents(equipmentQtyMap);
		addChargedItemComponents(equipmentQtyMap);
		return equipmentQtyMap;
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
		Map<Integer, Float> equMap = getEquipmentQtyMap();

		long eTotal = 0;
		for (int itemId: equMap.keySet())
		{
			float qty = equMap.get(itemId);
			int gePrice = getPrice(itemId);
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
	private Map<Integer, Float> chargedWeaponComponentQtyMap = new HashMap<>();
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
	private Map<Integer, Float> chargedItemQtyMap = new HashMap<>();
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

		if (overlay.getInventoryItemContainer() == null)
		{
			return new LinkedList<>();
		}

		Map<Integer, Float> qtyMap = getInventoryQtyMap();

		for (Integer itemId: qtyMap.keySet())
		{
			final ItemComposition itemComposition = itemManager.getItemComposition(itemId);

			String itemName = itemComposition.getName();

			Float qty = qtyMap.get(itemId);

			Integer price = runData.itemPrices.get(itemId);
			if (itemId == COINS || price == null)
			{
				price = 1;
			}

			ledgerItems.add(new InventoryTotalLedgerItem(itemName, qty, price, itemId));
		}

		return ledgerItems;
	}

	//avoid GC
	private Map<Integer, Float> inventoryQtyMap = new HashMap<>();

	Map<Integer, Float> getInventoryQtyMap()
	{
		inventoryQtyMap.clear();
		final ItemContainer itemContainer = overlay.getInventoryItemContainer();

		final Item[] items = itemContainer.getItems();

		final LinkedList<Item> allItems = new LinkedList<>(Arrays.asList(items));
		// only add when the runepouch is in the inventory
		if (allItems.stream().anyMatch(s -> s.getId() == RUNEPOUCH_ITEM_ID || s.getId() == DIVINE_RUNEPOUCH_ITEM_ID))
		{
			allItems.addAll(getRunepouchContents());
		}
		// only add when the looting bag is in the inventory
		if (allItems.stream().anyMatch(s -> s.getId() == ItemID.LOOTING_BAG || s.getId() == ItemID.LOOTING_BAG_22586))
		{
			allItems.addAll(lootingBagManager.getLootingBagContents());
		}
		
		for (Item item: allItems) {
			int itemId = item.getId();
			if(itemId == -1)
				continue;

			final ItemComposition itemComposition = itemManager.getItemComposition(itemId);

			String itemName = itemComposition.getName();
			final boolean ignore = runData.ignoredItems.stream().anyMatch(s -> {
				String lcItemName = itemName.toLowerCase();
				String lcS = s.toLowerCase();
				return lcItemName.contains(lcS);
			});
			if (ignore) { continue; }

			final boolean isNoted = itemComposition.getNote() != -1;
			final int realItemId = isNoted ? itemComposition.getLinkedNoteId() : itemId;

			inventoryQtyMap.merge(realItemId, (float) item.getQuantity(), Float::sum);
		}

		addChargedWeaponComponents(inventoryQtyMap);
		addChargedItemComponents(inventoryQtyMap);

		return inventoryQtyMap;
	}

	boolean needsLootingBagCheck()
	{
		if (this.state == InventoryTotalState.BANK)
			return false;
		final ItemContainer itemContainer = overlay.getInventoryItemContainer();
		if(itemContainer == null)
			return false;

		// only when the looting bag is in the inventory
		if (Arrays.asList(itemContainer.getItems()).stream().anyMatch(s -> s.getId() == ItemID.LOOTING_BAG || s.getId() == ItemID.LOOTING_BAG_22586))
		{
			return lootingBagManager.needsCheck();
		}

		return false;
	}

	//no GC
	private HashSet<String> chargeableItemsNeedingCheck = new HashSet<>();

	HashSet<String> getChargeableItemsNeedingCheck()
	{
		chargeableItemsNeedingCheck.clear();
		if (this.state == InventoryTotalState.BANK)
			return chargeableItemsNeedingCheck;

		final ItemContainer itemContainer = overlay.getInventoryItemContainer();
		//loop through container instead of getting qtyMap because we dont care about chargeable items in looting bag (actually can you even put something charged in a container? wouldnt be tradeable right?)
		if (itemContainer != null)
		{
			Item[] inventoryItems = itemContainer.getItems();
			for (Item item : inventoryItems)
			{
				if (weaponChargesManager.isChargeableWeapon(item.getId()) && !weaponChargesManager.hasChargeData(item.getId()))
				{
					chargeableItemsNeedingCheck.add(itemManager.getItemComposition(item.getId()).getName());
				} 
				else if (chargedItemManager.isChargeableItem(item.getId()) && !chargedItemManager.hasChargeData(item.getId()))
				{
					chargeableItemsNeedingCheck.add(itemManager.getItemComposition(item.getId()).getName());
				} 
			}
		}
		Map<Integer, Float> equMap = getEquipmentQtyMap();
		for (Integer itemId : equMap.keySet())
		{
			if (weaponChargesManager.isChargeableWeapon(itemId) && !weaponChargesManager.hasChargeData(itemId))
			{
				chargeableItemsNeedingCheck.add(itemManager.getItemComposition(itemId).getName());
			}
			else if (chargedItemManager.isChargeableItem(itemId) && !chargedItemManager.hasChargeData(itemId))
			{
				chargeableItemsNeedingCheck.add(itemManager.getItemComposition(itemId).getName());
			} 
		}

		return chargeableItemsNeedingCheck;
	}

	List<InventoryTotalLedgerItem> getProfitLossLedger(InventoryTotalRunData data)
	{
		Map<Integer, Integer> prices = data.itemPrices;
		Map<Integer, Float> initialQtys = data.initialItemQtys;
		Map<Integer, Float> qtys = data.itemQtys;

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
			String name = itemManager.getItemComposition(itemId).getName();
			Integer price = prices.get(itemId);
			if (price == null)
			{
				price = 1;
			}

			Float qtyDifference = qtyDifferences.get(itemId);

			if (ledgerItems.containsKey(name))
			{
				ledgerItems.get(name).addQuantityDifference(qtyDifference);
			}
			else
			{
				if (price > 0)
				{
					ledgerItems.put(name, new InventoryTotalLedgerItem(name, qtyDifference, price, itemId));
				}
			}
		}

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

	//dont GC
	private List<Item> runepouchItems = new ArrayList<>(RUNEPOUCH_AMOUNT_VARBITS.length);

	// from ClueScrollPlugin
	private List<Item> getRunepouchContents()
	{
		runepouchItems.clear();
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

			final int itemId = runepouchEnum.getIntValue(runeId);
			Item item = new Item(itemId, amount);
			runepouchItems.add(item);
		}
		return runepouchItems;
	}

	void updateRunData(boolean isNewRun, int itemId, float itemQty, int gePrice)
	{
		if (itemId != COINS && !runData.itemPrices.containsKey(itemId))
		{
			runData.itemPrices.put(itemId, gePrice);
		}

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

	int getPrice(int itemId)
	{
		if (runData.itemPrices.containsKey(itemId))
		{
			return runData.itemPrices.get(itemId);
		}
		else
		{
			//certain things should still have value (still need to figure out what else to include)
			if (itemId == ItemID.CRYSTAL_SHARD)
			{
				ItemComposition composition = itemManager.getItemComposition(itemId);
				return composition.getHaPrice();
			}
			else
			{
				return itemManager.getItemPrice(itemId);
			}
		}
	}

	// max invoke rate approximately once per tick
	// mainly so that initially this isn't getting invoked multiple times after item prices are added to the map
	void writeSavedData()
	{
		if (state == InventoryTotalState.BANK || Instant.now().toEpochMilli() - lastWriteSaveTime < 600)
		{
			return;
		}

		executor.execute(() ->
		{
			String json = gson.toJson(runData);
			configManager.setConfiguration(InventoryTotalConfig.GROUP, "inventory_total_data", json);
			lastWriteSaveTime = Instant.now().toEpochMilli();
		});
	}

	private InventoryTotalRunData getSavedData()
	{
		String json = configManager.getConfiguration(InventoryTotalConfig.GROUP, "inventory_total_data");

		InventoryTotalRunData savedData = gson.fromJson(json, InventoryTotalRunData.class);

		if (savedData == null)
		{
			return createRunData();
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

		return Instant
				.now()
				.minusMillis(runData.runStartTime)
				.toEpochMilli();
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

	public InventoryTotalMode getMode()
	{
		return mode;
	}

	void setState(InventoryTotalState state)
	{
		this.prevState = this.state;
		this.state = state;
	}

	public InventoryTotalState getState()
	{
		return state;
	}

	public InventoryTotalState getPreviousState()
	{
		return prevState;
	}

	public long getProfitGp()
	{
		return totalGp - initialGp;
	}

	void setTotalGp(long totalGp)
	{
		this.totalGp = totalGp;
	}

	public long getTotalGp()
	{
		return totalGp;
	}

	public InventoryTotalRunData getRunData()
	{
		return runData;
	}
}
