package com.ericversteeg;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SessionManager
{
	@Getter
	private Map<String, InventoryTotalRunData> activeTrips = new HashMap<>();
	private String activeSessionStartId;

	void startUp()
	{

	}

	void shutDown()
	{

	}

	void onTripStarted(InventoryTotalRunData runData)
	{
		activeTrips.put(runData.identifier, runData);
	}

	void onTripCompleted(InventoryTotalRunData runData)
	{
		// don't care about trips where nothing happened, can remove it from the history
		if (!tripHadChange(runData.initialItemQtys, runData.itemQtys))
		{
			log.info("nothing changed, ignoring trip");
			activeTrips.remove(runData.identifier);
			return;
		}
	}

	boolean tripHadChange(Map<Integer, Float> tripStart, Map<Integer, Float> tripEnd)
	{
		if (tripStart.size() != tripEnd.size())
		{
			return true;
		}

		for (Integer startId : tripStart.keySet())
		{
			if (!tripEnd.containsKey(startId) || tripEnd.get(startId) != tripStart.get(startId))
			{
				return true;
			}
		}
		for (Integer endId : tripEnd.keySet())
		{
			if (!tripStart.containsKey(endId) || tripEnd.get(endId) != tripStart.get(endId))
			{
				return true;
			}
		}
		return false;
	}
}
