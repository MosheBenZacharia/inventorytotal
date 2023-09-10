/*
 * Copyright (c) 2023, Moshe Ben-Zacharia <https://github.com/MosheBenZacharia>, nofatigue <https://github.com/nofatigue>
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
package com.ericversteeg;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

import java.awt.*;
import java.text.NumberFormat;

import static net.runelite.api.ScriptID.XPDROPS_SETDROPSIZE;
import static net.runelite.api.ScriptID.XPDROP_DISABLED;
import static net.runelite.api.widgets.WidgetInfo.TO_CHILD;
import static net.runelite.api.widgets.WidgetInfo.TO_GROUP;

@Slf4j
/*
	Implement gold drops.
	We do this by using the XPDrop mechanism, namely the Fake XPDrop script,
	which is intended to generate xp drops for maxed out skills.
	Fake XP Drops are composed of a skill sprite,
	and a text widget with a mod icon (<img=11> in text)
	So to create a gold drop, we create a fake xp drop, and interefere in the middle,
	and change the sprite and text to our liking.

	Flow is:

	1. create xp drop using runScript (see requestGoldDrop)
	2. getting in the middle of the drop, changing icon and text (see handleXpDrop)

	A more correct way to do this is probably by calling Item.GetImage with wanted
	coin quantity, which will give us correct coin icon and correct text,
	and simply drawing that image ourselfs somehow. Instead of using xp drop mechanism.
	*/
public class InventoryTotalGoldDrops {
	/*
	Free sprite ids for the gold icons.
	 */
	private static final int COINS_100_SPRITE_ID = -1337;
	private static final int COINS_250_SPRITE_ID = -1338;
	private static final int COINS_1000_SPRITE_ID = -1339;
	private static final int COINS_10000_SPRITE_ID = -1340;

	// Skill ordinal to send in the fake xp drop script.
	// doesn't matter which skill expect it's better not be attack/defense/magic to avoid collision with
	// XpDropPlugin which looks for those and might change text color
	private static final int XPDROP_SKILL = Skill.FISHING.ordinal();

	/*
	Singletons which will be provided at creation by the plugin
	 */
	private final ItemManager itemManager;
	private final Client client;

	/* var currentGoldDropValue will have
	the gold value of the current ongoing gold drop. 2 purposes:
	  1. to know the value later when we actually use it,
	  2. to know to catch the next fake xpdrop in onScriptPreFired
	*/
	private long currentGoldDropValue;

	InventoryTotalGoldDrops(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;

		prepareCoinSprite(10000, COINS_10000_SPRITE_ID);
		prepareCoinSprite(1000, COINS_1000_SPRITE_ID);
		prepareCoinSprite(250, COINS_250_SPRITE_ID);
		prepareCoinSprite(100, COINS_100_SPRITE_ID);

		currentGoldDropValue = 0L;

	}

	public void onScriptPreFired(ScriptPreFired scriptPreFired)
	{
        /*
        We check for scripts of type XPDROPS_SETDROPSIZE to interfere with the XPdrop
        and write our own values
         */

		// is this current script type?
		if (scriptPreFired.getScriptId() != XPDROPS_SETDROPSIZE)
		{
			return;
		}

		// Get xpdrop widget id using the stack
		// taken from XpDropPlugin!

		// This runs prior to the proc being invoked, so the arguments are still on the stack.
		// Grab the first argument to the script.
		final int[] intStack = client.getIntStack();
		final int intStackSize = client.getIntStackSize();

		final int widgetId = intStack[intStackSize - 4];

		// extract information from currentGoldDropValue
		boolean isThisGoldDrop =   (currentGoldDropValue != 0);
		long     goldDropValue =     currentGoldDropValue;

		// done with this gold drop anyway
		currentGoldDropValue = 0;

		handleXpDrop(widgetId, isThisGoldDrop, goldDropValue);

	}

