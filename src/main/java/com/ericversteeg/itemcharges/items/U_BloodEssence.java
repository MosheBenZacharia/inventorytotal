package com.ericversteeg.itemcharges.items;

import com.ericversteeg.itemcharges.ChargedItem;
import com.ericversteeg.itemcharges.triggers.*;
import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import com.ericversteeg.InventoryTotalConfig;
import com.ericversteeg.itemcharges.ChargesItem;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

public class U_BloodEssence extends ChargedItem {

    //avoid GC but don't confuse super class
    private final Map<Integer, Float> quantities = new HashMap<>();

    public U_BloodEssence(
            final Client client,
            final ClientThread client_thread,
            final ConfigManager configs,
            final ItemManager items,
            final ChatMessageManager chat_messages,
            final Notifier notifier,
            final Gson gson,
            final ScheduledExecutorService executorService
    ) {
        super(ChargesItem.BLOOD_ESSENCE, ItemID.BLOOD_ESSENCE_ACTIVE, client, client_thread, configs, items, chat_messages, notifier, gson, executorService);
        super.allow_chat_messages_when_not_present = true;
        this.config_key = InventoryTotalConfig.blood_essence;
        this.triggers_items = new TriggerItem[]{
                new TriggerItem(ItemID.BLOOD_ESSENCE_ACTIVE),
        };
        this.triggers_chat_messages = new TriggerChatMessage[]{
                new TriggerChatMessage("Your blood essence has (?<charges>.+) charges? remaining"),
                new TriggerChatMessage("You manage to extract power from the Blood Essence and craft (?<charges>.+) extra runes?\\.").decreaseDynamically(),
                new TriggerChatMessage("You activate the blood essence.").fixedCharges(1000),
        };
    }

    @Override
    protected  void onChargesUpdated()
    {
        quantities.clear();
        quantities.put(ItemID.BLOOD_ESSENCE, ((float) charges)/1000f);
    }

    @Override
    public Map<Integer, Float> getItemQuantities()
    {
        return quantities;
    }
}