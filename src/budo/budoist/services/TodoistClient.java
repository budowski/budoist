package budo.budoist.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Random;
import java.util.TimeZone;

import android.content.Context;
import android.util.Log;
import budo.budoist.models.Item;
import budo.budoist.models.Label;
import budo.budoist.models.Note;
import budo.budoist.models.OrderedModel;
import budo.budoist.models.Project;
import budo.budoist.models.Query;
import budo.budoist.models.SynchronizedModel;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.models.User;
import budo.budoist.models.SynchronizedModel.DirtyState;
import budo.budoist.services.TodoistOfflineStorage.ItemSortMode;

/**
 * Represents a Todoist client, which reads account information from the online Todoist
 * server (i.e. TodoistServer class), and also caches data in the offline storage (i.e. TodoistOfflineStorage class).
 * 
 * Open issues remaining on the server side (missing/non-functional APIs):
 * 		* getLabels - count field is always zero
 * 		* getAllNotes - get notes for ALL items
 * 
 * Other inner improvements:
 * 		* When deleting projects/items - recursive deletion should be on the client side (and not
 * 			on the view's side) - should also reorder items only when finished deleting all items.
 * 			This will make recursive deletion faster.
 * 		* Automatically calculate next due date for repeating tasks - advanced dates not supported:
 * 			** (see http://todoist.com/Help/timeInsert)
 * 			** "every 2nd monday"
 * 			** "ev other day starting 2. nov"
 *  	* Todoist text formatting: Not supported formatting:
 *  		** http://www.google.com (Google)
 *  				Turned into an equivalent of '<a href="http://www.google.com">Google</a>'
 * 
 * Features:
 * * Offline support
 * * Filter by projects, labels and queries
 * * Sort items by due date
 * * View and edit notes (for premium users)
 * * Edit labels (for premium users)
 * * View completed items as well
 * * Backup Todoist data frequently
 * * Many options for initial view (last used label/project, specific label/project, and many more)
 * * Todoist text formatting for projects/items/notes (e.g. "my %(b)bold% item!")
 * * Phone numbers, email and website addresses in items and notes are turned into clickable links
 * 
 * @author Yaron Budowski
 *
 */
public class TodoistClient {
	
	private static final String TAG = "TodoistClient";
	
	private Context mContext;
	private TodoistOfflineStorage mStorage;
	private User mUser;
	
	private boolean mIsLoggedIn;
	
	private boolean mIsCurrentlySyncing = false;
	private boolean mIsCurrentlyBackingUp = false;

	private static final int MIN_TEMP_ID = 1000000;
	private static final int MAX_TEMP_ID = 99999999;

	// Used when syncing
	private enum SyncResult {
		DO_NOTHING,
		UPDATE_LOCAL_TO_REMOTE,
		UPDATE_REMOTE_TO_LOCAL,
		ADD_REMOTE_TO_LOCAL,
		ADD_LOCAL_TO_REMOTE,
		DELETE_REMOTE,
		DELETE_LOCAL
	}
	
	private static final int MAX_ITEM_NAME_IN_PROGRESS = 30;
	private static final int MAX_PROJECT_NAME_IN_PROGRESS = 30;

	public interface ISyncProgress { public void onSyncProgress(String message, int progress); };
	
	
	public TodoistClient(Context context) {
		mContext = context;
		
		mStorage = new TodoistOfflineStorage(mContext);
		
		mIsLoggedIn = false;
		
		// Load last used user information
		mUser = mStorage.loadUser();
	}
	
	
	/*
	 * Sync related methods
	 */
	

	/*
	 * Note-Related methods
	 */
	

	/**
	 * Deletes a note locally (marks it as deleted); later on, when syncing, this note
	 * will be deleted from remote server and really deleted from local storage
	 * 
	 * @param note
	 * @throws PremiumAccountException 
	 */
	public void deleteNote(Note note) throws PremiumAccountException {
		if (!isPremium()) {
			// Only premium users can delete a note
			throw new PremiumAccountException();
		}
		
		Note existingNote = mStorage.getNote(note.id);
		
		if (existingNote.dirtyState == DirtyState.ADDED) {
			// If the note has been added and not yet synchronized online, we can
			// simply delete immediately (locally) without synchronizing with the online server
			mStorage.deleteNote(note);
			
		} else {
			note.dirtyState = DirtyState.DELETED;
			mStorage.addOrUpdateNote(note, null);
		}
		
		mStorage.updateItemNoteCount(mStorage.getItem(note.itemId));
	}
	
	/**
	 * Adds a note locally (to the storage/cache); later on, when syncing, this note
	 * will be added to the online server as well
	 * 
	 * @param note
	 * @throws PremiumAccountException 
	 */
	public void addNote(Note note) throws PremiumAccountException {
		if (!isPremium()) {
			// Only premium users can add a note
			throw new PremiumAccountException();
		}
		
		note.dirtyState = DirtyState.ADDED;
		
		// Need a temp ID until the note is sync'd online and given a "real" ID by the Todoist server
		note.id = generateRandomId(note);
		
		mStorage.addOrUpdateNote(note, null);
		mStorage.updateItemNoteCount(mStorage.getItem(note.itemId));
	}
	
	/**
	 * Updates a note locally (to the storage/cache); later on, when syncing, this note
	 * will be updated in the online server as well
	 * 
	 * @param note
	 * @param existingNote
	 */
	public void updateNote(Note note, Note existingNote) {
		if (existingNote.dirtyState != DirtyState.ADDED) {
			// If the note has been added and not yet synchronized online, there's no
			// need to change its dirty state to MODIFIED.
			note.dirtyState = DirtyState.MODIFIED;
		}
		
		mStorage.addOrUpdateNote(note, null);
	}


	/**
	 * Returns a list of all notes for an item (from cache/storage)
	 * 
	 * @param item
	 * @return
	 * @throws PremiumAccountException 
	 */
	public ArrayList<Note> getNotesByItem(Item item) throws PremiumAccountException {
		if (!isPremium()) {
			// Only premium users can use notes
			throw new PremiumAccountException();
		}
		
		return mStorage.getNotesByItem(item);
	}
	
	
	/*
	 * Label-Related methods
	 */
	


	/**
	 * Deletes a label locally (marks it as deleted); later on, when syncing, this label
	 * will be deleted from remote server and really deleted from local storage
	 * 
	 * @param label
	 * @throws PremiumAccountException 
	 */
	public void deleteLabel(Label label) throws PremiumAccountException {
		if (!isPremium()) {
			// Only premium users can delete a label
			throw new PremiumAccountException();
		}
		
		Label existingLabel = mStorage.getLabel(label.id);
		
		if (existingLabel.dirtyState == DirtyState.ADDED) {
			// If the label has been added and not yet synchronized online, we can
			// simply delete immediately (locally) without synchronizing with the online server
			mStorage.deleteLabel(label);
			
		} else {
			label.dirtyState = DirtyState.DELETED;
			mStorage.addOrUpdateLabel(label, null);
		}
	}


