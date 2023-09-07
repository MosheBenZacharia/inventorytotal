package com.ericversteeg;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup(InventoryTotalConfig.GROUP)
public interface InventoryTotalConfig extends Config
{
	String GROUP = "inventorytotal";
	String logOutTimeKey = "log_out_time";
	String fish_barrel = "fish_barrel";
	String kharedsts_memoirs = "kharedsts_memoirs";
	String ash_sanctifier = "ash_sanctifier";
	String bottomless_compost_bucket = "bottomless_compost_bucket";
	String bottomless_compost_bucket_type = "bottomless_compost_bucket_type";
	String log_basket = "log_basket";
	String looting_bag = "looting_bag";
	String blood_essence = "blood_essence";
	String gem_bag = "gem_bag";
	String herb_sack = "herb_sack";
	String seed_box = "seed_box";
	String sessionKey = "session_stats_";
	String sessionIdentifiersKey = "session_ids";

	static String getSessionKey(String identifier)
	{
		return sessionKey + identifier;
	}






	@ConfigSection(
		name = "Shared Settings",
		description = "The options that apply to both the trip overlay and session panel.",
		position = 50,
		closedByDefault = false
	)
	String sharedSettingSection = "sharedSettingSection";

	@ConfigItem(
			position = 22,
			keyName = "showExactGp",
			name = "Show Exact Gp",
			description = "Configures whether or not the exact gp value is visible.",
			section =  sharedSettingSection
	)
	default boolean showExactGp()
	{
		return false;
	}

    @ConfigItem(
			position = 32,
            keyName = "goldDrops",
            name = "Show Gold Drops",
            description = "Show each profit increase or decrease as an XP drop (only works in profit/loss mode).",
			section =  sharedSettingSection
    )
    default boolean goldDrops()
    {
        return false;
    }

	@ConfigItem(
			position = 34,
			keyName = "ignoredItems",
			name = "Ignored Items",
			description = "Ignore these items in your inventory (applies after banking).",
			section =  sharedSettingSection
	)
	default String ignoredItems() {
		return "Cannon barrels, Cannon base, Cannon furnace, Cannon stand";
	}










	@ConfigSection(
		name = "Trip Overlay Settings",
		description = "The options that control the inventory overlay, used to display stats on your active trip.",
		position = 100,
		closedByDefault = true
	)
	String tripOverlaySection = "tripOverlaySection";

	public static final String showTripOverlayKeyName = "showTripOverlay";
	@ConfigItem(
			position = 0,
			keyName = showTripOverlayKeyName,
			name = "Show Trip Overlay",
			description = "Enables/disables the active trip overlay.",
			section =  tripOverlaySection
	)
	default boolean showTripOverlay()
	{
		return true;
	}

