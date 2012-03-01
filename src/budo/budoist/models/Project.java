package budo.budoist.models;

import java.io.Serializable;
import java.util.Hashtable;

/**
 * Represents a Todoist Project (see {@link https://todoist.com/API/help#projects})
 * @author Yaron Budowski
 *
 */
public class Project extends OrderedModel implements Comparable<Project>, Serializable, Cloneable  {
	private static final long serialVersionUID = 1L;
	
	public int userId;
	public String rawName;
	public int itemCount; // How many items does this project hold?
	public int colorIndex; // Index in SUPPORTED_COLORS array
	public int indentLevel; // 1-4
	
	// Currently, these are the only supported colors in Todoist API; additionally, when adding/updating
	// the color of a project, we'll only pass the *index* of the color in the following list
	public static final Integer[] SUPPORTED_COLORS = {
		0xbde876,
		0xff8581,
		0xffc472,
		0xfaed75,
		0xa8c9e5,
		0x999999,
		0xe3a8e5,
		0xdddddd,
		0xfc603c,
		0xffcc00,
		0x74e8d4,
		0x3cd6fc
	};
	
	public Object clone() {
		return super.clone();
	}


	public int compareTo(Project other) {
		Project otherProject = (Project)(other);
		
		if (
				(this.id == otherProject.id) &&
				(compareObjects(this.rawName, otherProject.rawName)) &&
				(this.indentLevel == otherProject.indentLevel) &&
				(this.itemOrder == otherProject.itemOrder) &&
				(this.colorIndex == otherProject.colorIndex)
			) {
				return 0;
			} else {
				return 1;
			}
	}
	
	public Hashtable<String, Object> toKeyValue() {
		Hashtable<String, Object> ret = new Hashtable<String, Object>();
	
		if (id != 0)
			ret.put(KEY__PROJECT_ID, id);
		if (rawName != null)
			ret.put(KEY__NAME, rawName);
		
		ret.put(KEY__COLOR, colorIndex);
		ret.put(KEY__INDENT, indentLevel);
		ret.put(KEY__ITEM_ORDER_UPDATE, itemOrder);
		
		return ret;
	}
	
	public String toString() {
		return String.format("<Project: %d (owner user: %d); dirty: %s; name: '%s'; itemCount: %d; colorIndex: %X; indent: %d; itemOrder: %d>",
				id, userId, dirtyState.toString(), rawName, itemCount, colorIndex, indentLevel, itemOrder);
	}
	
	/**
	 * Converts the project's colorIndex into a real color
	 * 
	 * NOTE: Assumes color index is valid
	 * @return
	 */
	public int getColor() {
		return SUPPORTED_COLORS[this.colorIndex];
	}
	
	/**
	 * Returns formatted project name
	 * @return
	 */
	public String getName() {
		if (rawName == null) return null;
		
		if (rawName.startsWith("*")) {
			return rawName.substring(1);
		} else {
			return rawName;
		}
	}
	
	/**
	 * Sets the project's name
	 * @param name
	 * @param isGroup
	 * @return
	 */
	public void setName(String name, boolean isGroup) {
		if (isGroup) {
			rawName = "*" + name;
		} else {
			rawName = name;
		}
	}
	
	/**
	 * Returns whether or not the project is a group containing other projects
	 * @return
	 */
	public boolean isGroup() {
		// If the project name starts with an asterisk - it's a group containing other projects
		return ((rawName != null) && (rawName.startsWith("*")));
	}
	
	
	public Project() { }
	
	private static final String KEY__ID = "id";
	private static final String KEY__PROJECT_ID = "project_id";
	private static final String KEY__USER_ID = "user_id";
	private static final String KEY__NAME = "name";
	private static final String KEY__COLOR = "color";
	private static final String KEY__CACHE_COUNT = "cache_count";
	private static final String KEY__ITEM_ORDER = "item_order";
	private static final String KEY__ITEM_ORDER_UPDATE = "order"; // Field name is different when adding/updating a project
	private static final String KEY__INDENT = "indent";
	
	
	/**
	 * Initialize the Project object from key-value pairs
	 * @param parameters
	 */
	public Project(Hashtable<String, Object> params) {
		if (params.containsKey(KEY__ID))
			id = ((Integer)params.get(KEY__ID)).intValue();
		if (params.containsKey(KEY__USER_ID))
			userId = ((Integer)params.get(KEY__USER_ID)).intValue();
		
		rawName = (String)params.get(KEY__NAME);
		
		if (params.containsKey(KEY__CACHE_COUNT))
			itemCount = ((Integer)params.get(KEY__CACHE_COUNT)).intValue();
		if (params.containsKey(KEY__ITEM_ORDER))
			itemOrder = ((Integer)params.get(KEY__ITEM_ORDER)).intValue();
		if (params.containsKey(KEY__INDENT))
			indentLevel = ((Integer)params.get(KEY__INDENT)).intValue();
		
		if (params.containsKey(KEY__COLOR)) {
			int color = Integer.decode((String)params.get(KEY__COLOR)).intValue();
			
			// Find out the color index of the input color
			
			colorIndex = 0; // Default color index
			
			for (int i = 0; i < SUPPORTED_COLORS.length; i++) {
				if (color == SUPPORTED_COLORS[i]) {
					colorIndex = i;
					break;
				}
			}
			
		}
	}
}