	/**
	 * Updates a label locally (to the storage/cache); later on, when syncing, this label
	 * will be updated in the online server as well
	 * 
	 * @param label
	 * @throws PremiumAccountException 
	 */
	public void updateLabel(Label label) throws PremiumAccountException {
		if (!isPremium()) {
			// Only premium users can update a label
			throw new PremiumAccountException();
		}
		
		Label existingLabel = mStorage.getLabel(label.id);
		
		if (existingLabel.dirtyState != DirtyState.ADDED) {
			// If the label has been added and not yet synchronized online, there's no
			// need to change its dirty state to MODIFIED.
			label.dirtyState = DirtyState.MODIFIED;
		}
		
		mStorage.addOrUpdateLabel(label, null);
	}
	
	/**
	 * Adds a label locally (to the storage/cache); later on, when syncing, this label
	 * will be added to the online server as well.
	 * 
	 * @param label
	 */
	public void addLabel(Label label) {
		label.dirtyState = DirtyState.ADDED;
		
		// Need a temp ID until the label is sync'd online and given a "real" ID by the Todoist server
		label.id = generateRandomId(label);
		
		mStorage.addOrUpdateLabel(label, null);
	}
	
	


	/**
	 * Returns a label by ID
	 * 
	 * @return
	 */
	public Label getLabelById(int labelId) {
		Label label = mStorage.getLabel(labelId);
		
		if (label.dirtyState == DirtyState.DELETED) {
			return null; // Label is deleted - act is if it's not found
		} else {
			return label;
		}
	}
	

	/**
	 * Returns a list of all labels (from cache/storage)
	 * 
	 * @return
	 */
	public ArrayList<Label> getLabels() {
		ArrayList<Label> labels = mStorage.getLabels();
		
		// Don't return any labels marked as DELETED (since even though they were deleted locally,
		// they weren't deleted remotely yet)
		
		Iterator<Label> itr = labels.iterator();
		
		while (itr.hasNext()) {
			Label label = itr.next();
			if (label.dirtyState == DirtyState.DELETED) {
				itr.remove();
			}
		}
		return labels;
	}
	
	/**
	 * Helper method which converts all of an item's label IDs into @label strings inside the
	 * item's content.
	 * Note: This overrides any older label settings
	 * 
	 * @param item changed in-place
	 */
	private void convertItemLabelIdsIntoNames(Item item) {
		// Add all label IDs as "@label" strings into the item's content - this of course will
		// override any old label settings which we might have had
		
		ArrayList<String> labels = new ArrayList<String>();

		if (item.labelIds != null) {
			for (int i = 0; i < item.labelIds.size(); i++) {
				// Note: We assume item.labelIds contains only valid (existing) label IDs
				labels.add(mStorage.getLabel(item.labelIds.get(i)).name);
			}
		}
		
		item.setContent(item.getContent(), labels, item.canBeCompleted());
	}

	
	/*
	 * Item-Related methods
	 */
	

	/**
	 * Deletes an item locally (marks it as deleted); later on, when syncing, this item
	 * will be deleted from remote server and really deleted from local storage
	 * 
	 * @param item
	 * @param updateProjectItemCount should we update itemCount within the parent project?
	 */
	public void deleteItem(final Item item, final boolean updateProjectItemCount) {
		if (item.dirtyState == DirtyState.ADDED) {
			// If the item has been added and not yet synchronized online, we can
			// simply delete immediately (locally) without synchronizing with the online server
			mStorage.deleteItem(item);
		} else {
			item.dirtyState = DirtyState.DELETED;
			mStorage.addOrUpdateItem(item, null);
		}
		
		(new Thread(new Runnable() {
			@Override
			public void run() {
				if (updateProjectItemCount) {
					Project project = mStorage.getProject(item.projectId);
					project.itemCount--;
					mStorage.addOrUpdateProject(project, null);
				}
				
				if (item.labelIds != null) {
					mStorage.updateLabelsItemCount();
				}
			}
		})).start();
		
		// In case the deleted item has been deleted from the middle of the
		// items lists, we'll need to update the itemOrder of some items
		reorderItems(item, item.itemOrder);
	
	}


	/**
	 * Updates an item locally (to the storage/cache); later on, when syncing, this item
	 * will be updated in the online server as well
	 * 
	 * @param item
	 * @param existingItem
	 */
	public void updateItem(final Item item, final Item existingItem) {
		if (existingItem.dirtyState != DirtyState.ADDED) {
			// If the item has been added and not yet synchronized online, there's no
			// need to change its dirty state to MODIFIED.
			item.dirtyState = DirtyState.MODIFIED;
		}
		
		mStorage.addOrUpdateItem(item, existingItem);
		
		
		(new Thread(new Runnable() {
			@Override
			public void run() {
				boolean shouldUpdateLabelCount = false;
				
				if (item.completed != existingItem.completed) {
					// Item complete state has been changed
					
					Project project = mStorage.getProject(item.projectId);
					
					if (item.completed) {
						// Project has one less uncompleted item
						project.itemCount--;
					} else {
						// Project has one more uncompleted item
						project.itemCount++;
					}
						
					mStorage.addOrUpdateProject(project, null);
					
					if (item.labelIds != null) {
						// We'll also need to update itemCount for the item's labels (this is done later on in the function)
						shouldUpdateLabelCount = true;
					}
				}
				
				if (item.projectId != existingItem.projectId) {
					// Item has been moved to another project - update item count
					Project oldProject = mStorage.getProject(existingItem.projectId);
					Project newProject = mStorage.getProject(item.projectId);
					
					oldProject.itemCount--;
					newProject.itemCount++;
					
					mStorage.addOrUpdateProject(oldProject, null);
					mStorage.addOrUpdateProject(newProject, null);
					
					// Set the item as moved (from existingItem.projectId to item.projectId)
					mStorage.setItemMoved(existingItem, item.projectId);
				}
				
			
				
				if (!item.compareLabelIds(existingItem)) {
					mStorage.updateItemLabels(item);
					shouldUpdateLabelCount = true;
				}
				
				if (shouldUpdateLabelCount) {
					mStorage.updateLabelsItemCount();
				}
			}
		})).start();
		
		if ((item.itemOrder != existingItem.itemOrder) && (item.projectId == existingItem.projectId)) {
			// In case the updated item had its order changed to the middle of the items list, we'll need to update the itemOrder of some items
			reorderItems(item, existingItem.itemOrder);
			
		} else if (item.projectId != existingItem.projectId) {
			// In case the updated item was moved to another project, we'll need to update the itemOrder of some items
			
			// Re-order new project items
			reorderItems(item, Integer.MAX_VALUE /* Since when moving to a new project, the item is treated like a new item in this regard */);
			// Re-order original project items
			reorderItems(existingItem, existingItem.itemOrder);
		}
	
	}
	
