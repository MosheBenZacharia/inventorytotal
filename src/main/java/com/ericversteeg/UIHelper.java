package com.ericversteeg;

import java.awt.event.ActionListener;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;

import net.runelite.client.util.QuantityFormatter;

public class UIHelper
{
	private static final String PROFIT_LOSS_TIME_FORMAT = "%02d:%02d:%02d";
	private static final String PROFIT_LOSS_TIME_NO_HOURS_FORMAT = "%02d:%02d";

	public static String getTimeAgo(long timestamp) {
		long currentTime = Instant.now().toEpochMilli();
		long timeDiff = currentTime - timestamp;

		if (timeDiff < 0) {
			return "In the future";
		}

		long seconds = TimeUnit.MILLISECONDS.toSeconds(timeDiff);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(timeDiff);
		long hours = TimeUnit.MILLISECONDS.toHours(timeDiff);
		long days = TimeUnit.MILLISECONDS.toDays(timeDiff);
		
        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        } else if (minutes > 0) {
            return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        } else {
            return seconds + " second" + (seconds > 1 ? "s" : "") + " ago";
        }
	}

	public static  void clearListeners(JButton button)
	{
		ActionListener[] listeners = button.getActionListeners();
		for (ActionListener listener : listeners) {
			button.removeActionListener(listener);
		}
	}

	public static String formatTime(long millis)
	{
		long totalSecs = millis / 1000;
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

	public static long getGpPerHour(long runTime, long total)
	{
		float hours = ((float) runTime) / 3600000f;
		long gpPerHour = (long) (total / hours);
		return gpPerHour;
	}

	public static String formatGp(long total, boolean showExact)
	{
		if (showExact)
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
}
