package com.ericversteeg;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
class InventoryTotalOverlay extends Overlay
{
	private static final int TEXT_Y_OFFSET = 17;
	private static final String PROFIT_LOSS_TIME_FORMAT = "%02d:%02d:%02d";
	private static final String PROFIT_LOSS_TIME_NO_HOURS_FORMAT = "%02d:%02d";
	private static final String CHECK_LOOTING_BAG_TEXT = "Check Looting Bag";
	private static final int HORIZONTAL_PADDING = 10;
	private static final int BANK_CLOSE_DELAY = 1200;
	private static final int imageSize = 15;
	static final int COINS = ItemID.COINS_995;

	private final Client client;
	private final InventoryTotalPlugin plugin;
	private final InventoryTotalConfig config;

	private final ItemManager itemManager;
	private final SpriteManager spriteManager;

	private Widget inventoryWidget;
	private ItemContainer inventoryItemContainer;
	private ItemContainer equipmentItemContainer;

	private boolean onceBank = false;

	private boolean showInterstitial = false;

	private boolean postNewRun = false;
	private long newRunTime = 0;
	
	private long lastGpPerHour;
	private long lastGpPerHourUpdateTime;

	private BufferedImage coinsImage;
	private int lastCoinValue;
	private BufferedImage redXImage;

	@RequiredArgsConstructor
	class LedgerEntry
	{
		final String leftText;
		final Color leftColor;
		final String rightText;
		final Color rightColor;
		final boolean addGap;
	}

