package budo.budoist;

import java.io.Serializable;
import java.util.ArrayList;

import com.bugsense.trace.BugSenseHandler;

import budo.budoist.models.Label;
import budo.budoist.models.Project;
import budo.budoist.models.Query;
import budo.budoist.receivers.OnBootReceiver;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.services.TodoistServerException;
import budo.budoist.services.TodoistOfflineStorage.InitialView;
import budo.budoist.views.ItemListView;
import budo.budoist.views.LabelListView;
import budo.budoist.views.LoginOrRegisterView;
import budo.budoist.views.ProjectListView;
import budo.budoist.views.QueryListView;
import budo.budoist.views.LabelListView.LabelViewMode;
import budo.budoist.views.ProjectListView.ProjectViewMode;
import budo.budoist.views.QueryListView.QueryViewMode;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Bootloader class - initial loading of the application:
 * 		* If never sync'd before - shows the login screen
 * 		* Otherwise, shows the initial view (as set in the preferences)
 *
 * @author Yaron Budowski
 *
 */
public class Bootloader extends Activity {
    private TodoistApplication mApplication;
    private TodoistClient mClient;
    private TodoistOfflineStorage mStorage;
    
    public static final int REQUEST_CODE__EDIT_PROJECT = 1;
    public static final int REQUEST_CODE__EDIT_ITEM = 2;
    public static final int REQUEST_CODE__EDIT_LABEL = 3;
    public static final int REQUEST_CODE__SELECT_ITEM_LABELS = 4;
    public static final int REQUEST_CODE__MOVE_TO_PROJECT = 5;
    public static final int REQUEST_CODE__EDIT_NOTES = 6;
    public static final int REQUEST_CODE__SELECT_INITIAL_PROJECT = 7;
    public static final int REQUEST_CODE__SELECT_INITIAL_LABEL = 8;
	public static final int REQUEST_CODE__LOGIN = 9;
	public static final int REQUEST_CODE__REGISTER = 10;
	public static final int REQUEST_CODE__SETTINGS = 11;
	public static final int REQUEST_CODE__EDIT_QUERY = 12;
	public static final int REQUEST_CODE__SELECT_INITIAL_QUERY = 13;
    public static final int REQUEST_CODE__SELECT_DEFAULT_PROJECT = 14;
    public static final int REQUEST_CODE__OPEN_BACKUP_RESTORE_FILE = 15;
    
	@Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        BugSenseHandler.setup(this, "7ffbae8a");
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mStorage = mClient.getStorage();
        
        // Initialize the sync service in case it wasn't started in boot (i.e. when application is
        // first installed, and before first boot)
        OnBootReceiver.startRepeatingService(this);
   
    	if ((mClient.hasNeverLoggedIn()) || (mClient.hasNeverSynced())) {
    		// Never logged in before - Show a login/register screen
	        Intent intent = new Intent(getBaseContext(), LoginOrRegisterView.class);
	        startActivity(intent);
	        
    	} else {
    		
    		if ((mStorage.getSyncOnStartup()) && (!mClient.isCurrentlySyncing())) {
    			// Sync on startup (using another thread)
    			(new Thread(new Runnable() {
					@Override
					public void run() {
		         		try {
							mClient.login();
			        		mClient.syncAll(null);
						} catch (TodoistServerException e) {
							// Login/sync failed
							e.printStackTrace();
						}
 					}
				})).start();
    		}
    		
    		// Show the initial view, according to the preferences set by the user
    		
    		InitialView initialView = mStorage.getInitialView();
	        Intent intent = null;
	        
	        if ((initialView == InitialView.LAST_VIEWED_PROJECT) && (mStorage.getLastViewedProject() == 0)) {
	        	// No last viewed project - default to filter by projects view
	        	initialView = InitialView.FILTER_BY_PROJECTS;
	        } else if ((initialView == InitialView.LAST_VIEWED_LABEL) && (mStorage.getLastViewedLabel() == 0)) {
	        	// No last viewed label - default to filter by labels view
	        	initialView = InitialView.FILTER_BY_LABELS;
	        } else if (initialView == InitialView.FILTER_BY_PROJECTS_OR_LABELS_OR_QUERIES) {
	        	// Use last viewed filter mode (by projects or labels or queries)
	        	initialView = mStorage.getLastViewedFilter();
	        }
	        
	        switch (initialView) {
	        case FILTER_BY_LABELS:
		        intent = new Intent(getBaseContext(), LabelListView.class);
		        intent.putExtra(LabelListView.KEY__VIEW_MODE, LabelViewMode.FILTER_BY_LABELS.toString());
		        break;
		        
	        case FILTER_BY_PROJECTS:
		        intent = new Intent(getBaseContext(), ProjectListView.class);
		        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.FILTER_BY_PROJECTS.toString());
		        break;
		        
	        case FILTER_BY_QUERIES:
		        intent = new Intent(getBaseContext(), QueryListView.class);
		        intent.putExtra(QueryListView.KEY__VIEW_MODE, QueryViewMode.FILTER_BY_QUERIES.toString());
		        break;
	       
	        case LAST_VIEWED_LABEL:
	        case SPECIFIC_LABEL:
	        	int labelId = 0;
	        	if (initialView == InitialView.LAST_VIEWED_LABEL)
	        		labelId = mStorage.getLastViewedLabel();
	        	else if (initialView == InitialView.SPECIFIC_LABEL)
	        		labelId = mStorage.getInitialLabel();
	        	
	        	Label label = mStorage.getLabel(labelId);
	        	
	        	if (label == null) {
	        		// Special case - label was deleted - select the first available label
	        		ArrayList<Label> labels = mStorage.getLabels();
	        		
	        		if (labels.size() == 0) {
	        			// No labels available at all - revert to filter by projects
	        			mStorage.setInitialView(InitialView.FILTER_BY_PROJECTS);
	        			
				        intent = new Intent(getBaseContext(), ProjectListView.class);
				        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.FILTER_BY_PROJECTS.toString());
				        break;
	        		}

	        		label = labels.get(0);
	        		
	        		// Fix this issue in the storage
	        		if (initialView == InitialView.LAST_VIEWED_LABEL)
	        			mStorage.setLastViewedLabel(label.id);
	        		else if (initialView == InitialView.SPECIFIC_LABEL)
	        			mStorage.setInitialLabel(label.id);
	        	}
	        	
