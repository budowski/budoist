package budo.budoist;

import pl.polidea.treeview.TreeStateManager;
import budo.budoist.models.Project;
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
	
	private TreeStateManager<Project> mProjectTreeManager = null;
	
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
	
	
	/**
	 * Returns the last used project tree state (used so that the collapsible state of
	 * projects items in the tree view will be saved)
	 * @return
	 */
	public TreeStateManager<Project> getProjectTreeState() {
	    return mProjectTreeManager;
	}
	
	/**
	 * Sets the last used project tree state (used so that the collapsible state of
	 * projects items in the tree view will be saved)
	 * 
	 * @param manager new tree state manager
	 */
	public void setProjectTreeState(TreeStateManager<Project> manager) {
	    mProjectTreeManager = manager;
	}
	
}
