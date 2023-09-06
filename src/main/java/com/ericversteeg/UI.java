package com.ericversteeg;

import java.awt.*;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.swing.*;

import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

public class UI {
    public static class LootPanelData {
        JPanel lootPanel = new JPanel();
        JPanel containerPanel = new JPanel();
        List<InventoryTotalLedgerItem> previousLedger = new LinkedList<InventoryTotalLedgerItem>();
    }

    public static final ImageIcon PAUSE_ICON;
    public static final ImageIcon PLAY_ICON;
    public static final ImageIcon SESSIONINFO_GEAR_ICON;
    public static final ImageIcon SESSIONINFO_REFRESH_ICON;
    public static final ImageIcon SESSIONINFO_WRENCH_ICON;
    public static final ImageIcon SESSIONINFO_STOP_ICON;
    public static final ImageIcon SESSIONINFO_PLAY_ICON;
    public static final ImageIcon SESSIONINFO_TRASH_ICON;
    public static final ImageIcon SESSIONINFO_SAVE_ICON;
    public static final ImageIcon SESSIONINFO_GEAR_HOVER_ICON;
    public static final ImageIcon SESSIONINFO_REFRESH_HOVER_ICON;
    public static final ImageIcon SESSIONINFO_WRENCH_HOVER_ICON;
    public static final ImageIcon SESSIONINFO_STOP_HOVER_ICON;
    public static final ImageIcon SESSIONINFO_PLAY_HOVER_ICON;
    public static final ImageIcon SESSIONINFO_TRASH_HOVER_ICON;
    public static final ImageIcon SESSIONINFO_SAVE_HOVER_ICON;
    public static final ImageIcon SESSIONINFO_INFO_ICON;
    public static final ImageIcon SESSIONINFO_INFO_HOVER_ICON;
    public static final ImageIcon SESSIONINFO_GRID_ICON;
    public static final ImageIcon SESSIONINFO_GRID_HOVER_ICON;
    public static final ImageIcon SESSIONINFO_PLUS_ICON;
    public static final ImageIcon SESSIONINFO_PLUS_HOVER_ICON;

    private static final String PROFIT_LOSS_TIME_FORMAT = "%01d:%02d:%02d";
    private static final String PROFIT_LOSS_TIME_NO_HOURS_FORMAT = "%01d:%02d";
    private static final NumberFormat englishFormat = NumberFormat.getInstance(Locale.ENGLISH);
    private static final Color redLootBackgroundColor = new Color(48, 15, 15);
    private static final Color greenLootBackgroundColor = new Color(21, 43, 16);
    private static final int ITEMS_PER_ROW = 5;
    private static final Dimension ITEM_SIZE = new Dimension(40, 40);

    private static final float roundMultiplier = 1f / InventoryTotalPlugin.roundAmount;

    public static String formatQuantity(float quantity, boolean absolute) {
        if (absolute) {
            quantity = Math.abs(quantity);
        }
        quantity = Math.round(quantity * roundMultiplier) / roundMultiplier;
        String text = englishFormat.format(quantity);
        return text;
    }

