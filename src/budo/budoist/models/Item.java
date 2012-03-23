package budo.budoist.models;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import budo.budoist.models.User.DateFormat;
import budo.budoist.models.User.TimeFormat;

import android.graphics.Color;
import android.util.Log;

/**
 * Represents a Todoist Item (see {@link https://todoist.com/API/help#items})
 * @author Yaron Budowski
 *
 */
public class Item extends OrderedModel implements Comparable<Item>, Serializable, Cloneable {
	private static final long serialVersionUID = 1L;
	
	public int userId;
	public int projectId;
	public Date dueDate;
	public String dateString;
	public int indentLevel; // 1-5
	public int priority; // 1-4 (1 = very urgent; 4 = natural)
	public String rawContent;
	public ArrayList<Integer> labelIds; // List of label IDs (as received from server)
	public int noteCount;
	public boolean completed; // Completed/Uncompleted
	
	private final static String TAG = "Item";
	
	
	private static final String KEY__ID = "id";
	private static final String KEY__USER_ID = "user_id";
	private static final String KEY__PROJECT_ID = "project_id";
	private static final String KEY__CONTENT = "content";
	private static final String KEY__DUE_DATE = "due_date";
	private static final String KEY__DATE_STRING = "date_string";
	private static final String KEY__ITEM_ORDER = "item_order";
	private static final String KEY__INDENT = "indent";
	private static final String KEY__PRIORITY = "priority";
	private static final String KEY__LABELS = "labels";
	private static final String KEY__NOTE_COUNT = "note_count";
	private static final String KEY__CHECKED = "checked";
	
	private static final String DUE_DATE_FORMAT = "EEE MMM dd HH:mm:ss yyyy";
	
	private final static String LABEL_REG_EX = "(^| )\\@[a-zA-Z0-9_-]+";
	
	private final static String NO_DUE_DATE = "no due date";
	
	// Todoist's priority level is backwards :-P
	public final static int PRIORITY_1_HIGHEST = 4;
	public final static int PRIORITY_2 = 3;
	public final static int PRIORITY_3 = 2;
	public final static int PRIORITY_4_LOWEST = 1;
	
	
	public Object clone() {
		return super.clone();
	}
	
	/**
	 * Returns whether or not the item can be completed (if its content starts with an asterisk)
	 * @return
	 */
	public boolean canBeCompleted() {
		if (rawContent == null)
			return true;
		else
			return (!rawContent.startsWith("*"));
	}

	
	/**
	 * Compares the label IDs of two items
	 * @param other
	 * @return
	 */
	public boolean compareLabelIds(Item other) {
		return (compareArrays(this.labelIds, other.labelIds));
	}
	
	public int compareTo(Item other) {
		Item otherItem = (Item)(other);
		
		if (
				(this.id == otherItem.id) &&
				(this.projectId == otherItem.projectId) &&
				(compareObjects(this.dueDate, otherItem.dueDate)) &&
				(compareObjects(this.dateString, otherItem.dateString)) &&
				(this.indentLevel == otherItem.indentLevel) &&
				(this.itemOrder == otherItem.itemOrder) &&
				(this.priority == otherItem.priority) &&
				(this.completed == otherItem.completed) &&
				(this.noteCount == otherItem.noteCount) &&
				(compareObjects(this.getContent(), otherItem.getContent())) &&
				(compareArrays(this.labelIds, otherItem.labelIds))
			) {
				return 0;
			} else {
				return 1;
			}
	}
	
