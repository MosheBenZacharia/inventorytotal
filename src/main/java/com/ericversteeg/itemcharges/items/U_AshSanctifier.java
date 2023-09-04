//TODO: Attribute
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

public class U_AshSanctifier extends ChargedItem {

    //avoid GC but don't confuse super class
    private final Map<Integer, Float> quantities = new HashMap<>();

    public U_AshSanctifier(
            final Client client,
            final ClientThread client_thread,
            final ConfigManager configs,
            final ItemManager items,
            final ChatMessageManager chat_messages,
            final Notifier notifier,
            final Gson gson,
            final ScheduledExecutorService executorService
    ) {
        super(ChargesItem.ASH_SANCTIFIER, ItemID.ASH_SANCTIFIER, client, client_thread, configs, items, chat_messages, notifier, gson, executorService);
        this.config_key = InventoryTotalConfig.ash_sanctifier;
        this.triggers_items = new TriggerItem[]{
                new TriggerItem(ItemID.ASH_SANCTIFIER),
        };
        this.triggers_chat_messages = new TriggerChatMessage[]{
                new TriggerChatMessage("Your ash sanctifier has (?<charges>.+) charges? left."),
                new TriggerChatMessage("The ash sanctifier has (?<charges>.+) charges?. It is active and ready to scatter ashes.").onItemClick(),
        };
        this.triggers_xp_drops = new TriggerXPDrop[]{
                new TriggerXPDrop(Skill.PRAYER, 1),
        };
    }

    @Override
    protected  void onChargesUpdated()
    {
        quantities.clear();
        quantities.put(ItemID.DEATH_RUNE, ((float) charges)/10f);
    }

    @Override
    public Map<Integer, Float> getItemQuantities()
    {
        return quantities;
    }
}