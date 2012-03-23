package budo.budoist.views;

import java.util.ArrayList;
import java.util.Date;
import pl.polidea.treeview.InMemoryTreeStateManager;
import budo.budoist.R;
import budo.budoist.Bootloader;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Item;
import budo.budoist.models.Note;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.models.User;
import budo.budoist.receivers.AppService;
import budo.budoist.services.PremiumAccountException;
import budo.budoist.services.TodoistClient;
import budo.budoist.views.adapters.NoteTreeItemAdapter;
import budo.budoist.views.adapters.NoteTreeItemAdapter.IOnNoteDelete;
import budo.budoist.views.adapters.NoteTreeItemAdapter.IOnNoteEdit;
import pl.polidea.treeview.TreeBuilder;
import pl.polidea.treeview.TreeStateManager;
import pl.polidea.treeview.TreeViewList;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View.OnClickListener;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * Note List View
 * @author Yaron Budowski
 *
 */
public class NoteListView extends Activity implements IOnNoteDelete, IOnNoteEdit, OnClickListener {
    private static final String TAG = NoteListView.class.getSimpleName();
    private TreeViewList mTreeView;

    private static final int LEVEL_NUMBER = 4;
    private TreeStateManager<Note> mTreeManager = null;
    private NoteTreeItemAdapter mNoteAdapter;
    private boolean mCollapsible;
    
    private Item mItem = null;
	private int mNoteCount;
	
	private boolean mNotesModified = false;
    
    private TodoistApplication mApplication;
    private TodoistClient mClient;

    // A "Loading.." dialog used for long operations (such as delete/edit/add item)
	private ProgressDialog mLoadingDialog;
	
	private Context mContext;
 
    public static final String KEY__ITEM = "Item";
    public static final String KEY__NOTES_MODIFIED = "NotesModified";
    
    static final int MAX_ITEM_NAME_IN_DIALOG = 35;
    static final int MAX_NOTE_NAME_IN_DIALOG = 30;
    
	private User mUser;
	private Button mCloseButton;
	
