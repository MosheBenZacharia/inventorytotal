//TODO: attribute
package com.ericversteeg.itemcharges;

import com.ericversteeg.InventoryTotalConfig;
import com.ericversteeg.itemcharges.items.*;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;



@Slf4j
public class ChargedItemManager {


	public static final int CHARGES_UNKNOWN = -1;
	public static final int CHARGES_UNLIMITED = -2;

	@Inject
	private Client client;

	@Inject
	private ClientThread client_thread;

	@Inject
	private ItemManager items;

	@Inject
	private ConfigManager configs;

	@Inject
	private InventoryTotalConfig config;

	@Inject
	private ChatMessageManager chat_messages;

	@Inject
	private Notifier notifier;

	@Inject
	private Gson gson;

	private ChargedItem[] chargedItems;

	public void startUp() {
		chargedItems = new ChargedItem[]{
			new U_FishBarrel(client, client_thread, configs, items, chat_messages, notifier, gson),
			new U_LogBasket(client, client_thread, configs, items, chat_messages, notifier, gson),
			new S_KharedstMemoirs(client, client_thread, configs, items, chat_messages, notifier, gson),
			new U_BottomlessCompostBucket(client, client_thread, configs, items, chat_messages, notifier, gson),
			new U_AshSanctifier(client, client_thread, configs, items, chat_messages, notifier, gson),
			new U_BloodEssence(client, client_thread, configs, items, chat_messages, notifier, gson),
			new U_GemBag(client, client_thread, configs, items, chat_messages, notifier, gson),
			new U_HerbSack(client, client_thread, configs, items, chat_messages, notifier, gson),
			new U_SeedBox(client, client_thread, configs, items, chat_messages, notifier, gson),
		};
	}

	public void shutDown() {

	}

	@Subscribe
	public void onItemDespawned(final ItemDespawned event)
	{
		log.debug("ITEM DESPAWNED | " + event.getItem().getId());

		for (final ChargedItem chargedItem : this.chargedItems) {
			chargedItem.onItemDespawned(event);
		}
	}

	@Subscribe
	public void onStatChanged(final StatChanged event)
	{
		log.debug("STAT CHANGED | " + event.getSkill());

		for (final ChargedItem chargedItem : this.chargedItems) {
			chargedItem.onStatChanged(event);
		}
	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event) {
		log.debug("ITEM CONTAINER | " + event.getContainerId());

		for (final ChargedItem chargedItem : this.chargedItems) {
			chargedItem.onItemContainersChanged(event);
		}
	}

	@Subscribe
	public void onChatMessage(final ChatMessage event) {
		Arrays.stream(chargedItems).forEach(chargedItem -> chargedItem.onChatMessage(event));
		log.debug(
			"MESSAGE | " +
				"type: " + event.getType().name() +
				", message: " + event.getMessage().replaceAll("</?col.*?>", "") +
				", sender: " + event.getSender()
		);
	}

	@Subscribe
	public void onAnimationChanged(final AnimationChanged event) {
		Arrays.stream(chargedItems).forEach(chargedItem -> chargedItem.onAnimationChanged(event));
		if (event.getActor() == client.getLocalPlayer()) {
			log.debug("ANIMATION | " +
				"id: " + event.getActor().getAnimation()
			);
		}
	}

	@Subscribe
	public void onGraphicChanged(final GraphicChanged event) {
		Arrays.stream(chargedItems).forEach(chargedItem -> chargedItem.onGraphicChanged(event));
		if (event.getActor() == client.getLocalPlayer()) {
			log.debug("GRAPHIC | " +
				"id: " + event.getActor().getGraphic()
			);
		}
	}

	// @Subscribe
	// public void onConfigChanged(final ConfigChanged event) {
	// 	Arrays.stream(chargedItems).forEach(chargedItem -> chargedItem.onConfigChanged(event));
	// 	if (event.getGroup().equals(config.GROUP)) {
	// 		log.debug("CONFIG | " +
	// 			"key: " + event.getKey() +
	// 			", old value: " + event.getOldValue() +
	// 			", new value: " + event.getNewValue()
	// 		);
	// 	}
	// }

	@Subscribe
	public void onHitsplatApplied(final HitsplatApplied event) {
		Arrays.stream(chargedItems).forEach(chargedItem -> chargedItem.onHitsplatApplied(event));
		log.debug("HITSPLAT | " +
			"actor: " + (event.getActor() == client.getLocalPlayer() ? "self" : "enemy") +
			", type: " + event.getHitsplat().getHitsplatType() +
			", amount:" + event.getHitsplat().getAmount() +
			", others = " + event.getHitsplat().isOthers() +
			", mine = " + event.getHitsplat().isMine()
		);
	}

	@Subscribe
	public void onWidgetLoaded(final WidgetLoaded event) {
		Arrays.stream(chargedItems).forEach(chargedItem -> chargedItem.onWidgetLoaded(event));
		log.debug("WIDGET | " +
			"group: " + event.getGroupId()
		);
	}

	@Subscribe
	public void onMenuOptionClicked(final MenuOptionClicked event) {
		Arrays.stream(chargedItems).forEach(chargedItem -> chargedItem.onMenuOptionClicked(event));
		log.debug("OPTION | " +
			"option: " + event.getMenuOption() +
			", target: " + event.getMenuTarget() +
			", action name: " + event.getMenuAction().name() +
			", action id: " + event.getMenuAction().getId()
		);
	}

	@Subscribe
	public void onGameTick(final GameTick gametick) {
		for (final ChargedItem chargedItem : this.chargedItems) {
			chargedItem.onGameTick(gametick);
		}
	}


	/// API for InventoryTotalPlugin

	private  Map<Integer, Float> emptyMap = new HashMap<>();

	public boolean isChargeableItem(Integer itemId)
	{
		return getChargedItem(itemId) != null;
	}

	private ChargedItem getChargedItem(Integer itemId)
	{
		for (ChargedItem chargedItem : chargedItems) {
			//note that the item's item_id is constantly updated based on which variation is in your inventory/equipment
			if (chargedItem.item_id == itemId) {
				return chargedItem;
			}
		}
		return null;
	}

	public boolean hasChargeData(Integer itemId)
	{
		ChargedItem chargedItem = getChargedItem(itemId);
		if (chargedItem == null)
		{
			log.info("Didn't find a charged item for this itemID, this shouldn't happen.");
			return false;
		}
		return chargedItem.hasChargeData();
	}

	public Map<Integer, Float> getItemQuantities(Integer itemId)
	{
		ChargedItem chargedItem = getChargedItem(itemId);
		if (chargedItem == null)
		{
			log.info("Didn't find a charged item for this itemID, this shouldn't happen.");
			return emptyMap;
		}
		return chargedItem.getItemQuantities();
	}
}

