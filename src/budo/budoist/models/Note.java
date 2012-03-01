package budo.budoist.models;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;

import android.util.Log;

/**
 * Represents a Todoist Note (see {@link https://todoist.com/API/help#notes})
 * @author Yaron
 *
 */
public class Note  extends SynchronizedModel implements Comparable<Note>, Serializable, Cloneable {
	private static final long serialVersionUID = 1L;
	
	public int itemId;
	public Date postDate;
	public String content;
	
	private final static String TAG = "Note";
	
	
	private static final String KEY__ID = "id";
	private static final String KEY__NOTE_ID = "note_id";
	private static final String KEY__ITEM_ID = "item_id";
	private static final String KEY__CONTENT = "content";
	private static final String KEY__POST_DATE = "posted";
	
	private static final String POST_DATE_FORMAT = "EEE MMM dd HH:mm:ss yyyy";

	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	/**
	 * Returns a string representation of the note's post date (or null if it has no post date).
	 * Examples:
	 * 		If in same year --> "Dec 28", "Nov 13", ...
	 * 		Otherwise --> "Aug 28 2013"
	 *
	 * @return
	 */
	public String getPostDateDescription() {
		if ((postDate == null) || (postDate.getTime() == 0)) {
			return null;
		}
		
		String date;
		
		Calendar currentTime = Calendar.getInstance(); currentTime.setTime(new Date());
		Calendar postTime = Calendar.getInstance(); postTime.setTime(this.postDate);
		
		long currentTimeYear = currentTime.get(Calendar.YEAR);
		long postTimeYear = postTime.get(Calendar.YEAR);
		
		if (currentTimeYear == postTimeYear) {
			// Same year
			date = (new SimpleDateFormat("MMM d", Locale.US)).format(this.postDate);
		} else {
			date = (new SimpleDateFormat("MMM d yyyy", Locale.US)).format(this.postDate);
		}
		
		return date;
	}
	

	
	public int compareTo(Note other) {
		Note otherNote = (Note)(other);
		
		if (
				(this.id == otherNote.id) &&
				(this.itemId == otherNote.itemId) &&
				(compareObjects(this.content, otherNote.content))
			) {
				return 0;
			} else {
				return 1;
			}
	}

	public Hashtable<String, Object> toKeyValue() {
		Hashtable<String, Object> ret = new Hashtable<String, Object>();
	
		if (itemId != 0)
			ret.put(KEY__ITEM_ID, itemId);
		if (id != 0)
			ret.put(KEY__NOTE_ID, id);
		
		ret.put(KEY__CONTENT, content);
		
		return ret;
	}

	
	public String toString() {
		return String.format("<Note: %d (item: %d); content: '%s'; post date: %s>",
				id, itemId, content, postDate.toString());
	}

	public Note() { }

	
	/**
	 * Initialize the Note object from key-value pairs
	 * @param parameters
	 */
	public Note(Hashtable<String, Object> params) {
		if (params.containsKey(KEY__ID))
			id = ((Integer)params.get(KEY__ID)).intValue();
		if (params.containsKey(KEY__ITEM_ID))
			itemId = ((Integer)params.get(KEY__ITEM_ID)).intValue();

		content = (String)params.get(KEY__CONTENT);
		
		if (params.containsKey(KEY__POST_DATE)) {
			SimpleDateFormat formatter = new SimpleDateFormat(POST_DATE_FORMAT);
			try {
				postDate = formatter.parse((String)params.get(KEY__POST_DATE));
			} catch (ParseException e) {
				Log.e(TAG, String.format("Error while parsing post date field of note: %s", (String)params.get(KEY__POST_DATE)), e);
			}
		}
	}
}
