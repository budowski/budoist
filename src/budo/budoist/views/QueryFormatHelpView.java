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
 * Displays query format help
 * @author Yaron
 *
 */
public class QueryFormatHelpView extends Activity {
    private static final String TAG = QueryFormatHelpView.class.getSimpleName();

    private TodoistApplication mApplication;
    private TodoistClient mClient;
    private User mUser;
    
    
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mUser = mClient.getUser();
        
        setContentView(R.layout.query_format_help);
        
        Button closeButton = (Button)findViewById(R.id.query_format_button_close);
        closeButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				finish();
			}
		});
        
        setTitle("Query Format");
        
        Item item = new Item();
        TextView dateFormat;
        
        dateFormat = (TextView)findViewById(R.id.query_format_1);
        item.dateString = "today";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));
        
        dateFormat = (TextView)findViewById(R.id.query_format_2);
        item.dateString = "tomorrow";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));

        dateFormat = (TextView)findViewById(R.id.query_format_3);
        item.dateString = "friday";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));

        dateFormat = (TextView)findViewById(R.id.query_format_4);
        item.dateString = "next friday";
        item.calculateFirstDueDate(mUser.dateFormat);
        dateFormat.setText((new SimpleDateFormat("d MMM yyyy", Locale.US)).format(item.dueDate));
    
   }
}
