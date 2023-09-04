package com.ericversteeg.itemcharges.items;


import com.ericversteeg.InventoryTotalConfig;
import com.ericversteeg.itemcharges.ChargedItem;
import com.ericversteeg.itemcharges.ChargesItem;
import com.ericversteeg.itemcharges.triggers.TriggerChatMessage;
import com.ericversteeg.itemcharges.triggers.TriggerItem;
import com.ericversteeg.itemcharges.triggers.TriggerItemContainer;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class U_HerbSack extends ChargedItem {
    private static final String pickupRegex = "^You put the (Grimy\\s+[A-Za-z\\s]+)\\s+herb into your herb sack\\.$";
    private static final Pattern pickupPattern = Pattern.compile(pickupRegex);
    private static final String checkRegex = "(\\d+)\\s*x\\s+(.*)";
    private static final Pattern checkPattern = Pattern.compile(checkRegex);

    public U_HerbSack(
            final Client client,
            final ClientThread client_thread,
            final ConfigManager configs,
            final ItemManager items,
            final ChatMessageManager chat_messages,
            final Notifier notifier,
            final Gson gson
    ) {
        super(ChargesItem.HERB_SACK, ItemID.HERB_SACK, client, client_thread, configs, items, chat_messages, notifier, gson);

        this.config_key = InventoryTotalConfig.herb_sack;
        this.zero_charges_is_positive = true;
        this.triggers_items = new TriggerItem[]{
                new TriggerItem(ItemID.HERB_SACK),
                new TriggerItem(ItemID.OPEN_HERB_SACK),
        };
        this.triggers_chat_messages = new TriggerChatMessage[]{
                new TriggerChatMessage("The herb sack is empty.").onItemClick().extraConsumer((message) -> super.emptyOrClear()),
                new TriggerChatMessage(pickupRegex).extraConsumer(message -> {
                    if (super.hasChargeData()) {
                        final Matcher matcher = pickupPattern.matcher(message);
                        if (matcher.matches()) {
                            final String itemName = matcher.group(1);
                            Integer itemId = tryFindItemIdFromName(itemName);
                            if (itemId != null) {
                                super.addItems(itemId, 1f);
                            }
                        } else {
                            log.error("no herb match found for message: " + message);
                        }
                    }
                }),
                //check
                new TriggerChatMessage("You look in your herb sack and see:").onItemClick().extraConsumer((message) -> super.emptyOrClear()),
                new TriggerChatMessage("x Grimy").onItemClick().extraConsumer(message -> {

                    final Matcher matcher = checkPattern.matcher(message);
                    while (matcher.find()) {
                        try {
                            int amount = Integer.parseInt(matcher.group(1));
                            String name = matcher.group(2);
                            Integer itemId = tryFindItemIdFromName(name);
                            if (itemId != null)
                                super.addItems(itemId, (float) amount);
                        } catch (NumberFormatException e) {
                            log.error("couldn't parse herb sack check", e);
                        }
                    }
                }),
        };
        this.triggers_item_containers = new TriggerItemContainer[]{
                new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Open herb sack").menuOption("Fill").addDifference(),
                new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Herb sack").menuOption("Fill").addDifference(),
                new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Open herb sack").menuOption("Empty").addDifference(),
                new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Herb sack").menuOption("Empty").addDifference(),
                //Empty into bank doesn't make a chat message (unless it's already empty)
                new TriggerItemContainer(InventoryID.BANK.getId()).menuTarget("Open herb sack").menuOption("Empty").extraConsumer(super::emptyOrClear),
                new TriggerItemContainer(InventoryID.BANK.getId()).menuTarget("Herb sack").menuOption("Empty").extraConsumer(super::emptyOrClear),
        };
        //for herb sack this only works for single herbs, if dialog pops up we don't capture it. too complicated...
        this.supportsWidgetOnWidget = true;
    }
}
