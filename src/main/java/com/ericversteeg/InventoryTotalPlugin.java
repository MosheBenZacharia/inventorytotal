package com.ericversteeg;

import com.google.gson.Gson;
import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@PluginDescriptor(
	name = "Inventory Total",
	description = "Totals item prices in your inventory."
)

public class InventoryTotalPlugin extends Plugin
{
	static final int COINS = ItemID.COINS_995;
	static final int TOTAL_GP_INDEX = 0;
	static final int TOTAL_QTY_INDEX = 1;
	static final int NO_PROFIT_LOSS_TIME = -1;
	static final int RUNEPOUCH_ITEM_ID = 12791;
	static final int DIVINE_RUNEPOUCH_ITEM_ID = 27281;

	@Inject
	private InventoryTotalOverlay overlay;

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
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	private String profileKey = "";

	private InventoryTotalRunData runData;
	private InventoryTotalGoldDrops goldDropsObject;

	private InventoryTotalMode mode = InventoryTotalMode.TOTAL;

	private InventoryTotalState state = InventoryTotalState.NONE;
	private InventoryTotalState prevState = InventoryTotalState.NONE;

	private long totalGp = 0;
	private Long previousTotalGp = null;
	private long totalQty = 0;

	private long initialGp = 0;

	private long runStartTime = 0;

	private long lastWriteSaveTime = 0;

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

