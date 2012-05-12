package budo.budoist.services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;
import budo.budoist.models.Item;
import budo.budoist.models.Label;
import budo.budoist.models.Note;
import budo.budoist.models.Project;
import budo.budoist.models.Query;
import budo.budoist.models.User;
import budo.budoist.models.SynchronizedModel.DirtyState;
import budo.budoist.models.User.DateFormat;
import budo.budoist.models.User.TimeFormat;

/**
 * Holds all Todoist-related information for offline use (i.e. saves in phone storage)
 * 
 * @author Yaron Budowski
 *
 */
public class TodoistOfflineStorage {
	
	private final static String TAG = "TodoistOfflineStorage";
		
	private final static String PREFERENCES_TODOIST_USER = "todoist_user";
	private final static String PREFERENCES_USER_ID = "todoist_user.id";
	private final static String PREFERENCES_USER_EMAIL = "todoist_user.email";
	private final static String PREFERENCES_USER_PASSWORD = "todoist_user.password";
	private final static String PREFERENCES_USER_FULL_NAME = "todoist_user.full_name";
	private final static String PREFERENCES_USER_TIMEZONE = "todoist_user.timezone";
	private final static String PREFERENCES_USER_TIMEZONE_OFFSET_MINS = "todoist_user.timezone_offset_mins";
	private final static String PREFERENCES_USER_TIMEZONE_DAYLIGHT_SAVINGS = "todoist_user.timezone_daylight_savings";
	private final static String PREFERENCES_USER_TIME_FORMAT = "todoist_user.time_format";
	private final static String PREFERENCES_USER_DATE_FORMAT = "todoist_user.date_format";
	private final static String PREFERENCES_USER_PREMIUM_UNTIL = "todoist_user.premium_until";
	
	// When set to true, on the next login (which happens on sync), it'll update the online profile
	private final static String PREFERENCES_USER_PROFILE_MODIFIED = "todoist_user.profile_modified";
	
	private final static String PREFERENCES_TODOIST_DATA = "data";
	
	// Has the project list been re-ordered?
	private final static String PREFERENCES_DATA_PROJECTS_REORDERED = "data.projects_reordered";
	
	// This is a subsection under "data" in which contains keys (one per project ID - indicating
	// whether or not the items under that project have been reordered)
	private final static String PREFERENCES_TODOIST_DATA_ITEMS_REORDERED = "data.items_reordered";
	
	// Which items have been moved to a new project:
	// Each subsection is a key (of new/destination project ID), and each subsection contains
	// keys (item IDs) and values (their current project IDs)
	private final static String PREFERENCES_TODOIST_DATA_ITEMS_MOVED = "data.items_moved";
	
	private final static String PREFERENCES_DISPLAY = "display";
	private final static String PREFERENCES_DISPLAY_LAST_USED_ITEM_SORT = "display.last_used_item_sort";
	private final static String PREFERENCES_DISPLAY_INITIAL_ITEM_SORT = "display.initial_item_sort";
	private final static String PREFERENCES_DISPLAY_INITIAL_VIEW = "display.initial_view";
	private final static String PREFERENCES_DISPLAY_LAST_VIEWED_FILTER = "display.last_viewed_filter";
	private final static String PREFERENCES_DISPLAY_LAST_VIEWED_PROJECT = "display.last_viewed_project";
	private final static String PREFERENCES_DISPLAY_LAST_VIEWED_LABEL = "display.last_viewed_label";
	private final static String PREFERENCES_DISPLAY_LAST_VIEWED_QUERY = "display.last_viewed_query";
	private final static String PREFERENCES_DISPLAY_INITIAL_PROJECT = "display.initial_project";
	private final static String PREFERENCES_DISPLAY_INITIAL_LABEL = "display.initial_label";
	private final static String PREFERENCES_DISPLAY_INITIAL_QUERY = "display.initial_query";
	private final static String PREFERENCES_DISPLAY_TEXT_SIZE = "display.text_size";
	private final static String PREFERENCES_DISPLAY_SHOW_COMPLETED_ITEMS = "display.show_completed_items";
	private final static String PREFERENCES_DISPLAY_DEFAULT_PROJECT = "display.default_project";
	
	private final static String PREFERENCES_SYNC = "sync";
	private final static String PREFERENCES_SYNC_LAST_SYNC_TIME = "sync.last_sync_time";
	private final static String PREFERENCES_SYNC_FREQUENCY = "sync.sync_frequency";
	private final static String PREFERENCES_SYNC_ON_STARTUP = "sync.sync_on_startup";
	private final static String PREFERENCES_SYNC_ON_EXIT = "sync.sync_on_exit";
	
	private final static int DEFAULT_SYNC_FREQUENCY = 240;
	
	private final static String PREFERENCES_BACKUP = "backup";
	private final static String PREFERENCES_BACKUP_LAST_BACKUP_TIME = "backup.last_backup_time";
	private final static String PREFERENCES_BACKUP_FREQUENCY = "backup.backup_frequency";
	private final static String PREFERENCES_BACKUP_PATH = "backup.backup_path";
	
	private final static int DEFAULT_BACKUP_FREQUENCY = 0; // Never

	private TodoistDatabaseHelper mDbHelper = null;
	private Context mContext = null;

	private static final String BACKUP_FILENAME_TEMPLATE = "todoist_backup";
	
	
	// Definitions of columns for tables (used while backing up and restoration)
	
	private static String[] PROJECTS_COLUMN_NAMES = { DBConsts.PROJECTS_ID, DBConsts.PROJECTS_NAME, DBConsts.PROJECTS_COLOR, DBConsts.PROJECTS_INDENT, DBConsts.PROJECTS_ITEM_COUNT, DBConsts.PROJECTS_ORDER, DBConsts.PROJECTS_DIRTY_STATE };
	private static int[] PROJECTS_COLUMN_TYPES = { Types.INTEGER, Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.VARCHAR };
	
	private static String[] LABELS_COLUMN_NAMES = { DBConsts.LABELS_ID, DBConsts.LABELS_NAME, DBConsts.LABELS_COLOR, DBConsts.LABELS_COUNT, DBConsts.LABELS_DIRTY_STATE };
	private static int[] LABELS_COLUMN_TYPES = { Types.INTEGER, Types.VARCHAR, Types.INTEGER, Types.INTEGER, Types.VARCHAR };
	
	private static String[] ITEMS_COLUMN_NAMES = { DBConsts.ITEMS_ID, DBConsts.ITEMS_CONTENT, DBConsts.ITEMS_COMPLETED, DBConsts.ITEMS_DATE_STRING, DBConsts.ITEMS_DUE_DATE, DBConsts.ITEMS_INDENT, DBConsts.ITEMS_NOTE_COUNT, DBConsts.ITEMS_ORDER, DBConsts.ITEMS_PRIORITY, DBConsts.ITEMS_PROJECT_ID, DBConsts.ITEMS_DIRTY_STATE };
	private static int[] ITEMS_COLUMN_TYPES = { Types.INTEGER, Types.VARCHAR, Types.INTEGER, Types.VARCHAR, Types.BIGINT, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.VARCHAR };
				
	private static String[] ITEMS_TO_LABELS_COLUMN_NAMES = { DBConsts.ITEMS_TO_LABELS_ITEM_ID, DBConsts.ITEMS_TO_LABELS_LABEL_ID };
	private static int[] ITEMS_TO_LABELS_COLUMN_TYPES = { Types.INTEGER, Types.INTEGER };
	
	private static String[] NOTES_COLUMN_NAMES = { DBConsts.NOTES_ID, DBConsts.NOTES_CONTENT, DBConsts.NOTES_ITEM_ID, DBConsts.NOTES_POST_DATE, DBConsts.NOTES_DIRTY_STATE };
	private static int[] NOTES_COLUMN_TYPES = { Types.INTEGER, Types.VARCHAR, Types.INTEGER, Types.BIGINT, Types.VARCHAR };
	
	
    // What's the current sort mode for the item list
    public enum ItemSortMode {
    	LAST_USED_ORDER,
    	ORIGINAL_ORDER, // As specified by sortOrder parameter of each item
    	SORT_BY_DUE_DATE
    }
    
    public enum InitialView {
    	FILTER_BY_PROJECTS,
    	FILTER_BY_LABELS,
    	FILTER_BY_QUERIES,
    	FILTER_BY_PROJECTS_OR_LABELS_OR_QUERIES, // Remember last used filter (either by labels or projects or queries)
    	SPECIFIC_PROJECT,
    	SPECIFIC_LABEL,
    	SPECIFIC_QUERY,
    	LAST_VIEWED_PROJECT,
    	LAST_VIEWED_LABEL,
    	LAST_VIEWED_QUERY
    }
    
	private final static InitialView DEFAULT_INITIAL_VIEW = InitialView.FILTER_BY_PROJECTS;

	private static final int DEFAULT_TEXT_SIZE = 17; // In DP
  
	
	public TodoistOfflineStorage(Context context) {
		mContext = context;
		mDbHelper = new TodoistDatabaseHelper(mContext, DBConsts.DATABASE_NAME, null, DBConsts.DATABASE_VERSION);
	}
	
	
	/*
	 * Items-to-Labels related methods
	 */
	
	
	
	/**
	 * Updates an item's label (add/removes labels according to input item label IDs)
	 * @param item
	 */
	public void updateItemLabels(Item item) {
		SQLiteDatabase db;
		
		if (item.labelIds == null) {
			// No labels defined for this item
			return;
		}

		db = mDbHelper.getWritableDatabase();
		
		// First, delete all labels currently attached to this item
		db.delete(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_ITEM_ID + "=?", new String []{ String.valueOf(item.id) });
		
		// Next, add the labels attached to the item
		for (int i = 0; i < item.labelIds.size(); i++) {
			ContentValues labelValues = new ContentValues();
			labelValues.put(DBConsts.ITEMS_TO_LABELS_ITEM_ID, item.id);
			labelValues.put(DBConsts.ITEMS_TO_LABELS_LABEL_ID, item.labelIds.get(i).intValue());
			
			db.replace(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, null, labelValues);
		}
		
	}
	

	/**
	 * Returns all of an items's labels (full Label instances)
	 * @param item
	 * @return
	 */
	public ArrayList<Label> getItemLabels(Item item) {
		SQLiteDatabase db;
		Cursor c;
		ArrayList<Label> labels = new ArrayList<Label>();
		ArrayList<Integer> labelIds = getItemLabelIDs(item);
		
		db = mDbHelper.getWritableDatabase();
		
		for(int i = 0; i < labelIds.size(); i++) {
			int labelId = labelIds.get(i);
			
			// Get label details
			c = db.query(DBConsts.LABELS_TABLE_NAME, null, DBConsts.LABELS_ID +"=?", 
					new String []{ String.valueOf(labelId) },
					null, null, null, null);
			
			if (c.getCount() == 0) {
				Log.e(TAG, String.format("getItemLabels: No label details for label id %d", labelId));
				continue;
			}
			
			c.moveToFirst();
			labels.add(getLabelFromCursor(c));
			c.close();
		}

		
		return labels;
	}
	
	

	/**
	 * Returns an item's label IDs
	 */
	public ArrayList<Integer> getItemLabelIDs(Item item) {
		SQLiteDatabase db;
		Cursor c;
		ArrayList<Integer> labelIds = new ArrayList<Integer>();
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, null, DBConsts.ITEMS_TO_LABELS_ITEM_ID +"=?", 
				new String []{ String.valueOf(item.id) },
				null, null, null, null);
		
