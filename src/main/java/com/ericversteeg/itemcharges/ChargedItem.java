//TODO: attribute tictac7x
package com.ericversteeg.itemcharges;

import com.ericversteeg.InventoryTotalConfig;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import com.ericversteeg.itemcharges.triggers.TriggerAnimation;
import com.ericversteeg.itemcharges.triggers.TriggerChatMessage;
import com.ericversteeg.itemcharges.triggers.TriggerGraphic;
import com.ericversteeg.itemcharges.triggers.TriggerHitsplat;
import com.ericversteeg.itemcharges.triggers.TriggerItem;
import com.ericversteeg.itemcharges.triggers.TriggerItemContainer;
import com.ericversteeg.itemcharges.triggers.TriggerMenuOption;
import com.ericversteeg.itemcharges.triggers.TriggerReset;
import com.ericversteeg.itemcharges.triggers.TriggerWidget;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.reflect.Type;

@Slf4j
public class ChargedItem {
	public final ChargesItem infobox_id;
	public int item_id;
	protected final Client client;
	protected final ClientThread client_thread;
	protected final ItemManager items;
	protected final ConfigManager configs;
	protected final ChatMessageManager chat_messages;
	protected final Notifier notifier;
	protected final Gson gson;

	@Nullable public ItemContainer inventory;
	@Nullable protected ItemContainer equipment;
	@Nullable private Item[] inventory_items;

	@Nullable protected String config_key;
	@Nullable protected String[] extra_config_keys;
	@Nullable protected TriggerChatMessage[] triggers_chat_messages;
	@Nullable protected TriggerAnimation[] triggers_animations;
	@Nullable protected TriggerGraphic[] triggers_graphics;
	@Nullable protected TriggerHitsplat[] triggers_hitsplats;
	@Nullable protected TriggerItem[] triggers_items;
	@Nullable protected TriggerWidget[] triggers_widgets;
	@Nullable protected TriggerReset[] triggers_resets;
	@Nullable protected TriggerItemContainer[] triggers_item_containers;
	@Nullable protected TriggerMenuOption[] triggers_menu_options;
	protected boolean supportsWidgetOnWidget = false;

	private boolean in_equipment;
	private boolean in_inventory;
	private final List<String[]> menu_entries = new ArrayList<>();
	private int animation = -1;
	private int graphic = -1;
	private int lastUseOnMeTick = -2;
	private boolean isInInventoryOrEquipment;
	protected int charges = ChargedItemManager.CHARGES_UNKNOWN;
	private Map<Integer, Integer> itemQuantities = null;

	@Nullable public Integer negative_full_charges;
	public boolean zero_charges_is_positive = false;
	private int gametick = 0;
	private int gametick_before = 0;

	public ChargedItem(
		final ChargesItem infobox_id,
		final int item_id,
		final Client client,
		final ClientThread client_thread,
		final ConfigManager configs,
		final ItemManager items,
		final ChatMessageManager chat_messages,
		final Notifier notifier,
		final Gson gson
	) {
		this.infobox_id = infobox_id;
		this.item_id = item_id;
		this.client = client;
		this.client_thread = client_thread;
		this.configs = configs;
		this.items = items;
		this.chat_messages = chat_messages;
		this.notifier = notifier;
		this.gson = gson;

		client_thread.invokeLater(() -> {
			loadChargesFromConfig();
			onChargesUpdated();
		});
	}

	protected void emptyOrClear()
	{
		if (itemQuantities == null)
			itemQuantities = new HashMap<>();
		else
			itemQuantities.clear();
		onItemQuantitiesModified();
	}

	protected void addItems(Integer itemId, Integer count)
	{
		itemQuantities.merge(itemId, count, Integer::sum);
		onItemQuantitiesModified();
	}

	protected Integer getItemCount()
	{
		if (itemQuantities == null)
			return 0;
		Integer itemCount = 0;
		for	(Integer quantity : itemQuantities.values())
		{
			itemCount += quantity;
		}
		return itemCount;
	}

