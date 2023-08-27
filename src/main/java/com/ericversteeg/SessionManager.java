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

@Slf4j
public class SessionManager
{
	private final InventoryTotalPlugin plugin;
	private final InventoryTotalConfig config;

	@Getter
	private final Map<String, InventoryTotalRunData> activeTrips = new HashMap<>();
	@Getter
	private String activeSessionStartId;
	@Getter
	private String activeSessionEndId;
	@Getter
	private boolean isTracking = true;

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

	void startTracking()
	{
		isTracking = true;
		if (plugin.getState() == InventoryTotalState.BANK)
		{
			return;
		}
		InventoryTotalRunData activeTrip = plugin.getRunData();
		if (activeTrip != null && !activeTrip.isBankDelay && activeTrip.isInProgress())
		{
			onTripStarted(activeTrip);
		}
	}

	void stopTracking()
	{
		isTracking = false;
		//remove the active trip if there is one
		String activeTripIdentifier = null;
		for (InventoryTotalRunData trip : activeTrips.values())
		{
			if (trip.isInProgress())
			{
				activeTripIdentifier = trip.identifier;
			}
		}
		if (activeTripIdentifier != null)
		{
			deleteTrip(activeTripIdentifier);
		}
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
		Map<Integer, Float> initialQtys = new HashMap<>();
		Map<Integer, Float> qtys = new HashMap<>();
		for (InventoryTotalRunData runData : runDataSorted)
		{
			foundStart |= runData.identifier.equals(activeSessionStartId);
			if (!foundStart)
			{
				continue;
			}
			for (Integer initialId : runData.initialItemQtys.keySet())
			{
				initialQtys.merge(initialId, runData.initialItemQtys.get(initialId), Float::sum);
			}
			for (Integer itemId : runData.itemQtys.keySet())
			{
				qtys.merge(itemId, runData.itemQtys.get(itemId), Float::sum);
			}

			List<InventoryTotalLedgerItem> ledger = InventoryTotalPlugin.getProfitLossLedger(runData.initialItemQtys, runData.itemQtys);
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

		return new SessionStats(getSessionEndTime(), sessionRuntime, gains, losses, netTotal, tripCount, avgTripDuration, initialQtys, qtys);
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

	void deleteAllTrips()
	{
		List<InventoryTotalRunData> allTrips = new LinkedList<>(activeTrips.values());
		activeSessionStartId = null;
		activeSessionEndId = null;
		for(InventoryTotalRunData trip : allTrips)
		{
			if (trip.isInProgress())
			{
				activeSessionStartId = trip.identifier;
			}
			else
			{
				activeTrips.remove(trip.identifier);
			}
		}
	}

	void deleteTrip(String id)
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
		if (!isTracking)
		{
			return;
		}
		activeTrips.put(runData.identifier, runData);
		if (activeSessionStartId == null)
		{
			activeSessionStartId = runData.identifier;
		}
	}

	void onTripCompleted(InventoryTotalRunData runData)
	{
		if (!isTracking)
		{
			return;
		}
		// don't care about trips where nothing happened, can remove it from the history
		if (!tripHadChange(runData.initialItemQtys, runData.itemQtys))
		{
			log.info("nothing changed, ignoring trip");
			deleteTrip(runData.identifier);
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
