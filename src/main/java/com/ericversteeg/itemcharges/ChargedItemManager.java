//TODO: attribute
package com.ericversteeg.itemcharges;

import com.ericversteeg.InventoryTotalConfig;
import com.ericversteeg.itemcharges.items.U_FishBarrel;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;



@Slf4j
public class ChargedItemManager {

	private final int VARBIT_MINUTES = 8354;

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
	private OverlayManager overlays;

	@Inject
	private InventoryTotalConfig config;

	@Inject
	private ChatMessageManager chat_messages;

	@Inject
	private Notifier notifier;

	private ChargedItem[] infoboxes_charged_items;

	private final ZoneId timezone = ZoneId.of("Europe/London");
	private String date = LocalDateTime.now(timezone).format(DateTimeFormatter.ISO_LOCAL_DATE);

	public void startUp() {
		infoboxes_charged_items = new ChargedItem[]{
			new U_FishBarrel(client, client_thread, configs, items, chat_messages, notifier),
		};
	}

	public void shutDown() {

	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event) {
		log.debug("ITEM CONTAINER | " + event.getContainerId());

		for (final ChargedItem chargedItem : this.infoboxes_charged_items) {
			chargedItem.onItemContainersChanged(event);
		}
	}

	@Subscribe
	public void onChatMessage(final ChatMessage event) {
		Arrays.stream(infoboxes_charged_items).forEach(infobox -> infobox.onChatMessage(event));
		log.debug(
			"MESSAGE | " +
				"type: " + event.getType().name() +
				", message: " + event.getMessage().replaceAll("</?col.*?>", "") +
				", sender: " + event.getSender()
		);
	}

	@Subscribe
	public void onAnimationChanged(final AnimationChanged event) {
		Arrays.stream(infoboxes_charged_items).forEach(infobox -> infobox.onAnimationChanged(event));
		if (event.getActor() == client.getLocalPlayer()) {
			log.debug("ANIMATION | " +
				"id: " + event.getActor().getAnimation()
			);
		}
	}

	@Subscribe
	public void onGraphicChanged(final GraphicChanged event) {
		Arrays.stream(infoboxes_charged_items).forEach(infobox -> infobox.onGraphicChanged(event));
		if (event.getActor() == client.getLocalPlayer()) {
			log.debug("GRAPHIC | " +
				"id: " + event.getActor().getGraphic()
			);
		}
	}

	@Subscribe
	public void onConfigChanged(final ConfigChanged event) {
		Arrays.stream(infoboxes_charged_items).forEach(infobox -> infobox.onConfigChanged(event));
		if (event.getGroup().equals(config.GROUP)) {
			log.debug("CONFIG | " +
				"key: " + event.getKey() +
				", old value: " + event.getOldValue() +
				", new value: " + event.getNewValue()
			);
		}
	}

	@Subscribe
	public void onHitsplatApplied(final HitsplatApplied event) {
		Arrays.stream(infoboxes_charged_items).forEach(infobox -> infobox.onHitsplatApplied(event));
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
		Arrays.stream(infoboxes_charged_items).forEach(infobox -> infobox.onWidgetLoaded(event));
		log.debug("WIDGET | " +
			"group: " + event.getGroupId()
		);
	}

	@Subscribe
	public void onMenuOptionClicked(final MenuOptionClicked event) {
		Arrays.stream(infoboxes_charged_items).forEach(infobox -> infobox.onMenuOptionClicked(event));
		log.debug("OPTION | " +
			"option: " + event.getMenuOption() +
			", target: " + event.getMenuTarget() +
			", action name: " + event.getMenuAction().name() +
			", action id: " + event.getMenuAction().getId()
		);
	}

	@Subscribe
	public void onGameTick(final GameTick gametick) {
		for (final ChargedItem chargedItem : this.infoboxes_charged_items) {
			chargedItem.onGameTick(gametick);
		}
	}
}

