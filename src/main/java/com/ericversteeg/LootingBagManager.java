//TODO: Proper attribution to patrick watts

package com.ericversteeg;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
public class LootingBagManager
{
	@Data
	@AllArgsConstructor
	public class PickupAction
	{
		int itemId;
		WorldPoint worldPoint;
	}

	public static final int LOOTING_BAG_CONTAINER = 516;
	private static final Set<Integer> FEROX_REGION = ImmutableSet.of(12600, 12344);
	private static final int LOOTING_BAG_SIZE = 28;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	private final Map<Integer, Integer> bagItems = new HashMap<>();
	private int freeSlots = -1;
	private long value = -1;

	private PickupAction lastPickUpAction;

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded widgetLoaded)
	{
		if (widgetLoaded.getGroupId() != WidgetInfo.LOOTING_BAG_CONTAINER.getGroupId())
		{
			return;
		}
		updateValue();
	}

	private void updateValue()
	{
		ItemContainer itemContainer = client.getItemContainer(LOOTING_BAG_CONTAINER);
		if (itemContainer == null)
		{
			value = 0;
			freeSlots = LOOTING_BAG_SIZE;
			return;
		}
		long newValue = 0;
		bagItems.clear();
		freeSlots = LOOTING_BAG_SIZE;
		for (Item item : itemContainer.getItems())
		{
			if (item.getId() >= 0)
			{
				bagItems.merge(item.getId(), item.getQuantity(), Integer::sum);
				newValue += getPrice(item.getId()) * item.getQuantity();
				freeSlots--;
			}
		}
		value = newValue;
	}

	//dont GC
	private List<Item> lootingBagItems = new ArrayList<>(bagItems.size());

	List<Item> getLootingBagContents()
	{
		lootingBagItems.clear();
		//needs to be checked/calibrated
		if (freeSlots < 0)
		{
			return lootingBagItems;
		}
		for (Integer itemId: bagItems.keySet())
		{
			Item item = new Item(itemId, bagItems.get(itemId));
			lootingBagItems.add(item);
		}
		return lootingBagItems;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != LOOTING_BAG_CONTAINER)
		{
			return;
		}
		updateValue();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.GROUND_ITEM_THIRD_OPTION)
		{
			return;
		}
		if (!event.getMenuOption().equals("Take"))
		{
			return;
		}
		WorldPoint point = WorldPoint.fromScene(client, event.getParam0(), event.getParam1(), client.getPlane());
		lastPickUpAction = new PickupAction(event.getId(), point);
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		if (value < 0)
		{
			return;
		}

		// not in wilderness or ferox -> can't pickup items directly into looting bag
		if (client.getVarbitValue(Varbits.IN_WILDERNESS) == 0
			&& !FEROX_REGION.contains(client.getLocalPlayer().getWorldLocation().getRegionID()))
		{
			return;
		}

		// doesn't have open looting bag
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null || !inv.contains(ItemID.LOOTING_BAG_22586))
		{
			return;
		}

		if (lastPickUpAction == null)
		{
			return;
		}

		// not on same tile
		if (!event.getTile().getWorldLocation().equals(client.getLocalPlayer().getWorldLocation()))
		{
			return;
		}

		if (!event.getTile().getWorldLocation().equals(lastPickUpAction.getWorldPoint()))
		{
			return;
		}

		int itemId = event.getItem().getId();

		if (itemId != lastPickUpAction.getItemId())
		{
			return;
		}

		ItemComposition itemComposition = itemManager.getItemComposition(itemId);

		if (!itemComposition.isTradeable())
		{
			return;
		}

		if (!bagItems.containsKey(itemId) || !itemComposition.isStackable())
		{
			if (freeSlots <= 0)
				return;
			freeSlots--;
		}

		int quantity = event.getItem().getQuantity();
		value += getPrice(itemId) * quantity;
		bagItems.merge(itemId, quantity, Integer::sum);
	}

	public long getPrice(int itemId)
	{
		return itemManager.getItemPrice(itemId);
	}

	public String getValueText()
	{
		if (value < 0)
		{
			return "Check";
		}
		String text = "";
		if (value >= 10_000_000)
		{
			return text + value / 1_000_000 + "M";
		}
		if (value >= 100_000)
		{
			return text + value / 1000 + "k";
		}
		return text + value;
	}

	boolean needsCheck()
	{
		return freeSlots < 0;
	}

	public String getFreeSlotsText()
	{
		if (freeSlots < 0)
		{
			return "Check";
		}
		return Integer.toString(freeSlots);
	}
}
