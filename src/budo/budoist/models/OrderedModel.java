package budo.budoist.models;

import java.io.Serializable;

/**
 * Represents an ordered model (e.g. Item/Project) - has an itemOrder property
 * @author Yaron Budowski
 *
 */
public abstract class OrderedModel extends SynchronizedModel implements Serializable, Cloneable {
	private static final long serialVersionUID = 1L;
	
	public int itemOrder; // The model's order in the list (smaller number is at the top)
	
	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException e) {
			return null;
		}
	}

}