	private void handleXpDrop(int xpDropWidgetId, boolean isThisGoldDrop, long goldDropValue)
	{
		final Widget xpDropWidget;
		final Widget dropTextWidget;

		Widget[] xpDropWidgetChildren;

		// get widget from ID
		xpDropWidget = client.getWidget(TO_GROUP(xpDropWidgetId), TO_CHILD(xpDropWidgetId));

		if (xpDropWidget == null)
		{
			log.error("xpDropWidget was null");
			return;
		}

		xpDropWidgetChildren = xpDropWidget.getChildren();

		if (xpDropWidgetChildren.length < 1)
		{
			log.error(String.format("Unexpected xpDropWidgets length! %d", xpDropWidgetChildren.length));
			return;
		}

		dropTextWidget = xpDropWidgetChildren[0];

		if (isThisGoldDrop)
		{
			final Widget dropSpriteWidget;

			if (xpDropWidgetChildren.length < 2)
			{
				log.error(String.format(
					"Unexpected xpDropWidgetChildren length for a gold drop! length! %d",
					xpDropWidgetChildren.length));
				return;
			}

			dropSpriteWidget = xpDropWidgetChildren[1];


			xpDropToGoldDrop(dropTextWidget, dropSpriteWidget, goldDropValue);
		}
		else
		{
			// reset text color for all regular xpdrops
			resetXpDropTextColor(dropTextWidget);
		}


	}
	private void xpDropToGoldDrop(Widget dropTextWidget, Widget dropSpriteWidget, long goldDropValue)
	{
        /*
        Change xpdrop icon and text, to make a gold drop
         */

		dropTextWidget.setText(formatGoldDropText(goldDropValue));

		if (goldDropValue > 0)
		{
			// green text for profit
			dropTextWidget.setTextColor(Color.GREEN.getRGB());
		}
		else
		{
			// red for loss
			dropTextWidget.setTextColor(Color.RED.getRGB());
		}

		int spriteId = 0;
		long absValue = Math.abs(goldDropValue);
		if (absValue >= 10000)
		{
			spriteId = COINS_10000_SPRITE_ID;
		}
		else if (absValue >= 1000)
		{
			spriteId = COINS_1000_SPRITE_ID;
		}
		else if (absValue >= 250)
		{
			spriteId = COINS_250_SPRITE_ID;
		}
		else
		{
			spriteId = COINS_100_SPRITE_ID;
		}

		// change skill sprite to coin sprite
		dropSpriteWidget.setSpriteId(spriteId);
	}

	private void prepareCoinSprite(int quantity, int spriteId)
	{
        /*
        Prepare coin sprites for use in the gold drops.
        It seems item icons are not available as sprites with id,
        so we convert in this function.
        */

		// get image object by coin item id
		AsyncBufferedImage coin_image_raw = itemManager.getImage(ItemID.COINS_995, quantity, false);

		Runnable r = () -> {
			final SpritePixels coin_sprite = ImageUtil.getImageSpritePixels(coin_image_raw, client);
			// register new coin sprite by overriding a free sprite id
			client.getSpriteOverrides().put(spriteId, coin_sprite);
		};

		coin_image_raw.onLoaded(r);
		//run now in case it loaded instantly.
		//TODO: remove this if/when my PR ever merges
		r.run();
	}

	public void requestGoldDrop(long amount)
	{
		// save the value and mark an ongoing gold drop
		currentGoldDropValue = amount;

		///// Create a fake xp drop.
		// skill ordinal - we will replace the icon anyway

		// value - since we want to be able to pass negative numbers, we pass the value using
		// currentGoldDropValue instead of this argument, but still need to make sure the digits match for formatting

		if (amount < 0)
		{
			//force positive number, add extra digit to give room for minus sign on the left
			amount *= -10;
		}

		//Need to offset to the left by two digits to account for the fake xp drop red icon that we're getting rid of
		amount /= 100;
		//don't ever want a 0 drop (anything <100 will have too much space sadly, can potentially fix this by messing with the widget)
		if (amount == 0)
			amount = 1;

		client.runScript(XPDROP_DISABLED, XPDROP_SKILL, (int) amount);
	}

	private void resetXpDropTextColor(Widget xpDropTextWidget)
	{
		// taken from XpDropPlugin
		EnumComposition colorEnum = client.getEnum(EnumID.XPDROP_COLORS);
		int defaultColorId = client.getVarbitValue(Varbits.EXPERIENCE_DROP_COLOR);
		int color = colorEnum.getIntValue(defaultColorId);
		xpDropTextWidget.setTextColor(color);
	}

	private String formatGoldDropText(long goldDropValue)
	{
		return NumberFormat.getInstance().format(goldDropValue);
	}
}
