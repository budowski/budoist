package budo.budoist.views;

import budo.budoist.R;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Project;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import budo.budoist.views.adapters.ColorSpinnerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

/**
 * Edit/Create project view
 * @author Yaron Budowski
 *
 */
public class EditProjectView extends Activity implements TextWatcher, OnItemSelectedListener {
    private static final String TAG = EditProjectView.class.getSimpleName();

    private TodoistApplication mApplication;
    private TodoistClient mClient;
    private TodoistOfflineStorage mStorage;
    
    private Spinner mColorSpinner;
    private EditText mProjectName;
    private Spinner mOrderSpinner;
    private Spinner mIndentSpinner;
    private Button mOkButton;
    private Button mCancelButton;
    
    private Project mProject;
    private int mMaxOrder;
    
    public static final String KEY__PROJECT = "Project";
    
    private static final Integer[] INDENT_LEVELS = {1, 2, 3, 4};

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mStorage = mClient.getStorage();
        
        Bundle extras = getIntent().getExtras();
        
        mProject = (Project)extras.get(KEY__PROJECT);
        mMaxOrder = mStorage.getProjectsMaxOrder();
        
        if (mProject == null) {
        	mMaxOrder++; // If new project - we need to option to add it at the end of the list
        }
        
        setContentView(R.layout.edit_project);
        
        mOkButton = (Button)findViewById(R.id.project_ok_button);
        mOkButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mProject.setName(mProjectName.getText().toString() ,mProject.isGroup());
				mProject.colorIndex = mColorSpinner.getSelectedItemPosition();
				mProject.itemOrder = mOrderSpinner.getSelectedItemPosition() + 1;
				mProject.indentLevel = mIndentSpinner.getSelectedItemPosition() + 1;

				Intent intent = new Intent();
				intent.putExtra(KEY__PROJECT, mProject);
				setResult(RESULT_OK, intent);
				finish();
			}
		});
        
        mCancelButton = (Button)findViewById(R.id.project_cancel_button);
        mCancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
        
        mProjectName = (EditText)findViewById(R.id.project_name);
        mProjectName.addTextChangedListener(this);
        
        mColorSpinner = (Spinner)findViewById(R.id.project_color_selector);
        ColorSpinnerAdapter colorAdapter = new ColorSpinnerAdapter(
        		(Context)this,
        		android.R.layout.simple_spinner_dropdown_item,
        		Project.SUPPORTED_COLORS);
        mColorSpinner.setAdapter(colorAdapter);

        Integer[] orderLevels = new Integer[mMaxOrder];
        for (int i = 0; i < mMaxOrder; i++) { orderLevels[i] = i + 1; }
        mOrderSpinner = (Spinner)findViewById(R.id.project_order_selector);
        ArrayAdapter<Integer> orderAdapter = new ArrayAdapter<Integer>(
        		(Context)this,
        		android.R.layout.simple_spinner_item,
        		orderLevels);
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mOrderSpinner.setAdapter(orderAdapter);
        mOrderSpinner.setOnItemSelectedListener(this);

        mIndentSpinner = (Spinner)findViewById(R.id.project_indent_selector);
        ArrayAdapter<Integer> indentAdapter = new ArrayAdapter<Integer>(
        		(Context)this,
        		android.R.layout.simple_spinner_item,
        		INDENT_LEVELS);
        indentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mIndentSpinner.setAdapter(indentAdapter);
        mIndentSpinner.setOnItemSelectedListener(this);
        
        if (mProject == null) {
        	// Add a new project
        	mProject = new Project();
        	mProject.indentLevel = 1;
        	mProject.itemOrder = mMaxOrder;
        	this.setTitle("Add Project");
        	mOkButton.setEnabled(false);
        } else {
        	this.setTitle("Edit Project");
        }
        
        // Populate the form fields
        mProjectName.setText(mProject.getName());
        mColorSpinner.setSelection(mProject.colorIndex);
        mOrderSpinner.setSelection(mProject.itemOrder - 1); // item order is one-based (while spinner is zero-based)
        mIndentSpinner.setSelection(mProject.indentLevel - 1);
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

	@Override
	public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
			long arg3) {
		mOkButton.setEnabled(checkForm());
	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {
	}
	
	/**
	 * Returns whether or not the edit project form is valid (thus, affecting whether or not
	 * to disable the OK button)
	 * @return
	 */
	private boolean checkForm() {
		if (mProjectName.getText().length() == 0) {
			// No project name
			return false;
		} else if ((mOrderSpinner.getSelectedItemPosition() == 0) &&
			(mIndentSpinner.getSelectedItemPosition() > 0)){
			// The project in the first position cannot have a bigger-than-one indent level
			return false;
		} else {
			// All OK
			return true;
		}
	}
}