	@Inject
	private InventoryTotalOverlay(Client client, InventoryTotalPlugin plugin, InventoryTotalConfig config, ItemManager itemManager, SpriteManager spriteManager)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);

		this.client = client;
		this.plugin = plugin;
		this.config = config;

		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
	}

	void updatePluginState()
	{
		inventoryWidget = client.getWidget(WidgetInfo.INVENTORY);

		inventoryItemContainer = client.getItemContainer(InventoryID.INVENTORY);
		equipmentItemContainer = client.getItemContainer(InventoryID.EQUIPMENT);

		if (config.enableProfitLoss())
		{
			plugin.setMode(InventoryTotalMode.PROFIT_LOSS);
		}
		else
		{
			plugin.setMode(InventoryTotalMode.TOTAL);
		}

		boolean isBank = false;

		//Collect on bank
		//Don't want it to appear there but have it count as bank still
		Widget collectOnBank = client.getWidget(402, 2);
		if (collectOnBank != null && !collectOnBank.isHidden())
		{
			isBank = true;
		}

		if (inventoryWidget == null || inventoryWidget.getCanvasLocation().getX() < 0 || inventoryWidget.isHidden())
		{
			Widget [] altInventoryWidgets = new Widget[]
			{
				client.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER),
				//GE shouldn't count either
				client.getWidget(WidgetInfo.GRAND_EXCHANGE_INVENTORY_ITEMS_CONTAINER),
				//With equipment open
				client.getWidget(WidgetID.BANK_INVENTORY_GROUP_ID, 4),
				//With looting bag open
				client.getWidget(WidgetID.BANK_INVENTORY_GROUP_ID, 5),
				client.getWidget(WidgetInfo.DEPOSIT_BOX_INVENTORY_ITEMS_CONTAINER)
			};

			for (Widget altInventoryWidget: altInventoryWidgets)
			{
				inventoryWidget = altInventoryWidget;
				if (inventoryWidget != null && !inventoryWidget.isHidden())
				{
					isBank = true;
					if (!onceBank)
					{
						onceBank = true;
					}

					break;
				}
			}
		}

		if (isBank)
		{
			plugin.setState(InventoryTotalState.BANK);
		}
		else
		{
			plugin.setState(InventoryTotalState.RUN);
		}

		// before totals
		boolean newRun = plugin.getPreviousState() == InventoryTotalState.BANK && plugin.getState() == InventoryTotalState.RUN;
		plugin.getRunData().itemQtys.clear();

		// totals
		long inventoryTotal = plugin.getInventoryTotal(false);
		long equipmentTotal = plugin.getEquipmentTotal(false);


		long totalGp = inventoryTotal;
		if (plugin.getState() == InventoryTotalState.RUN && plugin.getMode() == InventoryTotalMode.PROFIT_LOSS)
		{
			totalGp += equipmentTotal;
		}

		plugin.setTotalGp(totalGp);

		// after totals
		if (newRun)
		{
			plugin.onNewRun();

			postNewRun = true;
			newRunTime = Instant.now().toEpochMilli();
		}
		else if (plugin.getPreviousState() == InventoryTotalState.RUN && plugin.getState() == InventoryTotalState.BANK)
		{
			plugin.onBank();
		}

		// check post new run
		if (postNewRun && (Instant.now().toEpochMilli() - newRunTime) > BANK_CLOSE_DELAY)
		{
			//make sure user didn't open the bank back up in those two ticks
			if (plugin.getState() == InventoryTotalState.RUN)
			{
				plugin.postNewRun();
			}
			else
			{
				hideInterstitial();
			}
			postNewRun = false;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		//TODO: do i need to even run this every frame? can i just run this once per tick?
		updatePluginState();

		boolean isInvHidden = inventoryWidget == null || inventoryWidget.isHidden();
		if (isInvHidden)
		{
			return null;
		}

		int height = 20;

		long total = plugin.getProfitGp();
		String totalText = formatGp(total);

		if (config.showGpPerHourOnOverlay() 
			&& plugin.getMode() == InventoryTotalMode.PROFIT_LOSS
			 && plugin.getState() == InventoryTotalState.RUN
			 && !showInterstitial)
		{
			total = getGpPerHour(plugin.elapsedRunTime(), (int) total);
			totalText = formatGp(total) + "/hr";
		}

		String formattedRunTime = getFormattedRunTime();
		String runTimeText = null;

		if (formattedRunTime != null)
		{
			runTimeText = " (" + formattedRunTime + ")";
		}

		if (showInterstitial)
		{
			total = 0;

			if (plugin.getMode() == InventoryTotalMode.PROFIT_LOSS)
			{
				totalText = "0";
				if (config.showGpPerHourOnOverlay())
					totalText += "/hr";
			}
			else
			{
				totalText = formatGp(plugin.getTotalGp());
			}
		}

		renderTotal(config, graphics, plugin, inventoryWidget,
				total, totalText, runTimeText, height);

		return null;
	}

	private boolean needsCheck()
	{
		return plugin.needsLootingBagCheck() || (plugin.getChargeableItemsNeedingCheck().size() != 0);
	}

	private BufferedImage getCoinsImage(int quantity)
	{
		if (coinsImage == null || quantity != lastCoinValue)
		{
			coinsImage = itemManager.getImage(ItemID.COINS_995, quantity, false);
			coinsImage = ImageUtil.resizeImage(coinsImage, imageSize, imageSize);
		}
		lastCoinValue = quantity;
		return coinsImage;
	}

	private BufferedImage getRedXImage()
	{
		if (redXImage == null)
		{
			redXImage = spriteManager.getSprite(SpriteID.UNKNOWN_DISABLED_ICON, 0);
			redXImage = ImageUtil.resizeImage(redXImage, imageSize, imageSize);
		}
		return redXImage;
	}

	private void renderTotal(InventoryTotalConfig config, Graphics2D graphics, InventoryTotalPlugin plugin,
							 Widget inventoryWidget, long total, String totalText,
							 String runTimeText, int height) {
		
		boolean showCoinStack = config.showCoinStack();
		boolean showCheckIcon = needsCheck();
		int numCoins;
		if (total > Integer.MAX_VALUE)
		{
			numCoins = Integer.MAX_VALUE;
		}
		else if (total < Integer.MIN_VALUE)
		{
			numCoins = Integer.MIN_VALUE;
		}
		else
		{
			numCoins = (int) total;
			if (numCoins == 0)
			{
				numCoins = 1000000;
			}
		}
		numCoins = Math.abs(numCoins);

		if ((total == 0 && !config.showOnEmpty()) || (plugin.getState() == InventoryTotalState.BANK && !config.showWhileBanking())) {
			return;
		}

		graphics.setFont(FontManager.getRunescapeSmallFont());
		final int totalWidth = graphics.getFontMetrics().stringWidth(totalText);

		int fixedRunTimeWidth = 0;
		int actualRunTimeWidth = 0;
		int imageWidthWithPadding = 0;

		if (runTimeText != null && runTimeText.length() >= 2) {
			fixedRunTimeWidth = 5 * (runTimeText.length() - 2) + (3 * 2) + 5;
			actualRunTimeWidth = graphics.getFontMetrics().stringWidth(runTimeText);
		}

		if (showCoinStack)
		{
			imageWidthWithPadding = imageSize + 3;
		}
		if (showCheckIcon)
		{
			imageWidthWithPadding += imageSize + 3;
		}

		int width = totalWidth + fixedRunTimeWidth + imageWidthWithPadding + HORIZONTAL_PADDING * 2;

		int x = (inventoryWidget.getCanvasLocation().getX() + inventoryWidget.getWidth() / 2) - (width / 2);
		switch (config.horizontalAlignment())
		{
			case CENTER:
				break;

			case LEFT:
				x = inventoryWidget.getCanvasLocation().getX();
				break;

			case RIGHT:
				x = inventoryWidget.getCanvasLocation().getX() + inventoryWidget.getWidth() - width;
				break;
		}

		int xOffset = config.inventoryXOffset();
		if (config.isInventoryXOffsetNegative())
		{
			xOffset *= -1;
		}
		x += xOffset;

		int yOffset = config.inventoryYOffset();
		if (config.isInventoryYOffsetNegative())
		{
			yOffset *= -1;
		}
		int y = inventoryWidget.getCanvasLocation().getY() - height - yOffset;

		Color backgroundColor;
		Color borderColor;
		Color textColor;

		if (plugin.getState() == InventoryTotalState.BANK || plugin.getMode() == InventoryTotalMode.TOTAL) {
			backgroundColor = config.totalColor();
			borderColor = config.borderColor();
			textColor = config.textColor();
		}
		else if (total >= 0) {
			backgroundColor = config.profitColor();
			borderColor = config.profitBorderColor();
			textColor = config.profitTextColor();
		}
		else {
			backgroundColor = config.lossColor();
			borderColor = config.lossBorderColor();
			textColor = config.lossTextColor();
		}

		int cornerRadius = config.cornerRadius();
		if (!config.roundCorners())
		{
			cornerRadius = 0;
		}

		int containerAlpha = backgroundColor.getAlpha();

		if (containerAlpha > 0) {
			graphics.setColor(borderColor);
			graphics.drawRoundRect(x, y, width + 1, height + 1, cornerRadius, cornerRadius);
		}

		graphics.setColor(backgroundColor);

		graphics.fillRoundRect(x + 1, y + 1, width, height, cornerRadius, cornerRadius);

		TextComponent textComponent = new TextComponent();

		textComponent.setColor(textColor);
		textComponent.setText(totalText);
		textComponent.setPosition(new Point(x + HORIZONTAL_PADDING, y + TEXT_Y_OFFSET));
		textComponent.render(graphics);

		if (runTimeText != null)
		{
			textComponent = new TextComponent();

			textComponent.setColor(textColor);
			textComponent.setText(runTimeText);
			textComponent.setPosition(new Point((x + width) - HORIZONTAL_PADDING - actualRunTimeWidth - imageWidthWithPadding, y + TEXT_Y_OFFSET));
			textComponent.render(graphics);
		}

		if (showCoinStack)
		{
			int imageOffset = 4;
			if (showCheckIcon)
				imageOffset -= imageWidthWithPadding / 2;

			BufferedImage image = getCoinsImage(numCoins);
			graphics.drawImage(image, (x + width) - HORIZONTAL_PADDING - imageSize + imageOffset, y + 3, null);
		}

		if (showCheckIcon)
		{
			int imageOffset = 4;

			BufferedImage redXImage = getRedXImage();
			graphics.drawImage(redXImage, (x + width) - HORIZONTAL_PADDING - imageSize + imageOffset, y + 3, null);
		}

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		int mouseX = mouse.getX();
		int mouseY = mouse.getY();

		RoundRectangle2D roundRectangle2D = new RoundRectangle2D.Double(x, y, width + 1, height + 1, cornerRadius, cornerRadius);
		if (roundRectangle2D.contains(mouseX, mouseY) && plugin.getState() != InventoryTotalState.BANK
				&& (Instant.now().toEpochMilli() - newRunTime) > (BANK_CLOSE_DELAY + 500) && config.showTooltip())
		{
			if (plugin.getMode() == InventoryTotalMode.PROFIT_LOSS)
			{
				renderProfitLossLedger(graphics);
			}
			else
			{
				renderLedger(graphics);
			}
		}
	}

	private static final float roundAmount = 0.1f;
	private static final float roundMultiplier = 1f/roundAmount;
	
	private String formatQuantity(float quantity)
	{
		quantity = Math.abs(quantity);
		quantity = Math.round(quantity * roundMultiplier) / roundMultiplier;
		String text = NumberFormat.getInstance(Locale.ENGLISH).format(quantity);
		return text;
	}

	private void renderLedger(Graphics2D graphics)
	{
		FontMetrics fontMetrics = graphics.getFontMetrics();

		java.util.List<InventoryTotalLedgerItem> ledger = plugin.getInventoryLedger().stream()
				.filter(item -> item.getQty() != 0).collect(Collectors.toList());

		if (ledger.isEmpty() && !needsCheck())
		{
			return;
		}

		ledger = ledger.stream().sorted(Comparator.comparingLong(o ->
				-((long) o.getQty() * o.getPrice()))
		).collect(Collectors.toList());

		String [] descriptions = ledger.stream().map(item -> {
			String desc = item.getDescription();
			if (item.getQty() != 0 && Math.abs(item.getQty()) != 1 && !item.getDescription().contains("Coins"))
			{
				desc = formatQuantity(item.getQty()) + " " + desc;
			}
			return desc;
		}).toArray(String[]::new);
		Long [] prices = ledger.stream().map(item -> (long) item.getQty() * item.getPrice()).toArray(Long[]::new);

		LinkedList<LedgerEntry> ledgerEntries = new LinkedList<>();
		if (descriptions.length == prices.length)
		{
			for (int i = 0; i < descriptions.length; i++)
			{
				String desc = descriptions[i];
				long price = prices[i];
				String rightText = formatNumber(price);
				Color leftColor = Color.decode("#FFF7E3");
				Color rightColor = price > 0 ? Color.GREEN : Color.WHITE;
				ledgerEntries.add(new LedgerEntry(desc, leftColor, rightText, rightColor, false));
			}
		}
		long total = ledger.stream().mapToLong(item -> (long) item.getQty() * item.getPrice()).sum();
		ledgerEntries.add(new LedgerEntry("Total", Color.ORANGE, formatNumber(total), priceToColor(total), true));
		if (plugin.needsLootingBagCheck())
		{
			ledgerEntries.add(new LedgerEntry("Check Looting Bag to Calibrate", Color.RED, "", Color.WHITE, false));
		}
		for (String itemName : plugin.getChargeableItemsNeedingCheck())
		{
			ledgerEntries.add(new LedgerEntry("Check " + itemName + " to Calibrate Charges", Color.RED, "", Color.WHITE, false));
		}


		Integer [] rowWidths = IntStream.range(0, ledgerEntries.size()).mapToObj(
				i -> fontMetrics.stringWidth(ledgerEntries.get(i).leftText)
						+ fontMetrics.stringWidth(ledgerEntries.get(i).rightText)
		).toArray(Integer[]::new);

		Arrays.sort(rowWidths);

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		int mouseX = mouse.getX();
		int mouseY = mouse.getY();

		int sectionPadding = 5;

		int rowW = rowWidths[rowWidths.length - 1] + 20 + HORIZONTAL_PADDING * 2;
		int rowH = fontMetrics.getHeight();

		int h = ledgerEntries.size() * rowH + TEXT_Y_OFFSET / 2 + sectionPadding + 2;

		int x = mouseX - rowW - 10;
		int y = mouseY - h / 2;

		int cornerRadius = 0;

		graphics.setColor(Color.decode("#1b1b1b"));
		graphics.fillRoundRect(x, y, rowW, h, cornerRadius, cornerRadius);

		int borderWidth = 1;

		graphics.setColor(Color.decode("#0b0b0b"));
		graphics.setStroke(new BasicStroke(borderWidth));
		graphics.drawRoundRect(x - borderWidth / 2, y - borderWidth / 2,
				rowW + borderWidth / 2, h + borderWidth / 2, cornerRadius, cornerRadius);

		renderLedgerEntries(ledgerEntries, x, y, rowW, rowH, sectionPadding, graphics);
	}

	private void renderProfitLossLedger(Graphics2D graphics)
	{
		FontMetrics fontMetrics = graphics.getFontMetrics();

		java.util.List<InventoryTotalLedgerItem> ledger = plugin.getProfitLossLedger().stream()
				.filter(item -> item.getQty() != 0).collect(Collectors.toList());

		java.util.List<InventoryTotalLedgerItem> gain = ledger.stream().filter(item -> item.getQty() > 0)
				.collect(Collectors.toList());

		java.util.List<InventoryTotalLedgerItem> loss = ledger.stream().filter(item -> item.getQty() < 0)
				.collect(Collectors.toList());

		gain = gain.stream().sorted(Comparator.comparingLong(o -> -((long) o.getQty() * o.getPrice()))).collect(Collectors.toList());
		loss = loss.stream().sorted(Comparator.comparingLong(o -> ((long) o.getQty() * o.getPrice()))).collect(Collectors.toList());

		ledger = new LinkedList<>();
		ledger.addAll(gain);
		ledger.addAll(loss);

		if (ledger.isEmpty() && !needsCheck())
		{
			return;
		}

		String [] descriptions = ledger.stream().map(item -> {
			String desc = item.getDescription();
			if (item.getQty() != 0 && Math.abs(item.getQty()) != 1 && !item.getDescription().contains("Coins"))
			{
				desc = formatQuantity(item.getQty()) + " " + desc;
			}
			return desc;
		}).toArray(String[]::new);
		Long [] prices = ledger.stream().map(item -> (long) item.getQty() * item.getPrice()).toArray(Long[]::new);

		LinkedList<LedgerEntry> ledgerEntries = new LinkedList<>();
		if (descriptions.length == prices.length)
		{
			String prevDesc = "";
			for (int i = 0; i < descriptions.length; i++)
			{
				Color leftColor = Color.decode("#FFF7E3");
				Color rightColor = Color.WHITE;
				boolean addGap = false;
				String desc = descriptions[i];

				if (i > 0 && prices[i - 1] >= 0 && prices[i] < 0 && !prevDesc.contains("Total"))
				{
					addGap = true;
				}

				prevDesc = desc;

				long price = prices[i];
				String formattedPrice = formatNumber(price);
				rightColor = priceToColor(price);

				ledgerEntries.add(new LedgerEntry(desc, leftColor, formattedPrice, rightColor, addGap));
			}
		}

		long totalGain = gain.stream().mapToLong(item -> (long) item.getQty() * item.getPrice()).sum();
		long totalLoss = loss.stream().mapToLong(item -> (long) item.getQty() * item.getPrice()).sum();
		long total = ledger.stream().mapToLong(item -> (long) item.getQty() * item.getPrice()).sum();
		ledgerEntries.add(new LedgerEntry("Total Gain", Color.YELLOW, formatNumber(totalGain), priceToColor(totalGain), true));
		ledgerEntries.add(new LedgerEntry("Total Loss", Color.YELLOW, formatNumber(totalLoss), priceToColor(totalLoss), false));
		ledgerEntries.add(new LedgerEntry("Net Total", Color.ORANGE, formatNumber(total), priceToColor(total), false));

		long runTime = plugin.elapsedRunTime();
		if (runTime != InventoryTotalPlugin.NO_PROFIT_LOSS_TIME)
		{
			long gpPerHour = getGpPerHour(runTime, total);
			String gpPerHourString = formatGp(gpPerHour);
			ledgerEntries.add(new LedgerEntry("GP/hr", Color.ORANGE, gpPerHourString, priceToColor(gpPerHour), false));
		}
		if (plugin.needsLootingBagCheck())
		{
			ledgerEntries.add(new LedgerEntry("Check Looting Bag to Calibrate", Color.RED, "", Color.WHITE, false));
		}
		for (String itemName : plugin.getChargeableItemsNeedingCheck())
		{
			ledgerEntries.add(new LedgerEntry("Check " + itemName + " to Calibrate Charges", Color.RED, "", Color.WHITE, false));
		}

		Integer [] rowWidths = IntStream.range(0, ledgerEntries.size()).mapToObj(
				i -> fontMetrics.stringWidth(ledgerEntries.get(i).leftText)
						+ fontMetrics.stringWidth(ledgerEntries.get(i).rightText)
		).toArray(Integer[]::new);

		Arrays.sort(rowWidths);

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		int mouseX = mouse.getX();
		int mouseY = mouse.getY();

		int sectionPadding = 5;

		int rowW = rowWidths[rowWidths.length - 1] + 20 + HORIZONTAL_PADDING * 2;
		int rowH = fontMetrics.getHeight();

		int sectionPaddingTotal = sectionPadding;
		if (!gain.isEmpty() && !loss.isEmpty())
		{
			sectionPaddingTotal += sectionPadding;
		}

		int h = ledgerEntries.size() * rowH + TEXT_Y_OFFSET / 2 + sectionPaddingTotal + 2;

		int x = mouseX - rowW - 10;
		int y = mouseY - h / 2;

		int cornerRadius = 0;

		graphics.setColor(Color.decode("#1b1b1b"));
		graphics.fillRoundRect(x, y, rowW, h, cornerRadius, cornerRadius);

		int borderWidth = 1;

		graphics.setColor(Color.decode("#0b0b0b"));
		graphics.setStroke(new BasicStroke(borderWidth));
		graphics.drawRoundRect(x - borderWidth / 2, y - borderWidth / 2,
				rowW + borderWidth / 2, h + borderWidth / 2, cornerRadius, cornerRadius);

		renderLedgerEntries(ledgerEntries, x, y, rowW, rowH, sectionPadding, graphics);
	}

	private long getGpPerHour(long runTime, long total)
	{
		//dont want to update too often
		long timeNow = Instant.now().toEpochMilli();
		if (timeNow - lastGpPerHourUpdateTime < 1000)
		{
			return lastGpPerHour;
		}

		float hours = ((float) runTime) / 3600000f;
		long gpPerHour = (long) (total / hours);

		lastGpPerHourUpdateTime = timeNow;
		lastGpPerHour = gpPerHour;
		return gpPerHour;
	}

	private String formatNumber(long number)
	{
		return QuantityFormatter.formatNumber(number);
	}

	private Color priceToColor(long price)
	{
		if (price > 0)
		{
			return Color.GREEN;
		}
		else if (price < 0)
		{
			return Color.RED;
		}
		else
		{
			return Color.WHITE;
		}
	}

	private void renderLedgerEntries(LinkedList<LedgerEntry> ledgerEntries, int x, int y, int rowW, int rowH, int sectionPadding, Graphics2D graphics)
	{
		FontMetrics fontMetrics = graphics.getFontMetrics();
		int yPosition = TEXT_Y_OFFSET;
		for (LedgerEntry ledgerEntry: ledgerEntries)
		{
			if(ledgerEntry.addGap)
				yPosition += sectionPadding;

			int textX = x + HORIZONTAL_PADDING;
			int textY = y + yPosition;

			TextComponent textComponent = new TextComponent();
			textComponent.setColor(ledgerEntry.leftColor);
			textComponent.setText(ledgerEntry.leftText);
			textComponent.setPosition(new Point(textX, textY));
			textComponent.render(graphics);
			
			String rightText = ledgerEntry.rightText;
			int textW = fontMetrics.stringWidth(rightText);
			textX = x + rowW - HORIZONTAL_PADDING - textW;

			textComponent = new TextComponent();
			textComponent.setColor(ledgerEntry.rightColor);
			textComponent.setText(rightText);
			textComponent.setPosition(new Point(textX, textY));
			textComponent.render(graphics);

			yPosition += rowH;
		}
	}

	private String formatGp(long total)
	{
		if (config.showExactGp())
		{
			return QuantityFormatter.formatNumber(total);
		}
		else
		{
			//decimal stack only works on positive numbers
			String totalText = QuantityFormatter.quantityToStackSize(Math.abs(total));
			if (total < 0)
				totalText = "-" + totalText;
			return totalText;
		}
	}

	private String getFormattedRunTime()
	{
		if (!config.showRunTime())
			return null;

		long runTime = plugin.elapsedRunTime();

		if (runTime == InventoryTotalPlugin.NO_PROFIT_LOSS_TIME)
		{
			return null;
		}

		long totalSecs = runTime / 1000;
		long totalMins = totalSecs / 60;

		long hrs = totalMins / 60;
		long mins = totalMins % 60;
		long secs = totalSecs % 60;

		if (hrs > 0)
		{
			return String.format(PROFIT_LOSS_TIME_FORMAT, hrs, mins, secs);
		}
		else
		{
			return String.format(PROFIT_LOSS_TIME_NO_HOURS_FORMAT, mins, secs);
		}
	}

	public ItemContainer getInventoryItemContainer()
	{
		return inventoryItemContainer;
	}

	public ItemContainer getEquipmentItemContainer()
	{
		return equipmentItemContainer;
	}

	public void showInterstitial()
	{
		showInterstitial = true;
	}

	public void hideInterstitial()
	{
		showInterstitial = false;
	}
}
