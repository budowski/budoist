package budo.budoist.models;

import java.io.Serializable;
import java.util.Hashtable;

/**
 * Represents a Todoist Label (see {@link https://todoist.com/API/help#labels})
 * @author Yaron Budowski
 *
 */
public class Label extends SynchronizedModel implements Comparable<Label>, Serializable, Cloneable {
	private static final long serialVersionUID = 1L;
	
	public int userId;
	public String name;
	public int colorIndex; // Index in SUPPORTED_COLORS array
	public int count; // Number of items with this label
	
	private final static String TAG = "Label";
	
	private static final String KEY__ID = "id";
	private static final String KEY__USER_ID = "uid";
	private static final String KEY__NAME = "name";
	private static final String KEY__COUNT = "count";
	private static final String KEY__COLOR_INDEX = "color";
	
	
	// Currently, these are the only supported colors in Todoist API; additionally, when adding/updating
	// the color of a label, we'll only pass the *index* of the color in the following list
	public static final Integer[] SUPPORTED_COLORS = {
		0x019412,
		0xA39D01,
		0xE73D02,
		0xE702A4,
		0x9902E7,
		0x1D02E7,
		0x0082C5,
		0x555555
	};
	
	
	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	
	
	public int compareTo(Label other) {
		Label otherLabel = (Label)(other);
		
		if (
				(this.id == otherLabel.id) &&
				(compareObjects(this.name, otherLabel.name)) &&
				(this.colorIndex == otherLabel.colorIndex)
			) {
				return 0;
			} else {
				return 1;
			}
	}
	
	/**
	 * Converts the label's colorIndex into a real color
	 * 
	 * NOTE: Assumes color index is valid
	 * @return
	 */
	public int getColor() {
		return SUPPORTED_COLORS[this.colorIndex];
	}
	
	public String toString() {
		return String.format("<Label: %d (owner user: %d); name: '%s'; colorIndex: %d; count: %d>",
				id, userId, name, colorIndex, count);
	}
	
	public Label() { }

	
	/**
	 * Initialize the Item object from key-value pairs
	 * @param parameters
	 */
	public Label(Hashtable<String, Object> params) {
		if (params.containsKey(KEY__ID))
			id = ((Integer)params.get(KEY__ID)).intValue();
		if (params.containsKey(KEY__USER_ID))
			userId = ((Integer)params.get(KEY__USER_ID)).intValue();
		if (params.containsKey(KEY__COUNT))
			count = ((Integer)params.get(KEY__COUNT)).intValue();
		if (params.containsKey(KEY__COLOR_INDEX))
			colorIndex = ((Integer)params.get(KEY__COLOR_INDEX)).intValue();
		
		name = (String)params.get(KEY__NAME);
	}
}
