package budo.budoist.views;

import java.text.SimpleDateFormat;
import java.util.Locale;

import budo.budoist.R;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Item;
import budo.budoist.models.User;
import budo.budoist.services.TodoistClient;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * Displays date format help
 * @author Yaron Budowski
 *
 */
public class DateFormatHelpView extends Activity {
    private static final String TAG = DateFormatHelpView.class.getSimpleName();

    private TodoistApplication mApplication;
    private TodoistClient mClient;
    private User mUser;
    
    
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mUser = mClient.getUser();
        
        setContentView(R.layout.date_format_help);
        
        Button closeButton = (Button)findViewById(R.id.date_format_button_close);
        closeButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
        
        setTitle("Due Date Format");
        
        Item item = new Item();
        TextView dateFormat;
        
        dateFormat = (TextView)findViewById(R.id.date_format_1);
        item.dateString = "today";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));
        
        dateFormat = (TextView)findViewById(R.id.date_format_2);
        item.dateString = "tomorrow";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));

        dateFormat = (TextView)findViewById(R.id.date_format_3);
        item.dateString = "friday";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));

        dateFormat = (TextView)findViewById(R.id.date_format_4);
        item.dateString = "next friday";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));

        dateFormat = (TextView)findViewById(R.id.date_format_5);
        item.dateString = "tom @ 16:30";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate) + " at 4:30pm");

        dateFormat = (TextView)findViewById(R.id.date_format_6);
        item.dateString = "fri at 2pm";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate) + " at 2pm");

        dateFormat = (TextView)findViewById(R.id.date_format_7);
        item.dateString = "10";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));

        dateFormat = (TextView)findViewById(R.id.date_format_8);
        item.dateString = "10/5";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));

        dateFormat = (TextView)findViewById(R.id.date_format_9);
        item.dateString = "10/5/2011 @ 2pm";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate) + " at 2pm");

        dateFormat = (TextView)findViewById(R.id.date_format_10);
        item.dateString = "+5";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText("5 days from now: " + (new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));
     
   }
}