	public Hashtable<String, Object> toKeyValue() {
		Hashtable<String, Object> ret = new Hashtable<String, Object>();
	
		if (id != 0)
			ret.put(KEY__ID, id);
		if (projectId != 0)
			ret.put(KEY__PROJECT_ID, projectId);
		
		ret.put(KEY__CONTENT, rawContent);
		
		if ((dateString != null) && (dateString.length() > 0) && (dateString.compareToIgnoreCase(NO_DUE_DATE) != 0))
			ret.put(KEY__DATE_STRING, dateString);
		
		if (priority != 0)
			ret.put(KEY__PRIORITY, priority);

		ret.put(KEY__INDENT, indentLevel);
		ret.put(KEY__ITEM_ORDER, itemOrder);
		
		return ret;
	}

	
	public String toString() {
		return String.format("<Item: %d (owner user: %d; project: %d); content: '%s'; completed: %b; indent: %d; itemOrder: %d; priority: %d; label ids: %s; due date: %s; date string: %s; dirtyState: %s>",
				id, userId, projectId, rawContent, completed, indentLevel, itemOrder, priority, (labelIds != null ? labelIds.toString(): "[]"), (dueDate == null ? "<null>" : dueDate.toString()), dateString, dirtyState.toString());
	}
	
	
	/**
	 * Returns whether or not the item has a recurring date
	 * @return
	 */
	public boolean isRecurring() {
		return ((dateString != null) && (dateString.toLowerCase().startsWith("ev")));
	}
	
	
	/**
	 * Returns the item's color according to its priority
	 * Examples:
	 * 		Priority 1 = Red
	 * 		Priority 2 = Blue
	 * 		Priority 3 = Green
	 * 		Priority 4 = Black
	 * @return
	 */
	public int getItemPriorityColor() {
		switch (this.priority) {
		case PRIORITY_1_HIGHEST:
			return 0xFF0000;
		case PRIORITY_2:
			return 0x006699;
		case PRIORITY_3:
			return 0x007700;
		case PRIORITY_4_LOWEST:
			return 0x202020;
		default:
			return Color.BLACK;
		}
	}
	
	
	private final static String REGEX_RELATIVE_DAYS = "today|tomorrow";
	private final static String REGEX_RELATIVE_DAYS_SHORT = "tod|tom";
	private final static String REGEX_DAYS_OF_WEEK = "sunday|monday|tuesday|wednesday|thursday|friday|saturday";
	private final static String REGEX_DAYS_OF_WEEK_SHORT = "sun|mon|tue|wed|thu|fri|sat";
	private final static String REGEX_MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december";
	private final static String REGEX_MONTHS_SHORT = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec";
	private final static String REGEX_24H_TIME = "(?:((?:[1-9]{1})|(?:[0-1][0-9])|(?:[1-2][0-3]))(?:\\:([0-5][0-9]))?)";
	private final static String REGEX_12H_TIME = "(?:(?:(0?[1-9]|1(?=[012])\\d)(?:\\:([0-5]\\d))?)([ap]m)?)";
	private final static String REGEX_TIME = "(?:" + REGEX_12H_TIME + "|" + REGEX_24H_TIME + ")";
	
	private final static String REGEX_DATE = "(3[01]|[12][0-9]|0?[1-9])";
	private final static String REGEX_DATE_SEPARATOR = "[ \\-\\.\\/]";
	private final static String REGEX_DATE_MONTH_NUM = "(0?[1-9]|1[012])";
	private final static String REGEX_DATE_MONTH_NAME = "(" + REGEX_MONTHS + "|" + REGEX_MONTHS_SHORT + ")";
	private final static String REGEX_DATE_MONTH = "(?:" + REGEX_DATE_MONTH_NAME + "|" + REGEX_DATE_MONTH_NUM + ")";
	private final static String REGEX_DATE_YEAR = "(\\d\\d\\d\\d)";
	private final static String REGEX_DATE_FULL =
		"(?:" +
			"(?:" +
				REGEX_DATE +
				"(?:" + REGEX_DATE_SEPARATOR + REGEX_DATE_MONTH +
					"(?:" + REGEX_DATE_SEPARATOR + REGEX_DATE_YEAR + ")?" +
				")?" +
			")|" +
			"(?:" +
				REGEX_DATE_MONTH +
				REGEX_DATE_SEPARATOR +
				REGEX_DATE +
				"(?:" + REGEX_DATE_SEPARATOR + REGEX_DATE_YEAR + ")?" +
			")" +
		")";
	
	private final static String REGEX_RELATIVE_DATE = "(?:\\+(\\d+))";
	private final static String REGEX_REAL_DATE =
		"(?:" + 
			REGEX_DATE_FULL + "|" +
			REGEX_RELATIVE_DATE +
		")" +
		"(?: +(?:(?:at +)|(?:\\@ ?))" + REGEX_TIME + ")?";
	
	private final static String REGEX_CONTEXTUAL_DATE =
		"(?:(next) )?" +
		"(" +
			REGEX_RELATIVE_DAYS + "|" + REGEX_RELATIVE_DAYS_SHORT + "|" +
			REGEX_DAYS_OF_WEEK + "|" + REGEX_DAYS_OF_WEEK_SHORT +
		")" +
		"(?: +(?:(?:at +)|(?:\\@ ?))" + REGEX_TIME + ")?";
	
	
	private final static String REGEX_RECURRING_DAY = 
		"(?:day|weekday|wday|week|month|(?:last day)|lday|" + REGEX_DAYS_OF_WEEK + "|" + REGEX_DAYS_OF_WEEK_SHORT + ")";
	
	private final static String REGEX_RECURRING_DATE = 
		"(?:every|ev) " +
		"(?:" + 
			"(" + REGEX_RECURRING_DAY + "(?: *, *" + REGEX_RECURRING_DAY + ")*)" + "|" +
			"(?:" + REGEX_DATE +
				"(?:" +
					REGEX_DATE_SEPARATOR + REGEX_DATE_MONTH +
				")?" +
			")|" +
			"(?:" +
					REGEX_DATE_MONTH + REGEX_DATE_SEPARATOR +
					REGEX_DATE +
			")|" +
			"(?:(\\d+) (days|weeks|months|years))" +
		")" +
		"(?: +(?:(?:at +)|(?:\\@ ?))" + REGEX_TIME + ")?";
		;
		
