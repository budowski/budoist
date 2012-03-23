package budo.budoist.views;

import java.io.Serializable;
import java.util.ArrayList;
import pl.polidea.treeview.InMemoryTreeStateManager;
import budo.budoist.R;
import budo.budoist.Bootloader;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Query;
import budo.budoist.models.User;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.services.TodoistServerException;
import budo.budoist.services.TodoistOfflineStorage.InitialView;
import budo.budoist.views.LabelListView.LabelViewMode;
import budo.budoist.views.ProjectListView.ProjectViewMode;
import budo.budoist.views.adapters.QueryTreeItemAdapter;
import pl.polidea.treeview.TreeBuilder;
import pl.polidea.treeview.TreeNodeInfo;
import pl.polidea.treeview.TreeStateManager;
import pl.polidea.treeview.TreeViewList;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Query List View
 * @author Yaron Budowski
 *
 */
public class QueryListView extends Activity implements OnItemClickListener {
    private static final String TAG = QueryListView.class.getSimpleName();
    private TreeViewList mTreeView;

    private static final int LEVEL_NUMBER = 4;
    private TreeStateManager<Query> mTreeManager = null;
    private QueryTreeItemAdapter mQueryAdapter;
    private boolean mCollapsible;
    
    private TodoistApplication mApplication;
    private TodoistClient mClient;
	private TodoistOfflineStorage mStorage;
	private User mUser;
	
	private RelativeLayout mTopToolbar;
	private LinearLayout mProjectsToolbarButton, mLabelsToolbarButton, mQueriesToolbarButton;
	private TextView mProjectsToolbarText, mLabelsToolbarText, mQueriesToolbarText;
	private ImageView mAddItemToolbarButton;
   
	// What's the current view mode for the query list
	public enum QueryViewMode {
		FILTER_BY_QUERIES, // Filter the items by a specific query
		SELECT_INITIAL_QUERY // A dialog used to select a query as the initial view filter
	}
	
	public static final String KEY__VIEW_MODE = "ViewMode";
	public static final String KEY__QUERY = "Query";
	
	private QueryViewMode mViewMode = QueryViewMode.FILTER_BY_QUERIES;

    public TodoistClient getClient() {
    	return mClient;
    }
    
