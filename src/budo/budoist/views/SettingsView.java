package budo.budoist.views;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import budo.budoist.R;
import budo.budoist.Bootloader;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Item;
import budo.budoist.models.Label;
import budo.budoist.models.Project;
import budo.budoist.models.Query;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.models.User;
import budo.budoist.models.User.DateFormat;
import budo.budoist.models.User.TimeFormat;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.services.TodoistOfflineStorage.InitialView;
import budo.budoist.services.TodoistOfflineStorage.ItemSortMode;
import budo.budoist.views.LabelListView.LabelViewMode;
import budo.budoist.views.ProjectListView.ProjectViewMode;
import budo.budoist.views.QueryListView.QueryViewMode;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;
import android.view.Display;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Displays the login screen
 * @author Yaron
 *
 */
public class SettingsView extends PreferenceActivity {
    private static final String TAG = SettingsView.class.getSimpleName();

    private TodoistApplication mApplication;
    private TodoistClient mClient;
    private User mUser;
    private TodoistOfflineStorage mStorage;
    
    private Preference mSyncNow;
    private ListPreference mSyncFrequency;
    private Preference mLoginDetails;
    private CheckBoxPreference mSyncOnStartup;
    private CheckBoxPreference mSyncOnExit;
    
    private Preference mDefaultProject;
    private ListPreference mSortMode;
    private ListPreference mInitialView;
    private ListPreference mTextSize;
    private CheckBoxPreference mShowCompletedItems;
    private ListPreference mDateFormat;
    private ListPreference mTimeFormat;
    
    private Preference mBackupNow;
    private Preference mRestoreBackup;
    private ListPreference mBackupFrequency;
    private Preference mSetBackupPath;
    
    private Preference mAbout;
    
    // Set to true if UI-related settings were changed
    private boolean mIsUIModified = false;
    
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mUser = mClient.getUser();
        mStorage = mClient.getStorage();
        
	    addPreferencesFromResource(R.xml.preferences); 
	    
	    this.setTitle("Settings");
	    