	public boolean hasChargeData()
	{
		return itemQuantities != null;
		//TODO: switch to this one
		//return this.charges != ChargedItemManager.CHARGES_UNKNOWN || itemQuantities != null;
	}

	//should be overriden by derived class if they are using charges (should work like weapon charges)
	public Map<Integer,Integer> getItemQuantities()
	{
		return this.itemQuantities;
	}

	public int getCharges() {
		return charges;
	}

	//avoid GC
	private Map<Integer, Integer> differenceMap = new HashMap<>();

	public void onItemContainersChanged(final ItemContainerChanged event) {
		if (event.getItemContainer() == null) return;

		// Find items difference before items are overridden.
		int items_difference = 0;
		if (event.getItemContainer().getId() == InventoryID.INVENTORY.getId() && inventory_items != null) {
			items_difference = itemsDifference(inventory_items, event.getItemContainer().getItems());
		}

		differenceMap.clear();
		if (event.getItemContainer().getId() == InventoryID.INVENTORY.getId() && inventory_items != null) {
			Item[] before = inventory_items;
			Item[] after = event.getItemContainer().getItems();
			for (Item beforeItem : before)
			{
				differenceMap.merge(beforeItem.getId(), 1, Integer::sum);
			}
			for (Item afterItem : after)
			{
				differenceMap.merge(afterItem.getId(), -1, Integer::sum);
			}
		}
		if (event.getContainerId() == InventoryID.INVENTORY.getId())
		{
			if (itemQuantities != null && supportsWidgetOnWidget &&
				(lastUseOnMeTick == client.getTickCount() || lastUseOnMeTick + 1 == client.getTickCount()))
			{
				for (Integer itemId : differenceMap.keySet())
				{
					Integer count = differenceMap.get(itemId);
					if (count > 0)
					{
						addItems(itemId, count);
					}
				}
			}
		}

		// Update inventory reference.
		if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
			inventory = event.getItemContainer();
			inventory_items = inventory.getItems();
		}

		if (triggers_item_containers != null) {
			for (final TriggerItemContainer trigger_item_container : triggers_item_containers) {
				// Item container is wrong.
				if (trigger_item_container.inventory_id != event.getContainerId()) continue;

				// Menu target check.
				if (
					trigger_item_container.menu_target != null &&
						menu_entries.stream().noneMatch(entry -> entry[0].equals(trigger_item_container.menu_target))
				) continue;

				// Menu option check.
				if (
					trigger_item_container.menu_option != null &&
						menu_entries.stream().noneMatch(entry -> entry[1].equals(trigger_item_container.menu_option))
				) continue;

				// Fixed charges.
				if (trigger_item_container.fixed_charges != null) {
					setCharges(trigger_item_container.fixed_charges);
					break;
				}

				// Increase by difference of amount of items.
				if (trigger_item_container.increase_by_difference) {
					increaseCharges(items_difference);
					break;
				}
				
				//add missing items
				if (trigger_item_container.add_difference && itemQuantities != null) {

					for (Integer itemId : differenceMap.keySet())
					{
						Integer count = differenceMap.get(itemId);
						if (count > 0)
						{
							addItems(itemId, count);
						}
					}
					break;
				}

				// Charges dynamically based on the items of the item container.
				if (event.getItemContainer() != null) {
					setCharges(event.getItemContainer().count());
					break;
				}
			}
		}

