//TODO: attribute
package com.ericversteeg.itemcharges.items;

import com.ericversteeg.InventoryTotalConfig;
import com.ericversteeg.itemcharges.ChargedItem;
import com.ericversteeg.itemcharges.ChargesItem;
import com.ericversteeg.itemcharges.triggers.TriggerChatMessage;
import com.ericversteeg.itemcharges.triggers.TriggerItem;
import com.ericversteeg.itemcharges.triggers.TriggerItemContainer;
import com.ericversteeg.itemcharges.triggers.TriggerMenuOption;
import com.google.common.collect.ImmutableMap;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class U_FishBarrel extends ChargedItem
{
	private final int FISH_BARREL_SIZE = 28;
	private static final String catchMessage = "^You catch (an?|some) ([a-zA-Z ]+)[.!]( It hardens as you handle it with your ice gloves\\.)?$";
	private static final Pattern catchPattern = Pattern.compile(catchMessage);
	private static final String checkRegex = "([0-9]+) x ([a-zA-Z ]+),? ?";
	private static final Pattern checkPattern = Pattern.compile(checkRegex);
	private Integer lastFishCaught = null;
	
	// maps the name of the fish as it appears in chat message to corresponding item ID
	private static final Map<String, Integer> FISH_TYPES_BY_NAME = ImmutableMap.<String, Integer>builder()
		// singular 'shrimp' may occur when fishing for Karambwanji
		.put("shrimp", ItemID.RAW_SHRIMPS)
		.put("shrimps", ItemID.RAW_SHRIMPS)
		.put("sardine", ItemID.RAW_SARDINE)
		.put("herring", ItemID.RAW_HERRING)
		.put("anchovies", ItemID.RAW_ANCHOVIES)
		.put("mackerel", ItemID.RAW_MACKEREL)
		.put("trout", ItemID.RAW_TROUT)
		.put("cod", ItemID.RAW_COD)
		.put("pike", ItemID.RAW_PIKE)
		.put("slimy swamp eel", ItemID.RAW_SLIMY_EEL)
		.put("salmon", ItemID.RAW_SALMON)
		.put("tuna", ItemID.RAW_TUNA)
		.put("rainbow fish", ItemID.RAW_RAINBOW_FISH)
		.put("cave eel", ItemID.RAW_CAVE_EEL)
		.put("lobster", ItemID.RAW_LOBSTER)
		.put("bass", ItemID.RAW_BASS)
		.put("leaping trout", ItemID.LEAPING_TROUT)
		.put("swordfish", ItemID.RAW_SWORDFISH)
		.put("lava eel", ItemID.RAW_LAVA_EEL)
		.put("leaping salmon", ItemID.LEAPING_SALMON)
		.put("monkfish", ItemID.RAW_MONKFISH)
		.put("karambwan", ItemID.RAW_KARAMBWAN)
		.put("leaping sturgeon", ItemID.LEAPING_STURGEON)
		.put("shark", ItemID.RAW_SHARK)
		.put("infernal eel", ItemID.INFERNAL_EEL)
		.put("anglerfish", ItemID.RAW_ANGLERFISH)
		.put("dark crab", ItemID.RAW_DARK_CRAB)
		.put("sacred eel", ItemID.SACRED_EEL)
		.build();

	public U_FishBarrel(
		final Client client,
		final ClientThread client_thread,
		final ConfigManager configs,
		final ItemManager items,
		final ChatMessageManager chat_messages,
		final Notifier notifier
	) {
		super(ChargesItem.FISH_BARREL, ItemID.FISH_BARREL, client, client_thread, configs, items, chat_messages, notifier);

		this.config_key = InventoryTotalConfig.fish_barrel;
		this.zero_charges_is_positive = true;
		this.triggers_items = new TriggerItem[]{
			new TriggerItem(ItemID.FISH_BARREL),
			new TriggerItem(ItemID.OPEN_FISH_BARREL),
			new TriggerItem(ItemID.FISH_SACK_BARREL),
			new TriggerItem(ItemID.OPEN_FISH_SACK_BARREL)
		};
		this.triggers_chat_messages = new TriggerChatMessage[]{
			new TriggerChatMessage("(Your|The) barrel is empty.").onItemClick().extraConsumer((message) ->
			{
				super.emptyOrClear();
			}),
			//new TriggerChatMessage("The barrel is full. It may be emptied at a bank.").onItemClick().fixedCharges(FISH_BARREL_SIZE),
			new TriggerChatMessage("(You catch .*)").extraConsumer(message -> {
				if ((item_id == ItemID.OPEN_FISH_BARREL || item_id == ItemID.OPEN_FISH_SACK_BARREL) && getItemCount() < FISH_BARREL_SIZE && super.hasChargeData()) {
					final Matcher matcher = catchPattern.matcher(message);
					if (matcher.matches())
					{
						final String fishName = matcher.group(2).toLowerCase();
						if (FISH_TYPES_BY_NAME.containsKey(fishName))
						{
							Integer fishId = FISH_TYPES_BY_NAME.get(fishName);
							lastFishCaught = fishId;
							super.itemQuantities.merge(fishId, 1, Integer::sum);
						}
					}
					else
					{
						log.error("no match found");
					}
				}
			}),
			//new TriggerChatMessage("The barrel is full. It may be emptied at a bank.").onItemClick().fixedCharges(FISH_BARREL_SIZE),
			new TriggerChatMessage("(.* enabled you to catch an extra fish.)").extraConsumer(message -> {
				if ((item_id == ItemID.OPEN_FISH_BARREL || item_id == ItemID.OPEN_FISH_SACK_BARREL) && getItemCount() < FISH_BARREL_SIZE && super.hasChargeData()) {
					
					if (lastFishCaught != null)
					{
						super.itemQuantities.merge(lastFishCaught, 1, Integer::sum);
					}
					else
					{
						log.error("last fish caught is null");
					}
				}
			}),
			new TriggerChatMessage("The barrel contains:").extraConsumer(message -> {
				
				super.emptyOrClear();
				//fix weird whitespace issue
				final Matcher matcher = checkPattern.matcher(message.replace("\u00A0", " "));
				while (matcher.find())
				{
					try
					{
						int fishAmount = Integer.parseInt(matcher.group(1));
						String fishName = matcher.group(2).toLowerCase().replace("raw ", "");
						if (FISH_TYPES_BY_NAME.containsKey(fishName))
						{
							Integer fishId = FISH_TYPES_BY_NAME.get(fishName);
							lastFishCaught = fishId;
							super.itemQuantities.merge(fishId, fishAmount, Integer::sum);
						}
						else
						{
							log.error("no match found");
						}
					}
					catch (NumberFormatException e)
					{
						log.error("couldn't parse fish barrel check", e);
					}
				}
			}),
		};
		this.triggers_item_containers = new TriggerItemContainer[]{
			new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Open fish barrel").menuOption("Fill").addDifference(),
			new TriggerItemContainer(InventoryID.INVENTORY.getId()).menuTarget("Fish barrel").menuOption("Fill").addDifference(),
		};
		this.triggers_menu_options = new TriggerMenuOption[]{
			new TriggerMenuOption("Open fish barrel", "Empty").extraConsumer((message) ->
			{
				super.emptyOrClear();
			}),
			new TriggerMenuOption("Fish barrel", "Empty").extraConsumer((message) ->
			{
				super.emptyOrClear();
			})
		};
	}
}