	/**
	 * Adds an item locally (to the storage/cache); later on, when syncing, this item
	 * will be added to the online server as well
	 * 
	 * @param item
	 */
	public void addItem(final Item item) {
		item.dirtyState = DirtyState.ADDED;
		
		// Need a temp ID until the item is sync'd online and given a "real" ID by the Todoist server
		item.id = generateRandomId(item);
		
		mStorage.addOrUpdateItem(item, null);
		(new Thread(new Runnable() {
			@Override
			public void run() {
				if (item.labelIds != null) {
					mStorage.updateItemLabels(item);
					mStorage.updateLabelsItemCount();
				}
				
				Project project = mStorage.getProject(item.projectId);
				project.itemCount++;
				mStorage.addOrUpdateProject(project, null);
			}
		})).start();
			
		// In case the added item has been placed in the middle of the
		// items lists, we'll need to update the itemOrder of some items
		reorderItems(item, Integer.MAX_VALUE /* No original item order */);
	}

	

	/**
	 * Returns a list of all items tagged with a specific label (from cache/storage)
	 * 
	 * @param label
	 * @param sortMode
	 * @return
	 */
	public ArrayList<Item> getItemsByLabel(Label label, ItemSortMode sortMode) {
		return mStorage.getItemsByLabel(label.id, sortMode, mStorage.getShowCompletedItems());
	}

	/**
	 * Returns a list of all items in a specific project (from cache/storage)
	 * 
	 * @param project
	 * @param sortMode
	 * @return
	 */
	public ArrayList<Item> getItemsByProject(Project project, ItemSortMode sortMode) {
		return mStorage.getItemsByProject(project.id, sortMode, mStorage.getShowCompletedItems());
	}


	/**
	 * Returns a list of all items (from cache/storage)
	 * 
	 * @param sortMode the order in which to return items
	 * @return
	 */
	public ArrayList<Item> getAllItems(ItemSortMode sortMode) {
		return mStorage.getAllItems(
				mStorage.getShowCompletedItems(),
				false, // Don't return deleted items
				sortMode);
	}

	
	
	/*
	 * Project-Related methods
	 */
	
	
	/**
	 * Returns a project by ID
	 * @param projectId
	 * @return
	 */
	public Project getProjectById(int projectId) {
		return mStorage.getProject(projectId);
	}
	
	/**
	 * Returns a list of all projects (from cache/storage)
	 * 
	 * @return
	 */
	public ArrayList<Project> getProjects() {
		ArrayList<Project> projects = mStorage.getProjects();
		
		// Don't return any projects marked as DELETED (since even though they were deleted locally,
		// they weren't deleted remotely yet)
		
		Iterator<Project> itr = projects.iterator();
		
		while (itr.hasNext()) {
			Project project = itr.next();
			if (project.dirtyState == DirtyState.DELETED) {
				itr.remove();
			}
		}
		return projects;
	}

	/**
	 * Deletes a project locally (marks it as deleted); later on, when syncing, this project
	 * will be deleted from remote server and really deleted from local storage
	 * 
	 * @param project
	 */
	public void deleteProject(Project project) {
		if (project.dirtyState == DirtyState.ADDED) {
			// If the project has been added and not yet synchronized online, we can
			// simply delete immediately (locally) without synchronizing with the online server
			mStorage.deleteProject(project);
		} else {
			project.dirtyState = DirtyState.DELETED;
			mStorage.addOrUpdateProject(project, null);
		}
		
		// Also mark all items under that project as deleted
		ArrayList<Item> items = this.getItemsByProject(project, ItemSortMode.ORIGINAL_ORDER);
		for (int i = 0; i < items.size(); i++) {
			this.deleteItem(items.get(i), false);
		}
		
		// In case the deleted project has been deleted from the middle of the
		// projects lists, we'll need to update the itemOrder of some projects
		reorderItems(project, project.itemOrder);
	}


	/**
	 * Updates a project locally (to the storage/cache); later on, when syncing, this project
	 * will be updated in the online server as well
	 * 
	 * @param project
	 * @param existingProject
	 */
	public void updateProject(Project project, Project existingProject) {
		if (existingProject.dirtyState != DirtyState.ADDED) {
			// If the project has been added and not yet synchronized online, there's no
			// need to change its dirty state to MODIFIED.
			project.dirtyState = DirtyState.MODIFIED;
		}
		
		mStorage.addOrUpdateProject(project, null);
		
		if (project.itemOrder != existingProject.itemOrder) {
			// In case the updated project had its order changed to the middle of the projects list, we'll need to update the itemOrder of some projects
			reorderItems(project, existingProject.itemOrder);
		}

	}
	
	/**
	 * Adds a project locally (to the storage/cache); later on, when syncing, this project
	 * will be added to the online server as well
	 * 
	 * @param project
	 */
	public void addProject(Project project) {
		project.dirtyState = DirtyState.ADDED;
		
		// Need a temp ID until the project is sync'd online and given a "real" ID by the Todoist server
		project.id = generateRandomId(project);
		
		mStorage.addOrUpdateProject(project, null);
		
		// In case the added project has been placed in the middle of the
		// projects lists, we'll need to update the itemOrder of some projects
		reorderItems(project, Integer.MAX_VALUE /* No original itemOrder */);
	}
	
	/*
	 * Query related methods
	 */
	
	
	/**
	 * Returns items by a query
	 * 
	 * @param query
	 */
	public ArrayList<Item> getItemsByQuery(Query query) {
		return mStorage.getItemsByQuery(query.query, mStorage.getShowCompletedItems());
	}

	/**
	 * Adds a query locally (to the storage/cache)
	 * 
	 * @param query
	 */
	public void addQuery(Query query) {
		// Need a temp ID
		query.id = generateRandomId(query);
		
		mStorage.addOrUpdateQuery(query);
	}
	
	/**
	 * Updates a query locally (to the storage/cache)
	 * 
	 * @param query
	 */
	public void updateQuery(Query query) {
		mStorage.addOrUpdateQuery(query);
	}

	/**
	 * Deletes a query locally (from the storage/cache)
	 * 
	 * @param query
	 */
	public void deleteQuery(Query query) {
		mStorage.deleteQuery(query);
	}
	
