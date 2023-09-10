package com.ericversteeg;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import com.google.gson.Gson;

public class InventoryTotalRunData
{
    String identifier = null;
    long profitLossInitialGp = 0;
    long runStartTime = 0;
    // if this is null the trip is in progress
    Long runEndTime = null;
    // add to this while paused? then subtract when calculating duration
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
        return (getEndTime() - runStartTime) - pauseTime;
    }

    long getEndTime()
    {
        return (runEndTime == null ? Instant.now().toEpochMilli() : runEndTime);
    }

    // its in the period between banking finished (onNewRun) and two ticks later
    // when we call onPostNewRun. we have this delay because of how you can withdraw from the bank,
    // close it immediately, and still get the items in your inventory a tick later.
    transient boolean isBankDelay;
    //first run needs to be reinitialized on first game tick.
    transient boolean isFirstRun;
}
