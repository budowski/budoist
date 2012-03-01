package budo.budoist.models;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

/**
 * Used for formatting text of items/projects (e.g. %(b)my bold text%)
 * @author Yaron Budowski
 *
 */
public class TodoistTextFormatter {
	
	private static final String REGEX_FORMAT = "%\\((b|i|u|hl|ui|iu)\\)\\s*(.+?)\\s*%";
	
	/**
	 * Returns a formatted text to be displayed on-screen
	 * @param text
	 * @return
	 */
	public static CharSequence formatText(String text) {
		SpannableStringBuilder builder = new SpannableStringBuilder();
		
		if (text == null) {
			return "";
		}
		
		Pattern pattern = Pattern.compile(REGEX_FORMAT, Pattern.CASE_INSENSITIVE);
		
		Matcher matcher = pattern.matcher(text);
		int previousStart = 0;
		
		// Find all formattings within the text
		while (matcher.find()) {
			String formatType = matcher.group(1);
			String formattedText = matcher.group(2);
			
			builder.append(text.subSequence(previousStart, matcher.start()));
			
			// Append the formatted text without any whitespace
			int textStart = builder.length();
			builder.append(formattedText);
			int textEnd = builder.length();
			
			// Stylize the appended text
			CharacterStyle styleSpan = null;
			
			if (formatType.equalsIgnoreCase("b")) {
				styleSpan = new StyleSpan(Typeface.BOLD);
				
			} else if (formatType.equalsIgnoreCase("i")) {
				styleSpan = new StyleSpan(Typeface.ITALIC);
				
			} else if (formatType.equalsIgnoreCase("u")) {
				styleSpan = new UnderlineSpan();
				
			} else if (formatType.equalsIgnoreCase("hl")) {
				styleSpan = new BackgroundColorSpan(0xFFFFF57D); // Light-yellow
				
			} else if ((formatType.equalsIgnoreCase("ui")) || (formatType.equalsIgnoreCase("iu"))) {
				// Both underline AND italic
				styleSpan = new UnderlineSpan();
				builder.setSpan(styleSpan, textStart, textEnd, 0);
			
				styleSpan = new StyleSpan(Typeface.ITALIC);
			}
			
			builder.setSpan(styleSpan, textStart, textEnd, 0);
			
			previousStart = matcher.end();
		}
		
		builder.append(text.subSequence(previousStart, text.length()));
		
		return builder;
	}

}
