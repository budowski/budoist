package budo.budoist.views;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import pl.polidea.treeview.InMemoryTreeStateManager;
import budo.budoist.R;
import budo.budoist.Bootloader;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Project;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.models.User;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.services.TodoistServerException;
import budo.budoist.services.TodoistOfflineStorage.InitialView;
import budo.budoist.views.LabelListView.LabelViewMode;
import budo.budoist.views.QueryListView.QueryViewMode;
import budo.budoist.views.adapters.ProjectTreeItemAdapter;
import pl.polidea.treeview.TreeBuilder;
import pl.polidea.treeview.TreeStateManager;
import pl.polidea.treeview.TreeViewList;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Project List View
 * 
 * @author Yaron Budowski
 * 
 */
public class ProjectListView extends Activity implements OnItemClickListener {
	private static final String TAG = ProjectListView.class.getSimpleName();
	private TreeViewList mTreeView;
	
	private RelativeLayout mTopToolbar;
	private LinearLayout mProjectsToolbarButton, mLabelsToolbarButton, mQueriesToolbarButton;
	private TextView mProjectsToolbarText, mLabelsToolbarText, mQueriesToolbarText;
	private ImageView mAddItemToolbarButton;

	private static final int LEVEL_NUMBER = 4;
	private TreeStateManager<Project> mTreeManager = null;
	private ProjectTreeItemAdapter mProjectAdapter;
	private boolean mCollapsible;

	private TodoistApplication mApplication;
	private TodoistClient mClient;
	private TodoistOfflineStorage mStorage;
	private User mUser;

	// What's the current view mode for the project list
	public enum ProjectViewMode {
		FILTER_BY_PROJECTS, // Filter the items by a specific project
		MOVE_TO_PROJECT, // A dialog used to select which project an item should be moved to
		SELECT_INITIAL_PROJECT, // A dialog used to select a project as the initial view filter
		SELECT_DEFAULT_PROJECT // A dialog used to select a project as the default project for new items
	}

	public static final String KEY__VIEW_MODE = "ViewMode";
	public static final String KEY__PROJECT = "Project";

	private ProjectViewMode mViewMode = ProjectViewMode.FILTER_BY_PROJECTS;

	private Project mProjectEdited; // Last project edited

	// A "Loading.." dialog used for long operations (such as delete/edit/add
	// project)
	private ProgressDialog mLoadingDialog;

	private Context mContext;
	
	
	public TodoistClient getClient() {
		return mClient;
	}

	/**
	 * Converts a project list into a tree item view (as set by itemOrder and
	 * indentLevel fields)
	 * 
	 * @param projects
	 */
	private void buildProjectList(ArrayList<Project> projects) {
		mTreeManager.clear();
		TreeBuilder<Project> treeBuilder = new TreeBuilder<Project>(
				mTreeManager);

		// First, sort by item order
		Collections.sort(projects, new Comparator<Project>() {
			@Override
			public int compare(Project project1, Project project2) {
				return project1.itemOrder - project2.itemOrder;
			}
		});

		// Add projects to tree sequently, adding more indent levels as needed
		int lastIndentLevel = 0;
    	int lastRealIndentLevel = 0;
    	
		for (int i = 0; i < projects.size(); i++) {
			// indentLevel starts from 1 (and not zero as the tree view expects)
			int currentIndentLevel = projects.get(i).indentLevel - 1;
			
			if (currentIndentLevel == lastRealIndentLevel) {
				// Remain on the same indent level as the previous project - this is done in order to deal
				// with cases in which there are several projects in the same indent level, but the indent
				// level difference from their parent is greater than one.
				currentIndentLevel = lastIndentLevel;
			} else {
				// Todoist website allows for neighboring items with indent levels difference greater than 1
				if (currentIndentLevel > lastIndentLevel + 1) {
					currentIndentLevel = lastIndentLevel + 1;
				}
			}
			
			lastRealIndentLevel = projects.get(i).indentLevel - 1;
			
			treeBuilder.sequentiallyAddNextNode(projects.get(i), currentIndentLevel);
			
			lastIndentLevel = currentIndentLevel;
		}
	}
	