		        intent = new Intent(getBaseContext(), ItemListView.class);
		        intent.putExtra(ItemListView.KEY__VIEW_MODE, ItemListView.ItemViewMode.FILTER_BY_LABELS.toString());
		        intent.putExtra(ItemListView.KEY__LABEL, (Serializable)label);
		        break;
		        
	        case LAST_VIEWED_PROJECT:
	        case SPECIFIC_PROJECT:
	        	int projectId = 0;
	        	if (initialView == InitialView.LAST_VIEWED_PROJECT)
	        		projectId = mStorage.getLastViewedProject();
	        	else if (initialView == InitialView.SPECIFIC_PROJECT)
	        		projectId = mStorage.getInitialProject();

	        	Project project = mStorage.getProject(projectId);
	        	
	        	if (project == null) {
	        		// Special case - project was deleted - select the first available project
	        		ArrayList<Project> projects = mStorage.getProjects();
	        		
	        		if (projects.size() == 0) {
	        			// No projects available at all - revert to filter by projects
	        			mStorage.setInitialView(InitialView.FILTER_BY_PROJECTS);
	        			
				        intent = new Intent(getBaseContext(), ProjectListView.class);
				        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.FILTER_BY_PROJECTS.toString());
				        break;
	        		}
        		
	        		project = projects.get(0);
	        		
	        		// Fix this issue in the storage
	        		if (initialView == InitialView.LAST_VIEWED_PROJECT)
	        			mStorage.setLastViewedProject(project.id);
	        		else if (initialView == InitialView.SPECIFIC_PROJECT)
	        			mStorage.setInitialProject(project.id);
	        	}
        	
	        	
		        intent = new Intent(getBaseContext(), ItemListView.class);
		        intent.putExtra(ItemListView.KEY__VIEW_MODE, ItemListView.ItemViewMode.FILTER_BY_PROJECTS.toString());
		        intent.putExtra(ItemListView.KEY__PROJECT, (Serializable)project);
		        break;
		        
	        case LAST_VIEWED_QUERY:
	        case SPECIFIC_QUERY:
	        	int queryId = 0;
	        	if (initialView == InitialView.LAST_VIEWED_QUERY)
	        		queryId = mStorage.getLastViewedQuery();
	        	else if (initialView == InitialView.SPECIFIC_QUERY)
	        		queryId = mStorage.getInitialQuery();
	        	
	        	Query query = mStorage.getQuery(queryId);
	        	
	        	if (query == null) {
	        		// Special case - query was deleted - select the first available query
	        		ArrayList<Query> queries = mStorage.getQueries();
	        		
	        		if (queries.size() == 0) {
	        			// No queries available at all - revert to filter by projects
	        			mStorage.setInitialView(InitialView.FILTER_BY_PROJECTS);
	        			
				        intent = new Intent(getBaseContext(), ProjectListView.class);
				        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.FILTER_BY_PROJECTS.toString());
				        break;
	        		}
         		
	        		query = queries.get(0);
	        		
	        		// Fix this issue in the storage
	        		if (initialView == InitialView.LAST_VIEWED_QUERY)
	        			mStorage.setLastViewedQuery(query.id);
	        		else if (initialView == InitialView.SPECIFIC_QUERY)
	        			mStorage.setInitialQuery(query.id);
	        	}
         	
	        	
		        intent = new Intent(getBaseContext(), ItemListView.class);
		        intent.putExtra(ItemListView.KEY__VIEW_MODE, ItemListView.ItemViewMode.FILTER_BY_QUERIES.toString());
		        intent.putExtra(ItemListView.KEY__QUERY, (Serializable)query);
		        break;
	        
	        }
	        
	        startActivity(intent);
    	}
   
        // Close this view, since it shouldn't be visible to the user (he shouldn't be able to
        // press back and reach this activity)
        finish();
    }

}
