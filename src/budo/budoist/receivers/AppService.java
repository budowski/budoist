package budo.budoist.receivers;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import budo.budoist.TodoistApplication;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.services.TodoistServerException;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import com.bugsense.trace.BugSenseHandler;

public class AppService extends WakefulIntentService {
    public static final String SYNC_COMPLETED_ACTION = "budo.budoist.action.SYNC_COMPLETED";
    
	private TodoistApplication mApplication;
    private TodoistClient mClient;
    private TodoistOfflineStorage mStorage;
    
	public AppService() {
	    super("AppService");
	}

	@Override
	protected void doWakefulWork(Intent intent) {
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mStorage = mClient.getStorage();
        
        if (!mClient.isCurrentlyBackingUp()) {
        	// Not currently backing up - see if we need to backup
         	int backupFrequencyMins = mStorage.getBackupFrequency();
        	long currentTimeMs = (new Date()).getTime();
        	long lastBackupTimeMs = mStorage.getLastBackupTime().getTime();
        	long timeDiffMins = (currentTimeMs - lastBackupTimeMs) / (1000 * 60);
        	
        	if ((backupFrequencyMins > 0) && (timeDiffMins >= backupFrequencyMins)) {
        		// Time to backup
         		try {
	        		mClient.backupData(this); // if successful, backupData will update last backup time
				} catch (IOException e) {
					// Backup failed - will try again next time
					e.printStackTrace();
				}
        		
        	}
        	
        }
        
      
        if ((!mClient.isCurrentlySyncing()) && (!mClient.hasNeverLoggedIn())) {
        	// Not currently syncing - see if we need to sync
        	
        	int syncFrequencyMins = mStorage.getSyncFrequency();
        	long currentTimeMs = (new Date()).getTime();
        	long lastSyncTimeMs = mStorage.getLastSyncTime().getTime();
        	long timeDiffMins = (currentTimeMs - lastSyncTimeMs) / (1000 * 60);
        	
        	if (timeDiffMins >= syncFrequencyMins) {
        		// Time to sync
        		try {
					mClient.login();
	        		mClient.syncAll(null); // if successful, syncAll will update last sync time
	        		
	        		// Tell any active views to refresh their project/label/note/item list
	        		Intent syncCompleteIntent = new Intent(SYNC_COMPLETED_ACTION);
	        		sendBroadcast(syncCompleteIntent);

				} catch (Exception e) {
					// Login/sync failed - will try again next time
					e.printStackTrace();
				}
        	}
        }
        
	}
}
