package budo.budoist.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OnAlarmReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		WakefulIntentService.acquireStaticLock(context);
	    context.startService(new Intent(context, AppService.class));
	}
}