	/**
	 * Calculates the next due date according to the dateString.
	 * Updates dueDate field accordingly
	 * 
	 * NOTE: This calculates only the FIRST occurrence of the due date (used when modifying a date string)
	 * 
	 * @see http://todoist.com/Help/timeInsert
	 * 
	 * @param dateFormat dd-mm-yyyy or mm-dd-yyyy?
	 * @param timeZoneOffsetMinutes the number of minutes of the user's local time zone
	 * 
	 */
	public void calculateFirstDueDate(DateFormat dateFormat, int timeZoneOffsetMinutes) {
		String date = this.dateString.trim().toLowerCase();
		
		Pattern patternContextualDate = Pattern.compile(REGEX_CONTEXTUAL_DATE, Pattern.CASE_INSENSITIVE);
		Pattern patternRealDate = Pattern.compile(REGEX_REAL_DATE, Pattern.CASE_INSENSITIVE);
		Pattern patternRecurringDate = Pattern.compile(REGEX_RECURRING_DATE, Pattern.CASE_INSENSITIVE);
		
		Matcher matcher;
		
		matcher = patternContextualDate.matcher(date);
		if (matcher.matches()) {
			calculateContextualDate(matcher);
		} else {
			matcher = patternRealDate.matcher(date);
			if (matcher.matches()) {
				calculateRealDate(matcher, dateFormat);
			} else {
				matcher = patternRecurringDate.matcher(date);
					if (matcher.matches()) {
						calculateRecurringDate(matcher, dateFormat);
					}
			}
		}
		
		
		// In case the calculated due date has a specific time of date set,
		// we need to convert it from the local time zone to GMT (since the getDueDateDescription
		// method assume this.dueDate is in GMT, not local time zone).
		
		if (this.dueDate != null) {
			Calendar dueDateCalendar = Calendar.getInstance(); dueDateCalendar.setTime(this.dueDate);
			if ((dueDateCalendar.get(Calendar.HOUR_OF_DAY) != 23) || (dueDateCalendar.get(Calendar.MINUTE) != 59)) {
				// It's a due date with a specific time of day - convert it from user's local time zone to GMT
				this.dueDate = new Date(this.dueDate.getTime() - (timeZoneOffsetMinutes * 60 * 1000));
			} else {
				// It's a due date marked for a single day (no specific time of day) - no need to convert
				// it to GMT (do nothing).
			}
		}

	}
	
	/**
	 * Private utility function that calculates a time format (i.e. REGEX_TIME)
	 * @param matcher
	 * @param startIndex the group index in which the REGEX_TIME match results start
	 * @return the number of seconds from 00:00:00 the input time represents
	 */
	private int calculateTime(Matcher matcher, int startIndex) {
		int hour = 0, mins = 0, secs = 0;
		
		if ((matcher.group(startIndex) == null) && (matcher.group(startIndex + 3) == null)) {
			// No hour provided (since it's optional) - In this case, Todoist defaults to 23:59:59
			hour = 23;
			mins = 59;
			secs = 59;
			
		} else if (matcher.group(startIndex) != null) {
			// 12-hour format
			hour = Integer.valueOf(matcher.group(startIndex));
			
			if ((matcher.group(startIndex + 2) != null) && (matcher.group(startIndex + 2).compareTo("pm") == 0)) {
				// The hour is in PM
				if (hour != 12) // Since 12pm == 12
					hour += 12;
				
			} else if (hour == 12) {
				// The hour is in AM and it's 12am (== 0)
				hour = 0;
			}
			
			if (matcher.group(startIndex + 1) != null) // Minutes are optional
				mins = Integer.valueOf(matcher.group(startIndex + 1));
				
		} else {
			// 24-hour format
			hour = Integer.valueOf(matcher.group(startIndex + 3));
			
			if (matcher.group(startIndex + 4) != null) // Minutes are optional
				mins = Integer.valueOf(matcher.group(startIndex + 4));
		}
		
		// Return final time (in seconds since 00:00:00)
		return (((hour * 60) + mins) * 60) + secs;
		
	}
	
	
	private int parseWeekDay(String day) {
		if ((day.equals("sunday")) || (day.equals("sun")))
			return Calendar.SUNDAY;
		else if ((day.equals("monday")) || (day.equals("mon")))
			return Calendar.MONDAY;
		else if ((day.equals("tuesday")) || (day.equals("tue")))
			return Calendar.TUESDAY;
		else if ((day.equals("wednesday")) || (day.equals("wed")))
			return Calendar.WEDNESDAY;
		else if ((day.equals("thursday")) || (day.equals("thu")))
			return Calendar.THURSDAY;
		else if ((day.equals("friday")) || (day.equals("fri")))
			return Calendar.FRIDAY;
		else if ((day.equals("saturday")) || (day.equals("sat")))
			return Calendar.SATURDAY;
		else
			return 0;
	}
	
