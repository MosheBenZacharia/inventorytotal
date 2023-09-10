/*
 * Copyright (c) 2023, Moshe Ben-Zacharia <https://github.com/MosheBenZacharia>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.ericversteeg.itemcharges.items;

import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ericversteeg.InventoryTotalConfig;
import com.ericversteeg.itemcharges.ChargedItem;
import com.ericversteeg.itemcharges.ChargesItem;
import com.ericversteeg.itemcharges.triggers.TriggerChatMessage;
import com.ericversteeg.itemcharges.triggers.TriggerItem;
import com.ericversteeg.itemcharges.triggers.TriggerItemContainer;
import com.ericversteeg.itemcharges.triggers.TriggerItemDespawn;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemID;
import net.runelite.api.TileItem;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

@Slf4j
public class U_GemBag extends ChargedItem
{
    private final int CAPACITY = 60;
    private static final String checkRegex = "Sapphires: (\\d+) \\/ Emeralds: (\\d+) \\/ Rubies: (\\d+) Diamonds: (\\d+) \\/ Dragonstones: (\\d+)";
    private static final Pattern checkPattern = Pattern.compile(checkRegex);

    public U_GemBag(
            final Client client,
            final ClientThread client_thread,
            final ConfigManager configs,
            final ItemManager items,
            final ChatMessageManager chat_messages,
            final Notifier notifier,
            final Gson gson,
            final ScheduledExecutorService executorService
    ) {
        super(ChargesItem.GEM_BAG, ItemID.GEM_BAG, client, client_thread, configs, items, chat_messages, notifier, gson, executorService);

        this.config_key = InventoryTotalConfig.gem_bag;
        this.zero_charges_is_positive = true;
        this.triggers_items = new TriggerItem[]{
                new TriggerItem(ItemID.GEM_BAG_12020),
                new TriggerItem(ItemID.OPEN_GEM_BAG, true),
        };
        this.trigger_item_despawn = new TriggerItemDespawn((TileItem tileItem) ->
        {
            if (tileItem.getId() == ItemID.UNCUT_SAPPHIRE ||
                tileItem.getId() == ItemID.UNCUT_EMERALD ||
                tileItem.getId() == ItemID.UNCUT_RUBY ||
                tileItem.getId() == ItemID.UNCUT_DIAMOND ||
                tileItem.getId() == ItemID.UNCUT_DRAGONSTONE)
            {
                addDespawnedGemIfHasCapacity(tileItem);
            }
        });
        this.triggers_chat_messages = new TriggerChatMessage[]{
                new TriggerChatMessage("The gem bag is now empty.").onItemClick().extraConsumer((message) -> { super.emptyOrClear(); }),
                new TriggerChatMessage("The gem bag is empty.").onItemClick().extraConsumer((message) -> { super.emptyOrClear(); }),
                new TriggerChatMessage(checkRegex).extraConsumer(message -> {

                    super.emptyOrClear();
                    final Matcher matcher = checkPattern.matcher(message);
                    while (matcher.find())
                    {
                        try
                        {
                            int sapphires = Integer.parseInt(matcher.group(1));
                            int emeralds = Integer.parseInt(matcher.group(2));
                            int rubies = Integer.parseInt(matcher.group(3));
                            int diamonds = Integer.parseInt(matcher.group(4));
                            int dragonstones = Integer.parseInt(matcher.group(5));

                            super.addItems(ItemID.UNCUT_SAPPHIRE, (float) sapphires);
                            super.addItems(ItemID.UNCUT_EMERALD, (float) emeralds);
                            super.addItems(ItemID.UNCUT_RUBY, (float) rubies);
                            super.addItems(ItemID.UNCUT_DIAMOND, (float) diamonds);
                            super.addItems(ItemID.UNCUT_DRAGONSTONE, (float) dragonstones);
                        }
                        catch (NumberFormatException e)
                        {
                            log.error("couldn't parse gem bag check", e);
                        }
                    }
                }),
        };
        this.triggers_item_containers = new TriggerItemContainer[]{
            new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Open gem bag").menuOption("Fill").addDifference(),
            new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Gem bag").menuOption("Fill").addDifference(),
            //Empty into bank doesn't make a chat message (unless it's already empty)
            new TriggerItemContainer(InventoryID.BANK.getId()).menuTarget("Open gem bag").menuOption("Empty").extraConsumer(() -> super.emptyOrClear()),
            new TriggerItemContainer(InventoryID.BANK.getId()).menuTarget("Gem bag").menuOption("Empty").extraConsumer(() -> super.emptyOrClear()),
        };
        this.supportsWidgetOnWidget = true;
    }

    private void addDespawnedGemIfHasCapacity(TileItem tileItem)
    {
        if (tileItem.getQuantity() == 1
                && (!super.itemQuantities.containsKey(tileItem.getId()) || super.itemQuantities.get(tileItem.getId()) < CAPACITY))
        {
            super.addItems(tileItem.getId(), 1f);
        }
    }
}
