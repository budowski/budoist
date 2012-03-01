package budo.budoist.services;

import budo.budoist.models.Query;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.util.Log;

public class TodoistDatabaseHelper extends SQLiteOpenHelper {
	private static final String TAG = "TodoistDatabaseHelper";
	
	private static final String CREATE_PROJECTS_TABLE = "create table " +
		DBConsts.PROJECTS_TABLE_NAME + " (" +
			DBConsts.PROJECTS_ID + " int not null unique, " +
			DBConsts.PROJECTS_NAME + " text not null, " +
			DBConsts.PROJECTS_COLOR + " int, " +
			DBConsts.PROJECTS_INDENT + " int, " +
			DBConsts.PROJECTS_ORDER + " int, " +
			DBConsts.PROJECTS_ITEM_COUNT + " int, " +
			DBConsts.PROJECTS_DIRTY_STATE + " text not null" +
		");";

	private static final String CREATE_ITEMS_TABLE = "create table " +
		DBConsts.ITEMS_TABLE_NAME + " (" +
			DBConsts.ITEMS_ID + " int not null unique, " +
			DBConsts.ITEMS_PROJECT_ID + " int not null, " +
			DBConsts.ITEMS_CONTENT + " text not null, " +
			DBConsts.ITEMS_DUE_DATE + " long, " +
			DBConsts.ITEMS_DATE_STRING + " text, " +
			DBConsts.ITEMS_INDENT + " int, " +
			DBConsts.ITEMS_ORDER + " int, " +
			DBConsts.ITEMS_PRIORITY + " int, " +
			DBConsts.ITEMS_NOTE_COUNT + " int, " +
			DBConsts.ITEMS_COMPLETED + " int not null, " +
			DBConsts.ITEMS_DIRTY_STATE + " text not null" +
		");";
	
	private static final String CREATE_NOTES_TABLE = "create table " +
		DBConsts.NOTES_TABLE_NAME + " (" +
			DBConsts.NOTES_ID + " int not null unique, " +
			DBConsts.NOTES_ITEM_ID + " int not null, " +
			DBConsts.NOTES_CONTENT + " text not null, " +
			DBConsts.NOTES_POST_DATE + " long, " +
			DBConsts.NOTES_DIRTY_STATE + " text not null" +
		");";

	private static final String CREATE_LABELS_TABLE = "create table " +
		DBConsts.LABELS_TABLE_NAME + " (" +
			DBConsts.LABELS_ID + " int not null unique, " +
			DBConsts.LABELS_NAME + " text not null, " +
			DBConsts.LABELS_COLOR + " int, " +
			DBConsts.LABELS_COUNT + " int not null, " +
			DBConsts.LABELS_DIRTY_STATE + " text not null" +
		");";
	
	private static final String CREATE_ITEMS_TO_LABELS_TABLE = "create table " +
		DBConsts.ITEMS_TO_LABELS_TABLE_NAME + " (" +
			DBConsts.ITEMS_TO_LABELS_ITEM_ID + " int not null, " +
			DBConsts.ITEMS_TO_LABELS_LABEL_ID + " int not null" +
		");";
	
	private static final String CREATE_QUERIES_TABLE = "create table " +
		DBConsts.QUERIES_TABLE_NAME + " (" +
			DBConsts.QUERIES_ID + " int not null unique, " +
			DBConsts.QUERIES_NAME + " text not null, " +
			DBConsts.QUERIES_QUERY + " text not null" +
		");";
	
	private static final String[] INITIAL_QUERIES_DESCRIPTIONS = new String[] {
			"Overdue and upcoming",
			"Overdue, upcoming and important",
			"Important"
		};
	
	private static final String[] INITIAL_QUERIES = new String[] {
			"overdue, 3 days",
			"overdue, 2 days, p1, p2, p3",
			"p1, p2, p3"
		};

	public TodoistDatabaseHelper(Context context, String name, CursorFactory factory, int version) {
		super(context, name, factory, version);
		
		Log.d(TAG, String.format("Creating TodoistDatabaseHelper; version: %d", version));
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "onCreate: Creating tables");
		
		try {
			Log.d(TAG, String.format("Executing query: %s", CREATE_PROJECTS_TABLE));
			db.execSQL(CREATE_PROJECTS_TABLE);
			
			Log.d(TAG, String.format("Executing query: %s", CREATE_ITEMS_TABLE));
			db.execSQL(CREATE_ITEMS_TABLE);
			
			Log.d(TAG, String.format("Executing query: %s", CREATE_NOTES_TABLE));
			db.execSQL(CREATE_NOTES_TABLE);
			
			Log.d(TAG, String.format("Executing query: %s", CREATE_LABELS_TABLE));
			db.execSQL(CREATE_LABELS_TABLE);
			
			Log.d(TAG, String.format("Executing query: %s", CREATE_ITEMS_TO_LABELS_TABLE));
			db.execSQL(CREATE_ITEMS_TO_LABELS_TABLE);
			
			Log.d(TAG, String.format("Executing query: %s", CREATE_QUERIES_TABLE));
			db.execSQL(CREATE_QUERIES_TABLE);
			
			// Insert some initial queries
			
			for (int i = 0; i < INITIAL_QUERIES.length; i++) {
				Query query = new Query();
				
				query.id = i + 1;
				query.name = INITIAL_QUERIES_DESCRIPTIONS[i];
				query.query = INITIAL_QUERIES[i];
				
				writeQuery(query, db);
			}
		
		} catch(SQLiteException ex) {
			Log.e(TAG, "Create table exception", ex);
		}
	}
	
	private void writeQuery(Query newQuery, SQLiteDatabase db) {
		ContentValues values = new ContentValues();
		
		values.put(DBConsts.QUERIES_ID, newQuery.id);
		values.put(DBConsts.QUERIES_NAME, newQuery.name);
		values.put(DBConsts.QUERIES_QUERY, newQuery.query);
		
		// Add/update the query in the queries list
		db.replace(DBConsts.QUERIES_TABLE_NAME, null, values);
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion,
			int newVersion) {
		Log.d(TAG, "onUpgrade: Upgrading from version " + oldVersion
				+" to "+newVersion
				+", which will destroy all if older version schema is not supported");
		
		db.execSQL("drop table if exists " + DBConsts.PROJECTS_TABLE_NAME);
		db.execSQL("drop table if exists " + DBConsts.ITEMS_TABLE_NAME);
		db.execSQL("drop table if exists " + DBConsts.NOTES_TABLE_NAME);
		db.execSQL("drop table if exists " + DBConsts.LABELS_TABLE_NAME);
		db.execSQL("drop table if exists " + DBConsts.ITEMS_TO_LABELS_TABLE_NAME);
		
		onCreate(db);
	}
}
