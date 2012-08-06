package budo.budoist.models;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

import android.util.Log;

/**
 * Represents a Todoist User (see {@link https://todoist.com/API/help#users})
 * @author Yaron Budowski
 *
 */
public class User {
	
	private final static String TAG = "User";
	
	public enum DateFormat {
		DD_MM_YYYY,
		MM_DD_YYYY
	}
	
	public enum TimeFormat {
		HH_MM,
		HH_PM_AM
	}
	
	
	public int id;
	public String apiToken;
	public String email;
	public String password;
	public String fullName;
	public String timezone;
	public int timezoneOffsetMinutes;
	public boolean timezoneDaylightSavingsTime;
	public TimeFormat timeFormat;
	public DateFormat dateFormat;
	public Date premiumUntil;
	
	private static final String KEY__ID = "id";
	private static final String KEY__EMAIL = "email";
	private static final String KEY__PASSWORD = "password";
	private static final String KEY__API_TOKEN = "api_token";
	private static final String KEY__API_TOKEN_EDIT = "token"; // Since when updating user details, it should be "token" and not "api_token"
	private static final String KEY__FULL_NAME = "full_name";
	private static final String KEY__TIMEZONE = "timezone";
	private static final String KEY__TIMEZONE_OFFSET = "tz_offset";
	private static final String KEY__TIME_FORMAT = "time_format";
	private static final String KEY__DATE_FORMAT = "date_format";
	private static final String KEY__PREMIUM_UNTIL = "premium_until";
	
	private static final String PREMIUM_UNTIL_DATE_FORMAT = "EEE dd MMM yyyy HH:mm:ss";
	
	public String toString() {
		return String.format("<User: %d; api token: %s; full name: %s; email: %s; password: %s; premium until: %s>",
				id, (apiToken != null ? apiToken : ""), (fullName != null ? fullName : ""),
				(email != null ? email : ""), (password != null ? password: ""),
				(premiumUntil != null ? premiumUntil.toString() : "<None>"));
	}
	
	public Hashtable<String, Object> toKeyValue() {
		Hashtable<String, Object> ret = new Hashtable<String, Object>();
	
		if (id != 0)
			ret.put(KEY__ID, id);
		if (apiToken != null)
			ret.put(KEY__API_TOKEN_EDIT, apiToken);
		if (email != null)
			ret.put(KEY__EMAIL, email);
		if (password != null)
			ret.put(KEY__PASSWORD, password);
		if (fullName != null)
			ret.put(KEY__FULL_NAME, fullName);
		if (timezone != null)
			ret.put(KEY__TIMEZONE, timezone);
		
		ret.put(KEY__TIME_FORMAT, (timeFormat == TimeFormat.HH_MM ? 0 : 1));
		ret.put(KEY__DATE_FORMAT, (dateFormat == DateFormat.DD_MM_YYYY ? 0 : 1));
		
		return ret;
	}
	
	public User() { }
	
	/**
	 * Initialize the User object from key-value pairs
	 * @param parameters
	 */
	@SuppressWarnings("unchecked")
	public User(Hashtable<String, Object> params) {
		if (params.containsKey(KEY__ID))
			id = ((Integer)params.get(KEY__ID)).intValue();
		email = (String)params.get(KEY__EMAIL);
		apiToken = (String)params.get(KEY__API_TOKEN);
		fullName = (String)params.get(KEY__FULL_NAME);
		timezone = (String)params.get(KEY__TIMEZONE);
		
		if (params.containsKey(KEY__TIMEZONE_OFFSET)) {
			ArrayList<Object> timezoneOffset = (ArrayList<Object>)params.get(KEY__TIMEZONE_OFFSET);
			timezoneOffsetMinutes = (((Integer)timezoneOffset.get(1)).intValue() * 60) +
									((Integer)timezoneOffset.get(2)).intValue();
			timezoneDaylightSavingsTime = (((Integer)timezoneOffset.get(3)).intValue() == 0 ? false : true);
		}
		
		if (params.containsKey(KEY__TIME_FORMAT)) {
			if ((Integer)params.get(KEY__TIME_FORMAT) == 0)
				timeFormat = TimeFormat.HH_MM;
			else
				timeFormat = TimeFormat.HH_PM_AM;
		}
		
		if (params.containsKey(KEY__DATE_FORMAT)) {
			if ((Integer)params.get(KEY__DATE_FORMAT) == 0)
				dateFormat = DateFormat.DD_MM_YYYY;
			else
				dateFormat = DateFormat.MM_DD_YYYY;
		}
		
		if (params.containsKey(KEY__PREMIUM_UNTIL)) {
			SimpleDateFormat formatter = new SimpleDateFormat(PREMIUM_UNTIL_DATE_FORMAT);
			try {
				premiumUntil = formatter.parse((String)params.get(KEY__PREMIUM_UNTIL));
			} catch (ParseException e) {
				Log.e(TAG, String.format("Error while parsing premium_until field of user: %s", (String)params.get(KEY__PREMIUM_UNTIL)), e);
			}
		}
	}
}