 	private void loadTopToolbar() {
 		mTopToolbar = (RelativeLayout)findViewById(R.id.top_toolbar);
 		
 		if (mViewMode == QueryViewMode.SELECT_INITIAL_QUERY) {
 			mTopToolbar.setVisibility(View.GONE);
 		}
 		
		mProjectsToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_projects);
		mLabelsToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_labels);
		mQueriesToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_queries);
		mAddItemToolbarButton = (ImageView)findViewById(R.id.top_toolbar_add_item);
		
		mProjectsToolbarText = (TextView)findViewById(R.id.top_toolbar_projects_text);
		mLabelsToolbarText = (TextView)findViewById(R.id.top_toolbar_labels_text);
		mQueriesToolbarText = (TextView)findViewById(R.id.top_toolbar_queries_text);
		
		mQueriesToolbarText.setTypeface(null, Typeface.BOLD);
		ImageView img = (ImageView)findViewById(R.id.top_toolbar_queries_image);
		img.setImageResource(R.drawable.queries_black);
		
		mAddItemToolbarButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	            Intent intent = new Intent(getBaseContext(), EditQueryView.class);
	            intent.putExtra(EditQueryView.KEY__QUERY, (Serializable)null);
	            startActivityForResult(intent, Bootloader.REQUEST_CODE__EDIT_QUERY);
			}
		});
	
		
		mProjectsToolbarButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
	        	// Filter by projects
		        Intent intent = new Intent(getBaseContext(), ProjectListView.class);
		        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.FILTER_BY_PROJECTS.toString());
		        startActivity(intent);
		        
		        finish();
			}
		});
		
		mLabelsToolbarButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Filter by labels
				Intent intent = new Intent(getBaseContext(), LabelListView.class);
				intent.putExtra(LabelListView.KEY__VIEW_MODE,
						LabelViewMode.FILTER_BY_LABELS.toString());
				startActivity(intent);
				
				finish();
			}
		});
		
		mLabelsToolbarButton.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				ImageView img = (ImageView)findViewById(R.id.top_toolbar_labels_image);
				if (event.getAction() == MotionEvent.ACTION_DOWN)
					img.setImageResource(R.drawable.labels_black);
				else if (event.getAction() == MotionEvent.ACTION_UP)
					img.setImageResource(R.drawable.labels);
			
				return false;
			}
		});

		mProjectsToolbarButton.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				ImageView img = (ImageView)findViewById(R.id.top_toolbar_projects_image);
				if (event.getAction() == MotionEvent.ACTION_DOWN)
					img.setImageResource(R.drawable.projects_black);
				else if (event.getAction() == MotionEvent.ACTION_UP)
					img.setImageResource(R.drawable.projects);
			
				return false;
			}
		});

	}
    
    /**
     * Converts a query list into a tree item view
     * @param queries
     */
    private void buildQueryList(ArrayList<Query> queries) {
    	mTreeManager.clear();
    	TreeBuilder<Query> treeBuilder = new TreeBuilder<Query>(mTreeManager);
    	
    	// Add queries to tree sequently
    	for (int i = 0; i < queries.size(); i++) {
    		treeBuilder.sequentiallyAddNextNode(queries.get(i), 0);
    	}
    }
    
 	@Override
	public void onDestroy() {
		super.onDestroy();

		if (mViewMode != QueryViewMode.FILTER_BY_QUERIES) {
			// Selecting a query action was canceled
			setResult(RESULT_CANCELED);
		}
	}
   
 	@Override
	public void onResume() {
		super.onResume();
		
		if (mViewMode == QueryViewMode.FILTER_BY_QUERIES) {
	        mStorage.setLastViewedFilter(InitialView.FILTER_BY_QUERIES);
		}
	}
   
    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean newCollapsible;
        
        overridePendingTransition(0, 0);
        
        Bundle extras = getIntent().getExtras();
		mViewMode = QueryViewMode.valueOf(extras.getString(KEY__VIEW_MODE));
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mStorage = mClient.getStorage();
        mUser = mClient.getUser();
        
        mStorage.setLastViewedFilter(InitialView.FILTER_BY_QUERIES);
        
        ArrayList<Query> queries = mClient.getQueries();
        
        mTreeManager = new InMemoryTreeStateManager<Query>();
        buildQueryList(queries);
        newCollapsible = true;
        
        setContentView(R.layout.queries_list);
        mTreeView = (TreeViewList) findViewById(R.id.queries_tree_view);
        mTreeView.setItemsCanFocus(false);
        
        loadTopToolbar();
        
		if (mViewMode == QueryViewMode.FILTER_BY_QUERIES) {
			mStorage.setLastViewedFilter(InitialView.FILTER_BY_QUERIES);
			this.setTitle("Queries");
		} else if (mViewMode == QueryViewMode.SELECT_INITIAL_QUERY) {
			this.setTitle("Select Initial Query to Filter by");
		}

       
        mQueryAdapter = new QueryTreeItemAdapter(this, mTreeManager, LEVEL_NUMBER);
        mTreeView.setAdapter(mQueryAdapter);
        setCollapsible(newCollapsible);
        registerForContextMenu(mTreeView);

    	mTreeView.setOnItemClickListener(this);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("treeManager", mTreeManager);
        outState.putBoolean("collapsible", this.mCollapsible);
        super.onSaveInstanceState(outState);
    }

    protected final void setCollapsible(boolean newCollapsible) {
        this.mCollapsible = newCollapsible;
        mTreeView.setCollapsible(this.mCollapsible);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
    	Intent intent;
    	
        switch (item.getItemId()) {
        case R.id.settings:
 	        intent = new Intent(getBaseContext(), SettingsView.class);
	        startActivityForResult(intent, Bootloader.REQUEST_CODE__SETTINGS);
       	
        	break;
        	
  		case R.id.sync_now:
			LoginView.syncNow(QueryListView.this, mClient, mUser.email, mUser.password, null);
			
			break;
       	
        default:
            return false;
        }
        return true;
    }

    /**
     * Called when the edit/add query or settings activity returns
     */
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Bootloader.REQUEST_CODE__SETTINGS) {
			if (resultCode == RESULT_OK) {
				// Refresh queries - happens when user changes text size, etc
    			(new Thread(new Runnable() {
					@Override
					public void run() {
						runOnUiThread(new Runnable() {
							public void run() {	
				                buildQueryList(mClient.getQueries());
							}
						});
		 
					}
				})).start();
 			
			}
		} else if (requestCode == Bootloader.REQUEST_CODE__EDIT_QUERY) {
    		if (resultCode == RESULT_OK) {
    			Query query = (Query)data.getExtras().get(EditQueryView.KEY__QUERY);
    			
    			if (query.id == 0) {
    				// Add query
    				mClient.addQuery(query);
    				
    			} else {
    				// Update query
					mClient.updateQuery(query);
    			}
    			
    			// Refresh visual label list
                buildQueryList(mClient.getQueries());
    		}
    	}
    }


    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
        final AdapterContextMenuInfo adapterInfo = (AdapterContextMenuInfo) menuInfo;
        final Query query = (Query)adapterInfo.targetView.getTag();
        final TreeNodeInfo<Query> info = mTreeManager.getNodeInfo(query);
        final MenuInflater menuInflater = getMenuInflater();
        
    	menuInflater.inflate(R.menu.query_context_menu, menu);
    	menu.findItem(R.id.context_menu_edit_query).setVisible(true);
    	menu.findItem(R.id.context_menu_delete_query).setVisible(true);
    
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
    	
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
                .getMenuInfo();
        final Query query = (Query)info.targetView.getTag();
        
        switch (item.getItemId()) {
        case R.id.context_menu_edit_query:
            Intent intent = new Intent(getBaseContext(), EditQueryView.class);
            intent.putExtra(EditQueryView.KEY__QUERY, query);
            startActivityForResult(intent, Bootloader.REQUEST_CODE__EDIT_QUERY);
 
        	return true;
        	
        case R.id.context_menu_delete_query:
        	
        	// User chose to delete a query - Prepare a yes/no dialog
        	
        	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        	    @Override
        	    public void onClick(DialogInterface dialog, int which) {
        	        switch (which){
        	        case DialogInterface.BUTTON_POSITIVE:
						mClient.deleteQuery(query);
						
        	        	mTreeManager.removeNodeRecursively(query);
        	            break;

        	        case DialogInterface.BUTTON_NEGATIVE:
        	            // No button clicked - do nothing
        	            break;
        	        }
        	    }
        	};

        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	builder.setMessage(String.format("Delete query '%s'?", query.name));
        	builder.setPositiveButton("Yes", dialogClickListener)
        	    .setNegativeButton("No", dialogClickListener);
        	
        	builder.show();
        	
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }
    

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		Query query = (Query)arg1.getTag();
		
		if (mViewMode == QueryViewMode.FILTER_BY_QUERIES) {
			// Show items list activity filtered by selected query
	        Intent intent = new Intent(getBaseContext(), ItemListView.class);
	        intent.putExtra(ItemListView.KEY__VIEW_MODE, ItemListView.ItemViewMode.FILTER_BY_QUERIES.toString());
	        intent.putExtra(ItemListView.KEY__QUERY, (Serializable)query);
	        startActivity(intent);
	        
	        mStorage.setLastViewedQuery(query.id);
	        
	        finish();
	        
		} else {
			// Return selected query and close activity
			Intent intent = new Intent();
			intent.putExtra(KEY__QUERY, query);
			setResult(RESULT_OK, intent);
			finish();			
		}
	}


 	@Override
	public void onBackPressed() {
		super.onBackPressed();
		if ((mStorage.getSyncOnExit()) && (!mClient.isCurrentlySyncing())) {
			// Sync on exit (using another thread)
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
	}

   
}
