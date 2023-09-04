//TODO: attribute
package com.ericversteeg.itemcharges.items;

import com.ericversteeg.itemcharges.ChargedItem;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import com.ericversteeg.InventoryTotalConfig;
import com.ericversteeg.itemcharges.ChargesItem;
import com.ericversteeg.itemcharges.triggers.TriggerAnimation;
import com.ericversteeg.itemcharges.triggers.TriggerChatMessage;
import com.ericversteeg.itemcharges.triggers.TriggerItem;
import com.ericversteeg.itemcharges.triggers.TriggerWidget;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

public class U_BottomlessCompostBucket extends ChargedItem {

    //avoid GC but don't confuse super class
    private final Map<Integer, Float> quantities = new HashMap<>();

    public U_BottomlessCompostBucket(
            final Client client,
            final ClientThread client_thread,
            final ConfigManager configs,
            final ItemManager items,
            final ChatMessageManager chat_messages,
            final Notifier notifier,
            final Gson gson,
            final ScheduledExecutorService executorService
    ) {
        super(ChargesItem.BOTTOMLESS_COMPOST_BUCKET, ItemID.BOTTOMLESS_COMPOST_BUCKET_22997, client, client_thread, configs, items, chat_messages, notifier, gson, executorService);
        this.config_key = InventoryTotalConfig.bottomless_compost_bucket;
        this.extra_config_keys = new String[]{"type"};
        this.triggers_items = new TriggerItem[]{
                new TriggerItem(ItemID.BOTTOMLESS_COMPOST_BUCKET).fixedCharges(0),
                new TriggerItem(ItemID.BOTTOMLESS_COMPOST_BUCKET_22997),
        };
        this.triggers_animations = new TriggerAnimation[]{
                new TriggerAnimation(8197).decreaseCharges(1),
                new TriggerAnimation(832).increaseCharges(2).onMenuOption("Take").unallowedItems(new int[]{ItemID.BUCKET})
        };
        this.triggers_chat_messages = new TriggerChatMessage[]{
                new TriggerChatMessage("Your bottomless compost bucket has a single use of (?<type>.+) ?compost remaining.").fixedCharges(1),
                new TriggerChatMessage("Your bottomless compost bucket has (?<charges>.+) uses of (?<type>.+) ?compost remaining."),
                new TriggerChatMessage("Your bottomless compost bucket doesn't currently have any compost in it!(?<type>.*)").fixedCharges(0),
                new TriggerChatMessage("Your bottomless compost bucket is currently holding one use of (?<type>.+?) ?compost.").fixedCharges(1),
                new TriggerChatMessage("Your bottomless compost bucket is currently holding (?<charges>.+) uses of (?<type>.+?) ?compost."),
                new TriggerChatMessage("You discard the contents of your bottomless compost bucket.(?<type>.*)").fixedCharges(0),
                new TriggerChatMessage("You fill your bottomless compost bucket with .* buckets? of (?<type>.+?) ?compost. Your bottomless compost bucket now contains a total of (?<charges>.+) uses.")
        };
    }

    private String getCompostType() {
        return configs.getConfiguration(InventoryTotalConfig.GROUP, InventoryTotalConfig.bottomless_compost_bucket_type);
    }

    @Override
    protected void onChargesUpdated()
    {
        quantities.clear();
        Integer itemId = null;
        String compostType = getCompostType();
        if (compostType == null)
        {
            return;
        }
        if (compostType.equals("ultra"))
        {
            itemId = ItemID.ULTRACOMPOST;
        }
        else if(compostType.equals("super"))
        {
            itemId = ItemID.SUPERCOMPOST;
        }
        else
        {
            itemId = ItemID.COMPOST;
        }
        quantities.put(itemId, (float) charges);
    }

    @Override
    public Map<Integer, Float> getItemQuantities()
    {
        return quantities;
    }
}
