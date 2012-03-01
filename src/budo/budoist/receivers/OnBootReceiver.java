package budo.budoist.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

public class OnBootReceiver extends BroadcastReceiver {
	// 5 minutes (in milliseconds)
	private static final int REPEAT_PERIOD = 300000;
  
	@Override
	public void onReceive(Context context, Intent intent) {
		OnBootReceiver.startRepeatingService(context);
	}
	
	public static void startRepeatingService(Context context) {
		AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
	    Intent i = new Intent(context, OnAlarmReceiver.class);
	    PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);

	    mgr.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
	                      SystemClock.elapsedRealtime() + 60000 /* Initial run in 60 seconds */,
	                      REPEAT_PERIOD,
	                      pi);

	}
}
