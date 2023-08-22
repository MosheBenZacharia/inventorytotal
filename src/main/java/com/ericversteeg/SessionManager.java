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
	private final long sessionRuntime;
	private final long totalGain;
	private final long totalLoss;
	private final long netTotal;
	private final int tripCount;
	private final long avgTripDuration;
}

@Slf4j
public class SessionManager
{
	private final InventoryTotalPlugin plugin;
	private final InventoryTotalConfig config;

	@Getter
	private Map<String, InventoryTotalRunData> activeTrips = new HashMap<>();
	@Getter
	private String activeSessionStartId;
	@Getter
	private String activeSessionEndId;

	public SessionManager(InventoryTotalPlugin plugin, InventoryTotalConfig config)
	{
		this.plugin = plugin;
		this.config = config;
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
		long tripDurationSum = 0;
		long totalPauseTime = 0;
		boolean foundStart = false;
		int tripCount = 0;
		for (InventoryTotalRunData runData : runDataSorted)
		{
			foundStart |= runData.identifier.equals(activeSessionStartId);
			if (!foundStart)
			{
				continue;
			}

			List<InventoryTotalLedgerItem> ledger = InventoryTotalPlugin.getProfitLossLedger(runData);
			for (InventoryTotalLedgerItem item : ledger)
			{
				long value = item.getCombinedValue();
				if (value > 0)
				{
					gains += value;
				}
				else
				{
					losses += value;
				}
			}
			long tripPauseTime = runData.pauseTime;
			long tripStartTime = runData.runStartTime;
			long tripEndTime = runData.isInProgress() ? Instant.now().toEpochMilli() : runData.runEndTime;
			tripDurationSum += (tripEndTime - tripStartTime) - tripPauseTime;
			totalPauseTime += tripPauseTime;
			tripCount++;

			if (activeSessionEndId != null && activeSessionEndId.equals(runData.identifier))
			{
				break;
			}
		}
		if (!foundStart)
		{
			log.error("couldn't find start session");
			return null;
		}
		long sessionRuntime = 0;
		if (config.ignoreBankTime())
		{
			sessionRuntime = tripDurationSum;
		}
		else
		{
			long sessionStartTime = getSessionStartTime();
			long sessionEndTime = getSessionEndTime();
			sessionRuntime = (sessionEndTime - sessionStartTime) - totalPauseTime;
		}
		long netTotal = gains + losses;
		long avgTripDuration = (long) (tripDurationSum / ((float) tripCount));

		return new SessionStats(sessionRuntime, gains, losses, netTotal, tripCount, avgTripDuration);
	}

	long getSessionStartTime()
	{
		if (activeSessionStartId == null)
		{
			return 0;
		}
		return getSessionStartTrip().runStartTime;
	}

	long getSessionEndTime()
	{
		return (activeSessionEndId == null) ? Instant.now().toEpochMilli()
			: (activeTrips.get(activeSessionEndId).isInProgress() ? Instant.now().toEpochMilli()
			: activeTrips.get(activeSessionEndId).runEndTime);
	}

	boolean isTimeInActiveSession(long time)
	{
		long startTime = getSessionStartTime();
		long endTime = getSessionEndTime();
		return time >= startTime && time <= endTime;
	}

	void setSessionStart(String id)
	{
		activeSessionStartId = id;
		if (id != null)
		{
			InventoryTotalRunData startTrip = getSessionStartTrip();
			InventoryTotalRunData endtrip = getSessionEndTrip();
			// order is messed up, just make end same as start
			if (endtrip != null && endtrip.runStartTime < startTrip.runStartTime)
			{
				setSessionEnd(id);
			}
		}
	}

	void setSessionEnd(String id)
	{
		activeSessionEndId = id;
		if (id != null)
		{
			InventoryTotalRunData startTrip = getSessionStartTrip();
			InventoryTotalRunData endtrip = getSessionEndTrip();
			// order is messed up, just make start same as end
			if (startTrip != null && startTrip.runStartTime > endtrip.runStartTime)
			{
				setSessionStart(id);
			}
		}
	}

	InventoryTotalRunData getSessionStartTrip()
	{
		if (activeSessionStartId == null)
		{
			return null;
		}
		return activeTrips.get(activeSessionStartId);
	}

	InventoryTotalRunData getSessionEndTrip()
	{
		if (activeSessionEndId == null)
		{
			if (activeTrips.size() == 0)
			{
				return null;
			}
			else
			{
				List<InventoryTotalRunData> sortedData = getSortedTrips();
				return sortedData.get(sortedData.size() - 1);
			}
		}
		return activeTrips.get(activeSessionEndId);
	}

	void deleteSession(String id)
	{
		activeTrips.remove(id);
		if (activeSessionStartId == id)
		{
			if (activeTrips.size() == 0)
			{
				activeSessionStartId = null;
			}
			else
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
		{
			activeSessionStartId = runData.identifier;
		}
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
			if (!tripEnd.containsKey(startId) || Math
				.abs(tripEnd.get(startId) - tripStart.get(startId)) > (InventoryTotalPlugin.roundAmount / 2f))
			{
				return true;
			}
		}
		for (Integer endId : tripEnd.keySet())
		{
			if (!tripStart.containsKey(endId)
				|| Math.abs(tripEnd.get(endId) - tripStart.get(endId)) > (InventoryTotalPlugin.roundAmount / 2f))
			{
				return true;
			}
		}
		return false;
	}
}