	/**
	 * Gets all queries
	 * 
	 * @return
	 */
	public ArrayList<Query> getQueries() {
		return mStorage.getQueries();
	}


	
	/*
	 * Utility methods
	 */
	
	
	/**
	 * Generates a random (temp) ID for an item (project/item/label/note/query) and makes sure
	 * no other item with the same ID exists. This is done when the item is added locally,
	 * but not yet remotely (not sync'd yet) - thus, until it is added remotely and given
	 * by Todoist server a proper ID, we provide it with a temp ID.
	 * 
	 * @param item
	 * @return
	 */
	private int generateRandomId(SynchronizedModel item) {
		Random rand = new Random(Calendar.getInstance().getTimeInMillis());
		int id;
		boolean idAlreadyExists = false;
		
		do {
			id = rand.nextInt(MAX_TEMP_ID - MIN_TEMP_ID + 1) + MIN_TEMP_ID;
			
			if (item instanceof Project) {
				idAlreadyExists = (mStorage.getProject(id) == null ? false : true);
			} else if (item instanceof Item) {
				idAlreadyExists = (mStorage.getItem(id) == null ? false : true);
			} else if (item instanceof Label) {
				idAlreadyExists = (mStorage.getLabel(id) == null ? false : true);
			} else if (item instanceof Note) {
				idAlreadyExists = (mStorage.getNote(id) == null ? false : true);
			} else if (item instanceof Query) {
				idAlreadyExists = (mStorage.getQuery(id) == null ? false : true);
			}
			
		} while (idAlreadyExists);
		
		return id;
	}
	
	/*
	 * Backup methods
	 */
	
	
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
	public String backupData(Context context) throws IOException {
		if (mIsCurrentlyBackingUp) {
			// Already in the process of backing up
			return null;
		}
		
		String backupFilename;
		
		try {
			mIsCurrentlyBackingUp = true;
			backupFilename = mStorage.backupTodoistData(context);
			
			// Save last backup time as now (do this only after successfully finishing backing up everything)
			mStorage.setLastBackupTime(new Date());
			
			mIsCurrentlyBackingUp = false;
			
		} catch (IOException exc) {
			mIsCurrentlyBackingUp = false;
			throw exc;
		}
		
		return backupFilename;
	}
	
	
	/**
	 * Restores all Todoist data (projects/items/labels/notes) from a zip file containing
	 * CSV files (where each CSV file represents a data table).
	 * 
	 * @param backupFilename
	 * @throws IOException 
	 */
	public void restoreData(String backupFilename) throws IOException {
		mStorage.restoreTodoistData(backupFilename);
	}

	
	/*
	 * Sync methods
	 */
	
	
	/**
	 * Synchronizes all items (projects/items/labels/notes) - offline vs. online
	 * 
	 * NOTE: This method assumes we have logged-in (using the login() method) prior to calling it.
	 * 
	 * @param callback optional callback called during sync (with progress updates)
	 * @throws TodoistServerException
	 */
	public void syncAll(ISyncProgress callback) throws TodoistServerException {
		if (mIsCurrentlySyncing) {
			// Syncing is already in progress
			return;
		}
		
		mIsCurrentlySyncing = true;
		
		try {
			syncProjects(callback);
			syncLabels(callback);
			syncItems(callback);
			
			if (isPremium()) {
				try {
					syncNotes(callback);
				} catch (PremiumAccountException e) {
					// Shouldn't happen - we already checked that user is indeed premium
					e.printStackTrace();
				}
			}
			
			// Save last sync time as now (do this only after successfully finishing syncing everything)
			mStorage.setLastSyncTime(new Date());
			
			if (callback != null) {
				callback.onSyncProgress("Syncing complete", 100);
			}
			
			mIsCurrentlySyncing = false;
			
		} catch (TodoistServerException exc) {
			mIsCurrentlySyncing = false;
			throw exc;
		}

	}
	

	/**
	 * Synchronizes notes (offline vs. online).
	 * 
	 * NOTE: Best practice is that syncItems will be called before this method, since it
	 * relies on the local (cached) list of items for retrieving notes (per item).
	 * 
	 * @param callback optional callback called during sync (with progress updates)
	 * @throws TodoistServerException, PremiumAccountException
	 */
	private void syncNotes(ISyncProgress callback) throws TodoistServerException, PremiumAccountException {
		if (!isPremium()) {
			// Only premium users can use notes
			throw new PremiumAccountException();
		}
		
		ArrayList<SynchronizedModel> offlineNotes = convertListToSyncModel(mStorage.getNotes());
		ArrayList<SynchronizedModel> onlineNotes = new ArrayList<SynchronizedModel>();
		
		// Get a list of all notes in all items (tasks)
		
		ArrayList<Item> items = mStorage.getNonDeletedItems();
		
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			
			if (callback != null) {
				String shortContent = TodoistTextFormatter.formatText(item.getContent()).toString();
	        	if (shortContent.length() > MAX_ITEM_NAME_IN_PROGRESS) shortContent = shortContent.subSequence(0, MAX_ITEM_NAME_IN_PROGRESS) + "...";
				
				callback.onSyncProgress(
						String.format("Syncing notes for item '%s'", shortContent),
						(int)(70 + ((30 * (1. / items.size())) * i)));
			}
			
			if (item.noteCount == 0) {
				// We assume that the item has been sync'd before syncing its notes - thus,
				// we know by its noteCount that it has no notes and we do not need to call getNotes.
				// Simply add an empty array of Notes
				onlineNotes.addAll(convertListToSyncModel(new ArrayList<Note>()));
				
			} else if (item.completed) {
				// Item is already completed - no need to sync its notes (simply add the local notes
				// to the remote note list - so nothing will be changed)
				onlineNotes.addAll(convertListToSyncModel(mStorage.getNotesByItem(item)));
				
			} else {
				onlineNotes.addAll(convertListToSyncModel(TodoistServer.getNotes(mUser, item)));
			}
		}
		
		syncLists(offlineNotes, onlineNotes);
		