		// Save inventory and equipment item containers for other uses.
		if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
			inventory = event.getItemContainer();
		} else if (event.getContainerId() == InventoryID.EQUIPMENT.getId()) {
			equipment = event.getItemContainer();
		}

		// No trigger items to detect charges.
		if (triggers_items == null) return;

		boolean in_equipment = false;
		boolean in_inventory = false;
		boolean inInventoryOrEquipment = false;
		Integer charges = null;

		for (final TriggerItem trigger_item : triggers_items) {
			// Find out if item is equipped.
			final boolean in_equipment_item = equipment != null && equipment.contains(trigger_item.item_id);
			final boolean in_inventory_item = inventory != null && inventory.contains(trigger_item.item_id);

			// Find out if infobox should be rendered.
			if (in_inventory_item || in_equipment_item) {
				inInventoryOrEquipment = true;

				// Update infobox item picture and tooltip dynamically based on the items if use has different variant of it.
				if (trigger_item.item_id != item_id) {
					updateInfobox(trigger_item.item_id);
				}

				if (in_equipment_item) in_equipment = true;
				if (in_inventory_item) in_inventory = true;
			}

			// Item not found, don't calculate charges.
			if (!in_equipment_item && !in_inventory_item) continue;

			// Find out charges for the item.
			if (trigger_item.fixed_charges != null) {
				if (charges == null) charges = 0;
				charges += inventory != null ? inventory.count(trigger_item.item_id) * trigger_item.fixed_charges : 0;
				charges += equipment != null ? equipment.count(trigger_item.item_id) * trigger_item.fixed_charges : 0;
			}
		}

		// Update infobox variables for other triggers.
		this.in_equipment = in_equipment;
		this.in_inventory = in_inventory;
		this.isInInventoryOrEquipment = inInventoryOrEquipment;
		if (charges != null) this.charges = charges;
	}

	public void onChatMessage(final ChatMessage event) {
		if (
			// No chat messages triggers.
			triggers_chat_messages == null ||
				// Message type that we are not interested in.
				event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.MESBOX ||
				// No config to save charges to.
				config_key == null ||
				// Not in inventory nor in equipment.
				(!in_inventory && !in_equipment)
		) return;

		final String message = event.getMessage().replaceAll("</?col.*?>", "").replaceAll("<br>", " ");

		for (final TriggerChatMessage chat_message : triggers_chat_messages) {
			final Pattern regex = chat_message.message;
			final Matcher matcher = regex.matcher(message);

			// Message does not match the pattern.
			if (!matcher.find()) continue;

			// Menu target check.
			if (
				chat_message.menu_target &&
					menu_entries.stream().noneMatch(entry -> entry[0].equals(items.getItemComposition(item_id).getName()))
			) continue;

			// Item needs to be equipped.
			if (chat_message.equipped && !in_equipment) continue;

			// Increase charges by fixed amount.
			if (chat_message.increase_charges != null) {
				increaseCharges(chat_message.increase_charges);

				// Decrease charges by fixed amount.
			} else if (chat_message.decrease_charges != null) {
				decreaseCharges(chat_message.decrease_charges);

				// Set charges to fixed amount.
			} else if (chat_message.fixed_charges != null) {
				setCharges(chat_message.fixed_charges);

				// Set charges from multiple amounts.
			} else if (chat_message.multiple_charges) {
				int charges = 0;

				final Matcher matcher_multiple = Pattern.compile(".*?(\\d+)").matcher(message);
				while (matcher_multiple.find()) {
					charges += Integer.parseInt(matcher_multiple.group(1));
				}

				setCharges(charges);

				// Custom consumer.
			} else if (chat_message.consumer != null) {
				chat_message.consumer.accept(message);

				// Set charges dynamically from the chat message.
			} else if (matcher.group("charges") != null) {
				try {
					final int charges = Integer.parseInt(matcher.group("charges").replaceAll(",", "").replaceAll("\\.", ""));

					if (chat_message.increase_dynamically) {
						increaseCharges(charges);
					} else {
						setCharges(charges);
					}
				} catch (final Exception ignored) {}

				// No trigger found.
			} else {
				continue;
			}

			// Check extra matches groups.
			if (extra_config_keys != null) {
				for (final String extra_group : extra_config_keys) {
					final String extra = matcher.group(extra_group);
					if (extra != null) {
						setConfiguration(config_key + "_" + extra_group, extra.replaceAll(",", ""));
					}
				}
			}

			// Notifications.
			if (chat_message.notification) {
				notifier.notify(chat_message.notification_message != null ? chat_message.notification_message : message);
			}

			// Chat message used, no need to check other messages.
			return;
		}
	}
	public void onWidgetLoaded(final WidgetLoaded event) {
		if (triggers_widgets == null || config_key == null) return;

		client_thread.invokeLater(() -> {
			for (final TriggerWidget trigger_widget : triggers_widgets) {
				if (event.getGroupId() != trigger_widget.group_id) continue;

				Widget widget = client.getWidget(trigger_widget.group_id, trigger_widget.child_id);
				if (trigger_widget.sub_child_id != null && widget != null) widget = widget.getChild(trigger_widget.sub_child_id);
				if (widget == null) continue;

				final String message = widget.getText().replaceAll("</?col.*?>", "").replaceAll("<br>", " ");
				final Pattern regex = Pattern.compile(trigger_widget.message);
				final Matcher matcher = regex.matcher(message);
				if (!matcher.find()) continue;

				// Charges amount is fixed.
				if (trigger_widget.charges != null) {
					setCharges(trigger_widget.charges);
					// Charges amount has custom logic.
				} else if (trigger_widget.consumer != null) {
					trigger_widget.consumer.accept(message);
					// Charges amount is dynamic.
				} else if (matcher.group("charges") != null) {
					final int charges = Integer.parseInt(matcher.group("charges").replaceAll(",", ""));

					// Charges increased dynamically.
					if (trigger_widget.increase_dynamically) {
						increaseCharges(charges);
					} else {
						setCharges(charges);
					}
				}

				// Check extra matches groups.
				if (extra_config_keys != null) {
					for (final String extra_group : extra_config_keys) {
						final String extra = matcher.group(extra_group);
						if (extra != null) setConfiguration(config_key + "_" + extra_group, extra);
					}
				}
			}
		});
	}

	// public void onConfigChanged(final ConfigChanged event) {
	// 	if (event.getGroup().equals(InventoryTotalConfig.GROUP) && event.getKey().equals(config_key)) {
	// 		charges = Integer.parseInt(event.getNewValue());
	// 	}
	// }

	public void onAnimationChanged(final AnimationChanged event) {
		// Player check.
		if (event.getActor() != client.getLocalPlayer()) return;

		// Save animation ID for others to use.
		animation = event.getActor().getAnimation();

		// No animations to check.
		if (inventory == null || triggers_animations == null || charges == ChargedItemManager.CHARGES_UNKNOWN || triggers_items == null) return;

		// Check all animation triggers.
		for (final TriggerAnimation trigger_animation : triggers_animations) {
			// Valid animation id check.
			if (trigger_animation.animation_id != event.getActor().getAnimation()) continue;

			// Unallowed items check.
			if (trigger_animation.unallowed_items != null) {
				boolean unallowed_items = false;
				for (final int item_id : trigger_animation.unallowed_items) {
					if (inventory.contains(item_id) || equipment != null && equipment.contains(item_id)) {
						unallowed_items = true;
						break;
					}
				}
				if (unallowed_items) continue;
			}

			// Equipped check.
			if (trigger_animation.equipped) {
				boolean equipped = false;
				for (final TriggerItem trigger_item : triggers_items) {
					if (equipment != null && equipment.contains(trigger_item.item_id)) {
						equipped = true;
						break;
					}
				}
				if (!equipped) continue;
			}

			// Menu target check.
			if (
				trigger_animation.menu_target &&
					menu_entries.stream().noneMatch(entry -> entry[0].equals(items.getItemComposition(item_id).getName()))
			) continue;

			// Menu option check.
			if (
				trigger_animation.menu_option != null &&
					menu_entries.stream().noneMatch(entry -> entry[1].equals(trigger_animation.menu_option))
			) continue;

			// Valid trigger, modify charges.
			if (trigger_animation.decrease_charges) {
				decreaseCharges(trigger_animation.charges);
			} else {
				increaseCharges(trigger_animation.charges);
			}
		}
	}

	public void onGraphicChanged(final GraphicChanged event) {
		// Player check.
		if (event.getActor() != client.getLocalPlayer()) return;

		// Save animation ID for others to use.
		graphic = event.getActor().getGraphic();

		// No animations to check.
		if (inventory == null || triggers_graphics == null || charges == ChargedItemManager.CHARGES_UNKNOWN || triggers_items == null) return;

		// Check all animation triggers.
		for (final TriggerGraphic trigger_graphic : triggers_graphics) {
			// Valid animation id check.
			if (!event.getActor().hasSpotAnim(trigger_graphic.graphic_id)) continue;

			// Unallowed items check.
			if (trigger_graphic.unallowed_items != null) {
				boolean unallowed_items = false;
				for (final int item_id : trigger_graphic.unallowed_items) {
					if (inventory.contains(item_id) || equipment != null && equipment.contains(item_id)) {
						unallowed_items = true;
						break;
					}
				}
				if (unallowed_items) continue;
			}

			// Equipped check.
			if (trigger_graphic.equipped) {
				boolean equipped = false;
				for (final TriggerItem trigger_item : triggers_items) {
					if (equipment != null && equipment.contains(trigger_item.item_id)) {
						equipped = true;
						break;
					}
				}
				if (!equipped) continue;
			}

			// Menu option check.
			if (trigger_graphic.menu_option != null && (menu_entries.stream().noneMatch(entry -> entry[1].equals(trigger_graphic.menu_option)))) continue;

			// Valid trigger, modify charges.
			if (trigger_graphic.decrease_charges) {
				decreaseCharges(trigger_graphic.charges);
			} else {
				increaseCharges(trigger_graphic.charges);
			}
		}
	}

	public void onHitsplatApplied(final HitsplatApplied event) {
		if (triggers_hitsplats == null) return;

		// Check all hitsplat triggers.
		for (final TriggerHitsplat trigger_hitsplat : triggers_hitsplats) {
			// Player check.
			if (trigger_hitsplat.self && event.getActor() != client.getLocalPlayer()) continue;

			// Enemy check.
			if (!trigger_hitsplat.self && (event.getActor() == client.getLocalPlayer() || event.getHitsplat().isOthers())) continue;

			// Hitsplat type check.
			if (trigger_hitsplat.hitsplat_id != event.getHitsplat().getHitsplatType()) continue;

			// Equipped check.
			if (trigger_hitsplat.equipped && triggers_items != null && equipment != null) {
				boolean equipped = false;
				for (final TriggerItem trigger_item : triggers_items) {
					if (equipment.contains(trigger_item.item_id)) {
						equipped = true;
						break;
					}
				}
				if (!equipped) continue;
			}

			// Animation check.
			if (trigger_hitsplat.animations != null) {
				boolean valid = false;
				for (final int animation : trigger_hitsplat.animations) {
					if (animation == this.animation) {
						valid = true;
						break;
					}
				}
				if (!valid) continue;
			}

			// Valid hitsplat, modify charges.
			decreaseCharges(trigger_hitsplat.discharges);
		}
	}


	public void onMenuOptionClicked(final MenuOptionClicked event) {

		if (event.getMenuAction() == MenuAction.WIDGET_TARGET_ON_WIDGET && this.supportsWidgetOnWidget) {
			ItemContainer itemContainer = client.getItemContainer(InventoryID.INVENTORY);
			if (itemContainer == null)
				return;
			Item itemA = itemContainer.getItem(client.getSelectedWidget().getIndex());
			if (itemA == null) 
				return;
			int itemAId = itemA.getId();
			Item itemB = itemContainer.getItem(event.getWidget().getIndex());
			if (itemB == null) 
				return;
			int itemBId = itemB.getId();

			boolean usedItemOnMe = this.item_id == itemAId || this.item_id == itemBId;
			if (usedItemOnMe)
			{
				lastUseOnMeTick = client.getTickCount();
			}
		}

		final String menu_target = event.getMenuTarget().replaceAll("</?col.*?>", "");
		final String menu_option = event.getMenuOption();

		if (
			// Not menu.
			menu_target.length() == 0 ||
				// Item not in inventory nor equipment.
				!in_inventory && !in_equipment ||
				// Menu option not found.
				menu_option == null || menu_option.length() == 0
		) {
			return;
		}

		// Gametick changed, clear previous menu entries since they are no longer valid.
		if (gametick > gametick_before + 1) {
			gametick = 0; gametick_before = 0;
			menu_entries.clear();
		}

		// Save menu option and target for other triggers to use.
		menu_entries.add(new String[]{menu_target, menu_option});

		// No menu option triggers.
		if (triggers_menu_options == null) return;

		for (final TriggerMenuOption trigger_menu_option : triggers_menu_options) {
			if (
				!trigger_menu_option.option.equals(menu_option) ||
					trigger_menu_option.target != null && !trigger_menu_option.target.equals(menu_target)
			) continue;

			// Fixed charges.
			new Thread(() -> {
				try { Thread.sleep(600); } catch (final Exception ignored) {}

				if (trigger_menu_option.charges != null)
				{
					setCharges(trigger_menu_option.charges);
				}
				else
				{
					trigger_menu_option.consumer.accept(menu_target);
				}
			}).start();

			// Menu option used.
			return;
		}
	}

	public void resetCharges() {
		if (triggers_resets == null) return;

		// Send message about item charges being reset if player has it on them.
		if (in_equipment || in_inventory) {
			client_thread.invokeLater(() -> {
				chat_messages.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage("<colHIGHLIGHT>" + items.getItemComposition(item_id).getName() + " daily charges have been reset.")
					.build()
				);
			});
		}

		// Check for item resets.
		for (final TriggerReset trigger_reset : triggers_resets) {
			// Same item variations have different amount of charges.
			if (trigger_reset.item_id != null) {
				if (item_id == trigger_reset.item_id) {
					setCharges(trigger_reset.charges);
				}

				// All variants of the item reset to the same amount of charges.
			} else {
				setCharges(trigger_reset.charges);
			}
		}
	}

	private void loadChargesFromConfig() {
		if (config_key == null) 
			return;
		
		try {
			charges = Integer.parseInt(configs.getConfiguration(InventoryTotalConfig.GROUP, config_key));
			return;
		} catch (final Exception ignored) {}

		//if that didn't work try loading map
		try {
			Type mapType = new com.google.gson.reflect.TypeToken<Map<Integer, Integer>>() {}.getType();
			itemQuantities = gson.fromJson(configs.getConfiguration(InventoryTotalConfig.GROUP, config_key), mapType);
			return;
		} catch (final Exception ignored) {}

	}

	public void setCharges(final int charges) {
		if (this.negative_full_charges != null && charges > this.negative_full_charges) return;

		this.charges = charges;
		onChargesUpdated();

		if (config_key != null) {
			setConfiguration(config_key, charges);
		}
	}

	private void onItemQuantitiesModified() {

		if (config_key != null) {
			setConfiguration(config_key, gson.toJson(this.itemQuantities));
		}
	}

	private void decreaseCharges(final int charges) {
		if (this.charges - charges < 0) return;
		setCharges(this.charges - charges);
	}

	public void increaseCharges(final int charges) {
		if (this.charges < 0) return;
		setCharges(this.charges + charges);
	}

	private void setConfiguration(final String key, @Nonnull final String value) {
		configs.setConfiguration(InventoryTotalConfig.GROUP, key, value);
	}

	private void setConfiguration(final String key, final int value) {
		configs.setConfiguration(InventoryTotalConfig.GROUP, key, value);
	}

	private void updateInfobox(final int item_id) {
		// Item id.
		this.item_id = item_id;
	}

	protected void onChargesUpdated() {}

	public void onGameTick(final GameTick ignored) {
		gametick++;
	}

	private int itemsDifference(final Item[] items_before, final Item[] items_after) {
		final int items_before_count = (int) Arrays.stream(items_before).filter(item -> item.getId() != -1).count();
		final int items_after_count = (int) Arrays.stream(items_after).filter(item -> item.getId() != -1).count();

		return Math.abs(items_before_count - items_after_count);

	}
}