	private class SyncReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			runOnUiThread(new Runnable() {
				public void run() {
					// Refresh visual note list
		            buildNoteList();
		            mNotesModified = true;
				}
			});

		}
	}
	
	private SyncReceiver mSyncReceiver = null;
	private boolean mIsSyncReceiverRegistered = false;
	

	
    
    /**
     * Converts a note list into a tree note view (as set by post date of the notes)
     */
    private void buildNoteList() {
    	mTreeManager.clear();
    	TreeBuilder<Note> treeBuilder = new TreeBuilder<Note>(mTreeManager);
		ArrayList<Note> notes = null;
		
    	try {
			notes = mClient.getNotesByItem(mItem);
		} catch (PremiumAccountException e) {
			// Shouldn't happen since this entire activity is shown only if the user is premium
		}
   	
    	// Add notes to tree sequently
    	for (int i = 0; i < notes.size(); i++) {
    		treeBuilder.sequentiallyAddNextNode(notes.get(i), 0);
    	}
    }
    
    public TodoistClient getClient() {
    	return mClient;
    }
    
    @Override
    public void onBackPressed() {
    	setNoteResult();
  	
    	super.onBackPressed();
    }
    
	@Override
	public void onResume() {
		super.onResume();
		
		if (!mIsSyncReceiverRegistered) {
			registerReceiver(mSyncReceiver, new IntentFilter(AppService.SYNC_COMPLETED_ACTION));
			mIsSyncReceiverRegistered = true;
		}
	}

 	@Override
	protected void onPause() {
		super.onPause();
		
		if (mIsSyncReceiverRegistered) {
			unregisterReceiver(mSyncReceiver);
			mIsSyncReceiverRegistered = false;
		}

		
		if ((mLoadingDialog != null) && (mLoadingDialog.isShowing()))
			mLoadingDialog.dismiss();
		
	}
   
    
    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		mSyncReceiver = new SyncReceiver();
		
        mContext = this;
        
        mNotesModified = false;
        
        Bundle extras = getIntent().getExtras();
       
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mUser = mClient.getUser();
        
        mItem = (Item)extras.get(KEY__ITEM);
        mNoteCount = mItem.noteCount;
        
    	String shortContent = TodoistTextFormatter.formatText(mItem.getContent()).toString();
    	if (shortContent.length() > MAX_ITEM_NAME_IN_DIALOG) shortContent = shortContent.subSequence(0, MAX_ITEM_NAME_IN_DIALOG) + "...";
 
    	this.setTitle("Notes for: " + shortContent);
    	
       
		mLoadingDialog = ProgressDialog.show(mContext, "", "Loading notes...");
		
		// Run this logic on a separate thread in order for the loading dialog to actually show
		(new Thread(new Runnable() {
			@Override
			public void run() {
	            mTreeManager = new InMemoryTreeStateManager<Note>();
	            buildNoteList();
		        
		        mNoteAdapter = new NoteTreeItemAdapter(NoteListView.this, NoteListView.this, NoteListView.this, mTreeManager, LEVEL_NUMBER);
		        
				runOnUiThread(new Runnable() {
					public void run() {	
				        setContentView(R.layout.notes_list);
				        
		                mCloseButton = (Button) findViewById(R.id.notes_button_close);
				        mCloseButton.setOnClickListener(NoteListView.this);
				        
				        mTreeView = (TreeViewList) findViewById(R.id.notes_tree_view);
				        mTreeView.setItemsCanFocus(false);
				        
				        mTreeView.setAdapter(mNoteAdapter);
				        setCollapsible(false);
				        registerForContextMenu(mTreeView);
				        
						if (mLoadingDialog.isShowing())
							mLoadingDialog.dismiss();
					}
				});
			}
		})).start();
		
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
        menu.findItem(R.id.add_note_menu_item).setVisible(true);
        
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
        case R.id.settings:
 	        Intent intent = new Intent(getBaseContext(), SettingsView.class);
	        startActivityForResult(intent, Bootloader.REQUEST_CODE__SETTINGS);
       	
        	break;
        
  		case R.id.sync_now:
			LoginView.syncNow(NoteListView.this, mClient, mUser.email, mUser.password, new Runnable() {
				@Override
				public void run() {
					// Refresh note list
		            buildNoteList();
				}
			});
			
			break;
			
 	    case R.id.add_note_menu_item:
	    	// Show an input box dialog for note content
	    	
			final AlertDialog.Builder alert = new AlertDialog.Builder(this);
			final EditText input = new EditText(this);
			Display display = getWindowManager().getDefaultDisplay();
			input.setWidth(display.getWidth());
			alert.setView(input);
			alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					String value = input.getText().toString().trim();
					
					if (value.length() == 0)
						return;
					
					final Note note = new Note();
					note.content = value;
					note.postDate = new Date();
					note.itemId = mItem.id;
					
	    			mLoadingDialog = ProgressDialog.show(mContext, "", "Adding note, please wait...");
	    			
		   			// Run this logic on a separate thread in order for the loading dialog to actually show
	    			(new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								mClient.addNote(note);
								mNoteCount++;
								
								mNotesModified = true;
							} catch (PremiumAccountException e) {
							}
							runOnUiThread(new Runnable() {
								public void run() {
							    	TreeBuilder<Note> treeBuilder = new TreeBuilder<Note>(mTreeManager);
									treeBuilder.sequentiallyAddNextNode(note, 0);
									
									if (mLoadingDialog.isShowing())
										mLoadingDialog.dismiss();
								}
							});
						}
	    			})).start();
	    			
				}
			});
	
			alert.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int whichButton) {
							dialog.cancel();
						}
					});
			alert.show();
			
        	break;
 	    default:
            return false;
        }
        
		return true;
        
    }

	@Override
	public void onNoteEdit(final Note note) {
    	// User chose to edit a note
		
    	// Show an input box dialog for note content
    	
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);
		final EditText input = new EditText(this);
		input.setText(note.content);
		Display display = getWindowManager().getDefaultDisplay();
		input.setWidth(display.getWidth());
		alert.setView(input);
		alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				// Update note
				note.content = input.getText().toString();
				mClient.updateNote(note, note);
				
				mNotesModified = true;
				
				// Refresh note list
				runOnUiThread(new Runnable() {
					public void run() {	
			            buildNoteList();
					}
				});

			}
		});
		
		alert.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						dialog.cancel();
					}
				});
		
		alert.show();

	}

    
	@Override
	public void onNoteDelete(final Note note) {
    	// User chose to delete a note - Prepare a yes/no dialog
    	
    	DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
    	    @Override
    	    public void onClick(DialogInterface dialog, int which) {
    	        switch (which){
    	        case DialogInterface.BUTTON_POSITIVE:
	    			mLoadingDialog = ProgressDialog.show(mContext, "", "Deleting note, please wait...");
	    			
		   			// Run this logic on a separate thread in order for the loading dialog to actually show
	    			(new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								mClient.deleteNote(note);
								mNoteCount--;
								
								mNotesModified = true;
							} catch (PremiumAccountException e) {
							}
	        	        	
							runOnUiThread(new Runnable() {
								public void run() {
									mTreeManager.removeNodeRecursively(note);
					    			
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

    	String shortContent = TodoistTextFormatter.formatText(note.content).toString();
    	if (shortContent.length() > MAX_NOTE_NAME_IN_DIALOG) shortContent = shortContent.subSequence(0, MAX_NOTE_NAME_IN_DIALOG) + "...";
    	
    	AlertDialog.Builder builder = new AlertDialog.Builder(this);
    	builder.setMessage(String.format("Delete note '%s'?", shortContent));
    	builder.setPositiveButton("Yes", dialogClickListener)
    	    .setNegativeButton("No", dialogClickListener);
    	
    	builder.show();
	}

	@Override
	public void onClick(View v) {
		// When the close button was clicked
		
		// Return the item (in case the note count was modified)
		setNoteResult();
		
		finish();
	}

     /**
     * Called when the settings activity returns
     */
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Bootloader.REQUEST_CODE__SETTINGS) {
			if (resultCode == RESULT_OK) {
				// Refresh notes - happens when user changes text size, etc
    			(new Thread(new Runnable() {
					@Override
					public void run() {
						runOnUiThread(new Runnable() {
							public void run() {	
					            buildNoteList();
							}
						});
		 
					}
				})).start();
 			
			}
		}
    }
    
    
    private void setNoteResult() {
    	// Return the item (in case the note count was modified)
		Intent intent = new Intent();
		mItem.noteCount = mNoteCount;
		intent.putExtra(KEY__ITEM, mItem);
		intent.putExtra(KEY__NOTES_MODIFIED, new Boolean(mNotesModified));
		
		setResult(RESULT_OK, intent);
    }
   
}
