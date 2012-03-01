package budo.budoist.views;

import budo.budoist.R;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Query;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Edit/Create Label view
 * @author Yaron Budowski
 *
 */
public class EditQueryView extends Activity implements TextWatcher {
    private static final String TAG = EditQueryView.class.getSimpleName();

    private TodoistApplication mApplication;
    
    private EditText mQueryName;
    private EditText mQueryContent;
    private Button mOkButton;
    private Button mCancelButton;
    private TextView mQueryHelp;
    
    private Query mQuery;
    
    public static final String KEY__QUERY = "QUERY";
    
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        
        Bundle extras = getIntent().getExtras();
        
        mQuery = (Query)extras.get(KEY__QUERY);
        setContentView(R.layout.edit_query);
        
        mQueryHelp = (TextView)findViewById(R.id.query_help);
        mQueryHelp.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Show a dialog with help on query formatting (as give from the URL
				// http://todoist.com/Help/timeQuery)
		        Intent intent = new Intent(getBaseContext(), QueryFormatHelpView.class);
	            startActivity(intent);
			}
        });
       
        mOkButton = (Button)findViewById(R.id.query_ok_button);
        mOkButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mQuery.name = mQueryName.getText().toString();
				mQuery.query = mQueryContent.getText().toString();

				Intent intent = new Intent();
				intent.putExtra(KEY__QUERY, mQuery);
				setResult(RESULT_OK, intent);
				finish();
			}
		});
        
        mCancelButton = (Button)findViewById(R.id.query_cancel_button);
        mCancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
        
        mQueryName = (EditText)findViewById(R.id.query_name);
        mQueryName.addTextChangedListener(this);
        
        mQueryContent = (EditText)findViewById(R.id.query_content);
        mQueryContent.addTextChangedListener(this);

        if (mQuery == null) {
        	// Add a new query
        	mQuery = new Query();
        	this.setTitle("Add Query");
        	mOkButton.setEnabled(false);
        } else {
        	this.setTitle("Edit Query");
        }
        
        // Populate the form fields
        mQueryName.setText(mQuery.name);
        mQueryContent.setText(mQuery.query);
    }

	@Override
	public void afterTextChanged(Editable arg0) {
		mOkButton.setEnabled(checkForm());
	}

	@Override
	public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
			int arg3) {
	}

	@Override
	public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
	}

	/**
	 * Returns whether or not the edit query form is valid (thus, affecting whether or not
	 * to disable the OK button)
	 * @return
	 */
	private boolean checkForm() {
		if (mQueryName.getText().length() == 0) {
			// No query name
			return false;
		} else if (mQueryContent.getText().length() == 0) {
			// No query content
			return false;
		} else {
			// All OK
			return true;
		}
	}
}
