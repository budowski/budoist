package budo.budoist.views;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import pl.polidea.treeview.InMemoryTreeStateManager;
import budo.budoist.R;
import budo.budoist.Bootloader;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Item;
import budo.budoist.models.Label;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.models.User;
import budo.budoist.receivers.AppService;
import budo.budoist.services.PremiumAccountException;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.services.TodoistServerException;
import budo.budoist.services.TodoistOfflineStorage.InitialView;
import budo.budoist.views.ProjectListView.ProjectViewMode;
import budo.budoist.views.QueryListView.QueryViewMode;
import budo.budoist.views.adapters.LabelTreeItemAdapter;
import pl.polidea.treeview.TreeBuilder;
import pl.polidea.treeview.TreeNodeInfo;
import pl.polidea.treeview.TreeStateManager;
import pl.polidea.treeview.TreeViewList;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Label List View
 * @author Yaron Budowski
 *
 */
public class LabelListView extends Activity implements OnItemClickListener, OnClickListener {
    private static final String TAG = LabelListView.class.getSimpleName();
    private TreeViewList mTreeView;
    private Button mOkButton;
    private Button mCancelButton;
    private LinearLayout mButtonsToolbar;
    
    private RelativeLayout mTopToolbar;
	private LinearLayout mProjectsToolbarButton, mLabelsToolbarButton, mQueriesToolbarButton;
	private TextView mProjectsToolbarText, mLabelsToolbarText, mQueriesToolbarText;
	private ImageView mAddItemToolbarButton;

    private static final int LEVEL_NUMBER = 4;
    private TreeStateManager<Label> mTreeManager = null;
    private LabelTreeItemAdapter mLabelAdapter;
    private boolean mCollapsible;
    
    private TodoistApplication mApplication;
    private TodoistClient mClient;
	private TodoistOfflineStorage mStorage;
	private User mUser;
    
    // What's the current view mode for the label list
    public enum LabelViewMode {
    	FILTER_BY_LABELS, // Filter the items by a specific label
    	SELECT_LABELS, // A dialog used to select which labels an item should have
		SELECT_INITIAL_LABEL // A dialog used to select a label as the initial view filter
    }
    
    public static final String KEY__VIEW_MODE = "ViewMode";
    public static final String KEY__LABEL = "Label";
    public static final String KEY__PROJECT = "Project";
    public static final String KEY__ITEM = "Item";
    
    private Item mItem;
    
    private LabelViewMode mViewMode = LabelViewMode.FILTER_BY_LABELS;
    
    private HashSet<Integer> mSelectedLabelIds;
	private Label mLabelEdited; // The current label being edited
	
