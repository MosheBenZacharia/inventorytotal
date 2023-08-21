package com.ericversteeg;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;

@RequiredArgsConstructor
@Getter
class SessionStats
{
	private final long sessionStartTime;
	private final long sessionEndTime;
	private final long totalGain;
	private final long totalLoss;
	private final long netTotal;
	private final int tripCount;
}

@Slf4j
public class SessionManager
{
	private final InventoryTotalPlugin plugin;

	@Getter
	private Map<String, InventoryTotalRunData> activeTrips = new HashMap<>();
	private String activeSessionStartId;
	private String activeSessionEndId;

	public SessionManager(InventoryTotalPlugin plugin)
	{
		this.plugin = plugin;
	}

	void startUp()
	{

	}

	void shutDown()
	{

	}

	private List<InventoryTotalRunData> getSortedTrips()
	{
		return activeTrips.values().stream().sorted(Comparator.comparingLong(o -> o.runStartTime))
				.collect(Collectors.toList());
	}

	SessionStats getActiveSessionStats()
	{
		if (activeSessionStartId == null)
		{
			return null;
		}
		List<InventoryTotalRunData> runDataSorted = getSortedTrips();

		long gains = 0;
		long losses = 0;

		boolean foundStart = false;
		int tripCount = 0;
		for (InventoryTotalRunData runData : runDataSorted)
		{
			foundStart |= runData.identifier.equals(activeSessionStartId);
			if (!foundStart)
			{
				continue;
			}

			List<InventoryTotalLedgerItem> ledger = plugin.getProfitLossLedger(runData);
			for (InventoryTotalLedgerItem item : ledger)
			{
				long value = item.getCombinedValue();
				if (value > 0)
				{
					gains += value;
				} else
				{
					losses += value;
				}
			}
			tripCount++;

			if (activeSessionEndId != null && activeSessionEndId.equals(runData.identifier))
			{
				break;
			}
		}
		log.info("active trip count: " +activeTrips.size());
		if (!foundStart)
		{
			log.error("couldn't find start session");
			return null;
		}
		long sessionStartTime = activeTrips.get(activeSessionStartId).runStartTime;
		long sessionEndTime = (activeSessionEndId == null) ? Instant.now().toEpochMilli()
				: (activeTrips.get(activeSessionEndId).isInProgress() ? Instant.now().toEpochMilli()
						: activeTrips.get(activeSessionEndId).runEndTime);
		long netTotal = gains + losses;

		return new SessionStats(sessionStartTime, sessionEndTime, gains, losses, netTotal, tripCount);
	}

	void setSessionStart(String id)
	{
		activeSessionStartId = id;
	}

	void setSessionEnd(String id)
	{
		activeSessionEndId = id;
	}

	void deleteSession(String id)
	{
		activeTrips.remove(id);
		if (activeSessionStartId == id)
		{
			if (activeTrips.size() == 0)
			{
				activeSessionStartId = null;
			} else
			{
				activeSessionStartId = getSortedTrips().get(0).identifier;
			}
		}
		if (activeSessionEndId == id)
		{
			activeSessionEndId = null;
		}
	}

	void onTripStarted(InventoryTotalRunData runData)
	{
		activeTrips.put(runData.identifier, runData);
		if (activeSessionStartId == null)
			activeSessionStartId = runData.identifier;
	}

	void onTripCompleted(InventoryTotalRunData runData)
	{
		// don't care about trips where nothing happened, can remove it from the history
		if (!tripHadChange(runData.initialItemQtys, runData.itemQtys))
		{
			log.info("nothing changed, ignoring trip");
			deleteSession(runData.identifier);
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
			if (!tripEnd.containsKey(startId) || Math.abs(tripEnd.get(startId) - tripStart.get(startId)) > (InventoryTotalPlugin.roundAmount/2f))
			{
				return true;
			}
		}
		for (Integer endId : tripEnd.keySet())
		{
			if (!tripStart.containsKey(endId) || Math.abs(tripEnd.get(endId) - tripStart.get(endId)) > (InventoryTotalPlugin.roundAmount/2f))
			{
				return true;
			}
		}
		return false;
	}
}
