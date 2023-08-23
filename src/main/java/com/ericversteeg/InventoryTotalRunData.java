package com.ericversteeg;

import java.time.Instant;
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
    boolean isPaused = false;

    Map<Integer, Float> initialItemQtys = new HashMap<>();
    Map<Integer, Float> itemQtys = new HashMap<>();

    LinkedList<String> ignoredItems = new LinkedList<>();

    boolean isInProgress()
    {
        return runEndTime == null;
    }

    long getRuntime()
    {
        return (runEndTime == null ? Instant.now().toEpochMilli() : runEndTime)
				- runStartTime;
    }

    //its in the period between banking finished (onNewRun) and two ticks later when we call onPostNewRun
    transient boolean showInterstitial;
}
