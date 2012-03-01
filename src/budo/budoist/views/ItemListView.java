package budo.budoist.views;

import java.io.Serializable;
import java.util.ArrayList;
import pl.polidea.treeview.InMemoryTreeStateManager;
import budo.budoist.R;
import budo.budoist.Bootloader;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Item;
import budo.budoist.models.Label;
import budo.budoist.models.Project;
import budo.budoist.models.Query;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.models.User;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.services.TodoistOfflineStorage.ItemSortMode;
import budo.budoist.views.LabelListView.LabelViewMode;
import budo.budoist.views.ProjectListView.ProjectViewMode;
import budo.budoist.views.QueryListView.QueryViewMode;
import budo.budoist.views.adapters.ItemTreeItemAdapter;
import budo.budoist.views.adapters.ItemTreeItemAdapter.IOnItemCompleted;
import budo.budoist.views.adapters.ItemTreeItemAdapter.IOnItemNotes;
import pl.polidea.treeview.TreeBuilder;
import pl.polidea.treeview.TreeStateManager;
import pl.polidea.treeview.TreeViewList;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

/**
 * Label List View
 * @author Yaron Budowski
 *
 */
public class ItemListView extends Activity implements IOnItemCompleted, IOnItemNotes {
    private static final String TAG = ItemListView.class.getSimpleName();
    private TreeViewList mTreeView;
    
	private LinearLayout mTopToolbar;
	private LinearLayout mProjectsToolbarButton, mLabelsToolbarButton, mQueriesToolbarButton;
	private TextView mProjectsToolbarText, mLabelsToolbarText, mQueriesToolbarText;

    private static final int LEVEL_NUMBER = 4;
    private TreeStateManager<Item> mTreeManager = null;
    private ItemTreeItemAdapter mItemAdapter;
    private boolean mCollapsible;
    
    private Label mFilterLabel = null;
    private Project mFilterProject = null;
    private Query mFilterQuery = null;
    
    private TodoistApplication mApplication;
    private TodoistClient mClient;
    private TodoistOfflineStorage mStorage;

    // A "Loading.." dialog used for long operations (such as delete/edit/add item)
	private ProgressDialog mLoadingDialog;
	
	private Context mContext;
	private boolean mFinishedLoadingItems = true;
	
 
    
    // What's the current view mode for the item list
    public enum ItemViewMode {
    	FILTER_BY_PROJECTS,
    	FILTER_BY_LABELS,
    	FILTER_BY_QUERIES
    }
    
  
    public static final String KEY__VIEW_MODE = "ViewMode";
    public static final String KEY__PROJECT = "Project";
    public static final String KEY__LABEL = "Label";
    public static final String KEY__QUERY = "Query";
    
    static final int MAX_ITEM_NAME_IN_DELETE_DIALOG = 20;
    
    
    private ItemViewMode mViewMode = ItemViewMode.FILTER_BY_PROJECTS;
    private ItemSortMode mSortMode = ItemSortMode.ORIGINAL_ORDER;
    
	private Item mItemEdited; // Last item edited
	private User mUser;
	private Menu mMenu;
    
    public ItemViewMode getViewMode() {
    	return mViewMode;
    }
    
