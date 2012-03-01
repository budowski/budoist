package budo.budoist.models;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Represents a model (e.g. Item/Project/Label/Note) that can be compared and synchronized
 * @author Yaron Budowski
 *
 */
public abstract class SynchronizedModel implements Serializable, Cloneable {
	private static final long serialVersionUID = 1L;
	
	// Uniquely identifies the model
	public int id;
	
	// Saves the state of the object
	public enum DirtyState {
		UNMODIFIED, // Nothing changed
		MODIFIED, // Local copy has been modified
		DELETED, // Local copy has been deleted
		ADDED // Local copy has been added
	}
	
	// Holds the local copy dirty state (in case it hasn't been sync'd yet with the remote copy)
	// - see DirtyState enum
	public DirtyState dirtyState = DirtyState.UNMODIFIED;
	
	
	protected Object clone() throws CloneNotSupportedException {
		return super.clone();
	}
	
	/*
	 * Utility methods for comparison
	 */

	
	/**
	 * Compares two objects; supports null objects
	 * 
	 * @param obj1
	 * @param obj2
	 * @return
	 */
	@SuppressWarnings("unchecked")
	protected boolean compareObjects(Comparable obj1, Comparable obj2) {
		return (
				((obj1 == null) && (obj2 == null)) ||
				((obj1 != null) && (obj2 != null) && (obj1.compareTo(obj2) == 0))
			);
	}
	
	/**
	 * Compares two arrays of integer (don't have to contains items in the same order)
	 * @param arr1
	 * @param arr2
	 * @return
	 */
	protected boolean compareArrays(ArrayList<Integer> arr1, ArrayList<Integer> arr2) {
		if ((arr1 == null) && (arr2 == null)) {
			return true;
		} else if (
				((arr1 == null) && (arr2 != null)) ||
				((arr2 == null) && (arr1 != null)) ||
				(arr1.size() != arr2.size())
			) {
			return false;
		}
		
		for (int i = 0; i < arr1.size(); i++) {
			if (!arr2.contains(arr1.get(i)))
				return false;
		}
		
		return true;
	}
}
