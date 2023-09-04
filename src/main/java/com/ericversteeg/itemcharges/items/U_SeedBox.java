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

import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class U_SeedBox extends ChargedItem {
    private static final String checkRegex = "^(\\d+)\\s+x\\s+([A-Za-z\\s]+seed)\\.$";
    private static final Pattern checkPattern = Pattern.compile(checkRegex);
    private static final String pickupRegex = "^You put\\s+(\\d+)\\s+x\\s+([A-Za-z\\s]+seed)\\s+straight into your open seed box\\.$";
    private static final Pattern pickupPattern = Pattern.compile(pickupRegex);
    private static final String storeRegex = "^Stored\\s+(\\d+)\\s+x\\s+([A-Za-z\\s]+seed)\\s+in your seed box\\.$";
    private static final Pattern storePattern = Pattern.compile(storeRegex);
    private static final String removeRegex = "^Emptied\\s+(\\d+)\\s+x\\s+([A-Za-z\\s]+seed)\\s+to your inventory\\.$";
    private static final Pattern removePattern = Pattern.compile(removeRegex);


    public U_SeedBox(
            final Client client,
            final ClientThread client_thread,
            final ConfigManager configs,
            final ItemManager items,
            final ChatMessageManager chat_messages,
            final Notifier notifier,
            final Gson gson,
            final ScheduledExecutorService executorService
    ) {
        super(ChargesItem.SEED_BOX, ItemID.SEED_BOX, client, client_thread, configs, items, chat_messages, notifier, gson, executorService);

        this.config_key = InventoryTotalConfig.seed_box;
        this.zero_charges_is_positive = true;
        this.triggers_items = new TriggerItem[]{
                new TriggerItem(ItemID.SEED_BOX),
                new TriggerItem(ItemID.OPEN_SEED_BOX),
        };
        this.triggers_chat_messages = new TriggerChatMessage[]{
                //Check
                new TriggerChatMessage("The seed box is empty.").onItemClick().extraConsumer((message) -> super.emptyOrClear()),
                //Empty into bank
                new TriggerChatMessage("Your seed box is now empty.").onItemClick().extraConsumer((message) -> super.emptyOrClear()),
                //check
                new TriggerChatMessage("The seed box contains:").onItemClick().extraConsumer((message) -> super.emptyOrClear()),
                new TriggerChatMessage(checkRegex).onItemClick().extraConsumer(message -> addMatches(checkPattern.matcher(message), false)),
                //Pickup
                new TriggerChatMessage(pickupRegex).extraConsumer(message -> addMatches(pickupPattern.matcher(message), false)),
                //Store
                new TriggerChatMessage(storeRegex).extraConsumer(message -> addMatches(storePattern.matcher(message), false)),
                //Remove
                new TriggerChatMessage(removeRegex).extraConsumer(message -> addMatches(removePattern.matcher(message), true)),
        };
    }

    private void addMatches(Matcher matcher, boolean remove)
    {   
        if (!super.hasChargeData())
            return;
        while (matcher.find()) {
            try {
                int amount = Integer.parseInt(matcher.group(1));
                String name = matcher.group(2);
                Integer itemId = tryFindItemIdFromName(name);
                if (itemId != null)
                    super.addItems(itemId, (float) (remove ? -amount : amount));
            } catch (NumberFormatException e) {
                log.error("couldn't parse seed box match", e);
            }
        }
    }
}