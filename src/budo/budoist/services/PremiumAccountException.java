package budo.budoist.services;

/**
 * An exception raised when trying to execute an action supported only in premium accounts,
 * while the current account is not premium.
 * 
 * @author Yaron Budowski
 *
 */
public class PremiumAccountException extends Exception {
	private static final long serialVersionUID = 1L;

	public PremiumAccountException() {
	}

}
