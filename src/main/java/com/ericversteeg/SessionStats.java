package com.ericversteeg;

import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
class SessionStats
{
	String sessionID;
	String sessionName;
	long sessionSaveTime;
	private final long sessionRuntime;
	private final long totalGain;
	private final long totalLoss;
	private final long netTotal;
	private final int tripCount;
	private final long avgTripDuration;
	private final Map<Integer, Float> initialQtys;
	private final Map<Integer, Float> qtys;

	//ui state
	transient boolean showDetails;
}
