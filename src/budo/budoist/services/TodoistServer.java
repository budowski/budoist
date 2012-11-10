package budo.budoist.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;

import android.util.Log;
import budo.budoist.models.Item;
import budo.budoist.models.Label;
import budo.budoist.models.Note;
import budo.budoist.models.Project;
import budo.budoist.models.User;


/**
 * Represents a Todoist server, with all of its functionalities
 * @author Yaron Budowski
 *
 */
public class TodoistServer {
	
	private final static String TAG = "TodoistServer";
	
	
	private final static String TODOIST_BASE_URL = "todoist.com/API/";
	
	private final static String URL_LOGIN = "login";
	private final static String URL_REGISTER = "register";
	private final static String URL_UPDATE_USER = "updateUser";
	
	private final static String URL_GET_PROJECTS = "getProjects";
	private final static String URL_GET_PROJECT = "getProject";
	private final static String URL_ADD_PROJECT = "addProject";
	private final static String URL_UPDATE_PROJECT = "updateProject";
	private final static String URL_UPDATE_PROJECT_ORDERS = "updateProjectOrders";
	private final static String URL_DELETE_PROJECT = "deleteProject";
	
	private final static String URL_GET_LABELS = "getLabels";
	private final static String URL_ADD_LABEL = "addLabel";
	private final static String URL_UPDATE_LABEL = "updateLabel";
	private final static String URL_UPDATE_LABEL_COLOR = "updateLabelColor";
	private final static String URL_DELETE_LABEL = "deleteLabel";
	
	private final static String URL_GET_UNCOMPLETED_ITEMS = "getUncompletedItems";
	private final static String URL_GET_COMPLETED_ITEMS = "getCompletedItems";
	private final static String URL_GET_ITEMS_BY_ID = "getItemsById";
	private final static String URL_ADD_ITEM = "addItem";
	private final static String URL_UPDATE_ITEM = "updateItem";
	private final static String URL_UPDATE_ORDERS = "updateOrders";
	private final static String URL_MOVE_ITEMS = "moveItems";
	private final static String URL_UPDATE_RECURRING_DATE = "updateRecurringDate";
	private final static String URL_DELETE_ITEMS = "deleteItems";
	private final static String URL_COMPLETE_ITEMS = "completeItems";
	private final static String URL_UNCOMPLETE_ITEMS = "uncompleteItems";
	
	private final static String URL_GET_NOTES = "getNotes";
	private final static String URL_ADD_NOTE = "addNote";
	private final static String URL_UPDATE_NOTE = "updateNote";
	private final static String URL_DELETE_NOTE = "deleteNote";
	
	private final static String URL_QUERY = "query";
	
	
	private final static String KEY__TOKEN = "token";
	private final static String KEY__EMAIL = "email";
	private final static String KEY__PASSWORD = "password";
	private final static String KEY__FULL_NAME = "full_name";
	private final static String KEY__TIMEZONE = "timezone";
	private final static String KEY__PROJECT_ID = "project_id";
	private final static String KEY__ITEM_ID_LIST = "item_id_list";
	private final static String KEY__AS_LIST = "as_list";
	private final static String KEY__OLD_NAME = "old_name";
	private final static String KEY__NEW_NAME = "new_name";
	private final static String KEY__NAME = "name";
	private final static String KEY__COLOR = "color";
	private final static String KEY__IDS = "ids";
	private final static String KEY__PROJECT_ITEMS = "project_items";
	private final static String KEY__TO_PROJECT = "to_project";
	private final static String KEY__ITEM_ID = "item_id";
	private final static String KEY__NOTE_ID = "note_id";
	private final static String KEY__QUERIES = "queries";
	private final static String KEY__DATA = "data";
	
	private static JsonServer mServer = new JsonServer(TODOIST_BASE_URL);