	/**
	 * Private utility function for parsing a month field - either a number of a named month (e.g. April/apr)
	 * @param value
	 * @return Calendar.JANUARY to Calendar.DECEMBER
	 */
	private int parseMonth(String month) {
		if ((month.equals("january")) || (month.equals("jan")))
			return Calendar.JANUARY;
		else if ((month.equals("february")) || (month.equals("feb")))
			return Calendar.FEBRUARY;
		else if ((month.equals("march")) || (month.equals("mar")))
			return Calendar.MARCH;
		else if ((month.equals("april")) || (month.equals("apr")))
			return Calendar.APRIL;
		else if (month.equals("may"))
			return Calendar.MAY;
		else if ((month.equals("june")) || (month.equals("jun")))
			return Calendar.JUNE;
		else if ((month.equals("july")) || (month.equals("jul")))
			return Calendar.JULY;
		else if ((month.equals("august")) || (month.equals("aug")))
			return Calendar.AUGUST;
		else if ((month.equals("september")) || (month.equals("sep")))
			return Calendar.SEPTEMBER;
		else if ((month.equals("october")) || (month.equals("oct")))
			return Calendar.OCTOBER;
		else if ((month.equals("november")) || (month.equals("nov")))
			return Calendar.NOVEMBER;
		else if ((month.equals("december")) || (month.equals("dec")))
			return Calendar.DECEMBER;
		else // Numeric month
			return Integer.valueOf(month) - 1; // -1 since Calendar.JANUARY == 0 (and not 1)
	}
	
	/**
	 * Private utility function for calculating a recurring date string
	 * @param matcher result from matching the regular expression for recurring date (i.e. REGEX_RECURRING_DATE)
	 * @param dateFormat dd-mm-yyyy or mm-dd-yyyy?
	 */
	private void calculateRecurringDate(Matcher matcher, DateFormat dateFormat) {
		Calendar c = Calendar.getInstance();
		
		int timeInDay = calculateTime(matcher, 10);
		
		if ((matcher.group(8) != null) && (matcher.group(9) != null)) {
			// Every X days/weeks/...
			// In this case, the first occurrence will always be today - use today's date
			
		} else if (matcher.group(1) != null) {
			// Every day/week/...
			
			// Since the user can write several options at once, we need to split it
			// e.g. "every sunday,tuesday,friday at 3pm"
			
			String[] days = matcher.group(1).split(" *, *");
			
			int closestDay = -1;
			int currentDay = c.get(Calendar.DAY_OF_WEEK);
			boolean hasLastDayOfMonth = false;
			
			for (int i = 0; i < days.length; i++){ 
				if ((days[i].equals("day")) || (days[i].equals("week")) || (days[i].equals("month"))) {
					// In this case, the first occurrence will always be today - use today's date
					closestDay = currentDay;
					break;
				} else if ((days[i].equals("weekday")) || (days[i].equals("wday"))) {
					if ((currentDay >= Calendar.MONDAY) && (currentDay <= Calendar.FRIDAY)) {
						// Current day is a weekday
						closestDay = currentDay;
						break;
					} else {
						// Choose Monday as the closest day
						int dayValue = Calendar.MONDAY;
						
						if (modulus(dayValue - currentDay, 7) < modulus(dayValue - currentDay, 7))
							closestDay = dayValue;
					}
					
				} else if ((days[i].equals("last day")) || (days[i].equals("lday"))) {
					// Last day of the month
					hasLastDayOfMonth = true;
					
				} else {
					// It's a named day (Sunday/Monday/...)
					int dayValue = parseWeekDay(days[i]);
					
					// Remember only the closest weekday
					if ((closestDay == -1) || (modulus(dayValue - currentDay, 7) < modulus(closestDay - currentDay, 7)))
						closestDay = dayValue;
				}
			}
			
			if (closestDay != -1)
				c.add(Calendar.DAY_OF_WEEK, modulus(closestDay - currentDay, 7));
			
			if (hasLastDayOfMonth) {
				// See if last day of the month is closer than current date
				Calendar lastDay = Calendar.getInstance();
				
				// Get to start of next month
				lastDay.set(Calendar.DAY_OF_MONTH, 1);
				lastDay.add(Calendar.MONTH, 1);
				// Substract by one day to reach the end of current month
				lastDay.add(Calendar.DAY_OF_MONTH, -1);
				
				if ((closestDay == -1) || (lastDay.before(c))) {
					// Last day of the month is closer
					c = lastDay;
				}
			}
			
		} else if ((matcher.group(2) != null) || (matcher.group(7) != null)) {
			// "Every 7 may" or "Every 7/5", ...
			
			int day = 0;
			int month = 0;
			
			if (matcher.group(2) != null) {
				// "Every 7 may" or "Every 30/5"
				
				if (matcher.group(3) != null) {
					// Every 7 may
					day = Integer.valueOf(matcher.group(2));
					month = parseMonth(matcher.group(3));
				} else {
					// Every 7/5
					
					if (dateFormat == DateFormat.DD_MM_YYYY) {
						day = Integer.valueOf(matcher.group(2));
						month = Integer.valueOf(matcher.group(4)) - 1; // -1 since months in Calendar are zero-based
					} else if (dateFormat == DateFormat.MM_DD_YYYY){
						day = Integer.valueOf(matcher.group(4));
						month = Integer.valueOf(matcher.group(2)) - 1; // -1 since months in Calendar are zero-based
					}
				}
				
			} else {
				// "Every May 7" or "Every 10/25"
				
				if (matcher.group(5) != null) {
					// Every May 7
					day = Integer.valueOf(matcher.group(7));
					month = parseMonth(matcher.group(5));
				} else {
					// Every 5/31
					day = Integer.valueOf(matcher.group(7));
					month = Integer.valueOf(matcher.group(6)) - 1; // -1 since months in Calendar are zero-based
				}
			}
			
			c.set(Calendar.MONTH, month);
			c.set(Calendar.DAY_OF_MONTH, day);
			
			if (c.before(Calendar.getInstance())) {
				// We're pass that date - assume next year
				c.add(Calendar.YEAR, 1);
			}
			
		}
	
		// Set to specific hour/minute in day
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.set(Calendar.SECOND, timeInDay);

		dueDate = c.getTime();
	}
	
	
	