		runData = new InventoryTotalRunData();
		goldDropsObject = new InventoryTotalGoldDrops(client, itemManager);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged e)
	{
		profileKey = configManager.getRSProfileKey();
		if (profileKey != null)
		{
			runData = getSavedData();
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired scriptPreFired)
	{
		goldDropsObject.onScriptPreFired(scriptPreFired);
	}
    @Subscribe
    public void onGameTick(GameTick gameTick)
    {
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

		// generate gold drop
		if (config.goldDrops() && tickProfit != 0)
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
		overlay.showInterstitial();

		runStartTime = Instant.now().toEpochMilli();

		runData.ignoredItems = getIgnoredItems();

		previousTotalGp = null;
	}

	// to handle same tick bank closing
	void postNewRun()
	{
		runData.initialItemQtys.clear();

		int inventoryTotal = getInventoryTotals(true)[0];
		int equipmentTotal = getEquipmentTotal(true);

		runData.profitLossInitialGp = inventoryTotal + equipmentTotal;

		if (mode == InventoryTotalMode.PROFIT_LOSS)
		{
			initialGp = runData.profitLossInitialGp;
		}
		else
		{
			initialGp = 0;
		}

		writeSavedData();

		overlay.hideInterstitial();
	}

	void onBank()
	{
		runData.profitLossInitialGp = 0;
		runData.itemPrices.clear();

		initialGp = 0;
		runStartTime = 0;

		writeSavedData();
	}

	int [] getInventoryTotals(boolean isNewRun)
	{
		if (overlay.getInventoryItemContainer() == null)
		{
			return new int [2];
		}

		int totalQty = 0;
		int totalGp = 0;
		Map<Integer, Integer> qtyMap = getInventoryQtyMap();

		for (Integer itemId: qtyMap.keySet())
		{
			int totalPrice;
			int gePrice = getPrice(itemId);
			int itemQty = qtyMap.get(itemId);

			if (itemId == COINS)
			{
				totalPrice = itemQty;
			}
			else
			{
				totalPrice = itemQty * gePrice;
			}

			totalGp += totalPrice;
			totalQty += itemQty;
			
			updateRunData(isNewRun, itemId, itemQty, gePrice);
		}

		int[] totals = new int[2];

		totals[TOTAL_GP_INDEX] = totalGp;
		totals[TOTAL_QTY_INDEX] = totalQty;

		return totals;
	}

	int getEquipmentTotal(boolean isNewRun)
	{
		ItemContainer itemContainer = overlay.getEquipmentItemContainer();

		if (itemContainer == null)
		{
			return 0;
		}

		Item ring = itemContainer.getItem(EquipmentInventorySlot.RING.getSlotIdx());
		Item ammo = itemContainer.getItem(EquipmentInventorySlot.AMMO.getSlotIdx());

		Player player = client.getLocalPlayer();

		int [] ids = player.getPlayerComposition().getEquipmentIds();

		LinkedList<Integer> eIds = new LinkedList<>();

		for (int id: ids)
		{
			if (id < 512)
			{
				continue;
			}

			eIds.add(id - 512);
		}

		if (ring != null)
		{
			eIds.add(ring.getId());
		}

		if (ammo != null)
		{
			eIds.add(ammo.getId());
		}

		int eTotal = 0;
		for (int itemId: eIds)
		{
			int qty = 1;
			if (ammo != null && itemId == ammo.getId())
			{
				qty = ammo.getQuantity();
			}

			int gePrice = getPrice(itemId);
			int totalPrice = qty * gePrice;

			eTotal += totalPrice;

			updateRunData(isNewRun, itemId, qty, gePrice);
		}

		return eTotal;
	}

	List<InventoryTotalLedgerItem> getInventoryLedger()
	{
		List<InventoryTotalLedgerItem> ledgerItems = new LinkedList<>();

		if (overlay.getInventoryItemContainer() == null)
		{
			return new LinkedList<>();
		}

		Map<Integer, Integer> qtyMap = getInventoryQtyMap();

		for (Integer itemId: qtyMap.keySet())
		{
			final ItemComposition itemComposition = itemManager.getItemComposition(itemId);

			String itemName = itemComposition.getName();

			Integer qty = qtyMap.get(itemId);

			Integer total = runData.itemPrices.get(itemId);
			if (itemId == COINS || total == null)
			{
				total = 1;
			}

			ledgerItems.add(new InventoryTotalLedgerItem(itemName, qty, total));
		}

		return ledgerItems;
	}

	Map<Integer, Integer> getInventoryQtyMap()
	{
		final ItemContainer itemContainer = overlay.getInventoryItemContainer();

		final Item[] items = itemContainer.getItems();

		final LinkedList<Item> allItems = new LinkedList<>(Arrays.asList(items));
		// only add when the runepouch is in the inventory
		if (allItems.stream().anyMatch(s -> s.getId() == RUNEPOUCH_ITEM_ID || s.getId() == DIVINE_RUNEPOUCH_ITEM_ID))
		{
			allItems.addAll(getRunepouchContents());
		}

		Map<Integer, Integer> qtyMap = new HashMap<>();

		for (Item item: allItems) {
			int itemId = item.getId();

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

			int itemQty = item.getQuantity();

			if (qtyMap.containsKey(realItemId))
			{
				qtyMap.put(realItemId, qtyMap.get(realItemId) + itemQty);
			}
			else
			{
				qtyMap.put(realItemId, itemQty);
			}
		}

		return qtyMap;
	}

	List<InventoryTotalLedgerItem> getProfitLossLedger()
	{
		Map<Integer, Integer> prices = runData.itemPrices;
		Map<Integer, Integer> initialQtys = runData.initialItemQtys;
		Map<Integer, Integer> qtys = runData.itemQtys;

		Map<Integer, Integer> qtyDifferences = new HashMap<>();

		HashSet<Integer> combinedQtyKeys = new HashSet<>();
		combinedQtyKeys.addAll(qtys.keySet());
		combinedQtyKeys.addAll(initialQtys.keySet());

		for (Integer itemId: combinedQtyKeys)
		{
			Integer initialQty = initialQtys.get(itemId);
			Integer qty = qtys.get(itemId);

			if (initialQty == null)
			{
				initialQty = 0;
			}

			if (qty == null)
			{
				qty = 0;
			}

			qtyDifferences.put(itemId, qty - initialQty);
		}

		List<InventoryTotalLedgerItem> ledgerItems = new LinkedList<>();

		for (Integer itemId: qtyDifferences.keySet())
		{
			final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			Integer price = prices.get(itemId);

			if (price == null)
			{
				price = 1;
			}

			Integer qtyDifference = qtyDifferences.get(itemId);

			List<InventoryTotalLedgerItem> filteredList = ledgerItems.stream().filter(
					item -> item.getDescription().equals(itemComposition.getName())).collect(Collectors.toList()
			);

			if (!filteredList.isEmpty())
			{
				filteredList.get(0).addQuantityDifference(qtyDifference);
			}
			else
			{
				if (price > 0)
				{
					ledgerItems.add(new InventoryTotalLedgerItem(itemComposition.getName(), qtyDifference, price));
				}
			}
		}

		return ledgerItems;
	}

	// from ClueScrollPlugin
	private List<Item> getRunepouchContents()
	{
		EnumComposition runepouchEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		List<Item> items = new ArrayList<>(RUNEPOUCH_AMOUNT_VARBITS.length);
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
			items.add(item);
		}
		return items;
	}

	void updateRunData(boolean isNewRun, int itemId, int itemQty, int gePrice)
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
			return itemManager.getItemPrice(itemId);
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

		String profile = configManager.getRSProfileKey();

		String json = gson.toJson(runData);
		configManager.setConfiguration(InventoryTotalConfig.GROUP, profile, "inventory_total_data", json);

		lastWriteSaveTime = Instant.now().toEpochMilli();
	}

	private InventoryTotalRunData getSavedData()
	{
		String profile = configManager.getRSProfileKey();
		String json = configManager.getConfiguration(InventoryTotalConfig.GROUP, profile, "inventory_total_data");

		InventoryTotalRunData savedData = gson.fromJson(json, InventoryTotalRunData.class);

		if (savedData == null)
		{
			return new InventoryTotalRunData();
		}
		return savedData;
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
		if (runStartTime == 0 || !config.showRunTime())
		{
			return NO_PROFIT_LOSS_TIME;
		}

		return Instant
				.now()
				.minusMillis(runStartTime)
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

	void setTotalQty(long totalQty)
	{
		this.totalQty = totalQty;
	}

	public long getTotalQty()
	{
		return totalQty;
	}

	public InventoryTotalRunData getRunData()
	{
		return runData;
	}
}