	private static final int MAX_ITEM_NAME_IN_TITLE = 30;
	
	
	private class SyncReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			runOnUiThread(new Runnable() {
				public void run() {
					// Refresh visual labels list
					buildLabelList(mClient.getLabels());
				}
			});

		}
	}
	
	private SyncReceiver mSyncReceiver = null;
	private boolean mIsSyncReceiverRegistered = false;
	
	@Override
	protected void onPause() {
		super.onPause();
		
		if (mIsSyncReceiverRegistered) {
			unregisterReceiver(mSyncReceiver);
			mIsSyncReceiverRegistered = false;
		}

	}

    
    public LabelViewMode getViewMode() {
    	return mViewMode;
    }
    
    public TodoistClient getClient() {
    	return mClient;
    }
    
 	private void loadTopToolbar() {
 		mTopToolbar = (RelativeLayout)findViewById(R.id.top_toolbar);
 		
 		if ((mViewMode == LabelViewMode.SELECT_INITIAL_LABEL) || (mViewMode == LabelViewMode.SELECT_LABELS)) {
 			mTopToolbar.setVisibility(View.GONE);
 		}
 		
		mProjectsToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_projects);
		mLabelsToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_labels);
		mQueriesToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_queries);
		mAddItemToolbarButton = (ImageView)findViewById(R.id.top_toolbar_add_item);
		
		mProjectsToolbarText = (TextView)findViewById(R.id.top_toolbar_projects_text);
		mLabelsToolbarText = (TextView)findViewById(R.id.top_toolbar_labels_text);
		mQueriesToolbarText = (TextView)findViewById(R.id.top_toolbar_queries_text);
		
		mLabelsToolbarText.setTypeface(null, Typeface.BOLD);
		ImageView img = (ImageView)findViewById(R.id.top_toolbar_labels_image);
		img.setImageResource(R.drawable.labels_black);
		
		mAddItemToolbarButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
	            Intent intent = new Intent(getBaseContext(), EditLabelView.class);
	            intent.putExtra(EditLabelView.KEY__LABEL, (Serializable)null);
	            startActivityForResult(intent, Bootloader.REQUEST_CODE__EDIT_LABEL);
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
		
		mQueriesToolbarButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Filter by queries
				Intent intent = new Intent(getBaseContext(), QueryListView.class);
				intent.putExtra(QueryListView.KEY__VIEW_MODE,
						QueryViewMode.FILTER_BY_QUERIES.toString());
				startActivity(intent);
				
				finish();
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

		mQueriesToolbarButton.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				ImageView img = (ImageView)findViewById(R.id.top_toolbar_queries_image);
				if (event.getAction() == MotionEvent.ACTION_DOWN)
					img.setImageResource(R.drawable.queries_black);
				else if (event.getAction() == MotionEvent.ACTION_UP)
					img.setImageResource(R.drawable.queries);
			
				return false;
			}
		});

	}
   
    /**
     * Converts a label list into a tree item view
     * @param labels
     */
    private void buildLabelList(ArrayList<Label> labels) {
    	mTreeManager.clear();
    	TreeBuilder<Label> treeBuilder = new TreeBuilder<Label>(mTreeManager);
    	
    	// Add labels to tree sequently
    	for (int i = 0; i < labels.size(); i++) {
    		treeBuilder.sequentiallyAddNextNode(labels.get(i), 0);
    	}
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	if (!mIsSyncReceiverRegistered) {
			registerReceiver(mSyncReceiver, new IntentFilter(AppService.SYNC_COMPLETED_ACTION));
			mIsSyncReceiverRegistered = true;
		}

		if (mViewMode == LabelViewMode.FILTER_BY_LABELS) {
	        mStorage.setLastViewedFilter(InitialView.FILTER_BY_LABELS);
		}
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean newCollapsible;
        
		mSyncReceiver = new SyncReceiver();
		
        overridePendingTransition(0, 0);
        
        Bundle extras = getIntent().getExtras();
        mViewMode = LabelViewMode.valueOf(extras.getString(KEY__VIEW_MODE));
        mItem = (Item)extras.getSerializable(KEY__ITEM);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mStorage = mClient.getStorage();
        mUser = mClient.getUser();
        
        ArrayList<Label> labels = mClient.getLabels();
        
        mTreeManager = new InMemoryTreeStateManager<Label>();
        buildLabelList(labels);
        newCollapsible = true;
    
        setContentView(R.layout.labels_list);
        mTreeView = (TreeViewList) findViewById(R.id.labels_tree_view);
        mTreeView.setItemsCanFocus(false);
        
        loadTopToolbar();
        
        mOkButton = (Button) findViewById(R.id.labels_button_ok);
        mCancelButton = (Button) findViewById(R.id.labels_button_cancel);
        mButtonsToolbar = (LinearLayout) findViewById(R.id.labels_list_button_toolbar);
        
        mOkButton.setOnClickListener(this);
        mCancelButton.setOnClickListener(this);

        if (mViewMode == LabelViewMode.FILTER_BY_LABELS) {
        	this.setTitle("Labels");
        	mButtonsToolbar.setVisibility(View.INVISIBLE);
	        mStorage.setLastViewedFilter(InitialView.FILTER_BY_LABELS);
        	
        } else if (mViewMode == LabelViewMode.SELECT_LABELS) {
        	String shortContent = TodoistTextFormatter.formatText(mItem.getContent()).toString();
        	
        	if ((shortContent == null) || (shortContent.length() == 0)) {
        		// No content set yet for item (happens when the item is new)
	        	this.setTitle(String.format("Select Labels for Item"));
        	} else {
	        	if (shortContent.length() > MAX_ITEM_NAME_IN_TITLE) shortContent = shortContent.subSequence(0, MAX_ITEM_NAME_IN_TITLE) + "...";
	        	this.setTitle(String.format("Select Labels for '%s'", shortContent));
        	}
        	mButtonsToolbar.setVisibility(View.VISIBLE);
        	
        } else if (mViewMode == LabelViewMode.SELECT_INITIAL_LABEL) {
			this.setTitle("Select Initial Label to Filter by");
        	mButtonsToolbar.setVisibility(View.INVISIBLE);
        }
        
        
        // Build the initially selected label list
        if ((mItem != null) && (mItem.labelIds != null)) {
        	mSelectedLabelIds = new HashSet<Integer>(mItem.labelIds);
        } else {
        	mSelectedLabelIds = new HashSet<Integer>();
        }
        
        mLabelAdapter = new LabelTreeItemAdapter(this, mSelectedLabelIds, mTreeManager, LEVEL_NUMBER);
        mTreeView.setAdapter(mLabelAdapter);
        setCollapsible(newCollapsible);
        registerForContextMenu(mTreeView);

        if ((mViewMode == LabelViewMode.FILTER_BY_LABELS) || (mViewMode == LabelViewMode.SELECT_INITIAL_LABEL)) {
        	mTreeView.setOnItemClickListener(this);
        }
    }
    
    
    @Override
    public void onDestroy() {
    	super.onDestroy();

		if (mViewMode != LabelViewMode.FILTER_BY_LABELS) {
			// Selecting a label action was canceled
			setResult(RESULT_CANCELED);
		}
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
			LoginView.syncNow(LabelListView.this, mClient, mUser.email, (mUser.googleLogin ? mUser.oauth2Token : mUser.password), mUser.googleLogin, new Runnable() {
				@Override
				public void run() {
					// Refresh label list
					buildLabelList(mClient.getLabels());
				}
			});
			
			break;
       	
        default:
            return false;
        }
        return true;
    }

    /**
     * Called when the edit/add label or settings activity returns
     */
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Bootloader.REQUEST_CODE__SETTINGS) {
			if (resultCode == RESULT_OK) {
				// Refresh labels - happens when user changes text size, etc
    			(new Thread(new Runnable() {
					@Override
					public void run() {
						runOnUiThread(new Runnable() {
							public void run() {	
				                buildLabelList(mClient.getLabels());
							}
						});
		 
					}
				})).start();
 			
			}
		} else if (requestCode == Bootloader.REQUEST_CODE__EDIT_LABEL) {
    		if (resultCode == RESULT_OK) {
    			Label label = (Label)data.getExtras().get(EditLabelView.KEY__LABEL);
    			
    			if (label.id == 0) {
    				// Add label
    				mClient.addLabel(label);
    				
                    if (mViewMode == LabelViewMode.SELECT_LABELS) {
                    	// Check-on the newly-added label
                    	mSelectedLabelIds.add(label.id);
                    }
 
    			} else {
    				// Update label
    				try {
						mClient.updateLabel(label);
					} catch (PremiumAccountException e) {
						// Should not happen - the context menu for "Edit label" does not appear is user is not premium
					}
    			}
    			
    			// Refresh visual label list
                buildLabelList(mClient.getLabels());
    		}
    	}
    }


    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
        final AdapterContextMenuInfo adapterInfo = (AdapterContextMenuInfo) menuInfo;
        final Label label = (Label)adapterInfo.targetView.getTag();
        final TreeNodeInfo<Label> info = mTreeManager.getNodeInfo(label);
        final MenuInflater menuInflater = getMenuInflater();
        
    	menuInflater.inflate(R.menu.label_context_menu, menu);
    	menu.findItem(R.id.context_menu_edit_label).setVisible(true);
    	menu.findItem(R.id.context_menu_delete_label).setVisible(true);
        
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
    	
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
                .getMenuInfo();
        final Label label = (Label)info.targetView.getTag();
        
        switch (item.getItemId()) {
        case R.id.context_menu_edit_label:
            if (!mClient.isPremium()) {
            	// Only premium users can edit and delete labels
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Sorry, only Todoist premium users can edit labels...");
                builder.setPositiveButton("OK", null);
                builder.show();

                return true;
            }
            
            Intent intent = new Intent(getBaseContext(), EditLabelView.class);
            intent.putExtra(EditLabelView.KEY__LABEL, label);
            mLabelEdited = label;
            startActivityForResult(intent, Bootloader.REQUEST_CODE__EDIT_LABEL);
 
        	return true;
        	
        case R.id.context_menu_delete_label:
             if (!mClient.isPremium()) {
            	// Only premium users can edit and delete labels
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Sorry, only Todoist premium users can delete labels...");
                builder.setPositiveButton("OK", null);
                builder.show();

                return true;
            }
        	
        	// User chose to delete a label - Prepare a yes/no dialog
        	
        	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        	    @Override
        	    public void onClick(DialogInterface dialog, int which) {
        	        switch (which){
        	        case DialogInterface.BUTTON_POSITIVE:
        	        	try {
							mClient.deleteLabel(label);
	                    	mSelectedLabelIds.remove(label.id);
						} catch (PremiumAccountException e) {
							// Should not happen (the context menu is not created if not premium)
						}
						
        	        	mTreeManager.removeNodeRecursively(label);
        	            break;

        	        case DialogInterface.BUTTON_NEGATIVE:
        	            // No button clicked - do nothing
        	            break;
        	        }
        	    }
        	};

        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	builder.setMessage(String.format("Delete label '%s'?", label.name));
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
		Label label = (Label)arg1.getTag();
		
		if (mViewMode == LabelViewMode.FILTER_BY_LABELS) {
			
			// Show items list activity filtered by selected label
	        Intent intent = new Intent(getBaseContext(), ItemListView.class);
	        intent.putExtra(ItemListView.KEY__VIEW_MODE, ItemListView.ItemViewMode.FILTER_BY_LABELS.toString());
	        intent.putExtra(ItemListView.KEY__LABEL, (Serializable)label);
	        startActivity(intent);
	        
	        mStorage.setLastViewedLabel(label.id);
	        
	        finish();
	        
		} else if (mViewMode == LabelViewMode.SELECT_INITIAL_LABEL) {
			// Return selected label and close activity
			Intent intent = new Intent();
			intent.putExtra(KEY__LABEL, label);
			setResult(RESULT_OK, intent);
			finish();
		}
	}


	@Override
	public void onClick(View arg0) {
		// OK/Cancel buttons were clicked (happens only in SELECT_LABELS view mode)
		
		if (arg0 == mOkButton) {
			// Return selected labels and close activity
			Intent intent = new Intent();
			mItem.labelIds = new ArrayList<Integer>(mSelectedLabelIds); // mItem.labelIds holds new label IDs
			intent.putExtra(KEY__ITEM, mItem);
			
			setResult(RESULT_OK, intent);
			finish();
			
		} else if (arg0 == mCancelButton) {
			setResult(RESULT_CANCELED);
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
					} catch (Exception e) {
						// Login/sync failed
						e.printStackTrace();
					}
				}
				})).start();
		}
	}

   
}
