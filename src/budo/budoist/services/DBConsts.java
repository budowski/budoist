package budo.budoist.services;

public class DBConsts {
	
	public static final String DATABASE_NAME = "todoist_storage";
	public static final int DATABASE_VERSION = 1;
	
	
	/*
	 * Table information
	 */
	
	
	public static final String PROJECTS_TABLE_NAME = "projects";
	public static final String PROJECTS_ID = "id";
	public static final String PROJECTS_NAME = "name";
	public static final String PROJECTS_ITEM_COUNT = "item_count";
	public static final String PROJECTS_COLOR = "color";
	public static final String PROJECTS_INDENT = "indent";
	public static final String PROJECTS_ORDER = "project_order";
	public static final String PROJECTS_DIRTY_STATE = "dirty_state";
	
	public static final String ITEMS_TABLE_NAME = "items";
	public static final String ITEMS_ID = "id";
	public static final String ITEMS_PROJECT_ID = "project_id";
	public static final String ITEMS_CONTENT = "content";
	public static final String ITEMS_DUE_DATE = "due_date";
	public static final String ITEMS_DATE_STRING = "date_string";
	public static final String ITEMS_INDENT = "indent";
	public static final String ITEMS_ORDER = "item_order";
	public static final String ITEMS_PRIORITY = "priority";
	public static final String ITEMS_NOTE_COUNT = "note_count";
	public static final String ITEMS_DIRTY_STATE = "dirty_state";
	public static final String ITEMS_COMPLETED = "completed";
	
	public static final String NOTES_TABLE_NAME = "notes";
	public static final String NOTES_ID = "id";
	public static final String NOTES_ITEM_ID = "item_id";
	public static final String NOTES_CONTENT = "content";
	public static final String NOTES_POST_DATE = "post_date";
	public static final String NOTES_DIRTY_STATE = "dirty_state";
	
	public static final String LABELS_TABLE_NAME = "labels";
	public static final String LABELS_ID = "id";
	public static final String LABELS_NAME = "name";
	public static final String LABELS_COLOR = "color";
	public static final String LABELS_COUNT = "item_count";
	public static final String LABELS_DIRTY_STATE = "dirty_state";
	
	public static final String ITEMS_TO_LABELS_TABLE_NAME = "items_to_labels";
	public static final String ITEMS_TO_LABELS_ITEM_ID = "item_id";
	public static final String ITEMS_TO_LABELS_LABEL_ID = "label_id";
	
	public static final String QUERIES_TABLE_NAME = "queries";
	public static final String QUERIES_ID = "id";
	public static final String QUERIES_NAME = "name";
	public static final String QUERIES_QUERY = "query";

}