	public enum ErrorCode {
		// Error codes returned by Todoist API
		LOGIN_ERROR,
		ALREADY_REGISTRED,
		TOO_SHORT_PASSWORD,
		INVALID_EMAIL,
		INVALID_TIMEZONE,
		INVALID_FULL_NAME,
		ERROR_PASSWORD_TOO_SHORT,
		ERROR_EMAIL_FOUND,
		ERROR_PROJECT_NOT_FOUND,
		ERROR_NAME_IS_EMPTY,
		ERROR_WRONG_DATE_SYNTAX,
		ERROR_ITEM_NOT_FOUND,
		UNKNOWN_ERROR,
		
		// This is *our* error code (not Todoist's) - in case an invalid HTTP response was received (not a 200 status code)
		INVALID_RESPONSE
	}
	
	
	/*
	 * User APIs
	 */
	
	
	/**
	 * Logins the user
	 * @param email
	 * @param password
	 * @return
	 * @throws TodoistServerException 
	 */
	public static User login(String email, String password) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		
		params.put(KEY__EMAIL, email);
		params.put(KEY__PASSWORD, password);
		
		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_LOGIN, params, true));
		
		return (new User(ret));
	}
	
	

	/**
	 * Registers a new user
	 * @param email
	 * @param fullName
	 * @param password at least 5 characters long
	 * @param timezone
	 * @return
	 * @throws TodoistServerException
	 */
	public static User register(String email, String fullName, String password, String timezone) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		
		params.put(KEY__EMAIL, email);
		params.put(KEY__FULL_NAME, fullName);
		params.put(KEY__PASSWORD, password);
		params.put(KEY__TIMEZONE, timezone);
		
		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_REGISTER, params, true));
		
		return (new User(ret));
	}
	
	
	public static void updateUser(User user) throws TodoistServerException {
		Hashtable<String, Object> params = user.toKeyValue();
		
		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_UPDATE_USER, params, true));
	}
	
	/*
	 * Project APIs
	 */
	
	
	/**
	 * Returns all of a user's projects
	 * @param user
	 * @throws TodoistServerException
	 */
	public static ArrayList<Project> getProjects(User user) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		ArrayList<Project> projects = new ArrayList<Project>();
		
		params.put(KEY__TOKEN, user.apiToken);
		
		ArrayList<Object> ret = (ArrayList<Object>)parseReturnValue(
				mServer.sendCommand(URL_GET_PROJECTS, params, false));
		
		// Parse all of the returning projects
		for (int i = 0; i < ret.size(); i++) {
			projects.add(new Project((Hashtable<String, Object>)ret.get(i)));
		}
		
		return projects;
	}
	
	
	/**
	 * Returns a specific project (by ID)
	 * @param user
	 * @param projectId
	 * @throws TodoistServerException
	 */
	public static Project getProject(User user, int projectId) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__PROJECT_ID, projectId);
		
		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_GET_PROJECT, params, false));
		
		return (new Project(ret));
	}
	
	
	/**
	 * Adds a new project
	 * @param user
	 * @param project
	 * @throws TodoistServerException
	 */
	public static Project addProject(User user, Project project) throws TodoistServerException {
		Hashtable<String, Object> params = project.toKeyValue();
		
		params.put(KEY__TOKEN, user.apiToken);

		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_ADD_PROJECT, params, false));
		
		return (new Project(ret));
	}
	
	/**
	 * Updates an existing project
	 * @param user
	 * @param project
	 * @throws TodoistServerException
	 */
	public static Project updateProject(User user, Project project) throws TodoistServerException {
		Hashtable<String, Object> params = project.toKeyValue();
		
		params.put(KEY__TOKEN, user.apiToken);

		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_UPDATE_PROJECT, params, false));
		
		return (new Project(ret));
	}
	
	
	/**
	 * Updates the order of the projects (according to their itemOrder properties)
	 * @param user
	 * @param projects
	 * @throws TodoistServerException
	 */
	public static void updateProjectOrders(User user, ArrayList<Project> projects) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		ArrayList<Integer> projectIds = new ArrayList<Integer>();
		
		// Sort by item order
    	Collections.sort(projects, new Comparator<Project>() {
			@Override
			public int compare(Project project1, Project project2) {
				return project1.itemOrder - project2.itemOrder;
			}
		});
		
    	// Convert to IDs
		for (int i = 0; i < projects.size(); i++) {
			projectIds.add(projects.get(i).id);
		}
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__ITEM_ID_LIST, projectIds);

		// An "OK" message string should be returned
		String ret = (String)parseReturnValue(
				mServer.sendCommand(URL_UPDATE_PROJECT_ORDERS, params, false));
	}
	
	/**
	 * Deletes a specific project (by ID)
	 * @param user
	 * @param project
	 * @throws TodoistServerException
	 */
	public static void deleteProject(User user, Project project) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__PROJECT_ID, project.id);
		
		String ret = (String)parseReturnValue(
				mServer.sendCommand(URL_DELETE_PROJECT, params, false));
	}
	
	
	/*
	 * Label APIs
	 */
	
	

	/**
	 * Get all existing labels
	 * @param user
	 * @throws TodoistServerException
	 */
	public static ArrayList<Label> getLabels(User user) throws TodoistServerException {
		Hashtable<String, Object> params =  new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);

		ArrayList<Label> labels = new ArrayList<Label>();
		Hashtable<String, Hashtable<String, Object>> ret = (Hashtable<String, Hashtable<String, Object>>)parseReturnValue(
				mServer.sendCommand(URL_GET_LABELS, params, false));
		

		// Parse all of the returning projects
		Enumeration<Hashtable<String, Object>> e = ret.elements();
		while (e.hasMoreElements()) {
			labels.add(new Label(e.nextElement()));
		}
		
		return labels;
	}
	
	/**
	 * Updates a label color
	 * @param user
	 * @param label
	 * @throws TodoistServerException
	 */
	public static void updateLabelColor(User user, Label label) throws TodoistServerException {
		Hashtable<String, Object> params =  new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__NAME, label.name);
		params.put(KEY__COLOR, label.colorIndex);
		
		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_UPDATE_LABEL_COLOR, params, false));
	}

	
	/**
	 * Adds a new label (with specific color)
	 * @param user
	 * @param label
	 * @throws TodoistServerException
	 */
	public static Label addLabel(User user, Label label) throws TodoistServerException {
		Hashtable<String, Object> params =  new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__NAME, label.name);
		params.put(KEY__COLOR, label.colorIndex);
		
		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_ADD_LABEL, params, false));
		
		return (new Label(ret));
	}
	
	
	/**
	 * Updates an existing label
	 * @param user
	 * @param oldName
	 * @param newName
	 * @throws TodoistServerException
	 */
	public static void updateLabel(User user, String oldName, String newName) throws TodoistServerException {
		Hashtable<String, Object> params =  new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__OLD_NAME, oldName);
		params.put(KEY__NEW_NAME, newName);

		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_UPDATE_LABEL, params, false));
	}
	
	/**
	 * Deletes an existing label
	 * @param user
	 * @param name
	 * @throws TodoistServerException
	 */
	public static void deleteLabel(User user, String name) throws TodoistServerException {
		Hashtable<String, Object> params =  new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__NAME, name);

		String ret = (String)parseReturnValue(
				mServer.sendCommand(URL_DELETE_LABEL, params, false));
	}
	
	
	/*
	 * Item APIs
	 */

	

	/**
	 * Get uncompleted items for a project
	 * @param user
	 * @param project
	 * @throws TodoistServerException
	 */
	public static ArrayList<Item> getUncompletedItems(User user, Project project) throws TodoistServerException {
		Hashtable<String, Object> params =  new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__PROJECT_ID, project.id);

		ArrayList<Item> items = new ArrayList<Item>();
		ArrayList<Object> ret = (ArrayList<Object>)parseReturnValue(
				mServer.sendCommand(URL_GET_UNCOMPLETED_ITEMS, params, false));
		

		// Parse all of the returning projects
		for (int i = 0; i < ret.size(); i++) {
			items.add(new Item((Hashtable<String, Object>)ret.get(i)));
		}
		
		return items;
	}
	

	/**
	 * Get completed items for a project
	 * @param user
	 * @param project
	 * @throws TodoistServerException
	 */
	public static ArrayList<Item> getCompletedItems(User user, Project project) throws TodoistServerException {
		Hashtable<String, Object> params =  new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__PROJECT_ID, project.id);

		ArrayList<Item> items = new ArrayList<Item>();
		ArrayList<Object> ret = (ArrayList<Object>)parseReturnValue(
				mServer.sendCommand(URL_GET_COMPLETED_ITEMS, params, false));
		

		// Parse all of the returning projects
		for (int i = 0; i < ret.size(); i++) {
			items.add(new Item((Hashtable<String, Object>)ret.get(i)));
		}
		
		return items;
	}


	/**
	 * Get items by id
	 * @param user
	 * @param ids
	 * @throws TodoistServerException
	 */
	public static ArrayList<Item> getItemsById(User user, ArrayList<Integer> ids) throws TodoistServerException {
		Hashtable<String, Object> params =  new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__IDS, ids.toString());

		ArrayList<Item> retItems = new ArrayList<Item>();
		ArrayList<Object> ret = (ArrayList<Object>)parseReturnValue(
				mServer.sendCommand(URL_GET_ITEMS_BY_ID, params, false));
		

		// Parse all of the returning projects
		for (int i = 0; i < ret.size(); i++) {
			retItems.add(new Item((Hashtable<String, Object>)ret.get(i)));
		}
		
		return retItems;
	}
	

	/**
	 * Adds a new item
	 * @param user
	 * @param item
	 * @throws TodoistServerException
	 */
	public static Item addItem(User user, Item item) throws TodoistServerException {
		Hashtable<String, Object> params = item.toKeyValue();
		
		if (params.containsKey(Item.KEY__DATE_STRING)) {
		    String dateString = (String) params.get(Item.KEY__DATE_STRING);
		    
		    if ((dateString != null) && (dateString.trim().length() == 0)) {
		        // When adding an item - don't send out an empty date string (returns an error)
		        params.remove(Item.KEY__DATE_STRING);
		    }
		}
		
		params.put(KEY__TOKEN, user.apiToken);

		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_ADD_ITEM, params, false));
		
		return (new Item(ret));
	}
	
	
	/**
	 * Updates an existing item
	 * @param user
	 * @param item
	 * @throws TodoistServerException
	 */
	public static Item updateItem(User user, Item item) throws TodoistServerException {
		Hashtable<String, Object> params = item.toKeyValue();
		
		params.put(KEY__TOKEN, user.apiToken);

		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_UPDATE_ITEM, params, false));
		
		return (new Item(ret));
	}
	

	/**
	 * Updates the order of the items (according to their itemOrder properties)
	 * @param user
	 * @param items
	 * @param project
	 * @throws TodoistServerException
	 */
	public static void updateItemOrders(User user, ArrayList<Item> items, Project project) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		ArrayList<Integer> itemIds = new ArrayList<Integer>();
		
		// Sort by item order
    	Collections.sort(items, new Comparator<Item>() {
			@Override
			public int compare(Item item1, Item item2) {
				return item1.itemOrder - item2.itemOrder;
			}
		});
		
    	// Convert to IDs
		for (int i = 0; i < items.size(); i++) {
			itemIds.add(items.get(i).id);
		}
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__PROJECT_ID, project.id);
		params.put(KEY__ITEM_ID_LIST, itemIds);

		// An "OK" message string should be returned
		String ret = (String)parseReturnValue(
				mServer.sendCommand(URL_UPDATE_ORDERS, params, false));
	}
	

	/**
	 * Moves items to a new project
	 * @param user
	 * @param items
	 * @param newProject
	 * @throws TodoistServerException
	 */
	public static void moveItems(User user, ArrayList<Item> items, Project newProject) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		Hashtable<String, ArrayList<String>> currentMapping = new Hashtable<String, ArrayList<String>>();

		// Create a mapping between project id -> list of item ids (both key and values are converted
		// to strings, since Todoist API won't accept integers)
		for (int i = 0; i < items.size(); i++) {
			String projectId = String.valueOf(items.get(i).projectId);
			
			if (!currentMapping.containsKey(projectId))
				currentMapping.put(projectId, (new ArrayList<String>()));
			
			currentMapping.get(projectId).add(String.valueOf(items.get(i).id));
		}

		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__PROJECT_ITEMS, currentMapping);
		params.put(KEY__TO_PROJECT, newProject.id);

		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_MOVE_ITEMS, params, false));
	}
	
	
	/**
	 * Deletes items
	 * @param user
	 * @param items
	 * @throws TodoistServerException
	 */
	public static void deleteItems(User user, ArrayList<Item> items) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		ArrayList<Integer> itemIds = new ArrayList<Integer>();

		for (int i = 0; i < items.size(); i++) {
			itemIds.add(items.get(i).id);
		}

		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__IDS, itemIds);

		String ret = (String)parseReturnValue(
				mServer.sendCommand(URL_DELETE_ITEMS, params, false));
	}
	

	/**
	 * Completes items
	 * @param user
	 * @param items
	 * @throws TodoistServerException
	 */
	public static void completeItems(User user, ArrayList<Item> items) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		ArrayList<Integer> itemIds = new ArrayList<Integer>();

		for (int i = 0; i < items.size(); i++) {
			itemIds.add(items.get(i).id);
		}

		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__IDS, itemIds);

		String ret = (String)parseReturnValue(
				mServer.sendCommand(URL_COMPLETE_ITEMS, params, false));
	}
	

	/**
	 * Uncompletes items
	 * @param user
	 * @param items
	 * @throws TodoistServerException
	 */
	public static void uncompleteItems(User user, ArrayList<Item> items) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		ArrayList<Integer> itemIds = new ArrayList<Integer>();

		for (int i = 0; i < items.size(); i++) {
			itemIds.add(items.get(i).id);
		}

		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__IDS, itemIds);

		String ret = (String)parseReturnValue(
				mServer.sendCommand(URL_UNCOMPLETE_ITEMS, params, false));
	}
	
	/**
	 * Updates the recurring date of an item
	 * 
	 * NOTE: Even though the API supports updating several items at once, we currently support
	 * 			one item update at a time. 
	 * @param user
	 * @param item
	 * @return updated item (with new due date)
	 * @throws TodoistServerException
	 */
	public static Item updateRecurringDate(User user, Item item) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		ArrayList<Integer> itemIds = new ArrayList<Integer>();
		ArrayList<Item> items = new ArrayList<Item>();

		itemIds.add(item.id);

		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__IDS, itemIds);

		ArrayList<Object> ret = (ArrayList<Object>)parseReturnValue(
				mServer.sendCommand(URL_UPDATE_RECURRING_DATE, params, false));
		
		// Parse all of the returning projects
		for (int i = 0; i < ret.size(); i++) {
			items.add(new Item((Hashtable<String, Object>)ret.get(i)));
		}
		
		// Since we only updated one item's recurring date, only one will be returned
		return items.get(0);
	}
	
	
	/*
	 * Notes APIs
	 */
	
	
	/**
	 * Adds a new note
	 * @param user
	 * @param note
	 * @throws TodoistServerException
	 */
	public static Note addNote(User user, Note note) throws TodoistServerException {
		Hashtable<String, Object> params = note.toKeyValue();
		
		params.put(KEY__TOKEN, user.apiToken);

		Hashtable<String, Object> ret = (Hashtable<String, Object>)parseReturnValue(
				mServer.sendCommand(URL_ADD_NOTE, params, false));
		
		return (new Note(ret));
	}
	
	/**
	 * Updates an existing note
	 * @param user
	 * @param note
	 * @throws TodoistServerException
	 */
	public static void updateNote(User user, Note note) throws TodoistServerException {
		Hashtable<String, Object> params = note.toKeyValue();
		
		params.put(KEY__TOKEN, user.apiToken);

		String ret = (String)parseReturnValue(mServer.sendCommand(URL_UPDATE_NOTE, params, false));
	}

	/**
	 * Deletes a specific note (by ID)
	 * @param user
	 * @param note
	 * @throws TodoistServerException
	 */
	public static void deleteNote(User user, Note note) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__ITEM_ID, note.itemId);
		params.put(KEY__NOTE_ID, note.id);
		
		String ret = (String)parseReturnValue(
				mServer.sendCommand(URL_DELETE_NOTE, params, false));
	}
	

	/**
	 * Returns all of an item's notes
	 * @param user
	 * @param item
	 * @throws TodoistServerException
	 */
	public static ArrayList<Note> getNotes(User user, Item item) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		ArrayList<Note> notes = new ArrayList<Note>();
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__ITEM_ID, item.id);
		
		ArrayList<Object> ret = (ArrayList<Object>)parseReturnValue(
				mServer.sendCommand(URL_GET_NOTES, params, false));
		
		// Parse all of the returning notes
		for (int i = 0; i < ret.size(); i++) {
			notes.add(new Note((Hashtable<String, Object>)ret.get(i)));
		}
		
		return notes;
	}
	
	
	/*
	 * Query APIs
	 */
	

	/**
	 * Returns item results for a query
	 * 
	 * Note: Only one concurrent query is supported, since Todoist API doesn't seem to handle multiple
	 * queries of different types gracefully.  
	 * 
	 * @param user
	 * @param item
	 * @throws TodoistServerException
	 */
	public static ArrayList<Item> query(User user, String query) throws TodoistServerException {
		Hashtable<String, Object> params = new Hashtable<String, Object>();
		ArrayList<String> queries = new ArrayList<String>();
		queries.add(query);
		
		params.put(KEY__TOKEN, user.apiToken);
		params.put(KEY__QUERIES, queries);
		
		// Return data is an array of hashes (where each hash is a query result - in our case there
		// is only one)
		Hashtable<String, Object> ret = ((ArrayList<Hashtable<String, Object>>)parseReturnValue(
				mServer.sendCommand(URL_QUERY, params, false))).get(0);
		
		// Parse all of the returning items
		ArrayList<Object> retItems = (ArrayList<Object>)ret.get(KEY__DATA);
		ArrayList<Item> items = new ArrayList<Item>();
		
		for (int i = 0; i < retItems.size(); i++) {
			items.add(new Item((Hashtable<String, Object>)retItems.get(i)));
		}
		
		return items;
	}
	
	
	
	/*
	 * Utility methods
	 */
	
	
	private static Object parseReturnValue(Object input) throws TodoistServerException {
		if ((input instanceof Hashtable<?, ?>) || (input instanceof ArrayList<?>)) {
			return input;
		} else if ((input instanceof String) && (((String)input).compareToIgnoreCase("ok") == 0)) {
			// An "OK" status code;
			return input;
		}
		
		// A string error code was returned
		
		if (input == null) {
			// Invalid HTTP response
			throw new TodoistServerException(ErrorCode.INVALID_RESPONSE);
			
		} else {
			// Parse error code returned from Todoist server
			String error = (String)input;
			ErrorCode errorCode;
			
			try {
				errorCode = ErrorCode.valueOf(error);
			} catch (IllegalArgumentException exc) {
				Log.e(TAG, String.format("Unknown error code returned: '%s'; returning UNKNOWN_ERROR instead", error));
				errorCode = ErrorCode.UNKNOWN_ERROR;
			}
			
			throw new TodoistServerException(errorCode);
		}
	}
}