		for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			int labelId = c.getInt(c.getColumnIndex(DBConsts.ITEMS_TO_LABELS_LABEL_ID));
			labelIds.add(labelId);
		}

		c.close();
		
		return labelIds;
	}
	
	

	/*
	 * Note related methods
	 */
	
	
	/**
	 * Adds (or updates) a note - note ID must be provided
	 * @param newNote New note instance
	 * @param oldNote Old note instance (optional) - used in case the note ID was changed
	 */
	public void addOrUpdateNote(Note newNote, Note oldNote) {
		SQLiteDatabase db;
		ContentValues values = new ContentValues();
		
		db = mDbHelper.getWritableDatabase();
		
		if ((oldNote != null) && (oldNote.id != newNote.id)) {
			// ID was changed - this happens when a note is added to local storage, but not yet
			// sync'd with online server (thus, it was assigned with a temp ID)
			
			// Delete old note record
			db.delete(DBConsts.NOTES_TABLE_NAME, DBConsts.NOTES_ID + "=?", new String []{ String.valueOf(oldNote.id) });
		}
		
		values.put(DBConsts.NOTES_ID, newNote.id);
		values.put(DBConsts.NOTES_CONTENT, newNote.content);
		values.put(DBConsts.NOTES_ITEM_ID, newNote.itemId);
		values.put(DBConsts.NOTES_POST_DATE, (newNote.postDate != null ? newNote.postDate.getTime() : 0));
		values.put(DBConsts.NOTES_DIRTY_STATE, newNote.dirtyState.toString());
		
		db.replace(DBConsts.NOTES_TABLE_NAME, null, values);
	}
	
	
	/**
	 * Deletes a note (by ID)
	 * @param note
	 */
	public void deleteNote(Note note) {
		SQLiteDatabase db;
		db = mDbHelper.getWritableDatabase();
		
		// Delete the note from the notes table
		db.delete(DBConsts.NOTES_TABLE_NAME, DBConsts.NOTES_ID + "=?", new String []{ String.valueOf(note.id) });
		
	}
	
	/**
	 * Returns a specific note (by ID)
	 * @param itemId
	 * @return
	 */
	public Note getNote(int noteId) {
		SQLiteDatabase db;
		Cursor c = null;
		Note note;
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.NOTES_TABLE_NAME, null, DBConsts.NOTES_ID +"=?", 
				new String []{ String.valueOf(noteId) },
				null, null, null, null);
		
		if (c.getCount() == 0) {
			c.close();
			return null;
		}
		
		c.moveToFirst();
		note = getNoteFromCursor(c);
		c.close();
		
		return note;
	}
	
	

	/**
	 * Returns all of an items's notes (excluding any DELETED notes), sorted by post date
	 * @param item
	 * @return
	 */
	public ArrayList<Note> getNotesByItem(Item item) {
		SQLiteDatabase db;
		Cursor c = null;
		ArrayList<Note> notes = new ArrayList<Note>();
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.NOTES_TABLE_NAME, null, DBConsts.NOTES_ITEM_ID +"=? AND " + DBConsts.NOTES_DIRTY_STATE + "<>?", 
				new String []{ String.valueOf(item.id), DirtyState.DELETED.toString() },
				null, null, DBConsts.NOTES_POST_DATE + " ASC", null);
		
		for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			notes.add(getNoteFromCursor(c));
		}

		c.close();
		
		return notes;
	}
	

	/**
	 * Returns all of a user's notes (for all items)
	 * @return
	 */
	public ArrayList<Note> getNotes() {
		SQLiteDatabase db;
		Cursor c = null;
		ArrayList<Note> notes = new ArrayList<Note>();
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.NOTES_TABLE_NAME, null, null, 
				null, null, null, null, null);
		
		for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			notes.add(getNoteFromCursor(c));
		}

		c.close();
		
		return notes;
	}
	
	/**
	 * Updates the noteCount for an item (actually counts how many notes the item currently has)
	 */
	public void updateItemNoteCount(Item item) {
		SQLiteDatabase db;
		Cursor c = null;
		
		db = mDbHelper.getWritableDatabase();
		
		// Get note count for current item
		c = db.query(DBConsts.NOTES_TABLE_NAME, null, DBConsts.NOTES_ITEM_ID +"=? AND " + DBConsts.NOTES_DIRTY_STATE + "<>?", 
				new String []{ String.valueOf(item.id), DirtyState.DELETED.toString() },
				null, null, null, null);
		
		int noteCount = c.getCount();
		c.close();
		
		if (noteCount != item.noteCount) {
			// Only update the item if its noteCount has been changed
			item.noteCount = noteCount;
			addOrUpdateItem(item, null);
		}
	}
	
	
	
	// Private note related methods
	
	private Note getNoteFromCursor(Cursor c) {
		Note note = new Note();
		
		note.id = c.getInt(c.getColumnIndex(DBConsts.NOTES_ID));
		note.content = c.getString(c.getColumnIndex(DBConsts.NOTES_CONTENT));
		note.itemId = c.getInt(c.getColumnIndex(DBConsts.NOTES_ITEM_ID));
		note.postDate = new Date(c.getLong(c.getColumnIndex(DBConsts.NOTES_POST_DATE)));
		note.dirtyState = DirtyState.valueOf(c.getString(c.getColumnIndex(DBConsts.NOTES_DIRTY_STATE)));
		
		return note;
	}
	

	/*
	 * Label related methods
	 */
	
	
	/**
	 * Adds (or updates) a label - label ID must be provided
	 * @param newLabel New label instance
	 * @param oldLabel Old label instance (optional) - used in case the label ID was changed
	 */
	public void addOrUpdateLabel(Label newLabel, Label oldLabel) {
		SQLiteDatabase db;
		ContentValues values = new ContentValues();
		
		db = mDbHelper.getWritableDatabase();
		
		if ((oldLabel != null) && (oldLabel.id != newLabel.id)) {
			// ID was changed - this happens when an label is added to local storage, but not yet
			// sync'd with online server (thus, it was assigned with a temp ID)
			
			// Delete old label record
			db.delete(DBConsts.LABELS_TABLE_NAME, DBConsts.LABELS_ID + "=?", new String []{ String.valueOf(oldLabel.id) });
			
			// Update all item-to-label with the old label ID (update to new label ID)
			ContentValues labelValues = new ContentValues();
			labelValues.put(DBConsts.ITEMS_TO_LABELS_LABEL_ID, newLabel.id);
			db.update(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, labelValues, DBConsts.ITEMS_TO_LABELS_LABEL_ID + "=?", new String []{ String.valueOf(oldLabel.id) });
		}
		
		values.put(DBConsts.LABELS_ID, newLabel.id);
		values.put(DBConsts.LABELS_NAME, newLabel.name.toLowerCase());
		values.put(DBConsts.LABELS_COLOR, newLabel.colorIndex);
		values.put(DBConsts.LABELS_COUNT, newLabel.count);
		values.put(DBConsts.LABELS_DIRTY_STATE, newLabel.dirtyState.toString());

		db.replace(DBConsts.LABELS_TABLE_NAME, null, values);
		
		if (newLabel.dirtyState == DirtyState.DELETED) {
			// Label was effectively deleted - delete all references to the label from the items_to_labels table
			// (Even though the label wasn't deleted completely - we still need to delete
			// any references to it)
			db.delete(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_LABEL_ID + "=?", new String []{ String.valueOf(newLabel.id) });
		}
		
		

	}
	
	
	/**
	 * Deletes a label (by ID) and all its links to items
	 * @param item
	 */
	public void deleteLabel(Label label) {
		SQLiteDatabase db;
		db = mDbHelper.getWritableDatabase();
		
		// First, delete the label from the labels table
		db.delete(DBConsts.LABELS_TABLE_NAME, DBConsts.LABELS_ID + "=?", new String []{ String.valueOf(label.id) });
		
		// Next, delete all references to the label from the items_to_labels table
		db.delete(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_LABEL_ID + "=?", new String []{ String.valueOf(label.id) });

	}
	/**
	 * Returns a specific label (by ID)
	 * @param labelName
	 * @return
	 */
	public Label getLabelByName(String labelName) {
		SQLiteDatabase db;
		Cursor c = null;
		Label label;
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.LABELS_TABLE_NAME, null, DBConsts.LABELS_NAME +"=?", 
				new String []{ labelName },
				null, null, null, null);
		
		if (c.getCount() == 0) {
			c.close();
			return null;
		}
		
		c.moveToFirst();
		label = getLabelFromCursor(c);
		c.close();
		
		return label;
	}

	/**
	 * Returns a specific label (by ID)
	 * @param labelId
	 * @return
	 */
	public Label getLabel(int labelId) {
		SQLiteDatabase db;
		Cursor c = null;
		Label label;
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.LABELS_TABLE_NAME, null, DBConsts.LABELS_ID +"=?", 
				new String []{ String.valueOf(labelId) },
				null, null, null, null);
		
		if (c.getCount() == 0) {
			c.close();
			return null;
		}
		
		c.moveToFirst();
		label = getLabelFromCursor(c);
		c.close();
		
		return label;
	}
	
	

	/**
	 * Returns all of a user's labels
	 * @return
	 */
	public ArrayList<Label> getLabels() {
		SQLiteDatabase db;
		Cursor c = null;
		ArrayList<Label> labels = new ArrayList<Label>();
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.LABELS_TABLE_NAME, null, null, 
				null, null, null, null, null);
		
		for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			labels.add(getLabelFromCursor(c));
		}

		c.close();
		
		return labels;
	}
	

	/**
	 * Updates the itemCount for all labels (actually counts how many items each labels currently has),
	 * excluding any DELETED and completed items)
	 */
	public void updateLabelsItemCount() {
		SQLiteDatabase db;
		Cursor c = null;
		
		ArrayList<Label> labels = getLabels();
		
		for (int i = 0; i < labels.size(); i++) {
			Label currentLabel = labels.get(i);
			
			if (currentLabel.dirtyState == DirtyState.DELETED) {
				// Label is to be deleted - no need to update its item count
				continue;
			}
			
			db = mDbHelper.getWritableDatabase();
			
			// Get item count for current label (don't count any DELETED items)
			String query = String.format(
					"SELECT COUNT(%s.%s) FROM %s, %s " +
					"WHERE " +
					"(%s.%s = %s.%s) AND " +
					"(%s.%s <> ?) AND " +
					"(%s.%s = ?) AND " +
					"(%s.%s = 0)",
					DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_ITEM_ID,
					DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TABLE_NAME,
					DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_ITEM_ID, DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_ID,
					DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_DIRTY_STATE,
					DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_LABEL_ID,
					DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_COMPLETED);
			c = db.rawQuery(query, new String []{ DirtyState.DELETED.toString(), String.valueOf(currentLabel.id) });
			
			c.moveToFirst();
			int itemCount = c.getInt(0);
			c.close();
			
			
			if (itemCount != currentLabel.count) {
				// Only update the label if its itemCount has been changed
				currentLabel.count = itemCount;
				addOrUpdateLabel(currentLabel, null);
			}
			
		}
	}
	
	
	
	// Private label related methods
	
	private Label getLabelFromCursor(Cursor c) {
		Label label = new Label();
		
		label.id = c.getInt(c.getColumnIndex(DBConsts.LABELS_ID));
		label.name = c.getString(c.getColumnIndex(DBConsts.LABELS_NAME));
		label.colorIndex = c.getInt(c.getColumnIndex(DBConsts.LABELS_COLOR));
		label.count = c.getInt(c.getColumnIndex(DBConsts.LABELS_COUNT));
		label.dirtyState = DirtyState.valueOf(c.getString(c.getColumnIndex(DBConsts.LABELS_DIRTY_STATE)));
		
		return label;
	}
	
	

	/*
	 * Item related methods
	 */
	
	
	/**
	 * Adds (or updates) an item - item ID must be provided
	 * @param newItem New item instance
	 * @param oldItem Old item instance (optional) - used in case the item ID was changed
	 */
	public void addOrUpdateItem(Item newItem, Item oldItem) {
		SQLiteDatabase db;
		ContentValues values = new ContentValues();
		
		db = mDbHelper.getWritableDatabase();

		if ((oldItem != null) && (oldItem.id != newItem.id)) {
			// ID was changed - this happens when an item is added to local storage, but not yet
			// sync'd with online server (thus, it was assigned with a temp ID)
			
			// Delete old item record
			db.delete(DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_ID + "=?", new String []{ String.valueOf(oldItem.id) });
			
			// Update all notes assigned to old item ID (update to new item ID)
			ContentValues noteValues = new ContentValues();
			noteValues.put(DBConsts.NOTES_ITEM_ID, newItem.id);
			db.update(DBConsts.NOTES_TABLE_NAME, noteValues, DBConsts.NOTES_ITEM_ID + "=?", new String []{ String.valueOf(oldItem.id) });
			// Update all labels assigned to old item ID (update to new item ID)
			ContentValues labelValues = new ContentValues();
			labelValues.put(DBConsts.ITEMS_TO_LABELS_ITEM_ID, newItem.id);
			db.update(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, labelValues, DBConsts.ITEMS_TO_LABELS_ITEM_ID + "=?", new String []{ String.valueOf(oldItem.id) });
		}
		
		
		values.put(DBConsts.ITEMS_ID, newItem.id);
		values.put(DBConsts.ITEMS_PROJECT_ID, newItem.projectId);
		values.put(DBConsts.ITEMS_CONTENT, newItem.rawContent);
		values.put(DBConsts.ITEMS_DATE_STRING, newItem.dateString);
		
		// In case the item has no due date, it is saved in the DB as a MAX_LONG value (used when
		// sorting by due date, in order for it to appear last)
		values.put(DBConsts.ITEMS_DUE_DATE, (((newItem.dueDate != null) && (newItem.dueDate.getTime() != 0)) ? newItem.dueDate.getTime() : Long.MAX_VALUE));
		
		values.put(DBConsts.ITEMS_INDENT, newItem.indentLevel);
		values.put(DBConsts.ITEMS_NOTE_COUNT, newItem.noteCount);
		values.put(DBConsts.ITEMS_ORDER, newItem.itemOrder);
		values.put(DBConsts.ITEMS_PRIORITY, newItem.priority);
		values.put(DBConsts.ITEMS_COMPLETED, (newItem.completed == false ? 0 : 1));
		values.put(DBConsts.ITEMS_DIRTY_STATE, newItem.dirtyState.toString());
		
		// Add/update the item in the items list
		db.replace(DBConsts.ITEMS_TABLE_NAME, null, values);
		
	}
	
	/**
	 * Deletes an item (by ID) and all notes and labels attached to it
	 * @param item
	 */
	public void deleteItem(Item item) {
		SQLiteDatabase db;
		db = mDbHelper.getWritableDatabase();
		
		// First, delete the item from the items table
		db.delete(DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_ID + "=?", new String []{ String.valueOf(item.id) });
		
		// Next, delete any notes attached to this item
		db.delete(DBConsts.NOTES_TABLE_NAME, DBConsts.NOTES_ITEM_ID + "=?", new String []{ String.valueOf(item.id) });
		
		// Finally, delete any labels attached to this item
		db.delete(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_ITEM_ID + "=?", new String []{ String.valueOf(item.id) });

	}
	
	/**
	 * Returns a specific item (by ID)
	 * @param itemId
	 * @return
	 */
	public Item getItem(int itemId) {
		SQLiteDatabase db;
		Cursor c = null;
		Item item;
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.ITEMS_TABLE_NAME, null, DBConsts.ITEMS_ID +"=?", 
				new String []{ String.valueOf(itemId) },
				null, null, null, null);
		
		if (c.getCount() == 0) {
			c.close();
			return null;
		}
		
		c.moveToFirst();
		item = getItemFromCursor(c);
		c.close();
		
		// Fill out the item's label IDs
		item.labelIds = getItemLabelIDs(item);
		
		return item;
	}
	
	/**
	 * Returns all of a user's items which are *not* in a dirtyState of DELETED *and*
	 * have a specific label ID
	 * 
	 * @param labelId
	 * @param sortMode
	 * @param getCompleted should completed items be returned as well?
	 * @return
	 */
	public ArrayList<Item> getItemsByLabel(int labelId, ItemSortMode sortMode, boolean getCompleted) {
		SQLiteDatabase db;
		Cursor c = null;
		
		db = mDbHelper.getWritableDatabase();
		
		String query = String.format(
				"SELECT * FROM %s, %s WHERE " +
				"(%s.%s = %s.%s) AND " +
				"(%s.%s <> ?) AND " +
				"(%s.%s = ?) ",
				DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_TABLE_NAME,
				DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_ID, DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_ITEM_ID,
				DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_DIRTY_STATE,
				DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_LABEL_ID);
		
		if (!getCompleted) {
			// Don't return any completed items
			query = String.format("%s AND (%s.%s = 0) ",
					query,
					DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_COMPLETED);
		}
				
		// Sort mode
		query = String.format("%s ORDER BY %s",
				query,
				getOrderby(sortMode));
		
		c = db.rawQuery(query, new String[]{ DirtyState.DELETED.toString(), String.valueOf(labelId) });
		
		return fillItemsFromCursor(db, c);
	}

	
	/**
	 * Returns all of a user's items which are *not* in a dirtyState of DELETED *and*
	 * have a specific project ID
	 * 
	 * @param projectId
	 * @param sortMode
	 * @param getCompleted should completed items be returned as well?
	 * @return
	 */
	public ArrayList<Item> getItemsByProject(int projectId, ItemSortMode sortMode, boolean getCompleted) {
		SQLiteDatabase db;
		Cursor c = null;
		
		db = mDbHelper.getWritableDatabase();
		
		String whereQuery = DBConsts.ITEMS_DIRTY_STATE + "<>? AND " + DBConsts.ITEMS_PROJECT_ID + "=?";
		
		if (!getCompleted) {
			// Return only completed items
			whereQuery += " AND " + DBConsts.ITEMS_COMPLETED + "=0";
		}
		
		c = db.query(DBConsts.ITEMS_TABLE_NAME, null, whereQuery, 
				new String []{ DirtyState.DELETED.toString(), String.valueOf(projectId) }, null, null, getOrderby(sortMode), null);
		
		return fillItemsFromCursor(db, c);
	}

	
	/**
	 * Returns all of a user's items which are *not* in a dirtyState of DELETED
	 * @return
	 */
	public ArrayList<Item> getNonDeletedItems() {
		SQLiteDatabase db;
		Cursor c = null;
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.ITEMS_TABLE_NAME, null, DBConsts.ITEMS_DIRTY_STATE + "<>?", 
				new String []{ DirtyState.DELETED.toString() }, null, null, null, null);
		
		return fillItemsFromCursor(db, c);
	}

	

	/**
	 * Returns all of a user's items
	 * @param getCompleted should completed items be shown as well?
	 * @param getDeleted should locally-deleted items be returned?
	 * @param sortMode the order in which to return items
	 * @return
	 */
	public ArrayList<Item> getAllItems(boolean getCompleted, boolean getDeleted, ItemSortMode sortMode) {
		SQLiteDatabase db;
		Cursor c = null;
		
		db = mDbHelper.getWritableDatabase();
		
		String query = String.format("SELECT * FROM %s", DBConsts.ITEMS_TABLE_NAME);
		
		if ((!getCompleted) || (!getDeleted)) {
			query += " WHERE ";
		}
		
		if (!getCompleted) {
			// Return only non-completed items
			query += String.format("%s = 0", DBConsts.ITEMS_COMPLETED);
		}
		
		if (!getDeleted) {
			if (!getCompleted) {
				query += " AND ";
			}
			// Return only non-deleted items
			query += String.format("%s <> '%s'", DBConsts.ITEMS_DIRTY_STATE, DirtyState.DELETED.toString());
		}
		
		// Add the item order
		query += String.format(" ORDER BY %s", getOrderby(sortMode));
		
		c = db.rawQuery(query, new String[] {});
		
		return fillItemsFromCursor(db, c);
	}
	
	
	
	// Private item related methods
	
	
	/**
	 * Private utility function that returns the "order by" query string in
	 * regards to the input item sort mdoe
	 * 
	 * @param sortMode
	 * @return
	 */
	private String getOrderby(ItemSortMode sortMode) {
		if (sortMode == ItemSortMode.ORIGINAL_ORDER) {
			// First, completed items at the end, then by project (if we're in filter by label
			// mode, this will group items from same project together) finally by itemOrder
			return String.format("%s ASC, %s ASC, %s ASC",
					DBConsts.ITEMS_COMPLETED, DBConsts.ITEMS_PROJECT_ID, DBConsts.ITEMS_ORDER);
			
		} else /*if (sortMode == ItemSortMode.SORT_BY_DUE_DATE) */ {
			// First, completed items at the end, afterwards sort by dueDate, then sort by priority, finally sort by project
			return String.format("%s ASC, %s ASC, %s DESC, %s ASC",
					DBConsts.ITEMS_COMPLETED, DBConsts.ITEMS_DUE_DATE,
					DBConsts.ITEMS_PRIORITY, DBConsts.ITEMS_PROJECT_ID);
		}
		
	}
	
	/**
	 * Private utility function for retrieving items a result cursor; for each item,
	 * also fills out its label IDs
	 * @param c
	 * @return
	 */
	private ArrayList<Item> fillItemsFromCursor(SQLiteDatabase db, Cursor c) {
		ArrayList<Item> items = new ArrayList<Item>();
		
		for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			Item newItem = getItemFromCursor(c);
			newItem.labelIds = getItemLabelIDs(newItem);
			items.add(newItem);
		}
		
		c.close();
		
		return items;
	}
	

	private Item getItemFromCursor(Cursor c) {
		Item item = new Item();
		
		item.id = c.getInt(c.getColumnIndex(DBConsts.ITEMS_ID));
		item.projectId = c.getInt(c.getColumnIndex(DBConsts.ITEMS_PROJECT_ID));
		item.rawContent = c.getString(c.getColumnIndex(DBConsts.ITEMS_CONTENT));
		item.dateString = c.getString(c.getColumnIndex(DBConsts.ITEMS_DATE_STRING));
		
		// In case the item has no due date, it is saved in the DB as a MAX_LONG value (used when
		// sorting by due date, in order for it to appear last)
		long dateNum = c.getLong(c.getColumnIndex(DBConsts.ITEMS_DUE_DATE));
		item.dueDate = new Date((dateNum == Long.MAX_VALUE ? 0 : dateNum));
		
		item.indentLevel = c.getInt(c.getColumnIndex(DBConsts.ITEMS_INDENT));
		item.noteCount = c.getInt(c.getColumnIndex(DBConsts.ITEMS_NOTE_COUNT));
		item.itemOrder = c.getInt(c.getColumnIndex(DBConsts.ITEMS_ORDER));
		item.priority = c.getInt(c.getColumnIndex(DBConsts.ITEMS_PRIORITY));
		item.completed = (c.getInt(c.getColumnIndex(DBConsts.ITEMS_COMPLETED)) == 0 ? false : true);
		item.dirtyState = DirtyState.valueOf(c.getString(c.getColumnIndex(DBConsts.ITEMS_DIRTY_STATE)));
		
		return item;
	}
	
	
	
	/*
	 * Projects related methods
	 */
	
	
	/**
	 * Adds (or updates) a project - project ID must be provided
	 * @param newProject New project instance
	 * @param oldProject Old project instance (optional) - used in case the project ID was changed
	 */
	public void addOrUpdateProject(Project newProject, Project oldProject) {
		SQLiteDatabase db;
		ContentValues values = new ContentValues();
		
		db = mDbHelper.getWritableDatabase();
		
		if ((oldProject != null) && (oldProject.id != newProject.id)) {
			// ID was changed - this happens when a project is added to local storage, but not yet
			// sync'd with online server (thus, it was assigned with a temp ID)
			
			// Delete old project record
			db.delete(DBConsts.PROJECTS_TABLE_NAME, DBConsts.PROJECTS_ID + "=?", new String []{ String.valueOf(oldProject.id) });
			
			// Update all items under that project (update to new project ID)
			ContentValues itemValues = new ContentValues();
			itemValues.put(DBConsts.ITEMS_PROJECT_ID, newProject.id);
			db.update(DBConsts.ITEMS_TABLE_NAME, itemValues, DBConsts.ITEMS_PROJECT_ID + "=?", new String []{ String.valueOf(oldProject.id) });
		}
		
		values.put(DBConsts.PROJECTS_ID, newProject.id);
		values.put(DBConsts.PROJECTS_NAME, newProject.rawName);
		values.put(DBConsts.PROJECTS_COLOR, newProject.colorIndex);
		values.put(DBConsts.PROJECTS_INDENT, newProject.indentLevel);
		values.put(DBConsts.PROJECTS_ITEM_COUNT, newProject.itemCount);
		values.put(DBConsts.PROJECTS_ORDER, newProject.itemOrder);
		values.put(DBConsts.PROJECTS_DIRTY_STATE, newProject.dirtyState.toString());
		
		
		db.replace(DBConsts.PROJECTS_TABLE_NAME, null, values);
	}
	
	
	/**
	 * Deletes a project (by ID)
	 * @param project
	 */
	public void deleteProject(Project project) {
		SQLiteDatabase db;
		
		db = mDbHelper.getWritableDatabase();
		
		// Delete the project from the project table
		db.delete(DBConsts.PROJECTS_TABLE_NAME, DBConsts.PROJECTS_ID + "=?", new String []{ String.valueOf(project.id) });
		
	}
	
	/**
	 * Returns a specific project (by ID)
	 * @param projectId
	 * @return
	 */
	public Project getProject(int projectId) {
		SQLiteDatabase db;
		Cursor c = null;
		Project project;
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.PROJECTS_TABLE_NAME, null, DBConsts.PROJECTS_ID +"=?", 
				new String []{ String.valueOf(projectId) },
				null, null, null, null);
		
		if (c.getCount() == 0) {
			c.close();
			return null;
		}
		
		c.moveToFirst();
		project = getProjectFromCursor(c);
		c.close();
		
		return project;
	}
	
	

	/**
	 * Returns all of a user's projects
	 * @return
	 */
	public ArrayList<Project> getProjects() {
		SQLiteDatabase db;
		Cursor c = null;
		ArrayList<Project> projects = new ArrayList<Project>();
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.PROJECTS_TABLE_NAME, null, null, 
				null, null, null, null, null);
		
		for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			projects.add(getProjectFromCursor(c));
		}

		c.close();
		
		return projects;
	}
	
	/**
	 * Returns the max order out of all projects (excluding any DELETED projects)
	 */
	public int getProjectsMaxOrder() {
		SQLiteDatabase db;
		Cursor c = null;
	
		db = mDbHelper.getWritableDatabase();
		
		// Get order out of all projcets
		c = db.query(DBConsts.PROJECTS_TABLE_NAME, new String []{ "MAX(" + DBConsts.PROJECTS_ORDER + ")" },
				DBConsts.ITEMS_DIRTY_STATE + "<>?",
				new String []{ DirtyState.DELETED.toString() },
				null, null, null, null);
		
		c.moveToFirst();
		int maxOrder = c.getInt(0);
		c.close();
		
		return maxOrder;
	}

	
	/**
	 * Returns the max item order out of all items in a specific project (excluding any DELETED items)
	 */
	public int getMaxItemOrderForProject(Project currentProject) {
		SQLiteDatabase db;
		Cursor c = null;
	
		db = mDbHelper.getWritableDatabase();
		
		// Get max item order for current project
		c = db.query(DBConsts.ITEMS_TABLE_NAME, new String []{ "MAX(" + DBConsts.ITEMS_ORDER + ")" },
				DBConsts.ITEMS_PROJECT_ID +"=? AND " + DBConsts.ITEMS_DIRTY_STATE + "<>?",
				new String []{ String.valueOf(currentProject.id), DirtyState.DELETED.toString() },
				null, null, null, null);
		
		c.moveToFirst();
		int maxItemOrder = c.getInt(0);
		c.close();
		
		return maxItemOrder;
	}

	
	/**
	 * Updates the itemCount for a projects (actually counts how many items the project currently has,
	 * excluding any items which are marked as DELETED or completed)
	 */
	public void updateProjectItemCount(Project currentProject) {
		SQLiteDatabase db;
		Cursor c = null;
	
		db = mDbHelper.getWritableDatabase();
		
		// Get item count for current project
		c = db.query(DBConsts.ITEMS_TABLE_NAME, new String []{ "COUNT(" + DBConsts.ITEMS_ID + ")" },
				DBConsts.ITEMS_PROJECT_ID +"=? AND " + DBConsts.ITEMS_DIRTY_STATE + "<>? AND " + DBConsts.ITEMS_COMPLETED + "=0", 
				new String []{ String.valueOf(currentProject.id), DirtyState.DELETED.toString() },
				null, null, null, null);
		
		c.moveToFirst();
		int itemCount = c.getInt(0);
		c.close();
		
		
		if (itemCount != currentProject.itemCount) {
			// Only update the project if its itemCount has been changed
			currentProject.itemCount = itemCount;
			addOrUpdateProject(currentProject, null);
		}

	}
	
	/**
	 * Updates the itemCount for all projects (actually counts how many items each project currently has,
	 * excluding any items which are marked as DELETED)
	 */
	public void updateAllProjectsItemCount() {
		ArrayList<Project> projects = getProjects();
		
		for (int i = 0; i < projects.size(); i++) {
			updateProjectItemCount(projects.get(i));
		}
	}
	
	
	// Private project related methods
	
	private Project getProjectFromCursor(Cursor c) {
		Project project = new Project();
		
		project.id = c.getInt(c.getColumnIndex(DBConsts.PROJECTS_ID));
		project.rawName = c.getString(c.getColumnIndex(DBConsts.PROJECTS_NAME));
		project.colorIndex = c.getInt(c.getColumnIndex(DBConsts.PROJECTS_COLOR));
		project.indentLevel = c.getInt(c.getColumnIndex(DBConsts.PROJECTS_INDENT));
		project.itemCount = c.getInt(c.getColumnIndex(DBConsts.PROJECTS_ITEM_COUNT));
		project.itemOrder = c.getInt(c.getColumnIndex(DBConsts.PROJECTS_ORDER));
		project.dirtyState = DirtyState.valueOf(c.getString(c.getColumnIndex(DBConsts.PROJECTS_DIRTY_STATE)));
		
		return project;
	}
	
	
	/*
	 * Query related methods
	 */
	
	/**
	 * Adds (or updates) a query - query ID must be provided
	 * @param newQuery New query instance
	 */
	public void addOrUpdateQuery(Query newQuery) {
		SQLiteDatabase db;
		ContentValues values = new ContentValues();
		
		db = mDbHelper.getWritableDatabase();
		
		values.put(DBConsts.QUERIES_ID, newQuery.id);
		values.put(DBConsts.QUERIES_NAME, newQuery.name);
		values.put(DBConsts.QUERIES_QUERY, newQuery.query);
		
		// Add/update the query in the queries list
		db.replace(DBConsts.QUERIES_TABLE_NAME, null, values);
		
	}
	
	/**
	 * Deletes a query (by ID)
	 * @param query
	 */
	public void deleteQuery(Query query) {
		SQLiteDatabase db;
		db = mDbHelper.getWritableDatabase();
		
		// Delete the query from the queries table
		db.delete(DBConsts.QUERIES_TABLE_NAME, DBConsts.QUERIES_ID + "=?", new String []{ String.valueOf(query.id) });
		
	}

	/**
	 * Returns all of a user's queries
	 * @return
	 */
	public ArrayList<Query> getQueries() {
		SQLiteDatabase db;
		Cursor c = null;
		
		db = mDbHelper.getWritableDatabase();
		
		// Return all queries
		c = db.query(DBConsts.QUERIES_TABLE_NAME, null, null, 
				null, null, null, null, null);
		
		ArrayList<Query> queries = new ArrayList<Query>();
		
		for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			queries.add(getQueryFromCursor(c));
		}

		c.close();
		
		return queries;
	}
	
	/**
	 * Returns a specific query (by ID)
	 * @param queryId
	 * @return
	 */
	public Query getQuery(int queryId) {
		SQLiteDatabase db;
		Cursor c = null;
		Query query;
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(DBConsts.QUERIES_TABLE_NAME, null, DBConsts.QUERIES_ID +"=?", 
				new String []{ String.valueOf(queryId) },
				null, null, null, null);
		
		if (c.getCount() == 0) {
			c.close();
			return null;
		}
		
		c.moveToFirst();
		query = getQueryFromCursor(c);
		c.close();
		
		return query;
	}

	private Query getQueryFromCursor(Cursor c) {
		Query query = new Query();
		
		query.id = c.getInt(c.getColumnIndex(DBConsts.QUERIES_ID));
		query.name = c.getString(c.getColumnIndex(DBConsts.QUERIES_NAME));
		query.query = c.getString(c.getColumnIndex(DBConsts.QUERIES_QUERY));
		
		return query;
	}
	

	
	/**
	 * Returns a list of items by a specific query (e.g. "today, tomorrow, p1, p2")
	 * @param query
	 * @param getCompleted should completed items be shown as well?
	 */
	public ArrayList<Item> getItemsByQuery(String query, boolean getCompleted) {
		String[] subQueries = query.split(",");
		ArrayList<Item> results = new ArrayList<Item>();
		ArrayList<Item> subQueryResults;
		Hashtable<Integer, Boolean> currentResults = new Hashtable<Integer, Boolean>();
		
		// Split into sub-queries, and parse each sub-query
		for (int i = 0; i < subQueries.length; i++) {
			subQueryResults = getItemsBySubQuery(subQueries[i], getCompleted);
			
			// Filter results of current sub-query (so we won't get duplicates in the
			// entire query result list)
			Iterator<Item> iter = subQueryResults.iterator();
			
			while (iter.hasNext()) {
				Item item = iter.next();
				Integer itemId = new Integer(item.id);
				
				if (!currentResults.containsKey(itemId)) {
					results.add(item);
					currentResults.put(itemId, true); // Remember that this item was returned from one of the sub-queries
				}
			}
		}
		
		return results;
	}
	
	
	private final static String REGEX_RELATIVE_DAYS = "today|tomorrow";
	private final static String REGEX_RELATIVE_DAYS_SHORT = "tod|tom";
	private final static String REGEX_DAYS_OF_WEEK = "sunday|monday|tuesday|wednesday|thursday|friday|saturday";
	private final static String REGEX_DAYS_OF_WEEK_SHORT = "sun|mon|tue|wed|thu|fri|sat";
	private final static String REGEX_MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december";
	private final static String REGEX_MONTHS_SHORT = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec";
	
	private final static String REGEX_DATE = "((?:3[01])|(?:[12]\\d)|(?:0?[1-9]))";
	private final static String REGEX_DATE_SEPARATOR = "[ \\-\\.\\/]";
	private final static String REGEX_DATE_MONTH = "((?:0?[1-9])|(?:1[0-2])|" + REGEX_MONTHS + "|" + REGEX_MONTHS_SHORT + ")";
	private final static String REGEX_DATE_YEAR = "(\\d\\d\\d\\d)";
	private final static String REGEX_DATE_FULL =
		"(?:" +
			"(?:" +
				REGEX_DATE +
				"(?:" + REGEX_DATE_SEPARATOR + REGEX_DATE_MONTH +
					"(?:" + REGEX_DATE_SEPARATOR + REGEX_DATE_YEAR + ")?" +
				")?" +
			")|" +
			"(?:" +
				"(" + REGEX_MONTHS + "|" + REGEX_MONTHS_SHORT + ") " +
				REGEX_DATE +
				"(?: " + REGEX_DATE_YEAR + ")?" +
			")" +
		")";

	private final static String REGEX_CONTEXTUAL_DATE =
		"(?:" +
			"(?:(next) )?" +
			"(" +
				REGEX_RELATIVE_DAYS + "|" + REGEX_RELATIVE_DAYS_SHORT + "|" +
				REGEX_DAYS_OF_WEEK + "|" + REGEX_DAYS_OF_WEEK_SHORT +
			")" +
		")|" +
		"(?:" +
			REGEX_DATE_FULL +
		")";
	
	private final static String REGEX_PRIORITY = "(?:priority|p)\\s*([1-4])";
	private final static String REGEX_LABEL = "\\s*\\@([a-zA-Z0-9_-]+)$";
	private final static String REGEX_DAYS_SCHEDULE = "(\\d+)\\s*days";

	
	/**
	 * Returns the start and end (in seconds since epoch) of a specific date, as set by c parameter
	 * @param c
	 * @return an array of two items - start and end of the day
	 */
	private long[] getDayStartAndEnd(Calendar c) {
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		long start = c.getTime().getTime();
		
		c.add(Calendar.DAY_OF_MONTH, 1);
		c.add(Calendar.MILLISECOND, -1);
		long end = c.getTime().getTime();
		
		return new long[]{ start, end };
	}
	
	private int parseWeekDay(String day) {
		if ((day.equals("sunday")) || (day.equals("sun")))
			return Calendar.SUNDAY;
		else if ((day.equals("monday")) || (day.equals("mon")))
			return Calendar.MONDAY;
		else if ((day.equals("tuesday")) || (day.equals("tue")))
			return Calendar.TUESDAY;
		else if ((day.equals("wednesday")) || (day.equals("wed")))
			return Calendar.WEDNESDAY;
		else if ((day.equals("thursday")) || (day.equals("thu")))
			return Calendar.THURSDAY;
		else if ((day.equals("friday")) || (day.equals("fri")))
			return Calendar.FRIDAY;
		else if ((day.equals("saturday")) || (day.equals("sat")))
			return Calendar.SATURDAY;
		else
			return 0;
	}
	
	/**
	 * Private utility function - since Java's % operator doesn't properly handle a negative X (in "X % Y")
	 * @param x
	 * @param y
	 * @return
	 */
	private int modulus(int x, int y)
	{
	    int result = x % y;
	    return (result < 0 ? (result + y) : result);
	}

	/**
	 * Parses the results of a regular expression (REGEX_CONTEXTUAL_DATE) into a Calendar instance
	 * Possible date queries: today, tomorrow, next friday, 10/5, ...
	 * 
	 * @param matcher
	 * @return
	 */
	private Calendar getDateFromQuery(Matcher matcher) {
		Calendar c = Calendar.getInstance();
		
		c.set(Calendar.HOUR_OF_DAY, 0);
		
		if (matcher.group(2) != null) {
			// "(Next) friday", "today", ...
			
			boolean isNext = (matcher.group(1) != null);
			String day = matcher.group(2);
			
			if ((day.compareTo("today") == 0) || (day.compareTo("tod") == 0)) {
				// Do nothing - use today's date
			} else if ((day.compareTo("tomorrow") == 0) || (day.compareTo("tom") == 0)) {
				c.add(Calendar.DAY_OF_MONTH, 1);
			} else {
				// It's a weekday (Sunday/Monday/...)
				int currentDay = c.get(Calendar.DAY_OF_WEEK);
				int dayValue = 0;
				
				dayValue = parseWeekDay(day);
				
				c.add(Calendar.DAY_OF_WEEK, modulus(dayValue - currentDay, 7));
				
				if (isNext) // Next Sunday/Monday/...
					c.add(Calendar.DAY_OF_WEEK, 7);
					
			}
			
		} else {
			// "10/5", "10", ...
			User user = loadUser();
			c = calculateRealDate(matcher, user.dateFormat);
		}
		
		return c;
	}
	
	/**
	 * Private utility function for calculating a real date string
	 * @param matcher result from matching the regular expression for a real date
	 * @param dateFormat dd-mm-yyyy or mm-dd-yyyy?
	 */
	private Calendar calculateRealDate(Matcher matcher, DateFormat dateFormat) {
		Calendar c = Calendar.getInstance();
		
		// Set to specific hour/minute in day
		c.set(Calendar.HOUR_OF_DAY, 0);
		
		if ((matcher.group(3) != null) && (matcher.group(4) == null)) {
			// Day-of-month only
			int dayOfMonth = Integer.valueOf(matcher.group(3));
			
			if (c.get(Calendar.DAY_OF_MONTH) > dayOfMonth) {
				// We're pass that date - assume next month
				c.add(Calendar.MONTH, 1);
			}
			
			c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
			
		} else if (((matcher.group(3) != null) && (matcher.group(4) != null) && (matcher.group(5) == null)) ||
				((matcher.group(6) != null) && (matcher.group(7) != null) && (matcher.group(8) == null))) {
			// Day-of-month and month only (either "23 sep" or "sep 23")
			
			if (matcher.group(4) != null) {
				// e.g. 23-09
				
				if (dateFormat == DateFormat.DD_MM_YYYY) {
					c.set(Calendar.MONTH, parseMonth(matcher.group(4)));
					c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(matcher.group(3)));
				} else if (dateFormat == DateFormat.MM_DD_YYYY) {
					c.set(Calendar.MONTH, parseMonth(matcher.group(3)));
					c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(matcher.group(4)));
				}
			} else {
				// e.g. sep 23
				c.set(Calendar.MONTH, parseMonth(matcher.group(6)));
				c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(matcher.group(7)));
			}
			
			if (c.before(Calendar.getInstance())) {
				// We're pass that date - assume next year
				c.add(Calendar.YEAR, 1);
			}
			
		} else if (((matcher.group(3) != null) && (matcher.group(4) != null) && (matcher.group(5) != null)) ||
				((matcher.group(6) != null) && (matcher.group(7) != null) && (matcher.group(8) != null))) {
			// All date fields provided
			
			if (matcher.group(5) != null) {
				// e.g. 27-09-2009
				if (dateFormat == DateFormat.DD_MM_YYYY) {
					c.set(
							Integer.valueOf(matcher.group(5)), /* Year */
							parseMonth(matcher.group(4)), /* Month */
							Integer.valueOf(matcher.group(3)) /* Day of month */
						);
				} else if (dateFormat == DateFormat.MM_DD_YYYY) {
					c.set(
							Integer.valueOf(matcher.group(5)), /* Year */
							parseMonth(matcher.group(3)), /* Month */
							Integer.valueOf(matcher.group(4)) /* Day of month */
						);
				}
			} else {
				// e.g. sep 27 2009
				c.set(
					Integer.valueOf(matcher.group(8)), /* Year */
					parseMonth(matcher.group(6)), /* Month */
					Integer.valueOf(matcher.group(7)) /* Day of month */
				);
			}
		}
		
		return c;
	}
	
	/**
	 * Private utility function for parsing a month field - either a number of a named month (e.g. April/apr)
	 * @param value
	 * @return Calendar.JANUARY to Calendar.DECEMBER
	 */
	private int parseMonth(String month) {
		if ((month.equals("january")) || (month.equals("jan")))
			return Calendar.JANUARY;
		else if ((month.equals("february")) || (month.equals("feb")))
			return Calendar.FEBRUARY;
		else if ((month.equals("march")) || (month.equals("mar")))
			return Calendar.MARCH;
		else if ((month.equals("april")) || (month.equals("apr")))
			return Calendar.APRIL;
		else if (month.equals("may"))
			return Calendar.MAY;
		else if ((month.equals("june")) || (month.equals("jun")))
			return Calendar.JUNE;
		else if ((month.equals("july")) || (month.equals("jul")))
			return Calendar.JULY;
		else if ((month.equals("august")) || (month.equals("aug")))
			return Calendar.AUGUST;
		else if ((month.equals("september")) || (month.equals("sep")))
			return Calendar.SEPTEMBER;
		else if ((month.equals("october")) || (month.equals("oct")))
			return Calendar.OCTOBER;
		else if ((month.equals("november")) || (month.equals("nov")))
			return Calendar.NOVEMBER;
		else if ((month.equals("december")) || (month.equals("dec")))
			return Calendar.DECEMBER;
		else // Numeric month
			return Integer.valueOf(month) - 1; // -1 since Calendar.JANUARY == 0 (and not 1)
	}

	/**
	 * Internal method used for parsing and finding items of a single sub-query (e.g.
	 * "today, tomorrow, p1, p2" is split into 4 sub-queries).
	 * Makes sure we don't return items that were found in previous sub-queries (using
	 * the currentResults parameter)
	 * 
	 * @param subQuery
	 * @param getCompleted should completed items be shown as well?
	 * @return
	 */
	private ArrayList<Item> getItemsBySubQuery(String subQuery, boolean getCompleted) {
		String filterQuery = null;
		String labelName = null;
		Matcher matcher;
		subQuery = subQuery.trim().toLowerCase();
		ArrayList<Item> items = new ArrayList<Item>();
		
		Pattern patternContextualDate = Pattern.compile(REGEX_CONTEXTUAL_DATE, Pattern.CASE_INSENSITIVE);
		Pattern patternPriority = Pattern.compile(REGEX_PRIORITY, Pattern.CASE_INSENSITIVE);
		Pattern patternLabel = Pattern.compile(REGEX_LABEL, Pattern.CASE_INSENSITIVE);
		Pattern patternDaysSchedule = Pattern.compile(REGEX_DAYS_SCHEDULE, Pattern.CASE_INSENSITIVE);
		
		if ((subQuery.equals("viewall")) || (subQuery.equals("va"))) {
			// Return all items (excluding deleted items)
			return this.getAllItems(getCompleted, false, ItemSortMode.SORT_BY_DUE_DATE);
		}
		
		matcher = patternLabel.matcher(subQuery);
		if (matcher.find()) { /* Do a find() and not a matches() since the pattern appears at the end */
			labelName = matcher.group(1);
			subQuery = subQuery.substring(0, matcher.start(0));
			
			if (subQuery.length() == 0) {
				// Just a filter by label
				Label label = this.getLabelByName(labelName);
				if (label != null) {
					int labelId = label.id;
					return this.getItemsByLabel(labelId, ItemSortMode.SORT_BY_DUE_DATE, getCompleted);
				} else {
					// Filter by label where label name doesn't exist - return an empty list
					return new ArrayList<Item>();
				}
			}
		}
		
		matcher = patternDaysSchedule.matcher(subQuery);
		if (matcher.matches()) {
			// 7 days, ...
			
			// Get all items within the due date of today and X days from now
			int dayCount = Integer.valueOf(matcher.group(1));
			
			Calendar c = Calendar.getInstance();
			long[] scheduleStart = getDayStartAndEnd(c);
			c = Calendar.getInstance(); c.add(Calendar.DAY_OF_MONTH, dayCount);
			long[] scheduleEnd = getDayStartAndEnd(c);
			
			// Sort by due date, then by priority
			filterQuery = String.format("%s BETWEEN %d AND %d ORDER BY %s ASC, %s DESC",
					DBConsts.ITEMS_DUE_DATE, scheduleStart[0], scheduleEnd[1],
					DBConsts.ITEMS_DUE_DATE, DBConsts.ITEMS_PRIORITY);
			
		}

		matcher = patternContextualDate.matcher(subQuery);
		if (matcher.matches()) {
			// A date query - "today", "tomorrow", "10/5", "next friday", ...
			long[] day = getDayStartAndEnd(getDateFromQuery(matcher));
			
			// Sort by due date, then by priority
			filterQuery = String.format("%s BETWEEN %d AND %d ORDER BY %s ASC, %s DESC",
					DBConsts.ITEMS_DUE_DATE, day[0], day[1],
					DBConsts.ITEMS_DUE_DATE, DBConsts.ITEMS_PRIORITY);
			
			Log.e(TAG, String.format("Query: %s", filterQuery));
			
		} else if ((subQuery.equals("overdue")) || (subQuery.equals("od"))) {
			// Overdue items
			Calendar c = Calendar.getInstance();
			long[] day = getDayStartAndEnd(c);
			
			// Any items with a due date before today (sort by due date, then by priority)
			filterQuery = String.format("%s < %d ORDER BY %s ASC, %s DESC",
					DBConsts.ITEMS_DUE_DATE, day[0],
					DBConsts.ITEMS_DUE_DATE, DBConsts.ITEMS_PRIORITY);
		}
		
		matcher = patternPriority.matcher(subQuery);
		if (matcher.matches()) {
			// Filter by priority
			int priority = 5 - Integer.valueOf(matcher.group(1)); // -5 since Todoist servers (and our DB) save it backwards
			
			// Sort by due date (after filtering by a specific priority)
			filterQuery = String.format("%s = %d ORDER BY %s ASC",
					DBConsts.ITEMS_PRIORITY, priority,
					DBConsts.ITEMS_DUE_DATE);
		}
		
		if (filterQuery == null) {
			// Invalid query
			return new ArrayList<Item>();
		}
		
		if (!getCompleted) {
			// Don't return completed items
			filterQuery = String.format("%s = 0 AND %s", DBConsts.ITEMS_COMPLETED, filterQuery);
		}
		
		// Don't return deleted items
		filterQuery = String.format("%s <> '%s' AND %s",
				DBConsts.ITEMS_DIRTY_STATE, DirtyState.DELETED.toString(),
				filterQuery);
		
		
		SQLiteDatabase db;
		Cursor cursor;
		
		if (labelName == null) {
			// "Regular" query
			String query = String.format(
					"SELECT * FROM %s " +
					"WHERE %s",
					DBConsts.ITEMS_TABLE_NAME,
					filterQuery);
			
			db = mDbHelper.getWritableDatabase();
			cursor = db.rawQuery(query, new String[] {});
			
		} else {
			// Query with a specific label name - need to involve several tables
			
			int labelId = 0;
			Label label = this.getLabelByName(labelName);
			
			if (label != null) {
				labelId = label.id;
			} else {
				// Filter by label where label name doesn't exist - return an empty list
				return new ArrayList<Item>();
			}
			
			String query = String.format(
					"SELECT * FROM %s,%s " +
					"WHERE " +
					"(%s.%s = %d) AND " +
					"(%s.%s = %s.%s) AND %s",
					DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_TABLE_NAME,
					DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_LABEL_ID, labelId,
					DBConsts.ITEMS_TO_LABELS_TABLE_NAME, DBConsts.ITEMS_TO_LABELS_ITEM_ID, DBConsts.ITEMS_TABLE_NAME, DBConsts.ITEMS_ID,
					filterQuery);
			
			db = mDbHelper.getWritableDatabase();
			cursor = db.rawQuery(query, new String[] { });
		
		}
		
		for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
			Item item = getItemFromCursor(cursor);
			
			item.labelIds = getItemLabelIDs(item); // Fill-in label IDs
			items.add(item);
		}
		
		cursor.close();

		return items;
	}
	
	
	/*
	 * General methods
	 */
	
	
	
	/**
	 * Clears storage completely - both user data (stored in preferences) and
	 * item/project/etc data (stored in database)
	 */
	public void clearStorage() {
		
		// First, clear user data
		
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_USER, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();

		editor.putInt(PREFERENCES_USER_ID, 0);
		editor.putString(PREFERENCES_USER_EMAIL, null);
		editor.putString(PREFERENCES_USER_PASSWORD, null);
		editor.putString(PREFERENCES_USER_FULL_NAME, null);
		editor.putString(PREFERENCES_USER_TIMEZONE, null);
		editor.putInt(PREFERENCES_USER_TIMEZONE_OFFSET_MINS, 0);
		editor.putBoolean(PREFERENCES_USER_TIMEZONE_DAYLIGHT_SAVINGS, false);
		editor.putString(PREFERENCES_USER_TIME_FORMAT, null);
		editor.putString(PREFERENCES_USER_DATE_FORMAT, null);
		editor.putLong(PREFERENCES_USER_PREMIUM_UNTIL, 0);
		editor.putBoolean(PREFERENCES_USER_PROFILE_MODIFIED, false);
		
		editor.commit();
		
		// Next, clear database (projects/items/labels/notes)
		clearTodoistData();
	}
	
	
	/**
	 * Clears Todoist data only (Projects/Labels/Items/Notes)
	 */
	public void clearTodoistData() {
		SQLiteDatabase db;

		db = mDbHelper.getWritableDatabase();
		
		db.delete(DBConsts.PROJECTS_TABLE_NAME, null, null);
		db.delete(DBConsts.ITEMS_TABLE_NAME, null, null);
		db.delete(DBConsts.LABELS_TABLE_NAME, null, null);
		db.delete(DBConsts.NOTES_TABLE_NAME, null, null);
		db.delete(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, null, null);
	}
	
	
	/*
	 * User related methods
	 */
	
	
	/**
	 * Saves user information
	 * 
	 * @param user
	 */
	public void saveUser(User user) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_USER, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_USER_ID, user.id);
		editor.putString(PREFERENCES_USER_EMAIL, user.email);
		editor.putString(PREFERENCES_USER_PASSWORD, user.password);
		editor.putString(PREFERENCES_USER_FULL_NAME, user.fullName);
		editor.putString(PREFERENCES_USER_TIMEZONE, user.timezone);
		editor.putInt(PREFERENCES_USER_TIMEZONE_OFFSET_MINS, user.timezoneOffsetMinutes);
		editor.putBoolean(PREFERENCES_USER_TIMEZONE_DAYLIGHT_SAVINGS, user.timezoneDaylightSavingsTime);
		editor.putString(PREFERENCES_USER_TIME_FORMAT, (user.timeFormat != null ? user.timeFormat.toString() : null));
		editor.putString(PREFERENCES_USER_DATE_FORMAT, (user.dateFormat != null ? user.dateFormat.toString() : null));
		editor.putLong(PREFERENCES_USER_PREMIUM_UNTIL, (user.premiumUntil != null ? user.premiumUntil.getTime() : 0));
		
		
		editor.commit();
	}
	
	
	/**
	 * Sets whether or not the user profile has been modified
	 * @param newValue
	 */
	public void setUserProfileModified(boolean newValue) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_USER, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putBoolean(PREFERENCES_USER_PROFILE_MODIFIED, newValue);
		editor.commit();
		
	}
	
	/**
	 * Returns whether or not the user profile has been modified
	 * @return
	 */
	public boolean getUserProfileModified() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_USER, Activity.MODE_PRIVATE);
		
		return preferences.getBoolean(PREFERENCES_USER_PROFILE_MODIFIED, false);
	}


	/**
	 * Loads user information
	 * 
	 */
	public User loadUser() {
		User user = new User();
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_USER, Activity.MODE_PRIVATE);
		
		user.id = preferences.getInt(PREFERENCES_USER_ID, 0);
		user.email = preferences.getString(PREFERENCES_USER_EMAIL, null);
		user.password = preferences.getString(PREFERENCES_USER_PASSWORD, null);
		user.fullName = preferences.getString(PREFERENCES_USER_FULL_NAME, null);
		user.timezone = preferences.getString(PREFERENCES_USER_TIMEZONE, null);
		user.timezoneOffsetMinutes = preferences.getInt(PREFERENCES_USER_TIMEZONE_OFFSET_MINS, 0);
		user.timezoneDaylightSavingsTime = preferences.getBoolean(PREFERENCES_USER_TIMEZONE_DAYLIGHT_SAVINGS, false);
		user.timeFormat = TimeFormat.valueOf(preferences.getString(PREFERENCES_USER_TIME_FORMAT, TimeFormat.HH_MM.toString()));
		user.dateFormat = DateFormat.valueOf(preferences.getString(PREFERENCES_USER_DATE_FORMAT, DateFormat.DD_MM_YYYY.toString()));
		user.premiumUntil = new Date(preferences.getLong(PREFERENCES_USER_PREMIUM_UNTIL, 0));
		
		return user;
	}
	

	
	/**
	 * Sets the indication that the projects have been reordered (and the server needs to be updated)
	 * 
	 * @param projectsReordered
	 */
	public void setProjectsReordered(boolean projectsReordered) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_DATA, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putBoolean(PREFERENCES_DATA_PROJECTS_REORDERED, projectsReordered);

		editor.commit();
	}
	
	/**
	 * Returns whether or not the projects have been reordered (and the server needs to be updated)
	 * 
	 * @return
	 */
	public boolean getProjectsReordered() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_DATA, Activity.MODE_PRIVATE);
		
		return preferences.getBoolean(PREFERENCES_DATA_PROJECTS_REORDERED, false);
	}

	/**
	 * Sets the indication when the items have been reordered under a specific project (and the server needs to be updated)
	 * 
	 * @param projectId
	 * @param itemsReordered
	 */
	public void setItemsReordered(int projectId, boolean itemsReordered) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_DATA_ITEMS_REORDERED, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		// The key is the project ID
		editor.putBoolean(String.valueOf(projectId), itemsReordered);

		editor.commit();
	}
	
	/**
	 * Returns whether or not the items have been reordered under a specific project (and the server needs to be updated)
	 * 
	 * @param projectId
	 * @return
	 */
	public boolean getItemsReordered(int projectId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_DATA_ITEMS_REORDERED, Activity.MODE_PRIVATE);
		
		// The key is the project ID
		return preferences.getBoolean(String.valueOf(projectId), false);
	}
	
	
	/**
	 * Sets whether or not an item has been moved to a new project
	 * 
	 * @param item
	 * @param newProjectId
	 */
	public void setItemMoved(Item item, int newProjectId) {
		// The is formatted as: newProjectId: itemId: currentProjectId, itemId: currentProjectId, ...
		String keyName = PREFERENCES_TODOIST_DATA_ITEMS_MOVED + "." + newProjectId;
		SharedPreferences preferences = mContext.getSharedPreferences(keyName, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		// The key is the item ID and the value is the project ID
		editor.putInt(String.valueOf(item.id), item.projectId);

		editor.commit();
	}
	
	/**
	 * Returns whether or not an item has been moved to a new project
	 * 
	 * @param destProjectId
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public ArrayList<Item> getItemsMoved(int destProjectId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_DATA_ITEMS_MOVED + "." + destProjectId, Activity.MODE_PRIVATE);
		ArrayList<Item> items = new ArrayList<Item>();
		
		Iterator<?> it = preferences.getAll().entrySet().iterator();
		
		while (it.hasNext()) {
			Entry<String, Integer> pairs = (Entry<String, Integer>)it.next();
			int itemId = Integer.valueOf(pairs.getKey());
			int currentProjectId = pairs.getValue();
			
			Item newItem = new Item();
			newItem.id = itemId;
			newItem.projectId = currentProjectId;
			
			items.add(newItem);
		}
		
		// The key is the project ID
		return items;
	}
	
	
	/**
	 * Clears out any indication of items moved for a specific project
	 * 
	 * @param destProjectId
	 */
	public void deleteItemsMoved(int destProjectId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_TODOIST_DATA_ITEMS_MOVED + "." + destProjectId, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.clear();
		editor.commit();
	}
	
	
	/**
	 * Sets the last used sort mode for items
	 * 
	 * @param sortMode
	 */
	public void setLastUsedItemsSortMode(ItemSortMode sortMode) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putString(PREFERENCES_DISPLAY_LAST_USED_ITEM_SORT, sortMode.toString());

		editor.commit();
	}
	
	/**
	 * Returns the last used sort mode for items
	 * 
	 * @return
	 */
	public ItemSortMode getLastUsedItemsSortMode() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return ItemSortMode.valueOf(preferences.getString(PREFERENCES_DISPLAY_LAST_USED_ITEM_SORT, ItemSortMode.ORIGINAL_ORDER.toString()));
	}

	
	/**
	 * Sets the initial sort mode for items
	 * 
	 * @param sortMode
	 */
	public void setInitialItemsSortMode(ItemSortMode sortMode) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putString(PREFERENCES_DISPLAY_INITIAL_ITEM_SORT, sortMode.toString());

		editor.commit();
	}
	
	/**
	 * Returns the initial sort mode for items
	 * 
	 * @return
	 */
	public ItemSortMode getInitialItemsSortMode() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return ItemSortMode.valueOf(preferences.getString(PREFERENCES_DISPLAY_INITIAL_ITEM_SORT, ItemSortMode.LAST_USED_ORDER.toString()));
	}

	
	/**
	 * Sets the last time the user has sync'd
	 * 
	 * @param syncTime
	 */
	public void setLastSyncTime(Date syncTime) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_SYNC, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putLong(PREFERENCES_SYNC_LAST_SYNC_TIME, syncTime.getTime());

		editor.commit();
	}
	
	/**
	 * Returns the last time the user has sync'd
	 * 
	 * @return
	 */
	public Date getLastSyncTime() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_SYNC, Activity.MODE_PRIVATE);
		
		return new Date(preferences.getLong(PREFERENCES_SYNC_LAST_SYNC_TIME, 0));
	}

	/**
	 * Sets the frequency of syncing (in minutes)
	 * 
	 * @param mins
	 */
	public void setSyncFrequency(int mins) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_SYNC, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_SYNC_FREQUENCY, mins);

		editor.commit();
	}
	
	/**
	 * Returns the frequency of syncing (in minutes)
	 * 
	 * @return
	 */
	public int getSyncFrequency() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_SYNC, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_SYNC_FREQUENCY, DEFAULT_SYNC_FREQUENCY);
	}
	
	/**
	 * Sets whether we should sync on startup
	 * 
	 * @param syncOnStartup
	 */
	public void setSyncOnStartup(boolean syncOnStartup) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_SYNC, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putBoolean(PREFERENCES_SYNC_ON_STARTUP, syncOnStartup);

		editor.commit();
	}
	
	/**
	 * Returns whether we should sync on startup
	 * 
	 * @return
	 */
	public boolean getSyncOnStartup() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_SYNC, Activity.MODE_PRIVATE);
		
		return preferences.getBoolean(PREFERENCES_SYNC_ON_STARTUP, false);
	}

	/**
	 * Sets whether we should sync on exit
	 * 
	 * @param syncOnExit
	 */
	public void setSyncOnExit(boolean syncOnExit) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_SYNC, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putBoolean(PREFERENCES_SYNC_ON_EXIT, syncOnExit);

		editor.commit();
	}
	
	/**
	 * Returns whether we should sync on exit
	 * 
	 * @return
	 */
	public boolean getSyncOnExit() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_SYNC, Activity.MODE_PRIVATE);
		
		return preferences.getBoolean(PREFERENCES_SYNC_ON_EXIT, false);
	}

	/**
	 * Sets the initial view
	 * 
	 * @param initialView
	 */
	public void setInitialView(InitialView initialView) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putString(PREFERENCES_DISPLAY_INITIAL_VIEW, initialView.toString());

		editor.commit();
	}
	
	/**
	 * Returns the initial view
	 * 
	 * @return
	 */
	public InitialView getInitialView() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return InitialView.valueOf(preferences.getString(PREFERENCES_DISPLAY_INITIAL_VIEW, DEFAULT_INITIAL_VIEW.toString()));
	}

	/**
	 * Sets the last viewed filter (filter by projects/labels)
	 * 
	 * @param filter
	 */
	public void setLastViewedFilter(InitialView filter) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putString(PREFERENCES_DISPLAY_LAST_VIEWED_FILTER, filter.toString());

		editor.commit();
	}
	
	/**
	 * Returns the last viewed filter (filter by projects/labels)
	 * 
	 * @return
	 */
	public InitialView getLastViewedFilter() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return InitialView.valueOf(preferences.getString(PREFERENCES_DISPLAY_LAST_VIEWED_FILTER, DEFAULT_INITIAL_VIEW.toString()));
	}

	/**
	 * Sets the last viewed project ID
	 * 
	 * @param projectId
	 */
	public void setLastViewedProject(int projectId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_DISPLAY_LAST_VIEWED_PROJECT, projectId);

		editor.commit();
	}
	
	/**
	 * Returns the last viewed project ID
	 * 
	 * @return
	 */
	public int getLastViewedProject() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_DISPLAY_LAST_VIEWED_PROJECT, 0);
	}

	/**
	 * Sets the last viewed label ID
	 * 
	 * @param labelId
	 */
	public void setLastViewedLabel(int labelId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_DISPLAY_LAST_VIEWED_LABEL, labelId);

		editor.commit();
	}
	
	/**
	 * Returns the last viewed label ID
	 * 
	 * @return
	 */
	public int getLastViewedLabel() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_DISPLAY_LAST_VIEWED_LABEL, 0);
	}

	/**
	 * Sets the last viewed query ID
	 * 
	 * @param queryId
	 */
	public void setLastViewedQuery(int queryId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_DISPLAY_LAST_VIEWED_QUERY, queryId);

		editor.commit();
	}
	
	/**
	 * Returns the last viewed query ID
	 * 
	 * @return
	 */
	public int getLastViewedQuery() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_DISPLAY_LAST_VIEWED_QUERY, 0);
	}

	
	/**
	 * Sets the initial project ID
	 * 
	 * @param projectId
	 */
	public void setInitialProject(int projectId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_DISPLAY_INITIAL_PROJECT, projectId);

		editor.commit();
	}
	
	/**
	 * Returns the initial project ID
	 * 
	 * @return
	 */
	public int getInitialProject() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_DISPLAY_INITIAL_PROJECT, 0);
	}

	/**
	 * Sets the initial label ID
	 * 
	 * @param labelId
	 */
	public void setInitialLabel(int labelId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_DISPLAY_INITIAL_LABEL, labelId);

		editor.commit();
	}
	
	/**
	 * Returns the initial label ID
	 * 
	 * @return
	 */
	public int getInitialLabel() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_DISPLAY_INITIAL_LABEL, 0);
	}
	
	/**
	 * Sets the initial query ID
	 * 
	 * @param queryId
	 */
	public void setInitialQuery(int queryId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_DISPLAY_INITIAL_QUERY, queryId);

		editor.commit();
	}
	
	/**
	 * Returns the initial query ID
	 * 
	 * @return
	 */
	public int getInitialQuery() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_DISPLAY_INITIAL_QUERY, 0);
	}

	/**
	 * Returns the text size (in DP) for projects/labels/items/notes
	 * 
	 * @return
	 */
	public int getTextSize() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_DISPLAY_TEXT_SIZE, DEFAULT_TEXT_SIZE);
	}

	/**
	 * Sets the text size (in DP) for projects/labels/items/notes
	 * 
	 * @param textSize
	 */
	public void setTextSize(int textSize) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_DISPLAY_TEXT_SIZE, textSize);

		editor.commit();
	}

	/**
	 * Sets the default project for new items
	 * 
	 * @param projectId
	 */
	public void setDefaultProject(int projectId) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_DISPLAY_DEFAULT_PROJECT, projectId);

		editor.commit();
	}
	
	/**
	 * Returns the default project for new items
	 * 
	 * @return
	 */
	public int getDefaultProject() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_DISPLAY_DEFAULT_PROJECT, 0);
	}

	
	/**
	 * Returns whether or not to show completed items as well
	 * 
	 * @return
	 */
	public boolean getShowCompletedItems() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		
		return preferences.getBoolean(PREFERENCES_DISPLAY_SHOW_COMPLETED_ITEMS, false);
	}

	/**
	 * Sets whether or not to show completed items as well
	 * 
	 * @param showCompleted
	 */
	public void setShowCompletedItems(boolean showCompleted) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_DISPLAY, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putBoolean(PREFERENCES_DISPLAY_SHOW_COMPLETED_ITEMS, showCompleted);

		editor.commit();
	}

	/*
	 * Backup related methods
	 */
	
	
	/**
	 * Returns the default backup path
	 * @return
	 */
	public String getDefaultBackupPath(Context context) {
		return String.format("%s/%s/files",
				Environment.getExternalStorageDirectory().getAbsolutePath(),
				context.getPackageName());
	}
	
	/**
	 * Backups all Todoist data (projects/items/labels/notes) into a zip file, containing
	 * CSV files (where each CSV file represents a data table).
	 * Determines internally the backup filename to use and returns it. The filename is comprised
	 * of the current date and time.
	 * 
	 * @param context
	 * @throws IOException 
	 * @returns the filename the Todoist data was saved to
	 */
	public String backupTodoistData(Context context) throws IOException {
	    String backupDir = getBackupPath();
	    
	    if (backupDir == null) {
	    	// Use default backup path
		    backupDir = getDefaultBackupPath(context);
	    }
	    
	    // Create the directory structure is non-existent
    	File dir = new File(backupDir);
    	dir.mkdirs();
    	
    	// The current date and time appear in the filename - determine the format (MM/DD/YYYY or DD/MM/YYYY)
    	String dateString;
    	Calendar c = Calendar.getInstance();
    	
    	if (loadUser().dateFormat == DateFormat.DD_MM_YYYY) {
    		dateString = String.format("%02d_%02d_%04d",
    				c.get(Calendar.DAY_OF_MONTH),
    				c.get(Calendar.MONTH) + 1 /* Month is zero based */,
    				c.get(Calendar.YEAR));
    		
    	} else { /* DateFormat.MM_DD_YYYY */
     		dateString = String.format("%02d_%02d_%04d",
    				c.get(Calendar.MONTH) + 1 /* Month is zero based */,
    				c.get(Calendar.DAY_OF_MONTH),
    				c.get(Calendar.YEAR));
    	}
    	
    	// Append the current time as well
    	dateString = String.format("%s__%02d_%02d",
    			dateString,
    			c.get(Calendar.HOUR_OF_DAY),
    			c.get(Calendar.MINUTE));
    	
    	// Compose final backup filename
	    String backupFilename = String.format("%s/%s_%s.zip",
	    		backupDir, BACKUP_FILENAME_TEMPLATE, dateString);
	    
	    backupTodoistData(backupFilename);
	    
		return backupFilename;
	}

	/**
	 * Backups all Todoist data (projects/items/labels/notes) into a zip file, containing
	 * CSV files (where each CSV file represents a data table).
	 * 
	 * @param filename
	 * @throws IOException 
	 */
	public void backupTodoistData(String filename) throws IOException {
		
		//
		// First, convert all tables to CSV strings
		//
		
		String projectsCsv = tableToCsv(DBConsts.PROJECTS_TABLE_NAME,
				PROJECTS_COLUMN_NAMES, PROJECTS_COLUMN_TYPES);
		
		String labelsCsv = tableToCsv(DBConsts.LABELS_TABLE_NAME,
				LABELS_COLUMN_NAMES, LABELS_COLUMN_TYPES);
		
		String itemsCsv = tableToCsv(DBConsts.ITEMS_TABLE_NAME,
				ITEMS_COLUMN_NAMES, ITEMS_COLUMN_TYPES);
	
		String itemsToLabelsCsv = tableToCsv(DBConsts.ITEMS_TO_LABELS_TABLE_NAME,
				ITEMS_TO_LABELS_COLUMN_NAMES, ITEMS_TO_LABELS_COLUMN_TYPES);
	
		String notesCsv = tableToCsv(DBConsts.NOTES_TABLE_NAME,
				NOTES_COLUMN_NAMES, NOTES_COLUMN_TYPES);
	
		//
		// Next, add all CSV files to a single Zip file
		//
		
		FileOutputStream dest = new FileOutputStream(filename, false); 
		ZipOutputStream zipOut = new ZipOutputStream(dest);
		
		writeZipBuffer(DBConsts.PROJECTS_TABLE_NAME, projectsCsv, zipOut);
		writeZipBuffer(DBConsts.LABELS_TABLE_NAME, labelsCsv, zipOut);
		writeZipBuffer(DBConsts.ITEMS_TABLE_NAME, itemsCsv, zipOut);
		writeZipBuffer(DBConsts.ITEMS_TO_LABELS_TABLE_NAME, itemsToLabelsCsv, zipOut);
		writeZipBuffer(DBConsts.NOTES_TABLE_NAME, notesCsv, zipOut);
		
		zipOut.close();

	}
	
	
	/**
	 * Utility function for writing a table CSV contents into a Zip file
	 * 
	 * @param tableName will be used as the filename of the CSV file
	 * @param data
	 * @param zipOut
	 * @throws IOException 
	 */
	private void writeZipBuffer(String tableName, String data, ZipOutputStream zipOut) throws IOException {
		ZipEntry zipEntry = new ZipEntry(tableName + ".csv");
		zipOut.putNextEntry(zipEntry);
		
		zipOut.write(new byte[]{ (byte)0xEF, (byte)0xBB, (byte)0xBF }); // Write the BOF (to indicate it's a UTF-8 encoded file)
		zipOut.write(data.getBytes("utf-8"));
		
		zipOut.closeEntry();
	}
	
	/**
	 * Utility function for converting the contents of a data table into a CSV file,
	 * along with headers.
	 * 
	 * @param tableName
	 * @param columns
	 * @param columnTypes
	 * @return the CSV contents
	 */
	private String tableToCsv(String tableName, String[] columns, int[] columnTypes) {
		StringBuilder csvOutput = new StringBuilder();
		SQLiteDatabase db;
		Cursor c = null;
		
		// Prepare the first CSV line - the list of column names
		
		for (int i = 0; i < columns.length; i++) {
			csvOutput.append(encodeValueForCsv(columns[i]));
			
			if (i < columns.length - 1)
				csvOutput.append(',');
		}
		
		csvOutput.append('\n');
		
		// Query the rows and write them as CSV
		
		db = mDbHelper.getWritableDatabase();
		
		c = db.query(tableName, columns, null, 
				null, null, null, null, null);
		
		for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
			// Write current rows as CSV
			
			for (int i = 0; i < columns.length; i++) {
				csvOutput.append(encodeValueForCsv(getColumnByType(c, columns[i], columnTypes[i])));
				
				if (i < columns.length - 1)
					csvOutput.append(',');
			}
			
			csvOutput.append('\n');
		}
		
		c.close();
		
		return csvOutput.toString();
	}
	
	/**
	 * Utility function for querying a column by type - since each column type has a different
	 * SQL function (such as getInt, getString, ...)
	 * 
	 * INTEGER -> getInt
	 * VARCHAR, LONGVARCHAR, CHAR -> getString
	 * BIGINT -> getLong
	 * 
	 * @param c
	 * @param columnName
	 * @param columnType
	 * @return
	 */
	private Object getColumnByType(Cursor c, String columnName, int columnType) {
		int columnIndex = c.getColumnIndex(columnName);
		
		switch (columnType) {
			case Types.INTEGER:
				return c.getInt(columnIndex);
			case Types.VARCHAR:
			case Types.LONGVARCHAR:
			case Types.CHAR:
				return c.getString(columnIndex);
			case Types.BIGINT:
				return c.getLong(columnIndex);
			default:
				return null;
		}
	}
	
	/**
	 * Utility function encoding a column/row value into a CSV formatted value (i.e. surrounding
	 * it with quotes and escaping any existing quotes and newlines)
	 * 
	 * @param value
	 * @return
	 */
	private StringBuilder encodeValueForCsv(Object value) {
		String objectStr = String.valueOf(value);
		StringBuilder output = new StringBuilder();
		
		if (value instanceof String)
			output.append('"');
		output.append(objectStr.replace("\n", "\\n").replace("\"", "\\\""));
		if (value instanceof String)
			output.append('"');
		
		return output;
	}
	
	
	/*
	 * Restore related methods
	 */
	
	
	/**
	 * Restores all Todoist data (projects/items/labels/notes) from a zip file containing
	 * CSV files (where each CSV file represents a data table).
	 * 
	 * NOTE: Deletes all previous local Todoist data before restoration
	 * 
	 * @param backupFilename
	 * @throws IOException 
	 */
	public void restoreTodoistData(String backupFilename) throws IOException {
		FileInputStream input = new FileInputStream(backupFilename);
		ZipInputStream zipIn = new ZipInputStream(new BufferedInputStream(input));
		ZipEntry entry;
		
		// If we've reached this far - this means it's a valid zip file - clear all storage
		clearTodoistData();
		
		while ((entry = zipIn.getNextEntry()) != null) {
			byte[] buffer = new byte[2048];
			int size;
			StringBuilder csvContents = new StringBuilder();
			
			// Read current CSV file contents
			while ((size = zipIn.read(buffer, 0, buffer.length)) != -1) {
				csvContents.append(new String(buffer, 0, size));
			}
			
			// See to which table does the CSV file belong to (since table name = filename without the .csv extension)
			
			String tableName = entry.getName().substring(0, entry.getName().length() - 4);
			
			if (tableName.equalsIgnoreCase(DBConsts.PROJECTS_TABLE_NAME)) {
				csvToTable(csvContents.toString(), DBConsts.PROJECTS_TABLE_NAME, PROJECTS_COLUMN_NAMES);
			} else if (tableName.equalsIgnoreCase(DBConsts.LABELS_TABLE_NAME)) {
				csvToTable(csvContents.toString(), DBConsts.LABELS_TABLE_NAME, LABELS_COLUMN_NAMES);
			} else if (tableName.equalsIgnoreCase(DBConsts.ITEMS_TABLE_NAME)) {
				csvToTable(csvContents.toString(), DBConsts.ITEMS_TABLE_NAME, ITEMS_COLUMN_NAMES);
			} else if (tableName.equalsIgnoreCase(DBConsts.ITEMS_TO_LABELS_TABLE_NAME)) {
				csvToTable(csvContents.toString(), DBConsts.ITEMS_TO_LABELS_TABLE_NAME, ITEMS_TO_LABELS_COLUMN_NAMES);
			} else if (tableName.equalsIgnoreCase(DBConsts.NOTES_TABLE_NAME)) {
				csvToTable(csvContents.toString(), DBConsts.NOTES_TABLE_NAME, NOTES_COLUMN_NAMES);
			}
		}
		
		zipIn.close();
		input.close();
		
	}
	

	/**
	 * Utility function for parsing the contents of a CSV file and importing it into a table
	 * 
	 * @param csvContents
	 * @param tableName
	 * @param columns
	 * 
	 */
	private void csvToTable(String csvContents, String tableName, String[] columns) {
		SQLiteDatabase db;
		db = mDbHelper.getWritableDatabase();
		
		// Parse the CSV file line-by-line
		
		String[] rows = csvContents.split("\n");
		
		for (int i = 1; i < rows.length; i++) { // Start parsing from 2nd row (first one is the headers row)
			
			// Parse current row into columns
			ContentValues values = new ContentValues();
			ArrayList<Object> csvValues = decodeCsvRow(rows[i]);
			
			// Fill-in the current entry's columns
			for (int c = 0; c < columns.length; c++) {
				Object csvValue = csvValues.get(c);
				if (csvValue instanceof String)
					values.put(columns[c], (String)csvValue);
				else
					values.put(columns[c], (Long)csvValue);
			}
			
			// Add the CSV entry to the table
			db.replace(tableName, null, values);
		}
	}
	

	/**
	 * Utility function decoding column values from a CSV-formatted row
	 * 
	 * @param row
	 * @return
	 */
	private ArrayList<Object> decodeCsvRow(String row) {
		ArrayList<Object> results = new ArrayList<Object>();
		
		int index = 0, nextIndex = 0;
		boolean isNumber = false;
		String currentResult;
		row = row.trim();
		
		while (index < row.length()) {
			if (row.charAt(index) == '\"') {
				// Current column value is a string - matching ending quote
				isNumber = false;
				
				nextIndex = index;
				do {
					nextIndex = row.indexOf("\"", nextIndex + 1);
				} while (row.charAt(nextIndex - 1) == '\\'); // Make sure we don't accidently come across escaped quotes
				
			} else {
				// Current column value is a number - add until end of line or next comma
				isNumber = true;
				nextIndex = row.indexOf(",", index + 1);
				
				if (nextIndex == -1) {
					// No comma - reached last column in row
					nextIndex = row.length();
				}
			}
			
			if (isNumber) {
				currentResult = row.substring(index, nextIndex).trim();
				index = nextIndex + 1;

				results.add(new Long(currentResult));
				
			} else {
				currentResult = row.substring(index + 1, nextIndex).trim();
				index = nextIndex + 2;
				
				// Un-escape newlines and quotes
				currentResult = currentResult.replace("\\n", "\n").replace("\\\"", "\"");
				results.add(currentResult);
			}
		}
		
		return results;
	}

	
	/**
	 * Sets the last time the user has made a backup
	 * 
	 * @param backupTime
	 */
	public void setLastBackupTime(Date backupTime) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_BACKUP, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putLong(PREFERENCES_BACKUP_LAST_BACKUP_TIME, backupTime.getTime());

		editor.commit();
	}
	
	/**
	 * Returns the last time the user has made a backup
	 * 
	 * @return
	 */
	public Date getLastBackupTime() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_BACKUP, Activity.MODE_PRIVATE);
		
		return new Date(preferences.getLong(PREFERENCES_BACKUP_LAST_BACKUP_TIME, 0));
	}

	/**
	 * Sets the frequency of backups (in minutes)
	 * 
	 * @param mins
	 */
	public void setBackupFrequency(int mins) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_BACKUP, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putInt(PREFERENCES_BACKUP_FREQUENCY, mins);

		editor.commit();
	}
	
	/**
	 * Returns the frequency of backups (in minutes)
	 * 
	 * @return
	 */
	public int getBackupFrequency() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_BACKUP, Activity.MODE_PRIVATE);
		
		return preferences.getInt(PREFERENCES_BACKUP_FREQUENCY, DEFAULT_BACKUP_FREQUENCY);
	}

	/**
	 * Sets the backup path/directory
	 * 
	 * @param path
	 */
	public void setBackupPath(String path) {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_BACKUP, Activity.MODE_PRIVATE);
		Editor editor = preferences.edit();
		
		editor.putString(PREFERENCES_BACKUP_PATH, path);

		editor.commit();
	}
	
	/**
	 * Returns the backup path/directory
	 * 
	 * @return
	 */
	public String getBackupPath() {
		SharedPreferences preferences = mContext.getSharedPreferences(PREFERENCES_BACKUP, Activity.MODE_PRIVATE);
		
		return preferences.getString(PREFERENCES_BACKUP_PATH, null);
	}

}