	@ConfigItem(
			position = 5,
			keyName = "enableProfitLoss",
			name = "Profit / Loss",
			description = "Configures whether or not current total is relative to start amount.",
			section =  tripOverlaySection
	)
	default boolean enableProfitLoss()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
			position = 50,
			keyName = "totalBackgroundColor",
			name = "Background Color",
			description = "Configures the background color.",
			section =  tripOverlaySection
	)
	default Color totalColor()
	{
		return Color.decode("#99903D");
	}

	@ConfigItem(
			position = 55,
			keyName = "totalTextColor",
			name = "Text Color",
			description = "Configures the text color.",
			section =  tripOverlaySection
	)
	default Color textColor()
	{
		return Color.decode("#FFF7E3");
	}

	@Alpha
	@ConfigItem(
			position = 60,
			keyName = "totalBorderColor",
			name = "Border Color",
			description = "Configures the border color.",
			section =  tripOverlaySection
	)
	default Color borderColor()
	{
		return Color.decode("#0E0E0E");
	}

	@Alpha
	@ConfigItem(
			position = 65,
			keyName = "profitBackgroundColor",
			name = "Profit Color",
			description = "Configures profit background color.",
			section =  tripOverlaySection
	)
	default Color profitColor()
	{
		return Color.decode("#245C2D");
	}

	@ConfigItem(
			position = 70,
			keyName = "profitTextColor",
			name = "Profit Text Color",
			description = "Configures profit text color.",
			section =  tripOverlaySection
	)
	default Color profitTextColor()
	{
		return Color.decode("#FFF7E3");
	}

	@Alpha
	@ConfigItem(
			position = 75,
			keyName = "profitBorderColor",
			name = "Profit Border Color",
			description = "Configures profit border color.",
			section =  tripOverlaySection
	)
	default Color profitBorderColor()
	{
		return Color.decode("#0E0E0E");
	}

	@Alpha
	@ConfigItem(
			position = 80,
			keyName = "lossBackgroundColor",
			name = "Loss Color",
			description = "Configures loss background color.",
			section =  tripOverlaySection
	)
	default Color lossColor()
	{
		return Color.decode("#5F1515");
	}

	@ConfigItem(
			position = 85,
			keyName = "lossTextColor",
			name = "Loss Text Color",
			description = "Configures loss text color.",
			section =  tripOverlaySection
	)
	default Color lossTextColor()
	{
		return Color.decode("#FFF7E3");
	}

	@Alpha
	@ConfigItem(
			position = 90,
			keyName = "lossBorderColor",
			name = "Loss Border Color",
			description = "Configures loss border color.",
			section =  tripOverlaySection
	)
	default Color lossBorderColor()
	{
		return Color.decode("#0E0E0E");
	}

	@ConfigItem(
			position = 11,
			keyName = "roundedCorners",
			name = "Rounded Corners",
			description = "Toggle rounded corners.",
			section =  tripOverlaySection
	)
	default boolean roundCorners()
	{
		return true;
	}

	@ConfigItem(
			position = 12,
			keyName = "cornerRadius",
			name = "Corner Radius",
			description = "Configures the corner radius.",
			section =  tripOverlaySection
	)
	default int cornerRadius()
	{
		return 10;
	}

	@ConfigItem(
			position = 13,
			keyName = "alignment",
			name = "Alignment",
			description = "Configures alignment.",
			section =  tripOverlaySection
	)
	default InventoryTotalAlignment horizontalAlignment()
	{
		return InventoryTotalAlignment.CENTER;
	}

	@ConfigItem(
			position = 14,
			keyName = "inventoryOffsetX",
			name = "Inventory Offset X",
			description = "Configures x-axis offset.",
			section =  tripOverlaySection
	)
	default int inventoryXOffset()
	{
		return 0;
	}

	@ConfigItem(
			position = 15,
			keyName = "inventoryOffsetXNegative",
			name = "Inventory Offset X Negative",
			description = "Configures whether or not the y-axis offset is a negative number.",
			section =  tripOverlaySection
	)
	default boolean isInventoryXOffsetNegative()
	{
		return false;
	}

	@ConfigItem(
			position = 16,
			keyName = "inventoryOffsetY",
			name = "Inventory Offset Y",
			description = "Configures y-axis offset.",
			section =  tripOverlaySection
	)
	default int inventoryYOffset()
	{
		return 42;
	}

	@ConfigItem(
			position = 17,
			keyName = "inventoryOffsetYNegative",
			name = "Inventory Offset Y Negative",
			description = "Configures whether or not the y-axis offset is a negative number.",
			section =  tripOverlaySection
	)
	default boolean isInventoryYOffsetNegative()
	{
		return false;
	}

	@ConfigItem(
			position = 18,
			keyName = "showLapTime",
			name = "Show Run Time",
			description = "Configures whether or not the run time is visible.",
			section =  tripOverlaySection
	)
	default boolean showRunTime()
	{
		return false;
	}

	@ConfigItem(
			position = 20,
			keyName = "showGpPerHourOnOverlay",
			name = "Show GP/hr On Overlay",
			description = "Configures whether or not gp/hr is shown instead of net total when in profit / loss mode.",
			section =  tripOverlaySection
	)
	default boolean showGpPerHourOnOverlay()
	{
		return true;
	}

	@ConfigItem(
			position = 24,
			keyName = "showCoinStack",
			name = "Show Coin Stack",
			description = "Configures whether or not the coin stack image is visible.",
			section =  tripOverlaySection
	)
	default boolean showCoinStack()
	{
		return true;
	}

	@ConfigItem(
			position = 26,
			keyName = "showWhileBanking",
			name = "Show While Banking",
			description = "Configures whether or not the total is visible while banking.",
			section =  tripOverlaySection
	)
	default boolean showWhileBanking()
	{
		return true;
	}

	@ConfigItem(
			position = 28,
			keyName = "showOnEmpty",
			name = "Show On Empty",
			description = "Configures whether or not to show the total when inventory is empty.",
			section =  tripOverlaySection
	)
	default boolean showOnEmpty()
	{
		return true;
	}

	@ConfigItem(
			position = 30,
			keyName = "showTooltip",
			name = "Show Ledger on Hover",
			description = "Show the ledger when hovering over the trip overlay.",
			section =  tripOverlaySection
	)
	default boolean showLedgerOnHover()
	{
		return true;
	}







	@ConfigSection(
		name = "Session Panel Settings",
		description = "The options that control the side panel, showing active session stats and session history.",
		position = 150,
		closedByDefault = true
	)
	String sessionPanelSection = "sessionPanelSection";

	public static final String enableSessionPanelKeyName = "enableSessionPanel";
	@ConfigItem(
			position = 0,
			keyName = enableSessionPanelKeyName,
			name = "Enable Session Panel",
			description = "Enables/disables the session side panel.",
			section =  sessionPanelSection
	)
	default boolean enableSessionPanel()
	{
		return true;
	}

	@ConfigItem(
			position = 5,
			keyName = "autoResumeTrip",
			name = "Auto Resume Trip",
			description = "Automatically resume a paused trip when a profit change is detected.",
			section =  sessionPanelSection
	)
	default boolean autoResumeTrip() {
		return true;
	}

	@ConfigItem(
			position = 10,
			keyName = "ignoreBankTime",
			name = "Ignore Bank time",
			description = "Don't count time with a bank UI open towards session time.",
			section =  sessionPanelSection
	)
	default boolean ignoreBankTime() {
		return false;
	}

	public static final String sidePanelPositionKeyName = "sidePanelPosition";
    @ConfigItem(
            position = 20,
            keyName = sidePanelPositionKeyName,
            name = "Side Panel Position",
            description = "Panel icon position, Lower # = higher pos, Higher # = lower pos ",
			section =  sessionPanelSection
    )
    default int sidePanelPosition() { return 6; }








	@ConfigSection(
		name = "Untradeable Values",
		description = "Customize value for items that are not tradeable.",
		position = 200,
		closedByDefault = true
	)
	String untradeableValuesSection = "untradeableValuesSection";

	@ConfigItem(
			position = 5,
			keyName = "tokkulKaramjaGloves",
			name = "Tokkul: Include Karamja Gloves",
			description = "Include karamja glove discount for tokkul price.",
			section =  untradeableValuesSection
	)
	default boolean tokkulKaramjaGloves()
	{
		return false;
	}

	@ConfigItem(
			position = 6,
			keyName = "tokkulValue",
			name = "Tokkul:",
			description = "Uses overstock price for buy value, normal stock for sell value.",
			section =  untradeableValuesSection
	)
	default ValueRemapper.TokkulOverride tokkulValue()
	{
		return ValueRemapper.TokkulOverride.NO_VALUE;
	}
}
