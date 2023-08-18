//TODO: attribute
package com.ericversteeg.itemcharges.items;

import com.ericversteeg.InventoryTotalConfig;
import com.ericversteeg.itemcharges.ChargedItem;
import com.ericversteeg.itemcharges.ChargesItem;
import com.ericversteeg.itemcharges.triggers.TriggerChatMessage;
import com.ericversteeg.itemcharges.triggers.TriggerItem;
import com.ericversteeg.itemcharges.triggers.TriggerItemContainer;
import com.ericversteeg.itemcharges.triggers.TriggerMenuOption;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.http.api.item.ItemPrice;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class U_LogBasket extends ChargedItem
{
	private final int CAPACITY = 28;
	private static final String logMessage = "^You get some ([a-zA-Z ]+)[.!]?$";
	private static final Pattern logPattern = Pattern.compile(logMessage);
	private static final String checkRegex = "([0-9]+) x ([a-zA-Z ]+),? ?";
	private static final Pattern checkPattern = Pattern.compile(checkRegex);
	private Integer lastNatureOfferingTickCount = null;
	
	public U_LogBasket(
		final Client client,
		final ClientThread client_thread,
		final ConfigManager configs,
		final ItemManager items,
		final ChatMessageManager chat_messages,
		final Notifier notifier,
		final Gson gson
	) {
		super(ChargesItem.LOG_BASKET, ItemID.LOG_BASKET, client, client_thread, configs, items, chat_messages, notifier, gson);

		this.config_key = InventoryTotalConfig.log_basket;
		this.zero_charges_is_positive = true;
        this.triggers_items = new TriggerItem[]{
            new TriggerItem(ItemID.LOG_BASKET),
            new TriggerItem(ItemID.OPEN_LOG_BASKET),
        };
		this.triggers_chat_messages = new TriggerChatMessage[]{
            new TriggerChatMessage("(Your|The) basket is empty.").onItemClick().extraConsumer((message) -> { super.emptyOrClear(); }),
            new TriggerChatMessage("You bank all your logs.").onItemClick().extraConsumer((message) -> { super.emptyOrClear(); }),
			new TriggerChatMessage("(You get some.* logs)").extraConsumer(message -> {
				log.info(message);
				if ((item_id == ItemID.OPEN_LOG_BASKET || item_id == ItemID.OPEN_FORESTRY_BASKET) && getItemCount() < CAPACITY && super.hasChargeData()) {
					final Matcher matcher = logPattern.matcher(message);
					if (matcher.matches())
					{
						final String logName = matcher.group(1).toLowerCase().trim();
						Integer itemId = tryFindItemIdFromName(logName);
						if (itemId != null)
						{
							super.addItems(itemId, 1f);
							if (getItemCount() < CAPACITY)
							{
								if (lastNatureOfferingTickCount == client.getTickCount())
								{
									super.addItems(itemId, 1f);
								}
							}
						}
					}
					else
					{
						log.error("no match found");
					}
				}
			}),
			new TriggerChatMessage("(The nature offerings enabled you to chop an extra log.)").extraConsumer(message -> {
				
				lastNatureOfferingTickCount = client.getTickCount();
			}),
			new TriggerChatMessage("The basket contains:").extraConsumer(message -> {
				
				super.emptyOrClear();
				//fix weird whitespace issue
				final Matcher matcher = checkPattern.matcher(message.replace("\u00A0", " "));
				while (matcher.find())
				{
					try
					{
						int amount = Integer.parseInt(matcher.group(1));
						String name = matcher.group(2).toLowerCase();
						Integer itemId = tryFindItemIdFromName(name);
						if (itemId != null)
							super.addItems(itemId, (float) amount);
					}
					catch (NumberFormatException e)
					{
						log.error("couldn't parse log basket check", e);
					}
				}
			}),
		};
		this.triggers_item_containers = new TriggerItemContainer[]{
			new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Open log basket").menuOption("Fill").addDifference(),
			new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Log basket").menuOption("Fill").addDifference(),
		};
		this.supportsWidgetOnWidget = true;
	}

	private Integer tryFindItemIdFromName(String name)
	{
		List<ItemPrice> results = items.search(name);
		if(results != null && !results.isEmpty())
		{
			for (ItemPrice result : results)
			{
				if (result.getName().toLowerCase().equals(name))
				{
					return result.getId();
				}
			}
		}
		return null;
	}
}