		// Update note count per item
		for (int i = 0; i < items.size(); i++) {
			mStorage.updateItemNoteCount(items.get(i));
		}
	}
	
	/**
	 * Synchronizes labels (offline vs. online)
	 * 
	 * @param callback optional callback called during sync (with progress updates)
	 * @throws TodoistServerException 
	 */
	private void syncLabels(ISyncProgress callback) throws TodoistServerException {
		if (callback != null) {
			callback.onSyncProgress("Syncing labels", 20);
		}

		ArrayList<SynchronizedModel> offlineLabels = convertListToSyncModel(mStorage.getLabels());
		ArrayList<SynchronizedModel> onlineLabels = convertListToSyncModel(TodoistServer.getLabels(mUser));
		
		syncLists(offlineLabels, onlineLabels);
		
		if (callback != null) {
			callback.onSyncProgress("Syncing labels", 30);
		}
	}

	/**
	 * Synchronizes items (offline vs. online)
	 * 
	 * NOTE: Best practice is that syncProjects will be called before this method, since it
	 * relies on the local (cached) list of projects for retrieving items (per project).
	 * 
	 * @param callback optional callback called during sync (with progress updates)
	 * @throws TodoistServerException 
	 */
	private void syncItems(ISyncProgress callback) throws TodoistServerException {
		ArrayList<SynchronizedModel> offlineItems = convertListToSyncModel(mStorage.getAllItems(true, true, ItemSortMode.ORIGINAL_ORDER));
		ArrayList<SynchronizedModel> onlineItems = new ArrayList<SynchronizedModel>();
		
		if (callback != null) {
			callback.onSyncProgress("Syncing items", 30);
		}
		
		// Get a list of all items in all projects
		
		ArrayList<Project> projects = this.getProjects();
		
		for (int i = 0; i < projects.size(); i++) {
			Project project = projects.get(i);
			
			if (callback != null) {
				String shortContent = TodoistTextFormatter.formatText(project.getName()).toString();
	        	if (shortContent.length() > MAX_PROJECT_NAME_IN_PROGRESS) shortContent = shortContent.subSequence(0, MAX_PROJECT_NAME_IN_PROGRESS) + "...";
	        	
				callback.onSyncProgress(
						String.format("Syncing items for project '%s'", shortContent),
						(int)(30 + ((40 * (1. / projects.size())) * i)));
			}
			
			
			// First, see if any items need to be moved to this particular project
			ArrayList<Item> itemsToBeMoved = mStorage.getItemsMoved(project.id);
			if (itemsToBeMoved.size() > 0) {
				TodoistServer.moveItems(mUser, itemsToBeMoved, project);
				mStorage.deleteItemsMoved(project.id);
			}

			// Next, see if the items under this project need to be re-ordered
			if (mStorage.getItemsReordered(project.id)) {
				// Need to update remote item list order for this project
				TodoistServer.updateItemOrders(mUser, mStorage.getItemsByProject(project.id, ItemSortMode.ORIGINAL_ORDER, true), project);
				mStorage.setItemsReordered(project.id, false);
			}
			
			onlineItems.addAll(convertListToSyncModel(TodoistServer.getCompletedItems(mUser, project)));
			onlineItems.addAll(convertListToSyncModel(TodoistServer.getUncompletedItems(mUser, project)));
		}
		
		
		syncLists(offlineItems, onlineItems);
		
		// Update item count for labels and projects
		mStorage.updateAllProjectsItemCount();
		mStorage.updateLabelsItemCount();
	}
	
	
	/**
	 * Synchronizes projects (offline vs. online)
	 * 
	 * @param callback optional callback called during sync (with progress updates)
	 * @throws TodoistServerException
	 */
	private void syncProjects(ISyncProgress callback) throws TodoistServerException {
		ArrayList<Project> projects = this.getProjects();
		
		if (callback != null) {
			callback.onSyncProgress("Syncing projects", 5);
		}
	
		if (mStorage.getProjectsReordered()) {
			// Need to update remote project list order
			TodoistServer.updateProjectOrders(mUser, projects);
			mStorage.setProjectsReordered(false);
		}
		
		
		ArrayList<SynchronizedModel> offlineProjects = convertListToSyncModel(mStorage.getProjects());
		ArrayList<SynchronizedModel> onlineProjects = convertListToSyncModel(TodoistServer.getProjects(mUser));
		
		syncLists(offlineProjects, onlineProjects);
		
		if (callback != null) {
			callback.onSyncProgress("Syncing projects", 20);
		}
	
	}
	
	
	// Helper methods for syncing
	
	/**
	 * Converts a list to a generic list of SynchronizedModel's
	 */
	private ArrayList<OrderedModel> convertListToOrderedModel(ArrayList<?> list) {
		ArrayList<OrderedModel> ret = new ArrayList<OrderedModel>();
		
		for (int i = 0; i < list.size(); i++) {
			ret.add((OrderedModel)list.get(i));
		}
		
		return ret;
	}
	
	
	/**
	 * Converts a list to a generic list of SynchronizedModel's
	 */
	private ArrayList<SynchronizedModel> convertListToSyncModel(ArrayList<?> list) {
		ArrayList<SynchronizedModel> ret = new ArrayList<SynchronizedModel>();
		
		for (int i = 0; i < list.size(); i++) {
			ret.add((SynchronizedModel)list.get(i));
		}
		
		return ret;
	}
	
	/**
	 * Syncs a list of items (local and remote)
	 * @throws TodoistServerException 
	 */
	private void syncLists(ArrayList<SynchronizedModel> localItems, ArrayList<SynchronizedModel> remoteItems) throws TodoistServerException {
		// First, create a mapping of local item IDs (so we could quickly find items later on)
		Hashtable<Integer, SynchronizedModel> idsToItems = new Hashtable<Integer, SynchronizedModel>();

		if ((localItems.size() == 0) && (remoteItems.size() == 0))
			return; // Both lists are empty - nothing to do here
		
		for (int i = 0; i < localItems.size(); i++) {
			idsToItems.put(new Integer(localItems.get(i).id), localItems.get(i));
		}

		for (int i = 0; i < remoteItems.size(); i++) {
			SynchronizedModel remoteItem = remoteItems.get(i);
			SynchronizedModel localItem = idsToItems.get(new Integer(remoteItem.id)); // Even if no matching local item exists, we know how to handle it
			
			SyncResult syncResult = checkItemsForSync(localItem, remoteItem);
			
			if (syncResult == SyncResult.ADD_REMOTE_TO_LOCAL) {
				Log.e("Budoist", String.format("SyncResult: %s; Local item: %s; Remote item: %s;",
						syncResult.toString(), (localItem != null ? localItem.toString() : "<null>"), remoteItem.toString()));
			}
			
			handleSyncResult(localItem, remoteItem, syncResult);
			
			// This is done so we'll know which local items were dealt with (so afterwards
			// we'll traverse all of the local items which do not have a remote copy)
			idsToItems.remove(new Integer(remoteItem.id));
		}
		
		// Now, traverse through all of the remaining local items (which do not have a remote copy)
		Enumeration<SynchronizedModel> e = idsToItems.elements();
		
		while (e.hasMoreElements()) {
			SynchronizedModel localItem = e.nextElement();
			SyncResult syncResult = checkItemsForSync(localItem, null /* No remote copy exists */);
			
			//Log.d(TAG, String.format("SyncResult: %s; Local item: %s; Remote item: <null>;",
			//		syncResult.toString(), localItem.toString()));

			
			handleSyncResult(localItem, null, syncResult);
		}
	}
	
	
	/**
	 * Executes a sync result (e.g. Add remote item)
	 * 
	 * @param localItem
	 * @param remoteItem
	 * @param syncResult
	 * @throws TodoistServerException
	 */
	private void handleSyncResult(SynchronizedModel localItem, SynchronizedModel remoteItem, SyncResult syncResult) throws TodoistServerException {

		if (syncResult == SyncResult.ADD_LOCAL_TO_REMOTE) {
			syncAddLocalToRemote(localItem, remoteItem);
		} else if (syncResult == SyncResult.ADD_REMOTE_TO_LOCAL) {
			syncAddRemoteToLocal(localItem, remoteItem);
		} else if (syncResult == SyncResult.DELETE_LOCAL) {
			syncDeleteLocal(localItem, remoteItem);
		} else if (syncResult == SyncResult.DELETE_REMOTE) {
			syncDeleteRemote(localItem, remoteItem);
		} else if (syncResult == SyncResult.UPDATE_LOCAL_TO_REMOTE) {
			syncUpdateLocalToRemote(localItem, remoteItem);
		} else if (syncResult == SyncResult.UPDATE_REMOTE_TO_LOCAL) {
			syncUpdateRemoteToLocal(localItem, remoteItem);
		} else {
			// Do nothing
		}
	}
	
	

	/**
	 * Updates a local item copy from a remote (online) item
	 * @param localItem
	 * @param remoteItem
	 * @throws TodoistServerException
	 */
	private void syncUpdateRemoteToLocal(SynchronizedModel localItem, SynchronizedModel remoteItem) throws TodoistServerException {
		
		if (remoteItem instanceof Project) {
			mStorage.addOrUpdateProject((Project)remoteItem, null);
			
		} else if (remoteItem instanceof Item) {
			mStorage.addOrUpdateItem((Item)remoteItem, (Item)localItem);
			if (!(((Item)remoteItem).compareLabelIds((Item)localItem))) {
				// Only when the labels were modified - update the storage
				mStorage.updateItemLabels((Item)remoteItem);
			}
			
		} else if (remoteItem instanceof Label) {
			mStorage.addOrUpdateLabel((Label)remoteItem, null);
			
		} else if (remoteItem instanceof Note) {
			mStorage.addOrUpdateNote((Note)remoteItem, null);
		}
		
	}

	/**
	 * Updates a remote (online) item from a local copy; if successful (i.e. no exception raised),
	 * also updates the local copy (with new details, but also with UNMODIFIED dirty state)
	 * @param localItem
	 * @param remoteItem
	 * @throws TodoistServerException
	 */
	private void syncUpdateLocalToRemote(SynchronizedModel localItem, SynchronizedModel remoteItem) throws TodoistServerException {
		
		if (localItem instanceof Project) {
			// Update project remotely
			Project onlineProject = TodoistServer.updateProject(mUser, (Project)localItem);
			mStorage.addOrUpdateProject(onlineProject, (Project)localItem);
			
		} else if (localItem instanceof Item) {
			Item local = (Item)localItem, remote = (Item)remoteItem; 
			Item onlineItem; 
			ArrayList<Item> items = new ArrayList<Item>();
			items.add(local);
		
			if ((local.completed) && (!remote.completed)) {
				// Item was completed
				
				if ((local.isRecurring()) &&
					(local.dateString != null) &&
					(local.dateString.compareToIgnoreCase(remote.dateString) == 0)) {
					// When marking as complete a recurring item (when its due string is
					// left unmodified), instead of marking the remote copy as complete,
					// we simply update its next recurring date
					local.completed = false;
					onlineItem = TodoistServer.updateRecurringDate(mUser, local);
					mStorage.addOrUpdateItem(onlineItem, local); // Save new due date
						
						
				} else {
					TodoistServer.completeItems(mUser, items);
					remote.completed = local.completed;
				}
				
			} else if ((!local.completed) && (remote.completed)) {
				// Item was uncompleted
				TodoistServer.uncompleteItems(mUser, items);
				remote.completed = local.completed;
			}
			
			if (local.compareTo(remote) != 0) {
				// Local item is (still) different than remote item - update remote item regulary
				this.convertItemLabelIdsIntoNames((Item)localItem); // Make it so that the item's content contains all of the @label's
				onlineItem = TodoistServer.updateItem(mUser, (Item)localItem);
				mStorage.addOrUpdateItem(onlineItem, (Item)localItem);
				
			} else if (local.dirtyState == DirtyState.MODIFIED){
				// In case both local and remote copies are the same, the local copy should not
				// remain as MODIFIED.
				local.dirtyState = DirtyState.UNMODIFIED;
				mStorage.addOrUpdateItem(local, null);
			}
			
		} else if (localItem instanceof Label) {
			// In order to update a label, we must have its old (original) name.
			// So we'll call getLabels API and see what is the original label name (we'll
			// compare the labels by ID).
			
			ArrayList<Label> labels = TodoistServer.getLabels(mUser);
			Label updatedLabel = (Label)localItem;
			
			// TODO: If several labels were updated at once, this method will be called several times,
			// and each time it will call the getLabels API (multiple GET requests)

			for (int i = 0; i < labels.size(); i++) {
				Label onlineLabel = labels.get(i);
				
				if (onlineLabel.id == updatedLabel.id) {
					// Found our label
					
					if (onlineLabel.colorIndex != updatedLabel.colorIndex) {
						// Update label color
						onlineLabel.colorIndex = updatedLabel.colorIndex;
						TodoistServer.updateLabelColor(mUser, onlineLabel);
					}
					
					if (!onlineLabel.name.equalsIgnoreCase(updatedLabel.name)) {
						// Update label name
						TodoistServer.updateLabel(mUser, onlineLabel.name, updatedLabel.name);
					}
					
					updatedLabel.dirtyState = DirtyState.UNMODIFIED;
					mStorage.addOrUpdateLabel(updatedLabel, null);
					
					break;
				}
			}
			
		} else if (localItem instanceof Note) {
			// Update note remotely
			TodoistServer.updateNote(mUser, (Note)localItem);
			
			// Just update the note's dirty state to unmodified
			Note note = (Note)localItem;
			note.dirtyState = DirtyState.UNMODIFIED;
			mStorage.addOrUpdateNote(note, note);
		}
	}


	/**
	 * Deletes a remote (online) item from the Todoist server; if successful (i.e. no exception raised),
	 * also deletes local item.
	 * @param localItem
	 * @param remoteItem
	 * @throws TodoistServerException
	 */
	private void syncDeleteRemote(SynchronizedModel localItem, SynchronizedModel remoteItem) throws TodoistServerException {
		
		if (localItem instanceof Project) {
			TodoistServer.deleteProject(mUser, (Project)localItem);
			mStorage.deleteProject((Project)localItem);
			
		} else if (localItem instanceof Item) {
			ArrayList<Item> items = new ArrayList<Item>();
			items.add((Item)localItem);
			TodoistServer.deleteItems(mUser, items);
			mStorage.deleteItem((Item)localItem);
			
		} else if (localItem instanceof Label) {
			TodoistServer.deleteLabel(mUser, ((Label)localItem).name);
			mStorage.deleteLabel((Label)localItem);
			
		} else if (localItem instanceof Note) {
			TodoistServer.deleteNote(mUser, (Note)localItem);
			mStorage.deleteNote((Note)localItem);
		}
	}

	/**
	 * Deletes a local item from the local storage
	 * @param localItem
	 * @param remoteItem
	 * @throws TodoistServerException
	 */
	private void syncDeleteLocal(SynchronizedModel localItem, SynchronizedModel remoteItem) throws TodoistServerException {
		
		if (localItem instanceof Project) {
			mStorage.deleteProject((Project)localItem);
			
		} else if (localItem instanceof Item) {
			mStorage.deleteItem((Item)localItem);
			
		} else if (localItem instanceof Label) {
			mStorage.deleteLabel((Label)localItem);
			
		} else if (localItem instanceof Note) {
			mStorage.deleteNote((Note)localItem);
		}
	}

	/**
	 * Adds a remote item to the local storage
	 * @param localItem
	 * @param remoteItem
	 * @throws TodoistServerException
	 */
	private void syncAddRemoteToLocal(SynchronizedModel localItem, SynchronizedModel remoteItem) throws TodoistServerException {
		
		if (remoteItem instanceof Project) {
			mStorage.addOrUpdateProject((Project)remoteItem, null);
			
		} else if (remoteItem instanceof Item) {
			mStorage.addOrUpdateItem((Item)remoteItem, null);
			mStorage.updateItemLabels((Item)remoteItem);
			
		} else if (remoteItem instanceof Label) {
			mStorage.addOrUpdateLabel((Label)remoteItem, null);
			
		} else if (remoteItem instanceof Note) {
			mStorage.addOrUpdateNote((Note)remoteItem, null);
		}
	}
	
	/**
	 * Adds a local item to the remote Todoist server; if successful (i.e. no exception raised),
	 * updates the local copy (e.g. with new ID, new dirty state - unmodified)
	 * @param localItem
	 * @param remoteItem
	 * @throws TodoistServerException
	 */
	private void syncAddLocalToRemote(SynchronizedModel localItem, SynchronizedModel remoteItem) throws TodoistServerException {
		
		if (localItem instanceof Project) {
			Project onlineProject = TodoistServer.addProject(mUser, (Project)localItem);
			// Update project (which includes a new ID assigned by online Todoist server)
			mStorage.addOrUpdateProject(onlineProject, (Project)localItem);
			
		} else if (localItem instanceof Item) {
			Item local = (Item)localItem;
			this.convertItemLabelIdsIntoNames(local); // Make it so that the item's content contains all of the @label's
			
			Item onlineItem = TodoistServer.addItem(mUser, local);
			
			// Update item (which includes a new ID assigned by online Todoist server)
			mStorage.addOrUpdateItem(onlineItem, local);
			
		} else if (localItem instanceof Label) {
			Label onlineLabel = TodoistServer.addLabel(mUser, (Label)localItem);
			// Update label (which includes a new ID assigned by online Todoist server)
			mStorage.addOrUpdateLabel(onlineLabel, (Label)localItem);
		
		} else if (localItem instanceof Note) {
			Note onlineNote = TodoistServer.addNote(mUser, (Note)localItem);
			// Update note (which includes a new ID assigned by online Todoist server)
			mStorage.addOrUpdateNote(onlineNote, (Note)localItem);
		}
	}
	
	/**
	 * Checks two items (local and remote) and determines what syncing operation should be made
	 * @param localItem
	 * @param remoteItem
	 * @return see SyncResult enum
	 */
	@SuppressWarnings("unchecked")
	private SyncResult checkItemsForSync(SynchronizedModel localItem, SynchronizedModel remoteItem) {
		
		if (localItem == null) {
			// No corresponding local item exists (only remote item) - add it locally
			return SyncResult.ADD_REMOTE_TO_LOCAL;
			
		} else if (remoteItem == null) {
			// Only local item exists
			
			if (localItem.dirtyState == DirtyState.ADDED) {
				// Local item has been added (and hadn't had a chance to add it remotely as well)
				return SyncResult.ADD_LOCAL_TO_REMOTE;
			} else if (localItem.dirtyState == DirtyState.DELETED) {
				// Both local and remote items have been deleted - delete local
				return SyncResult.DELETE_LOCAL;
			} else if (localItem.dirtyState == DirtyState.UNMODIFIED) {
				// Remote copy has been deleted - delete local copy as well
				return SyncResult.DELETE_LOCAL;
			} else if (localItem.dirtyState == DirtyState.MODIFIED) {
				// A problematic case in which the remote copy has been deleted and local copy
				// has been modified (but not deleted).
				// In this case, we choose to update local copy to remote copy
				// TODO: Should this behavior be configurable by the user as an advanced setting?
				return SyncResult.ADD_LOCAL_TO_REMOTE;
			}
		}
		
		//
		// If we've reached this far, this means both local and remote copy exist
		//

		
		if (localItem.dirtyState == DirtyState.DELETED) {
			// Need to delete remote copy
			return SyncResult.DELETE_REMOTE;
		}
		
		boolean areEqual = (((Comparable)localItem).compareTo(remoteItem) == 0 ? true : false);

		if (localItem.dirtyState != DirtyState.UNMODIFIED) {
			// Local copy has been modified (and we hadn't had a chance to update the online copy)
			// Note: In case both remote and local copy have been modified, we prefer to use offline
			// version since we have no way of knowing which copy is newer.
			// TODO: Should this behavior be configurable by the user as an advanced setting?
			return SyncResult.UPDATE_LOCAL_TO_REMOTE;
		} else {
			
			if (areEqual) {
				// Items are equal - nothing to update
				return SyncResult.DO_NOTHING;
			} else {
				// Otherwise - This means the remote copy is newer
				return SyncResult.UPDATE_REMOTE_TO_LOCAL;
			}
		}
	}
	
	
	/**
	 * Reorders projects/items in case the new/modified/deleted project/item was added in the middle of the list (so
	 * we need to update the itemOrder property of several projects/items)
	 * @param modifiedItem
	 * @param originalItemOrder
	 */
    private void reorderItems(final OrderedModel modifiedItem, int originalItemOrder) {
    	ArrayList<OrderedModel> items;
    	boolean itemsReordered = false;
    	
    	// Should the modified item be placed before or after its new order?
    	// Example:
    	//		Original order: 1,2,3,4
    	//
    	//		Modified item: 3 -> 2
    	//		After: 1,2*,3,4 (placed BEFORE)
    	//
    	//		Modified item: 2 -> 3
    	//		After: 1,2,3*,4 (placed AFTER)
    	final boolean placeBeforeItem = (modifiedItem.itemOrder < originalItemOrder ? true : false);

    	if (modifiedItem instanceof Project)
    		items = convertListToOrderedModel(this.getProjects()); // this.getProjects() won't return any DELETED projects
    	else { /*if (modifiedItem instanceof Item)*/
    		
    		// Get all items under same project
	    	Project project = new Project(); project.id = ((Item)modifiedItem).projectId;
    		items = convertListToOrderedModel(this.getItemsByProject(project, ItemSortMode.ORIGINAL_ORDER)); // this.getItemsByProject() won't return any DELETED items
    	}
    	
    	// First, sort by item order
    	Collections.sort(items, new Comparator<OrderedModel>() {
			@Override
			public int compare(OrderedModel item1, OrderedModel item2) {
				int order = item1.itemOrder - item2.itemOrder;
				if (order == 0) {
					// Same order - can happen if modified item was placed instead of an existing
					// item - in this case, the modified item should be placed before/after the existing item
					if (
							((item1.id == modifiedItem.id) && (placeBeforeItem)) ||
							((item2.id == modifiedItem.id) && (!placeBeforeItem))
						) {
						return -1;
					} else {
						return 1;
					}
				} else {
					return order;
				}
			}
		});
    	
    	// Make sure all items have the proper order field
    	for (int i = 0; i < items.size(); i++) {
    		OrderedModel currentItem = items.get(i);
    		
    		if (currentItem.itemOrder != i + 1) {
    			currentItem.itemOrder = i + 1;
    			
    			// No need to change the dirtyFlag of the project/item since we only modify the itemOrder,
    			// and the server will take care of updating it when the project/item is added/updated remotely
    			if (currentItem instanceof Project)
    				mStorage.addOrUpdateProject((Project)currentItem, null);
    			else if (currentItem instanceof Item)
    				mStorage.addOrUpdateItem((Item)currentItem, (Item)currentItem); // Pass the same item as the "old" item, since we only modified the itemOrder property (thus insuring that the project/label item count won't be updated for nothing)

    			itemsReordered = true;
    		}
    	}
    	
    	if (itemsReordered) {
			// This will flag the higher-level syncProjects/syncItems method that the local projects/items
			// have been re-ordered and the remote server needs to updated (using updateProjectOrders/updateItemOrders)
			if (modifiedItem instanceof Project) {
				mStorage.setProjectsReordered(true);
			} else if (modifiedItem instanceof Item) {
				// Mark current project items as re-ordered
				mStorage.setItemsReordered(((Item)modifiedItem).projectId, true);
			}
    	}
    	
    }
    
	
	/*
	 * General methods
	 */
	
	
	/**
	 * Clears the cache (storage) of both user information and projects/items/notes/labels
	 * 
	 * NOTE: After calling this method, user must re-login (if previously logged-in in current session)
	 */
	public void clearCache() {
		mStorage.clearStorage();
		
		// Reset login properties
		
		mIsLoggedIn = false;
		
		// Load an empty user from cache
		mUser = mStorage.loadUser();
	}
	
	
	/*
	 * User related methods
	 */
	
	
	/**
	 * Updates user details (email, name, timezone, etc)
	 * 
	 * @param user
	 */
	public void updateUser(User user) {
		mStorage.saveUser(mUser);
		mStorage.setUserProfileModified(true); // User profile has been locally modified
	}
	
	/**
	 * Registers a user and saves user information in storage
	 * @param email
	 * @param fullName
	 * @param password
	 * @throws TodoistServerException
	 */
	public void register(String email, String fullName, String password) throws TodoistServerException {
		// Get local timezone
		TimeZone tz = TimeZone.getDefault();
		String timezone = tz.getID();
		
		mUser = TodoistServer.register(email, fullName, password, timezone);
		
		// If we've reached this far, this means registration was successful - save user information
		mUser.password = password; // Since password is not returned from server
		mStorage.saveUser(mUser);
		mStorage.setUserProfileModified(false);
		mIsLoggedIn = true;
	}
	
	
	/**
	 * Logins the user with last used login details
	 * @throws TodoistServerException 
	 * 
	 */
	public void login() throws TodoistServerException {
		Log.d(TAG, String.format("Login with previously used email: %s and password: %s",
				mUser.email, mUser.password));
		login(mUser.email, mUser.password);
	}
	
	/**
	 * Logins the user; if successful, saves user information in storage
	 * @param email
	 * @param password
	 * @throws TodoistServerException
	 */
	public void login(String email, String password) throws TodoistServerException {
		mUser = TodoistServer.login(email, password);
		
		// If we've reached this far, this means login was successful - save user information
		
		if (mStorage.getUserProfileModified() == true) {
			// The user profile has been locally modified - update remote details
			User localUser = mStorage.loadUser();
			localUser.apiToken = mUser.apiToken; // Since we need to be logged-in for the update
			mUser = localUser; // Save local user as the "real" updated user
			
			// Update the remote user details
			TodoistServer.updateUser(localUser);
			
			mStorage.setUserProfileModified(false); // Profile is no longer modified
			
		} else {
			// Saved the updated user profile received from server
			mUser.password = password; // Since password is not returned from server
			mStorage.saveUser(mUser);
		}
		
		mIsLoggedIn = true;
	}
	
	/**
	 * Whether the user is currently logged in
	 * @return
	 */
	public boolean isLoggedIn() {
		return mIsLoggedIn;
	}
	
	
	/**
	 * Whether or not the user has never logged in
	 * @return
	 */
	public boolean hasNeverLoggedIn() {
		// If no user ID was stored, this means we never successfully logged in
		return (mUser.id == 0);
	}
	
	/**
	 * Returns whether or not the user has never sync'd online
	 * @return
	 */
	public boolean hasNeverSynced() {
		return (mStorage.getLastSyncTime().getTime() == 0);
	}
	
	public User getUser() {
		return mUser;
	}
	
	
	/**
	 * Whether or not the user has a premium account
	 * @return
	 */
	public boolean isPremium() {
		Date now = new Date();
		
		return (
				(mUser.premiumUntil != null) &&
				(now.before(mUser.premiumUntil))
				);
	}
	
	
	public TodoistOfflineStorage getStorage() {
		return mStorage;
	}
	
	public boolean isCurrentlySyncing() {
		return mIsCurrentlySyncing;
	}
	
	public boolean isCurrentlyBackingUp() {
		return mIsCurrentlyBackingUp;
	}

}