	    mLoginDetails = (Preference)findPreference("login_details");
	    mLoginDetails.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				// Show the login screen
		        Intent intent = new Intent(getBaseContext(), LoginView.class);
		        startActivity(intent);
				return false;
			}
		});
	    
	    mSyncNow = (Preference)findPreference("sync_now");
	    // Use Item's date formatting capabilities for displaying last sync time
	    refreshSyncNowDate();
	    
	    mSyncNow.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				LoginView.syncNow(SettingsView.this, mClient, mUser.email, mUser.password,  new Runnable() {
					@Override
					public void run() {
						// Syncing is complete - refresh the last sync time
						SettingsView.this.refreshSyncNowDate();
					}
				});
				
				return false;
			}
		});
	    
	    mSyncFrequency = (ListPreference)findPreference("sync_frequency");
	    mSyncFrequency.setValue(String.valueOf(mStorage.getSyncFrequency()));
	    mSyncFrequency.setSummary(mSyncFrequency.getEntry());
	    
	    mSyncFrequency.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int valueIndex = Arrays.asList(mSyncFrequency.getEntryValues()).indexOf(newValue);
				String entry = (String) mSyncFrequency.getEntries()[valueIndex];
				preference.setSummary(entry);
				
				mStorage.setSyncFrequency(Integer.valueOf((String)newValue));
				
				return true;
			}
		});
	    
	    mSyncOnStartup = (CheckBoxPreference)findPreference("sync_on_startup");
	    mSyncOnStartup.setChecked(mStorage.getSyncOnStartup());

	    mSyncOnStartup.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mStorage.setSyncOnStartup((Boolean)newValue);
				return true;
			}
		});
    
 	    mSyncOnExit = (CheckBoxPreference)findPreference("sync_on_exit");
	    mSyncOnExit.setChecked(mStorage.getSyncOnExit());

	    mSyncOnExit.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mStorage.setSyncOnExit((Boolean)newValue);
				return true;
			}
		});
       
	    mDefaultProject = (Preference)findPreference("default_project");
		Project project = mStorage.getProject(mStorage.getDefaultProject());
		
		if (project == null) {
			// No default project set (or default project was deleted) - use the first available project instead
			ArrayList<Project> projects = mStorage.getProjects();
			
			if (projects.size() > 0) {
				project = projects.get(0);
				
				// Fix this issue in the storage
				mStorage.setDefaultProject(project.id);
			}
		}
		
	    mDefaultProject.setSummary(TodoistTextFormatter.formatText(project.getName()));
	    mDefaultProject.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				// Show a project list in order for the user to select the default project for new items
		        Intent intent = new Intent(getBaseContext(), ProjectListView.class);
		        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.SELECT_DEFAULT_PROJECT.toString());
	            startActivityForResult(intent, Bootloader.REQUEST_CODE__SELECT_DEFAULT_PROJECT);
			
				return false;
			}
		});
    
	    mSortMode = (ListPreference)findPreference("item_sort_mode");
	    mSortMode.setValue(String.valueOf(mStorage.getInitialItemsSortMode()));
	    mSortMode.setSummary(mSortMode.getEntry());

	    mSortMode.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int valueIndex = Arrays.asList(mSortMode.getEntryValues()).indexOf(newValue);
				String entry = (String) mSortMode.getEntries()[valueIndex];
				preference.setSummary(entry);
				
				mStorage.setInitialItemsSortMode(ItemSortMode.valueOf((String)newValue));
				
				return true;
			}
		});
    
	    mInitialView = (ListPreference)findPreference("initial_view");
	    InitialView initialView = mStorage.getInitialView();
	    mInitialView.setValue(initialView.toString());
	    
	    refreshInitialViewSummary(initialView);

	    mInitialView.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				InitialView initialView = InitialView.valueOf((String)newValue);
				
				if (initialView == InitialView.SPECIFIC_PROJECT) {
					// Show a project list in order for the user to select the initial filter project
			        Intent intent = new Intent(getBaseContext(), ProjectListView.class);
			        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.SELECT_INITIAL_PROJECT.toString());
		            startActivityForResult(intent, Bootloader.REQUEST_CODE__SELECT_INITIAL_PROJECT);
			        
			        return false;
			        
				} else if (initialView == InitialView.SPECIFIC_LABEL) {
					// Show a label list in order for the user to select the initial filter label
			        Intent intent = new Intent(getBaseContext(), LabelListView.class);
			        intent.putExtra(LabelListView.KEY__VIEW_MODE, LabelViewMode.SELECT_INITIAL_LABEL.toString());
		            startActivityForResult(intent, Bootloader.REQUEST_CODE__SELECT_INITIAL_LABEL);
			        
			        return false;
			        
				} else if (initialView == InitialView.SPECIFIC_QUERY) {
					// Show a query list in order for the user to select the initial filter query
			        Intent intent = new Intent(getBaseContext(), QueryListView.class);
			        intent.putExtra(LabelListView.KEY__VIEW_MODE, QueryViewMode.SELECT_INITIAL_QUERY.toString());
		            startActivityForResult(intent, Bootloader.REQUEST_CODE__SELECT_INITIAL_QUERY);
			        
			        return false;
		        
				} else {
					// Simply set the filter value immediately
					refreshInitialViewSummary(initialView);
					mStorage.setInitialView(initialView);
					return true;
				}
			}
		});
       
	    mTextSize = (ListPreference)findPreference("text_size");
	    mTextSize.setValue(String.valueOf(mStorage.getTextSize()));
	    mTextSize.setSummary(mTextSize.getEntry());

	    mTextSize.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int valueIndex = Arrays.asList(mTextSize.getEntryValues()).indexOf(newValue);
				String entry = (String) mTextSize.getEntries()[valueIndex];
				preference.setSummary(entry);
				
				mStorage.setTextSize(Integer.valueOf((String)newValue));
				
				mIsUIModified = true;
				
				return true;
			}
		});
     
	    mShowCompletedItems = (CheckBoxPreference)findPreference("show_completed_items");
	    mShowCompletedItems.setChecked(mStorage.getShowCompletedItems());

	    mShowCompletedItems.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				mStorage.setShowCompletedItems((Boolean)newValue);
				
				mIsUIModified = true;
				
				return true;
			}
		});
    
	    mDateFormat = (ListPreference)findPreference("date_format");
	    mDateFormat.setValue(String.valueOf(mUser.dateFormat));
	    mDateFormat.setSummary(mDateFormat.getEntry());

	    mDateFormat.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int valueIndex = Arrays.asList(mDateFormat.getEntryValues()).indexOf(newValue);
				String entry = (String) mDateFormat.getEntries()[valueIndex];
				preference.setSummary(entry);
				
				mUser.dateFormat = DateFormat.valueOf((String)newValue);
				mClient.updateUser(mUser);
				
				mIsUIModified = true;
				
				return true;
			}
		});
    
	    mTimeFormat = (ListPreference)findPreference("time_format");
	    mTimeFormat.setValue(String.valueOf(mUser.timeFormat));
	    mTimeFormat.setSummary(mTimeFormat.getEntry());

	    mTimeFormat.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int valueIndex = Arrays.asList(mTimeFormat.getEntryValues()).indexOf(newValue);
				String entry = (String) mTimeFormat.getEntries()[valueIndex];
				preference.setSummary(entry);
				
				mUser.timeFormat = TimeFormat.valueOf((String)newValue);
				mClient.updateUser(mUser);
				
				mIsUIModified = true;
				
				return true;
			}
		});    
	    
	    mBackupNow = (Preference)findPreference("backup_now");
	    // Use Item's date formatting capabilities for displaying last backup time
	    refreshBackupNowDate();
	    mBackupNow.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				if (mClient.isCurrentlyBackingUp()) {
					 Toast.makeText(SettingsView.this, "Backup is already running...", Toast.LENGTH_SHORT).show();
					 return false;
				}
				
				final ProgressDialog loadingDialog = ProgressDialog.show(SettingsView.this, "", "Backup in progress...");
				
				(new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							final String backupFilename = mClient.backupData(SettingsView.this);
						
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									loadingDialog.hide();
									Toast.makeText(SettingsView.this, "Backup complete: " + backupFilename, Toast.LENGTH_LONG).show();
									refreshBackupNowDate();
								}
							});
							
						} catch (IOException e) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									loadingDialog.hide();
									Toast.makeText(SettingsView.this, "Backup failed - probably no disk space or SD card is not available.", Toast.LENGTH_LONG).show();
								}
							});
						}
					}
				})).start();
				
				return false;
			}
		});
	    
	    mRestoreBackup = (Preference)findPreference("restore_backup");
	    mRestoreBackup.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				// Show a yes/no dialog for backup restore
				
		    	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
		    	    @Override
		    	    public void onClick(DialogInterface dialog, int which) {
		    	        switch (which){
		    	        case DialogInterface.BUTTON_POSITIVE:
		    	        	// User chose "yes" - Display an "Open file" dialog
		    	        	
		    	        	Intent intent = new Intent(getBaseContext(), FileDialog.class);
    	        		    String path = mStorage.getBackupPath();
						    if (path == null) {
						    	// Use default path
							    path = mStorage.getDefaultBackupPath(SettingsView.this);
						    }
		    	        	intent.putExtra(FileDialog.START_PATH, path);
		    	        	intent.putExtra(FileDialog.SELECTION_MODE, SelectionMode.MODE_OPEN);
		    	        	
		    	        	startActivityForResult(intent, Bootloader.REQUEST_CODE__OPEN_BACKUP_RESTORE_FILE);
		    	        	
		    	            break;
		
		    	        case DialogInterface.BUTTON_NEGATIVE:
		    	            // No button clicked - do nothing
		    	            break;
		    	        }
		    	    }
		    	};
		
		    	AlertDialog.Builder builder = new AlertDialog.Builder(SettingsView.this);
		    	builder.setMessage("Are you sure you want to restore a backup?\nAll existing data will be ERASED before restoration.");
		    	builder.setPositiveButton("Yes", dialogClickListener)
		    	    .setNegativeButton("No", dialogClickListener);
		    	
		    	builder.show();
			
				return false;
			}
		});

	    
	    mBackupFrequency = (ListPreference)findPreference("backup_frequency");
	    mBackupFrequency.setValue(String.valueOf(mStorage.getBackupFrequency()));
	    mBackupFrequency.setSummary(mBackupFrequency.getEntry());
	    
	    mBackupFrequency.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				int valueIndex = Arrays.asList(mBackupFrequency.getEntryValues()).indexOf(newValue);
				String entry = (String) mBackupFrequency.getEntries()[valueIndex];
				preference.setSummary(entry);
				
				mStorage.setBackupFrequency(Integer.valueOf((String)newValue));
				
				return true;
			}
		});
	    
	    mSetBackupPath = (Preference)findPreference("set_backup_path");
	    String path = mStorage.getBackupPath();
	    if (path == null) {
	    	// Use default path
		    path = mStorage.getDefaultBackupPath(this);
	    }
	    mSetBackupPath.setSummary(path);
	    
	    mSetBackupPath.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				
				// Show an input dialog for entering the new path
				
				final AlertDialog.Builder alert = new AlertDialog.Builder(SettingsView.this);
				final EditText input = new EditText(SettingsView.this);
				Display display = getWindowManager().getDefaultDisplay();
				input.setWidth(display.getWidth());
				input.setText(Environment.getExternalStorageDirectory().getAbsolutePath() + "/");
				input.setSelection(input.getText().length());
				alert.setView(input);
				alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String path = input.getText().toString().trim();
						
						if (path.length() == 0)
							return;
						if (path.endsWith("/"))
							path = path.substring(0, path.length() - 1);
						
						mStorage.setBackupPath(path);
					    mSetBackupPath.setSummary(path);
					}
				});
		
				alert.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int whichButton) {
								dialog.cancel();
							}
						});
				alert.show();
			
				return true;
			}
		});
    
 	    mAbout = (Preference)findPreference("about");
	    mAbout.setOnPreferenceClickListener(new OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
		        Intent intent = new Intent(getBaseContext(), AboutView.class);
		        startActivity(intent);
				return true;
			}
	    });
   
    }
    
    private void refreshBackupNowDate() {
	    Item item = new Item(); item.dueDate = mStorage.getLastBackupTime();
		String dateString = (item.dueDate.getTime() == 0 ? "Never": item.getDueDateDescription(mUser.timeFormat));
	    mBackupNow.setSummary("Last Backup: " + dateString);
    }
    
    private void refreshSyncNowDate() {
 	    Item item = new Item(); item.dueDate = mStorage.getLastSyncTime();
		String dateString = item.getDueDateDescription(mUser.timeFormat);
	    mSyncNow.setSummary("Last Sync: " + dateString);
    }
    
    private void refreshInitialViewSummary(InitialView initialView) {
 	    if (initialView == InitialView.SPECIFIC_LABEL) {
 	    	Label label = mStorage.getLabel(mStorage.getInitialLabel());
 	    	
 	    	if (label == null) {
 	    		// Special case - label was deleted - revert to filter by labels
 	    		mStorage.setInitialView(InitialView.FILTER_BY_LABELS);
 	    		mInitialView.setValue(InitialView.FILTER_BY_LABELS.toString());
 	    		refreshInitialViewSummary(InitialView.FILTER_BY_LABELS);
 	    		return;
 	    	}
 	    	
	    	mInitialView.setSummary("Filter by label: " + label.name);
	    	
	    } else if (initialView == InitialView.SPECIFIC_PROJECT) {
	    	Project project = mStorage.getProject(mStorage.getInitialProject());
	    	
 	    	if (project == null) {
 	    		// Special case - project was deleted - revert to filter by projects
 	    		mStorage.setInitialView(InitialView.FILTER_BY_PROJECTS);
 	    		mInitialView.setValue(InitialView.FILTER_BY_PROJECTS.toString());
 	    		refreshInitialViewSummary(InitialView.FILTER_BY_PROJECTS);
 	    		return;
 	    	}
     	
	    	mInitialView.setSummary("Filter by project: " +
	    			TodoistTextFormatter.formatText(project.getName()));
	    	
	    } else if (initialView == InitialView.SPECIFIC_QUERY) {
	    	Query query = mStorage.getQuery(mStorage.getInitialQuery());
	    	
 	    	if (query == null) {
 	    		// Special case - query was deleted - revert to filter by queries
 	    		mStorage.setInitialView(InitialView.FILTER_BY_QUERIES);
 	    		mInitialView.setValue(InitialView.FILTER_BY_QUERIES.toString());
 	    		refreshInitialViewSummary(InitialView.FILTER_BY_QUERIES);
 	    		return;
 	    	}
    	
	    	mInitialView.setSummary("Filter by query: " + query.name);
	    	
	    } else {
	    	
			int valueIndex = Arrays.asList(mInitialView.getEntryValues()).indexOf(initialView.toString());
			String entry = (String) mInitialView.getEntries()[valueIndex];
		    mInitialView.setSummary(entry);
	    }
    }
    
    /**
     * Called when returning from a dialog for selecting initial filter project/label
     */
    @Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
    	if (requestCode == Bootloader.REQUEST_CODE__OPEN_BACKUP_RESTORE_FILE) {
    		if (resultCode == RESULT_OK) {
    			// Restore a backup
    			
				final ProgressDialog loadingDialog = ProgressDialog.show(SettingsView.this, "", "Backup restoration in progress...");
				
				(new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							String backupFilename = data.getStringExtra(FileDialog.RESULT_PATH);
							
							mClient.restoreData(backupFilename);
							
							mIsUIModified = true; // Notify the main UI that everything was modified (need to refresh)
						
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									loadingDialog.hide();
									Toast.makeText(SettingsView.this, "Backup restoration complete", Toast.LENGTH_LONG).show();
								}
							});
							
						} catch (IOException e) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									loadingDialog.hide();
									Toast.makeText(SettingsView.this, "Backup restoration failed - probably backup file not available or is invalid.", Toast.LENGTH_LONG).show();
								}
							});
						}
					}
				})).start();
				
    		}
    		
    	} else if (requestCode == Bootloader.REQUEST_CODE__SELECT_DEFAULT_PROJECT) {
    		if (resultCode == RESULT_OK) {
    			// User chose the default project for new items
    			Project project = (Project)data.getExtras().get(ProjectListView.KEY__PROJECT);
    			
    			// Save the selected project
    			mStorage.setDefaultProject(project.id);
    			
    			// Visually refresh the settings window
			    mDefaultProject.setSummary(TodoistTextFormatter.formatText(project.getName()));
    		}
   	
 		} else if (requestCode == Bootloader.REQUEST_CODE__SELECT_INITIAL_PROJECT) {
    		if (resultCode == RESULT_OK) {
    			// User chose the initial project
    			Project project = (Project)data.getExtras().get(ProjectListView.KEY__PROJECT);
    			
    			// Save the selected project
    			mStorage.setInitialView(InitialView.SPECIFIC_PROJECT);
    			mStorage.setInitialProject(project.id);
    			
    			// Visually refresh the settings window
			    mInitialView.setValue(InitialView.SPECIFIC_PROJECT.toString());
			    refreshInitialViewSummary(InitialView.SPECIFIC_PROJECT);
    		}
    		
		} else if (requestCode == Bootloader.REQUEST_CODE__SELECT_INITIAL_LABEL) {
    		if (resultCode == RESULT_OK) {
    			// User chose the initial label
    			Label label = (Label)data.getExtras().get(LabelListView.KEY__LABEL);
    			
    			// Save the selected label
    			mStorage.setInitialView(InitialView.SPECIFIC_LABEL);
    			mStorage.setInitialLabel(label.id);
    			
    			// Visually refresh the settings window
			    mInitialView.setValue(InitialView.SPECIFIC_LABEL.toString());
			    refreshInitialViewSummary(InitialView.SPECIFIC_LABEL);
    		}
    		
 		} else if (requestCode == Bootloader.REQUEST_CODE__SELECT_INITIAL_QUERY) {
    		if (resultCode == RESULT_OK) {
    			// User chose the initial query
    			Query query = (Query)data.getExtras().get(QueryListView.KEY__QUERY);
    			
    			// Save the selected query
    			mStorage.setInitialView(InitialView.SPECIFIC_QUERY);
    			mStorage.setInitialQuery(query.id);
    			
    			// Visually refresh the settings window
			    mInitialView.setValue(InitialView.SPECIFIC_QUERY.toString());
			    refreshInitialViewSummary(InitialView.SPECIFIC_QUERY);
    		}
   		
		}
    }
    
    @Override
	public void onBackPressed () {
 		if (mIsUIModified) {
			setResult(RESULT_OK); // Notify calling activity that the UI settings were modified
		} else {
			setResult(RESULT_CANCELED);
		}   	
 		
		super.onBackPressed();
    }
 
}