	private void loadTopToolbar() {
		
 		mTopToolbar = (RelativeLayout)findViewById(R.id.top_toolbar);
		
 		if (
 				(mViewMode == ProjectViewMode.MOVE_TO_PROJECT) ||
 				(mViewMode == ProjectViewMode.SELECT_DEFAULT_PROJECT) ||
 				(mViewMode == ProjectViewMode.SELECT_INITIAL_PROJECT)) {
 			mTopToolbar.setVisibility(View.GONE);
 		}
		
		mProjectsToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_projects);
		mLabelsToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_labels);
		mQueriesToolbarButton = (LinearLayout)findViewById(R.id.top_toolbar_queries);
		mAddItemToolbarButton = (ImageView)findViewById(R.id.top_toolbar_add_item);
		
		mProjectsToolbarText = (TextView)findViewById(R.id.top_toolbar_projects_text);
		mLabelsToolbarText = (TextView)findViewById(R.id.top_toolbar_labels_text);
		mQueriesToolbarText = (TextView)findViewById(R.id.top_toolbar_queries_text);
		
		mProjectsToolbarText.setTypeface(null, Typeface.BOLD);
		ImageView img = (ImageView)findViewById(R.id.top_toolbar_projects_image);
		img.setImageResource(R.drawable.projects_black);
		
		
		mAddItemToolbarButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				Intent intent = new Intent(getBaseContext(), EditProjectView.class);
				intent.putExtra(EditProjectView.KEY__PROJECT, (Serializable) null);
				startActivityForResult(intent,
						Bootloader.REQUEST_CODE__EDIT_PROJECT);
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

	}

	@Override
	protected void onPause() {
		super.onPause();

		if ((mLoadingDialog != null) && (mLoadingDialog.isShowing()))
			mLoadingDialog.dismiss();
	}

	@Override
	public void onResume() {
		super.onResume();
		
		if (mViewMode == ProjectViewMode.FILTER_BY_PROJECTS) {
	        mStorage.setLastViewedFilter(InitialView.FILTER_BY_PROJECTS);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		boolean newCollapsible;

        overridePendingTransition(0, 0);
        
		mContext = this;

		Bundle extras = getIntent().getExtras();
		mViewMode = ProjectViewMode.valueOf(extras.getString(KEY__VIEW_MODE));

		mApplication = (TodoistApplication) getApplication();
		mClient = mApplication.getClient();
		mStorage = mClient.getStorage();
		mUser = mClient.getUser();

		if (mViewMode == ProjectViewMode.FILTER_BY_PROJECTS) {
			mStorage.setLastViewedFilter(InitialView.FILTER_BY_PROJECTS);
			this.setTitle("Projects");
		} else if (mViewMode == ProjectViewMode.MOVE_TO_PROJECT) {
			this.setTitle("Move to Project");
		} else if (mViewMode == ProjectViewMode.SELECT_INITIAL_PROJECT) {
			this.setTitle("Select Initial Project to Filter by");
		} else if (mViewMode == ProjectViewMode.SELECT_DEFAULT_PROJECT) {
			this.setTitle("Select Default Project for New Items");
		}

		if (savedInstanceState == null) {
			mTreeManager = new InMemoryTreeStateManager<Project>();

			ArrayList<Project> projects = mClient.getProjects();
			buildProjectList(projects);

			Log.d(TAG, mTreeManager.toString());
			newCollapsible = true;
		} else {
			mTreeManager = (TreeStateManager<Project>) savedInstanceState
					.getSerializable("treeManager");
			newCollapsible = savedInstanceState.getBoolean("collapsible");
		}

		setContentView(R.layout.projects_list);
		mTreeView = (TreeViewList) findViewById(R.id.projects_tree_view);

		loadTopToolbar();
		
		mProjectAdapter = new ProjectTreeItemAdapter(this, mTreeManager,
				LEVEL_NUMBER);
		mTreeView.setAdapter(mProjectAdapter);
		setCollapsible(newCollapsible);
		registerForContextMenu(mTreeView);

		Resources res = getResources();
		mTreeView
				.setExpandedDrawable(res.getDrawable(R.drawable.expanded_mark));
		mTreeView.setCollapsedDrawable(res
				.getDrawable(R.drawable.collapsed_mark));

		mTreeView.setOnItemClickListener(this);

	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (mViewMode != ProjectViewMode.FILTER_BY_PROJECTS) {
			// Selecting a project action was canceled
			setResult(RESULT_CANCELED);
		}
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
			LoginView.syncNow(ProjectListView.this, mClient, mUser.email, mUser.password, null);
			
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
		menuInflater.inflate(R.menu.project_context_menu, menu);
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(final MenuItem item) {

		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		final Project project = (Project) info.targetView.getTag();

		switch (item.getItemId()) {
		case R.id.context_menu_collapse_all_items:
			mTreeManager.collapseChildren(null);
			return true;
		case R.id.context_menu_expand_all_items:
			mTreeManager.expandEverythingBelow(null);
			return true;
		case R.id.context_menu_edit_project:
			Intent intent = new Intent(getBaseContext(), EditProjectView.class);
			intent.putExtra(EditProjectView.KEY__PROJECT, project);
			mProjectEdited = project;
			startActivityForResult(intent,
					Bootloader.REQUEST_CODE__EDIT_PROJECT);

			return true;

		case R.id.context_menu_delete_project:

			// User chose to delete a project - Prepare a yes/no dialog

			DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case DialogInterface.BUTTON_POSITIVE:
						mLoadingDialog = ProgressDialog
								.show(mContext, "",
										"Deleting project and sub-projects, please wait...");

						// Run this logic on a separate thread in order for the
						// loading dialog to actually show
						(new Thread(new Runnable() {
							@Override
							public void run() {
								deleteProjectsRecursively(project);

								runOnUiThread(new Runnable() {
									public void run() {
										// Refresh visual projects list
										buildProjectList(mClient.getProjects());

										if (mLoadingDialog.isShowing())
											mLoadingDialog.dismiss();
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

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder
					.setMessage(String
							.format(
									"Delete project '%s'?\nThis will delete all items and sub-projects as well.",
									TodoistTextFormatter.formatText(project.getName()).toString()));
			builder.setPositiveButton("Yes", dialogClickListener)
					.setNegativeButton("No", dialogClickListener);

			builder.show();

			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	private void deleteProjectsRecursively(Project project) {
		// Delete all of the project's children
		for (Project child : mTreeManager.getChildren(project)) {
			deleteProjectsRecursively(child);
		}

		// Delete current project
		mClient.deleteProject(project);
	}

	/**
	 * Called when the edit/add project or settings activity returns
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Bootloader.REQUEST_CODE__SETTINGS) {
			if (resultCode == RESULT_OK) {
				// Refresh projects - happens when user changes text size, etc
    			(new Thread(new Runnable() {
					@Override
					public void run() {
						final ArrayList<Project> projects = mClient.getProjects();
						runOnUiThread(new Runnable() {
							public void run() {	
								buildProjectList(projects);
							}
						});
		 
					}
				})).start();
 			
			}
		} else if (requestCode == Bootloader.REQUEST_CODE__EDIT_PROJECT) {
			if (resultCode == RESULT_OK) {
				final Project project = (Project) data.getExtras().get(
						EditProjectView.KEY__PROJECT);

				if (project.id == 0) {
					mLoadingDialog = ProgressDialog.show(this, "",
							"Adding project, please wait...");
				} else {
					mLoadingDialog = ProgressDialog.show(this, "",
							"Updating project, please wait...");
				}

				// Run this logic on a separate thread in order for the loading
				// dialog to actually show
				(new Thread(new Runnable() {
					@Override
					public void run() {
						if (project.id == 0) {
							// Add project
							mClient.addProject(project);
						} else {
							// Update project
							mClient.updateProject(project, mProjectEdited);
						}

						runOnUiThread(new Runnable() {
							public void run() {
								// Refresh visual projects list
								buildProjectList(mClient.getProjects());

								if (mLoadingDialog.isShowing())
									mLoadingDialog.dismiss();
							}
						});

					}
				})).start();
			}
		}
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		// Project list item clicked
		Project project = (Project) arg1.getTag();

		if (mViewMode == ProjectViewMode.FILTER_BY_PROJECTS) {
			// Show items list activity filtered by selected project
			Intent intent = new Intent(getBaseContext(), ItemListView.class);
			intent.putExtra(ItemListView.KEY__VIEW_MODE,
					ItemListView.ItemViewMode.FILTER_BY_PROJECTS.toString());
			intent.putExtra(ItemListView.KEY__PROJECT, (Serializable) project);
			startActivity(intent);
			
	        mStorage.setLastViewedProject(project.id);
	        
	        finish();

		} else {
			// Return selected project and close activity
			Intent intent = new Intent();
			intent.putExtra(KEY__PROJECT, project);
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