	/**
	 * Private utility function for calculating a real date string
	 * @param matcher result from matching the regular expression for real date (i.e. REGEX_REAL_DATE)
	 * @param dateFormat dd-mm-yyyy or mm-dd-yyyy?
	 */
	private void calculateRealDate(Matcher matcher, DateFormat dateFormat) {
		Calendar c = Calendar.getInstance();
		
		int timeInDay = calculateTime(matcher, 10);
		// Set to specific hour/minute in day
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.set(Calendar.SECOND, timeInDay);
		
		if ((matcher.group(1) != null) && (matcher.group(2) == null) && (matcher.group(3) == null)) {
			// Day-of-month only
			int dayOfMonth = Integer.valueOf(matcher.group(1));
			
			if (c.get(Calendar.DAY_OF_MONTH) > dayOfMonth) {
				// We're pass that date - assume next month
				c.add(Calendar.MONTH, 1);
			}
			
			c.set(Calendar.DAY_OF_MONTH, dayOfMonth);
			
		} else if (((matcher.group(1) != null) && ((matcher.group(2) != null) || (matcher.group(3) != null)) && (matcher.group(4) == null)) ||
				(((matcher.group(5) != null) || (matcher.group(6) != null)) && (matcher.group(7) != null) && (matcher.group(8) == null))) {
			// Day-of-month and month only ("23 sep", "sep 23", "23-9", "9-23")
			
			if ((matcher.group(5) == null) && (matcher.group(6) == null)) {
				// dd-mm
				
				if (matcher.group(2) != null) {
					// e.g. 23-sep
					c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(matcher.group(1)));
					c.set(Calendar.MONTH, parseMonth(matcher.group(2)));
				} else {
					// e.g. 23-9
					if (dateFormat == DateFormat.DD_MM_YYYY) {
						c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(matcher.group(1)));
						c.set(Calendar.MONTH, Integer.valueOf(matcher.group(3)) - 1); // -1 since months in Calendar are zero-based
					} else if (dateFormat == DateFormat.MM_DD_YYYY) {
						c.set(Calendar.MONTH, Integer.valueOf(matcher.group(1)) - 1); // -1 since months in Calendar are zero-based
						c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(matcher.group(3)));
					}
				}
				
			} else {
				// mm-dd
				
				if (matcher.group(5) != null) {
					// e.g. sep 23
					c.set(Calendar.MONTH, parseMonth(matcher.group(5)));
					c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(matcher.group(7)));
				} else {
					// e.g. 9-23
					c.set(Calendar.MONTH, Integer.valueOf(matcher.group(6)) - 1); // -1 since months in Calendar are zero-based
					c.set(Calendar.DAY_OF_MONTH, Integer.valueOf(matcher.group(7)));
				}
			}
			
			if (c.before(Calendar.getInstance())) {
				// We're pass that date - assume next year
				c.add(Calendar.YEAR, 1);
			}
			
		} else if (((matcher.group(1) != null) && ((matcher.group(2) != null) || (matcher.group(3) != null)) && (matcher.group(4) != null)) ||
				(((matcher.group(5) != null) || (matcher.group(6) != null)) && (matcher.group(7) != null) && (matcher.group(8) != null))) {
			// All date fields provided
			
			if (matcher.group(1) != null) {
				// dd-mm-yyyy
				
				if (matcher.group(3) != null) {
					// e.g. 27-09-2009
					if (dateFormat == DateFormat.DD_MM_YYYY) {
						c.set(
								Integer.valueOf(matcher.group(4)), /* Year */
								Integer.valueOf(matcher.group(3)) - 1, /* Month: -1 since months in Calendar are zero-based */
								Integer.valueOf(matcher.group(1)) /* Day of month */
							);
					} else if (dateFormat == DateFormat.MM_DD_YYYY) {
						c.set(
								Integer.valueOf(matcher.group(4)), /* Year */
								Integer.valueOf(matcher.group(1)) - 1, /* Month: -1 since months in Calendar are zero-based */
								Integer.valueOf(matcher.group(3)) /* Day of month */
							);
					}
				} else {
					// e.g. 27-sep-2009
					c.set(
							Integer.valueOf(matcher.group(4)), /* Year */
							parseMonth(matcher.group(2)), /* Month */
							Integer.valueOf(matcher.group(1)) /* Day of month */
						);
				}
				
			} else {
				// mm-dd-yyyy
				
				if (matcher.group(5) != null) {
					// e.g. sep-27-2009
					c.set(
						Integer.valueOf(matcher.group(8)), /* Year */
						parseMonth(matcher.group(5)), /* Month */
						Integer.valueOf(matcher.group(7)) /* Day of month */
					);
				} else {
					// e.g. 12-27-2009
					c.set(
						Integer.valueOf(matcher.group(8)), /* Year */
						Integer.valueOf(matcher.group(6)) - 1, /* Month: -1 since months in Calendar are zero-based */
						Integer.valueOf(matcher.group(7)) /* Day of month */
					);
				}
			}
			
		} else if (matcher.group(9) != null) {
			// Relative day (e.g. +5)
			c.add(Calendar.DAY_OF_MONTH, Integer.valueOf(matcher.group(9)));
		}
		
		dueDate = c.getTime();
	}
	
	
	/**
	 * Private utility function for calculating a contextual date string
	 * @param matcher result from matching the regular expression for contextual date (i.e. REGEX_CONTEXTUAL_DATE)
	 */
	private void calculateContextualDate(Matcher matcher) {
		Calendar c = Calendar.getInstance();
		
		boolean isNext = (matcher.group(1) != null);
		String day = matcher.group(2);
		
		int timeInDay = calculateTime(matcher, 3);
		
		// Set to specific hour/minute in day
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.set(Calendar.SECOND, timeInDay);
		
		if ((day.compareTo("today") == 0) || (day.compareTo("tod") == 0)) {
			// Do nothing - use today's date
		} else if ((day.compareTo("tomorrow") == 0) || (day.compareTo("tom") == 0)) {
			c.add(Calendar.DAY_OF_MONTH, 1);
		} else {
			// It's a weekday (Sunday/Monday/...)
			int currentDay = c.get(Calendar.DAY_OF_WEEK);
			int dayValue = 0;
			
			dayValue = parseWeekDay(day);
			
			c.add(Calendar.DAY_OF_WEEK, modulus(dayValue - currentDay, 7));
			
			if (isNext) // Next Sunday/Monday/...
				c.add(Calendar.DAY_OF_WEEK, 7);
				
		}
		
		dueDate = c.getTime();
	}
	
	/**
	 * Private utility function - since Java's % operator doesn't properly handle a negative X (in "X % Y")
	 * @param x
	 * @param y
	 * @return
	 */
	private int modulus(int x, int y)
	{
	    int result = x % y;
	    return (result < 0 ? (result + y) : result);
	}
	
	
	/**
	 * Returns a string representation of the task's due date (or null if it has no due date).
	 * Examples:
	 * 		If today/tomorrow --> "Today"/"Tomorrow"
	 * 		If in less than one week --> "Sunday", "Monday", ...
	 * 		If in same year --> "Dec 28", "Nov 13", ...
	 * 		Otherwise --> "Aug 28 2013"
	 *
	 * @param timeFormat 22:00 or 10pm?
	 * @param timeZoneOffsetMinutes the number of minutes of the user's local time zone
	 * 
	 * @return
	 */
	public String getDueDateDescription(TimeFormat timeFormat, int timeZoneOffsetMinutes) {
		if ((dueDate == null) || (dueDate.getTime() == 0)) {
			return null;
		}
		
		// Convert to user's time zone
		Date localDate;
		Calendar dueDateCalendar = Calendar.getInstance(); dueDateCalendar.setTime(this.dueDate);
		if ((dueDateCalendar.get(Calendar.HOUR_OF_DAY) != 23) || (dueDateCalendar.get(Calendar.MINUTE) != 59)) {
			// It's a due date with a specific time of day - convert it to user's local time zone for display
			localDate = new Date(this.dueDate.getTime() + (timeZoneOffsetMinutes * 60 * 1000));
		} else {
			// It's a due date marked for a single day (no specific time of day) - no need to convert
			// it to local time zone
			localDate = this.dueDate;
		}
		
		String date;
		
		Calendar currentTime = Calendar.getInstance(); currentTime.setTime(new Date());
		Calendar dueTime = Calendar.getInstance(); dueTime.setTime(localDate);
		Calendar tomorrow = Calendar.getInstance();
		tomorrow.add(Calendar.DAY_OF_MONTH, 1);
		Calendar oneWeekAhead = Calendar.getInstance();
		oneWeekAhead.set(Calendar.HOUR_OF_DAY, 0);
		oneWeekAhead.set(Calendar.MINUTE, 0);
		oneWeekAhead.set(Calendar.SECOND, 0);
		oneWeekAhead.add(Calendar.DAY_OF_MONTH, 8);
		oneWeekAhead.add(Calendar.MINUTE, -1); // Set to end of the 7th day
		
		long currentTimeYear = currentTime.get(Calendar.YEAR);
		long dueTimeYear = dueTime.get(Calendar.YEAR);
		long currentTimeMonth = currentTime.get(Calendar.MONTH);
		long dueTimeMonth = dueTime.get(Calendar.MONTH);
		long currentTimeDay = currentTime.get(Calendar.DAY_OF_MONTH);
		long dueTimeDay = dueTime.get(Calendar.DAY_OF_MONTH);
		
		if ((currentTimeYear == dueTimeYear) && (currentTimeMonth == dueTimeMonth) && (currentTimeDay == dueTimeDay)) {
			date = "Today";
		} else if ((tomorrow.get(Calendar.DAY_OF_MONTH) == dueTimeDay) &&
			(tomorrow.get(Calendar.MONTH) == dueTimeMonth) &&
			(tomorrow.get(Calendar.YEAR) == dueTimeYear)) {
			date = "Tomorrow";
		} else if ((dueTime.before(oneWeekAhead)) && (dueTime.after(currentTime))) {
			// Less than one week - Return the day of the week
			date = (new SimpleDateFormat("EEEE", Locale.US)).format(localDate);
			
		} else if (currentTimeYear == dueTimeYear) {
			// Same year
			date = (new SimpleDateFormat("MMM d", Locale.US)).format(localDate);
		} else {
			date = (new SimpleDateFormat("MMM d yyyy", Locale.US)).format(localDate);
		}
	
		// Add time if specified
		
		if ((dueTime.get(Calendar.HOUR_OF_DAY) != 23) || (dueTime.get(Calendar.MINUTE) != 59)) {
			if (timeFormat == TimeFormat.HH_MM)
				date += (new SimpleDateFormat(" @ HH:mm", Locale.US)).format(localDate);
			else if (timeFormat == TimeFormat.HH_PM_AM)
				date += (new SimpleDateFormat(" @ h:mm a", Locale.US)).format(localDate);
		}
		
		return date;
	}
	
	
	/**
	 * Returns the background color of the due date string (as to be displayed in the item list).
	 * Examples:
	 * 		Overdue --> Red
	 * 		Today --> Green
	 * 		Tomorrow --> Blue
	 * 		In 2-7 days --> Yellow
	 * 		Otherwise --> White
	 * @return
	 */
	public int getDueDateColor() {
		if ((dateString == null) || (dateString.compareToIgnoreCase(NO_DUE_DATE) == 0) ||
				(dueDate == null) || (dueDate.getTime() == 0)) {
			return 0xFFFFFF; // White
		}
	
		Calendar currentTime = Calendar.getInstance(); currentTime.setTime(new Date());
		Calendar dueTime = Calendar.getInstance(); dueTime.setTime(this.dueDate);
		Calendar tomorrow = Calendar.getInstance();
		tomorrow.add(Calendar.DAY_OF_MONTH, 1);
		Calendar oneWeekAhead = Calendar.getInstance();
		oneWeekAhead.set(Calendar.HOUR_OF_DAY, 0);
		oneWeekAhead.set(Calendar.MINUTE, 0);
		oneWeekAhead.set(Calendar.SECOND, 0);
		oneWeekAhead.add(Calendar.DAY_OF_MONTH, 8);
		oneWeekAhead.add(Calendar.MINUTE, -1); // Set to end of the 7th day
		
		long currentTimeYear = currentTime.get(Calendar.YEAR);
		long dueTimeYear = dueTime.get(Calendar.YEAR);
		long currentTimeMonth = currentTime.get(Calendar.MONTH);
		long dueTimeMonth = dueTime.get(Calendar.MONTH);
		long currentTimeDay = currentTime.get(Calendar.DAY_OF_MONTH);
		long dueTimeDay = dueTime.get(Calendar.DAY_OF_MONTH);
		
		if ((currentTimeYear == dueTimeYear) && (currentTimeMonth == dueTimeMonth) && (currentTimeDay == dueTimeDay)) {
			// Today
			return 0xB8F7B6; // Light-Green
		} else if ((tomorrow.get(Calendar.DAY_OF_MONTH) == dueTimeDay) &&
			(tomorrow.get(Calendar.MONTH) == dueTimeMonth) &&
			(tomorrow.get(Calendar.YEAR) == dueTimeYear)) {
			// Tomorrow
			return 0xD9E8FE; // Light-Blue
		} else if (dueTime.before(currentTime)) {
			// Overdue
			return 0xF7CBCB; // Light-Red
		} else if (dueTime.before(oneWeekAhead)) {
			// Less than one week
			return 0xFFFFCC; // Light-Yellow
		} else {
			// Further away
			return 0xFFFFFF; /// White
		}
	
	}
	
	
	
	/**
	 * Returns formatted item content
	 * @return
	 */
	public String getContent() {
		// Remove all label references
		if (rawContent != null) {
			String content = rawContent.replaceAll(LABEL_REG_EX, "");
			if (content.startsWith("*"))
				return content.substring(1);
			else
				return content;
		} else {
			return null;
		}
		
	}
	
	/**
	 * Returns all labels associated with this item
	 * @return
	 */
	public ArrayList<String> getLabels() {
		Pattern pattern = Pattern.compile(LABEL_REG_EX, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(rawContent);
		ArrayList<String> labels = new ArrayList<String>();
		
		while (matcher.find()) {
			labels.add(matcher.group());
		}
		
		return labels;
	}
	
	/**
	 * Sets the project's name
	 * @param name
	 * @param isGroup
	 * @param canBeCompleted
	 * @return
	 */
	public void setContent(String content, ArrayList<String> labels, boolean canBeCompleted) {
		StringBuilder newContent = new StringBuilder(content);
		
		if (labels != null) {
			// Append labels to content
			for (int i = 0; i < labels.size(); i++) {
				newContent.append(" @");
				newContent.append(labels.get(i));
			}
		}
		
		rawContent = (canBeCompleted == false ? "*" : "") + newContent.toString();
	}
	
	

	public Item() { }

	
	/**
	 * Initialize the Item object from key-value pairs
	 * @param parameters
	 */
	@SuppressWarnings("unchecked")
	public Item(Hashtable<String, Object> params) {
		if (params.containsKey(KEY__ID))
			id = ((Integer)params.get(KEY__ID)).intValue();
		if (params.containsKey(KEY__USER_ID))
			userId = ((Integer)params.get(KEY__USER_ID)).intValue();
		if (params.containsKey(KEY__PROJECT_ID))
			projectId = ((Integer)params.get(KEY__PROJECT_ID)).intValue();
		
		rawContent = (String)params.get(KEY__CONTENT);
		
		if (params.containsKey(KEY__DUE_DATE)) {
			SimpleDateFormat formatter = new SimpleDateFormat(DUE_DATE_FORMAT);
			try {
				dueDate = formatter.parse((String)params.get(KEY__DUE_DATE));
			} catch (ParseException e) {
				Log.e(TAG, String.format("Error while parsing due_date field of user: %s", (String)params.get(KEY__DUE_DATE)), e);
			}
		} else {
			dueDate = new Date(0);
		}
		
		dateString = (String)params.get(KEY__DATE_STRING);
		
		if (params.containsKey(KEY__PRIORITY))
			priority = ((Integer)params.get(KEY__PRIORITY)).intValue();
		if (params.containsKey(KEY__ITEM_ORDER))
			itemOrder = ((Integer)params.get(KEY__ITEM_ORDER)).intValue();
		if (params.containsKey(KEY__INDENT))
			indentLevel = ((Integer)params.get(KEY__INDENT)).intValue();
		
		if (params.containsKey(KEY__LABELS)) {
			labelIds = (ArrayList<Integer>) ((ArrayList<Integer>)params.get(KEY__LABELS)).clone();
		}
		
		if (params.containsKey(KEY__NOTE_COUNT))
			noteCount = ((Integer)params.get(KEY__NOTE_COUNT)).intValue();

		
		if (params.containsKey(KEY__CHECKED))
			completed = (((Integer)params.get(KEY__CHECKED)).intValue() == 0 ? false : true);

		
	}
}
