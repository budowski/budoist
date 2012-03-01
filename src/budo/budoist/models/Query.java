package budo.budoist.models;

import java.io.Serializable;

/**
 * Represents a Todoist Query (used internally with budoist to filter items)
 * @author Yaron Budowski
 *
 */
public class Query  extends SynchronizedModel implements Serializable, Cloneable {
	private static final long serialVersionUID = 1L;
	
	public String name;
	public String query;
	
	private final static String TAG = "Query";
	
	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

	public String toString() {
		return String.format("<Query: %d; name: '%s'; query: %s>",
				id, name, query);
	}

	public Query() { }

}