    public static JLabel createIconButton(ImageIcon defaultIcon, ImageIcon hoverIcon, String tooltipText, Runnable onClick) {
        JLabel label = new JLabel(defaultIcon);
        label.setToolTipText(tooltipText);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    onClick.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                label.setIcon(hoverIcon);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setIcon(defaultIcon);
            }
        });
        return label;
    }

    public static boolean ledgersMatch(List<InventoryTotalLedgerItem> ledgerOne, List<InventoryTotalLedgerItem> ledgerTwo) {
        if (ledgerOne == null || ledgerTwo == null) {
            return false;
        }
        if (ledgerOne.size() != ledgerTwo.size()) {
            return false;
        }
        for (int i = 0; i < ledgerOne.size(); ++i) {
            InventoryTotalLedgerItem itemOne = ledgerOne.get(i);
            InventoryTotalLedgerItem itemTwo = ledgerTwo.get(i);

            if (itemOne.getQty() != itemTwo.getQty()) {
                return false;
            }
            if (itemOne.getItemId() != itemTwo.getItemId()) {
                return false;
            }
        }

        return true;
    }

    public static String getTimeAgo(long timestamp) {
        long currentTime = Instant.now().toEpochMilli();
        long timeDiff = currentTime - timestamp;

        if (timeDiff < 0) {
            return "In the future";
        }

        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeDiff) % 60;
        long hours = TimeUnit.MILLISECONDS.toHours(timeDiff);
        long days = TimeUnit.MILLISECONDS.toDays(timeDiff);

        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " and " + minutes + " minute" + (minutes != 1 ? "s" : "") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else {
            return "less than a minute ago";
        }
    }

    public static void clearListeners(AbstractButton button) {
        ActionListener[] listeners = button.getActionListeners();
        for (ActionListener listener : listeners) {
            button.removeActionListener(listener);
        }
    }

    public static Component addVerticalRigidBox(Container container, int size) {
        return container.add(Box.createRigidArea(new Dimension(0, size)));
    }

    public static String formatTime(long millis) {
        long totalSecs = millis / 1000;
        long totalMins = totalSecs / 60;

        long hrs = totalMins / 60;
        long mins = totalMins % 60;
        long secs = totalSecs % 60;

        if (hrs > 0) {
            return String.format(PROFIT_LOSS_TIME_FORMAT, hrs, mins, secs);
        } else {
            return String.format(PROFIT_LOSS_TIME_NO_HOURS_FORMAT, mins, secs);
        }
    }

    public static List<InventoryTotalLedgerItem> filterAndSortLedger(List<InventoryTotalLedgerItem> ledger) {
        //don'tneed to filter here, getprofitlossledger does this at the bottom

        // filter out anything with no change or change that will get rounded to 0
        // ledger = ledger.stream().filter(item -> Math.abs(item.getQty()) > (InventoryTotalPlugin.roundAmount / 2f))
        //         .collect(Collectors.toList());

        // sort by profit descending
        ledger = ledger.stream().sorted(Comparator.comparingLong(o -> -(o.getCombinedValue())))
                .collect(Collectors.toList());

        return ledger;
    }

    public static String buildToolTip(String name, String quantity, String price, String combinedValue) {
        return "<html>" + name + " x " + quantity
                + "<br/>Price: " + price
                + "<br/>Total: " + combinedValue + "</html>";
    }

    public static long getGpPerHour(long runTime, long total) {
        float hours = ((float) runTime) / 3600000f;
        long gpPerHour = (long) (total / hours);
        return gpPerHour;
    }

    public static String formatGp(long total, boolean showExact) {
        if (showExact) {
            return QuantityFormatter.formatNumber(total);
        } else {
            return QuantityFormatter.quantityToStackSize(total);
        }
    }

    static void updateLootGrid(List<InventoryTotalLedgerItem> ledger, LootPanelData lootPanelData, ItemManager itemManager, InventoryTotalConfig config) {
        if (UI.ledgersMatch(ledger, lootPanelData.previousLedger)) {
            return;
        }
        lootPanelData.previousLedger = ledger;
        JPanel containerCurrent = new JPanel();
        int totalItems = ledger.size();

        // tpData.containerPanel.setBorder(new EmptyBorder(2, 2, 5, 2));
        // containerCurrent.setBorder(new EmptyBorder(2, 2, 5, 2));

        // Calculates how many rows need to be display to fit all items
        final int rowSize = ((totalItems % ITEMS_PER_ROW == 0) ? 0 : 1) + totalItems / ITEMS_PER_ROW;
        lootPanelData.containerPanel.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));
        containerCurrent.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));

        // Create stacked items from the item list, calculates total price and then
        // displays all the items in the UI.
        for (InventoryTotalLedgerItem ledgerItem : ledger) {
            final JPanel slot = new JPanel();
            boolean wasGain = ledgerItem.getQty() > 0;
            slot.setLayout(new GridLayout(1, 1, 0, 0));
            slot.setBackground(wasGain ? greenLootBackgroundColor : redLootBackgroundColor);
            slot.setPreferredSize(ITEM_SIZE);

            final JLabel itemLabel = new JLabel();

            itemLabel.setToolTipText(UI.buildToolTip(ledgerItem.getDescription(),
                    UI.formatQuantity(ledgerItem.getQty(), false),
                    UI.formatGp(ledgerItem.getPrice(), config.showExactGp()),
                    UI.formatGp(ledgerItem.getCombinedValue(), config.showExactGp())));
            itemLabel.setVerticalAlignment(SwingConstants.CENTER);
            itemLabel.setHorizontalAlignment(SwingConstants.CENTER);

            AsyncBufferedImage itemImage = itemManager.getImage(ledgerItem.getItemId(),
                    (int) Math.ceil(Math.abs(ledgerItem.getQty())), Math.ceil(Math.abs(ledgerItem.getQty())) > 1);
            itemImage.addTo(itemLabel);

            slot.add(itemLabel);
            containerCurrent.add(slot);
        }
        if (totalItems < ITEMS_PER_ROW || totalItems % ITEMS_PER_ROW != 0) {
            int extraBoxes;
            if (totalItems % ITEMS_PER_ROW != 0 && totalItems >= ITEMS_PER_ROW) {
                int i = totalItems;
                while (i % ITEMS_PER_ROW != 0) {
                    i++;
                }
                extraBoxes = i - totalItems;
            } else {
                extraBoxes = ITEMS_PER_ROW - totalItems;
            }
            for (int i = 0; i < extraBoxes; i++) {
                final JPanel slot = new JPanel();
                slot.setLayout(new GridLayout(1, 1, 0, 0));
                slot.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                slot.setPreferredSize(ITEM_SIZE);

                containerCurrent.add(slot);
            }
        }

        lootPanelData.lootPanel.remove(lootPanelData.containerPanel);
        lootPanelData.containerPanel = containerCurrent;
        lootPanelData.lootPanel.add(lootPanelData.containerPanel);
        lootPanelData.lootPanel.revalidate();
        lootPanelData.lootPanel.repaint();
    }

    static {
        BufferedImage pausePNG = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-pause.png");
        BufferedImage playPNG = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-play.png");

        PAUSE_ICON = new ImageIcon(pausePNG);
        PLAY_ICON = new ImageIcon(playPNG);

        //Session info tray
        final float hoverAlphaOffset = .53f;
        final BufferedImage importIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-gear.png");
        SESSIONINFO_GEAR_ICON = new ImageIcon(importIcon);
        SESSIONINFO_GEAR_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(importIcon, hoverAlphaOffset));
        final BufferedImage refreshIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-refresh.png");
        SESSIONINFO_REFRESH_ICON = new ImageIcon(refreshIcon);
        SESSIONINFO_REFRESH_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(refreshIcon, hoverAlphaOffset));
        final BufferedImage wrenchIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-wrench.png");
        SESSIONINFO_WRENCH_ICON = new ImageIcon(wrenchIcon);
        SESSIONINFO_WRENCH_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(wrenchIcon, hoverAlphaOffset));
        final BufferedImage stopIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-stop.png");
        SESSIONINFO_STOP_ICON = new ImageIcon(stopIcon);
        SESSIONINFO_STOP_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(stopIcon, hoverAlphaOffset));
        final BufferedImage playIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-play.png");
        SESSIONINFO_PLAY_ICON = new ImageIcon(playIcon);
        SESSIONINFO_PLAY_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(playIcon, hoverAlphaOffset));
        final BufferedImage trashIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-trash.png");
        SESSIONINFO_TRASH_ICON = new ImageIcon(trashIcon);
        SESSIONINFO_TRASH_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(trashIcon, hoverAlphaOffset));
        final BufferedImage saveIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-save.png");
        SESSIONINFO_SAVE_ICON = new ImageIcon(saveIcon);
        SESSIONINFO_SAVE_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(saveIcon, hoverAlphaOffset));
        final BufferedImage infoIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-info.png");
        SESSIONINFO_INFO_ICON = new ImageIcon(infoIcon);
        SESSIONINFO_INFO_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(infoIcon, hoverAlphaOffset));
        final BufferedImage gridIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-grid.png");
        SESSIONINFO_GRID_ICON = new ImageIcon(gridIcon);
        SESSIONINFO_GRID_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(gridIcon, hoverAlphaOffset));
        final BufferedImage plusIcon = ImageUtil.loadImageResource(InventoryTotalPlugin.class, "/gpperhour-session-plus.png");
        SESSIONINFO_PLUS_ICON = new ImageIcon(plusIcon);
        SESSIONINFO_PLUS_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(plusIcon, hoverAlphaOffset));
    }
}
