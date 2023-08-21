package com.ericversteeg;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;

public class InventoryTotalRunData {
    String identifier = null;
    long profitLossInitialGp = 0;
	long runStartTime = 0;
    //if this is null the trip is in progress
    Long runEndTime = null;
    //add to this while paused? then subtract when calculating duration
    long pauseTime = 0;

    // static item prices so that when ItemManager updates, the Profit / Loss value doesn't all of a sudden change
    // this is cleared and repopulated at the start of each new run (after bank) and whenever new items hit the inventory
    Map<Integer, Integer> itemPrices = new HashMap<>();
	//so we can do name lookups on the swing thread
	Map<Integer, String> itemNames = new HashMap<>();
    Map<Integer, Float> initialItemQtys = new HashMap<>();
    Map<Integer, Float> itemQtys = new HashMap<>();

    LinkedList<String> ignoredItems = new LinkedList<>();

    boolean isInProgress()
    {
        return runEndTime == null;
    }

    //its in the period between banking finished (onNewRun) and two ticks later when we call onPostNewRun
    transient boolean showInterstitial;
}
