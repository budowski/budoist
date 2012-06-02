package budo.budoist.services;

import budo.budoist.models.Item;
import budo.budoist.services.TodoistServer.ErrorCode;


/**
 * When trying to add/update an item with an invalid date string
 * @author Yaron Budowski
 *
 */
public class InvalidDateStringException extends Exception {
	private static final long serialVersionUID = 1L;
	private Item mItem;
	
	public InvalidDateStringException(Item item) {
		mItem = item;
	}
	
	public Item getItem() { return mItem; }
}
