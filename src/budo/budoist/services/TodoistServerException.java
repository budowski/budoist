package budo.budoist.services;

import budo.budoist.services.TodoistServer.ErrorCode;


/**
 * When the Todoist server returns an error (as specified in getErrorCode)
 * @author Yaron
 *
 */
public class TodoistServerException extends Exception {
	private static final long serialVersionUID = 1L;
	private ErrorCode mErrorCode;
	
	public TodoistServerException(ErrorCode errorCode) {
		mErrorCode = errorCode;
	}
	
	public ErrorCode getErrorCode() { return mErrorCode; }
}