    public ItemSortMode getSortMode() {
    	return mSortMode;
    }
    
    
  	private void loadTopToolbar() {
 		mTopToolbar = (LinearLayout)findViewById(R.id.top_toolbar);
 		
		mProjectsToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_projects);
		mLabelsToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_labels);
		mQueriesToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_queries);
		
		mProjectsToolbarText = (TextView)findViewById(R.id.top_toolbar_projects_text);
		mLabelsToolbarText = (TextView)findViewById(R.id.top_toolbar_labels_text);
		mQueriesToolbarText = (TextView)findViewById(R.id.top_toolbar_queries_text);
		
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
     * Loads the item list, according to the view mode and sort type
     * @return
     */
    private ArrayList<Item> getItemList() {
    	ArrayList<Item> items = null;
    	
    	mFinishedLoadingItems = false;
    	
         if (mViewMode == ItemViewMode.FILTER_BY_LABELS) {
        	items = mClient.getItemsByLabel(mFilterLabel, mSortMode);
        } else if (mViewMode == ItemViewMode.FILTER_BY_PROJECTS) {
        	items = mClient.getItemsByProject(mFilterProject, mSortMode);
        } else if (mViewMode == ItemViewMode.FILTER_BY_QUERIES) {
        	items = mClient.getItemsByQuery(mFilterQuery);
        }
    	
    	mFinishedLoadingItems = true;
    	
    	return items;
    }
    
    /**
     * Converts an items list into a tree item view (as set by itemOrder and indentLevel fields, or
     * in case we're sorting by due date - by the dueDate fields of the items)
     */
    private void buildItemList(ArrayList<Item> items) {
    	mTreeManager.clear();
    	TreeBuilder<Item> treeBuilder = new TreeBuilder<Item>(mTreeManager);
   	
    	// Add items to tree sequently, adding more indent levels as needed
    	for (int i = 0; i < items.size(); i++) {
	   		int indent;
    		if ((mSortMode == ItemSortMode.SORT_BY_DUE_DATE) || (mViewMode == ItemViewMode.FILTER_BY_LABELS) || (mViewMode == ItemViewMode.FILTER_BY_QUERIES)) {
    			// When filtering by a label or when sorting by due date, no indentation is used
    			indent = 0;
    		} else {
        		// Todoist indentLevel starts from 1 (and not zero as the tree view expects)
    			indent = items.get(i).indentLevel - 1;
    		}

    		treeBuilder.sequentiallyAddNextNode(items.get(i), indent);
    	}
    }
    
    public TodoistClient getClient() {
    	return mClient;
    }
    
    private void setSortMode(ItemSortMode sortMode) {
    	mSortMode = sortMode;
    	
    	// Remember last used sort mode
    	mStorage.setLastUsedItemsSortMode(mSortMode);
    	
    	if (mMenu != null) {
	    	if (mSortMode == ItemSortMode.ORIGINAL_ORDER) {
		        mMenu.findItem(R.id.sort_items_due_date).setVisible(true);
		        mMenu.findItem(R.id.sort_items_original).setVisible(false);
	    	} else if (mSortMode == ItemSortMode.SORT_BY_DUE_DATE) {
		        mMenu.findItem(R.id.sort_items_due_date).setVisible(false);
		        mMenu.findItem(R.id.sort_items_original).setVisible(true);
	    	}
    	}
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        overridePendingTransition(0, 0);
        
        mContext = this;
        
        Bundle extras = getIntent().getExtras();
       
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mStorage = mClient.getStorage();
        mUser = mClient.getUser();
        
        mViewMode = ItemViewMode.valueOf(extras.getString(KEY__VIEW_MODE));
        
        ItemSortMode initialSortMode = mStorage.getInitialItemsSortMode();
        ItemSortMode currentSortMode = ItemSortMode.ORIGINAL_ORDER;
        
        if (initialSortMode == ItemSortMode.LAST_USED_ORDER) {
        	// Simply use last used sort mode
	        currentSortMode = mStorage.getLastUsedItemsSortMode();
        } else if (initialSortMode == ItemSortMode.ORIGINAL_ORDER) {
        	currentSortMode = ItemSortMode.ORIGINAL_ORDER;
        } else if (initialSortMode == ItemSortMode.SORT_BY_DUE_DATE) {
        	currentSortMode = ItemSortMode.SORT_BY_DUE_DATE;
        }
        
        setSortMode(currentSortMode);
        
        if (mViewMode == ItemViewMode.FILTER_BY_LABELS) {
        	mFilterLabel = (Label)extras.get(KEY__LABEL);
        	this.setTitle("Label: " + mFilterLabel.name);
        	
        } else if (mViewMode == ItemViewMode.FILTER_BY_PROJECTS) {
        	mFilterProject = (Project)extras.get(KEY__PROJECT);
        	this.setTitle("Project: " + TodoistTextFormatter.formatText(mFilterProject.getName()).toString());
        	
        } else if (mViewMode == ItemViewMode.FILTER_BY_QUERIES) {
        	mFilterQuery = (Query)extras.get(KEY__QUERY);
        	this.setTitle("Query: " + mFilterQuery.name);
        }
        
		mLoadingDialog = ProgressDialog.show(mContext, "", "Loading items...");
		
		// Run this logic on a separate thread in order for the loading dialog to actually show
		(new Thread(new Runnable() {
			@Override
			public void run() {
				final ArrayList<Item> items;
				final ArrayList<Label> labels;
				
		        if ((savedInstanceState == null) || (savedInstanceState.getSerializable("treeManager") == null)) {
		        	items = getItemList();
		        } else {
		        	items = null;
		        }
		        
		        labels = mClient.getLabels();

				runOnUiThread(new Runnable() {
					public void run() {	
				        if ((savedInstanceState == null) || (savedInstanceState.getSerializable("treeManager") == null)) {
				            mTreeManager = new InMemoryTreeStateManager<Item>();
				            buildItemList(items);
				        } else {
				            mTreeManager = (TreeStateManager<Item>)savedInstanceState.getSerializable("treeManager");
				        }
				        
				        mItemAdapter = new ItemTreeItemAdapter(ItemListView.this, ItemListView.this, ItemListView.this, labels, mTreeManager, LEVEL_NUMBER);
						
				        setContentView(R.layout.items_list);
				        
				        loadTopToolbar();
				        
				        mTreeView = (TreeViewList) findViewById(R.id.items_tree_view);
				        
				        mTreeView.setLongClickable(true);
				        mTreeView.setItemsCanFocus(false);
				        
				        mTreeView.setAdapter(mItemAdapter);
				        setCollapsible(true);
				        registerForContextMenu(mTreeView);
				        
						if (mLoadingDialog.isShowing())
							mLoadingDialog.dismiss();
					}
				});
			}
		})).start();

        /*
        if (mViewMode == ItemSortMode.FILTER_BY_LABELS) {
        	mTreeView.setOnItemClickListener(this);
        }
        */
    }
    
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
		try {
			// In case the activity was paused in the middle of refreshing/loading items - we need
			// time for the getItemList function to complete
			while (!mFinishedLoadingItems) {
					Thread.sleep(50);
			}
		} catch (InterruptedException e) {
		}

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
        
        this.mMenu = menu;
       
        setSortMode(mSortMode);
        
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
    	
        menu.findItem(R.id.add_item_menu_item).setVisible(true);
        
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
			LoginView.syncNow(ItemListView.this, mClient, mUser.email, mUser.password, null);
			
			break;
			
	    case R.id.sort_items_due_date:
			mLoadingDialog = ProgressDialog.show(mContext, "", "Sorting items...");
		
			// Run this logic on a separate thread in order for the loading dialog to actually show
			(new Thread(new Runnable() {
				@Override
				public void run() {
			    	setSortMode(ItemSortMode.SORT_BY_DUE_DATE);
			    	
			    	final ArrayList<Item> items = getItemList();
			    	
					runOnUiThread(new Runnable() {
						public void run() {	
			    			buildItemList(items); // Since this changes the UI
			    			
							if (mLoadingDialog.isShowing())
								mLoadingDialog.dismiss();
						}
					});
				}
			})).start();
			
	    	break;
	    	
	    case R.id.sort_items_original:
			mLoadingDialog = ProgressDialog.show(mContext, "", "Sorting items...");
		
			// Run this logic on a separate thread in order for the loading dialog to actually show
			(new Thread(new Runnable() {
				@Override
				public void run() {
			    	setSortMode(ItemSortMode.ORIGINAL_ORDER);
			    	
			    	final ArrayList<Item> items = getItemList();
			    	
					runOnUiThread(new Runnable() {
						public void run() {	
			    			buildItemList(items); // Since this changes the UI
			    			
							if (mLoadingDialog.isShowing())
								mLoadingDialog.dismiss();
						}
					});
				}
			})).start();
			
	    	break;
    	
	    case R.id.add_item_menu_item:
            intent = new Intent(getBaseContext(), EditItemView.class);
            intent.putExtra(EditItemView.KEY__ITEM, (Serializable)null);
            
            if (mViewMode == ItemViewMode.FILTER_BY_PROJECTS) {
	            intent.putExtra(EditItemView.KEY__PROJECT, mFilterProject); // So we'll set the initial project for the new item
            }
            startActivityForResult(intent, Bootloader.REQUEST_CODE__EDIT_ITEM);
            
        	break;
 	    default:
            return false;
        }
        
		return true;
        
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
    	
        final MenuInflater menuInflater = getMenuInflater();
        
        menuInflater.inflate(R.menu.item_context_menu, menu);
        
        if (mClient.isPremium()) {
	    	menu.findItem(R.id.context_menu_edit_notes).setVisible(true);
        } else {
	    	menu.findItem(R.id.context_menu_edit_notes).setVisible(false);
        }
        
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(final MenuItem menuItem) {
    	
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuItem
                .getMenuInfo();
        final Item item = (Item)info.targetView.getTag();
        
        Intent intent;
        
        switch (menuItem.getItemId()) {
        case R.id.context_menu_expand_all_items:
            mTreeManager.expandEverythingBelow(null);
            return true;
        case R.id.context_menu_collapse_all_items:
            mTreeManager.collapseChildren(null);
            return true;
            
        case R.id.context_menu_edit_notes:
			// Show the notes for the selected item
			mItemEdited = item;
	        intent = new Intent(getBaseContext(), NoteListView.class);
	        intent.putExtra(NoteListView.KEY__ITEM, item);
	        startActivityForResult(intent, Bootloader.REQUEST_CODE__EDIT_NOTES);
        	return true;

        case R.id.context_menu_edit_item:
            intent = new Intent(getBaseContext(), EditItemView.class);
            intent.putExtra(EditItemView.KEY__ITEM, item);
            mItemEdited = item;
            startActivityForResult(intent, Bootloader.REQUEST_CODE__EDIT_ITEM);
            
        	return true;
        	
        case R.id.context_menu_delete_item:
        	
        	// User chose to delete an item - Prepare a yes/no dialog
        	
        	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
        	    @Override
        	    public void onClick(DialogInterface dialog, int which) {
        	        switch (which){
        	        case DialogInterface.BUTTON_POSITIVE:
				  		// Don't let the user switch between screen orientations (causes the adding/updating process to restart)
						setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
						
		    			mLoadingDialog = ProgressDialog.show(mContext, "", "Deleting item and sub-items, please wait...");
		    			
			   			// Run this logic on a separate thread in order for the loading dialog to actually show
		    			(new Thread(new Runnable() {
							@Override
							public void run() {
		        	        	deleteItemsRecursively(item);
		        	        	
		        	        	final ArrayList<Item> items = getItemList();
		        	        	
    							runOnUiThread(new Runnable() {
									public void run() {	
						    			buildItemList(items); // Since this changes the UI
						    			
										if (mLoadingDialog.isShowing())
											mLoadingDialog.dismiss();
										
										ItemListView.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
									}
    							});
							}
		    			})).start();
		    			
        	            break;

        	        case DialogInterface.BUTTON_NEGATIVE:
        	            // No button clicked - do nothing
        	            break;
        	        }
        	    }
        	};

        	String shortContent = TodoistTextFormatter.formatText(item.getContent()).toString();
        	if (shortContent.length() > MAX_ITEM_NAME_IN_DELETE_DIALOG) shortContent = shortContent.subSequence(0, MAX_ITEM_NAME_IN_DELETE_DIALOG) + "...";
        	
        	AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	builder.setMessage(String.format("Delete item '%s'?\nThis will delete all sub-items as well.", shortContent));
        	builder.setPositiveButton("Yes", dialogClickListener)
        	    .setNegativeButton("No", dialogClickListener);
        	
        	builder.show();
        	
            return true;
        default:
            return super.onContextItemSelected(menuItem);
        }
    }
    
    private void deleteItemsRecursively(Item item) {
    	// Delete all of the item's children
        for (Item child : mTreeManager.getChildren(item)) {
        	deleteItemsRecursively(child);
        }
        
    	// Delete current item
    	mClient.deleteItem(item, true);
    }
    
	@Override
	protected void onPause() {
		super.onPause();
		
		if ((mLoadingDialog != null) && (mLoadingDialog.isShowing()))
			mLoadingDialog.dismiss();
	}
	
    /**
     * Called when the edit/add item or settings activity returns
     */
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Bootloader.REQUEST_CODE__EDIT_NOTES) {
			if (resultCode == RESULT_OK) {
				// See if the notes for the item were modified
    			boolean notesModified = (Boolean)data.getExtras().get(NoteListView.KEY__NOTES_MODIFIED);
    			
    			if (notesModified) {
    				// Refresh the item list
    				// TODO: Refresh only the item modified?
    				
	    			mLoadingDialog = ProgressDialog.show(this, "", "Loading items...");
	    			
	    			// Run this logic on a separate thread in order for the loading dialog to actually show
	    			(new Thread(new Runnable() {
						@Override
						public void run() {
	        	        	final ArrayList<Item> items = getItemList();
			    			
							runOnUiThread(new Runnable() {
								public void run() {	
					    			buildItemList(items); // Since this changes the UI
					    			
									if (mLoadingDialog.isShowing())
										mLoadingDialog.dismiss();
								}
							});
			 
						}
					})).start();
	    				
    			}
			}
		} else if (requestCode == Bootloader.REQUEST_CODE__SETTINGS) {
			if (resultCode == RESULT_OK) {
				// Refresh items - happens when user changes text size, etc
    			mLoadingDialog = ProgressDialog.show(this, "", "Loading items...");
    			
    			// Run this logic on a separate thread in order for the loading dialog to actually show
    			(new Thread(new Runnable() {
					@Override
					public void run() {
        	        	final ArrayList<Item> items = getItemList();
		    			
						runOnUiThread(new Runnable() {
							public void run() {	
				    			buildItemList(items); // Since this changes the UI
				    			
								if (mLoadingDialog.isShowing())
									mLoadingDialog.dismiss();
							}
						});
		 
					}
				})).start();
 			
			}
		} else if (requestCode == Bootloader.REQUEST_CODE__EDIT_ITEM) {
    		if (resultCode == RESULT_OK) {
    			// TODO: Refresh item's note count
    			// Problem: this is time-consuming and there can't seem to be a way to refresh
    			// a single item
    			final Item item = (Item)data.getExtras().get(EditItemView.KEY__ITEM);
    			
		  		// Don't let the user switch between screen orientations (causes the adding/updating process to restart)
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
				
    			if (item.id == 0) {
	    			mLoadingDialog = ProgressDialog.show(this, "", "Adding item, please wait...");
    			} else {
	    			mLoadingDialog = ProgressDialog.show(this, "", "Updating item, please wait...");
    			}
    			
    			// Run this logic on a separate thread in order for the loading dialog to actually show
    			(new Thread(new Runnable() {
					@Override
					public void run() {
		    			if (item.id == 0) {
		    				// Add item
		    				item.calculateFirstDueDate(mUser.dateFormat);
		    				mClient.addItem(item);
		    			} else {
		    				// Update item
		    				if (!mItemEdited.dateString.equals(item.dateString)) {
		    					// Date string was modified - re-calculate the due date
		    					item.calculateFirstDueDate(mUser.dateFormat);
		    				}

		    				mClient.updateItem(item, mItemEdited);
		    			}
		    			
		    			// Refresh the labels in case the user modified them (e.g. added a new label,
		    			// changed label color, renamed it, etc)
		    			mItemAdapter.setLabels(mClient.getLabels());
		    			
        	        	final ArrayList<Item> items = getItemList();
		    			
						runOnUiThread(new Runnable() {
							public void run() {	
				    			buildItemList(items); // Since this changes the UI
				    			
								if (mLoadingDialog.isShowing())
									mLoadingDialog.dismiss();
								
								ItemListView.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
							}
						});
		 
					}
				})).start();
   		}
    	}
    }

	@Override
	public void onItemCompleted(Item item, boolean value) {
		if (value) {
			// Complete (check-on) all child items as well
			completeItemsRecursively(item);
			
			if (mTreeManager.getChildren(item).size() > 0) {
				// Only refresh if the item has children that need refreshing (since they've been
				// marked as completed as well)
				mTreeManager.refresh();
			}
			
		} else {
			// Just un-complete this item (and not its children - this is the current
			// Todoist website behaviour)
			Item originalItem = (Item)item.clone();
			item.completed = false;
	    	mClient.updateItem(item, originalItem);
		}
	}

    private void completeItemsRecursively(Item item) {
    	// Complete all of the item's children
        for (Item child : mTreeManager.getChildren(item)) {
        	completeItemsRecursively(child);
        }
        
    	// Complete (update) current item
		Item originalItem = (Item)item.clone();
        item.completed = true;
    	mClient.updateItem(item, originalItem);
    }
    
	@Override
	public void onItemNotes(Item item) {
		// Show the notes for the selected item
		mItemEdited = item;
		
        Intent intent = new Intent(getBaseContext(), NoteListView.class);
        intent.putExtra(NoteListView.KEY__ITEM, item);
        startActivityForResult(intent, Bootloader.REQUEST_CODE__EDIT_NOTES);
        
	}
	
	@Override
	public void onBackPressed () {
		super.onBackPressed();
		
		Intent intent = null;
		
		// Return to either projects, labels or queries view
		
		if (mViewMode == ItemViewMode.FILTER_BY_LABELS) {
		    intent = new Intent(this, LabelListView.class);
		    intent.putExtra(LabelListView.KEY__VIEW_MODE, LabelViewMode.FILTER_BY_LABELS.toString());
		    
		} else if (mViewMode == ItemViewMode.FILTER_BY_PROJECTS) {
		    intent = new Intent(this, ProjectListView.class);
		    intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.FILTER_BY_PROJECTS.toString());
		    
		} else if (mViewMode == ItemViewMode.FILTER_BY_QUERIES) {
		    intent = new Intent(this, QueryListView.class);
			intent.putExtra(QueryListView.KEY__VIEW_MODE, QueryViewMode.FILTER_BY_QUERIES.toString());
			
		}
		
        startActivity(intent);
	}
	
}
