//TODO: attribute
package com.ericversteeg.itemcharges.items;

import com.ericversteeg.InventoryTotalConfig;
import com.ericversteeg.itemcharges.ChargedItem;
import com.ericversteeg.itemcharges.ChargesItem;
import com.ericversteeg.itemcharges.triggers.TriggerChatMessage;
import com.ericversteeg.itemcharges.triggers.TriggerItem;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
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

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class S_KharedstMemoirs extends ChargedItem {

    //avoid GC but don't confuse super class
    private final Map<Integer, Float> quantities = new HashMap<>();

    public S_KharedstMemoirs(
            final Client client,
            final ClientThread client_thread,
            final ConfigManager configs,
            final ItemManager items,
            final ChatMessageManager chat_messages,
            final Notifier notifier,
            final Gson gson
    ) {
        super(ChargesItem.KHAREDSTS_MEMOIRS, ItemID.KHAREDSTS_MEMOIRS, client, client_thread, configs, items, chat_messages, notifier, gson);
        this.config_key = InventoryTotalConfig.kharedsts_memoirs;
        this.triggers_items = new TriggerItem[]{
                new TriggerItem(ItemID.KHAREDSTS_MEMOIRS),
                new TriggerItem(ItemID.BOOK_OF_THE_DEAD)
        };
        this.triggers_chat_messages = new TriggerChatMessage[]{
                new TriggerChatMessage("(Kharedst's Memoirs?)|(The Book of the Dead) now has (?<charges>.+) (memories|memory) remaining."),
                new TriggerChatMessage("(Kharedst's Memoirs?)|(The Book of the Dead) holds no charges?.").fixedCharges(0),
                new TriggerChatMessage("On the inside of the cover a message is displayed in dark ink. It reads: (?<charges>.+) (memories|memory) remain."),
                new TriggerChatMessage("(Kharedst's Memoirs?)|(The Book of the Dead) now has (?<charges>.+) charges.")
        };
    }

    @Override
    protected  void onChargesUpdated()
    {
        log.info("charges updated: "+ charges);
        quantities.clear();
        quantities.put(ItemID.LAW_RUNE, (float) charges);
        quantities.put(ItemID.BODY_RUNE, (float) charges);
        quantities.put(ItemID.MIND_RUNE, (float) charges);
        quantities.put(ItemID.SOUL_RUNE, (float) charges);
    }

    @Override
    public Map<Integer, Float> getItemQuantities()
    {
        return quantities;
    }
}
