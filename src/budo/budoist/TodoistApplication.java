package budo.budoist;

import budo.budoist.services.TodoistClient;
import android.app.Application;

/**
 * Extended Application instance - used for sharing global data accross different activities
 * (for example: the TodoistClient instance)
 * 
 * @author Yaron Budowski
 *
 */
public class TodoistApplication extends Application {
	private TodoistClient mClient;
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mClient = new TodoistClient(getBaseContext());
	}
	
	/**
	 * Returns the TodoistClient used accross all activities
	 * @return
	 */
	public TodoistClient getClient() {
		return mClient;
	}
}
