package budo.budoist.views;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import budo.budoist.R;
import budo.budoist.Bootloader;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Item;
import budo.budoist.models.Project;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistOfflineStorage;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView.OnItemSelectedListener;

/**
 * Edit/Create item view
 * @author Yaron Budowski
 *
 */
public class EditItemView extends Activity implements TextWatcher, OnItemSelectedListener {
    private static final String TAG = EditItemView.class.getSimpleName();
    
    private static final int DATE_DIALOG_ID = 1;
    
    private TodoistApplication mApplication;
    private TodoistClient mClient;
    private TodoistOfflineStorage mStorage;
    
    private EditText mItemContent;
    private EditText mItemDueString;
    private Spinner mOrderSpinner;
    private Spinner mIndentSpinner;
    private Button mOkButton;
    private Button mCancelButton;
    private Button mSelectLabelsButton;
    private ImageButton mSelectDueDateButton;
    private ImageButton mSelectProjectButton;
    private TextView mItemProjectName;
    private TextView mDateHelp;
    
    private CheckBox mPriority1, mPriority2, mPriority3, mPriority4;
    
    private Item mItem;
    private int mMaxOrder;
    
    public static final String KEY__ITEM = "Item";
    public static final String KEY__PROJECT = "Project";
    public static final String KEY__MAX_ORDER = "MaxOrder";
    
    private static final Integer[] INDENT_LEVELS = {1, 2, 3, 4, 5};
    
    private Project mItemProject = null;
    
    private int mYear, mMonth, mDay;
    
    // The callback received when the user "sets" the date in the dialog
    private DatePickerDialog.OnDateSetListener mDateSetListener =
    	new DatePickerDialog.OnDateSetListener() {
	    	public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
	    		mYear = year;
	    		mMonth = monthOfYear;
	    		mDay = dayOfMonth;
	    		
	    		String dueString = (new SimpleDateFormat("d MMM yyyy", Locale.US)).format(
	    				(new GregorianCalendar(mYear, mMonth, mDay)).getTime());
	    		
	    		mItemDueString.setText(dueString);
    		}
    	}; 
    
    private void refreshProject() {
    	if (mItemProject == null) {
    		// No project - use the default one
    		mItemProject = mStorage.getProject(mStorage.getDefaultProject());
    		
    		if (mItemProject == null) {
    			// No default project set (or default project was deleted) - use the first available project instead
    			ArrayList<Project> projects = mStorage.getProjects();
    			
    			if (projects.size() > 0) {
    				mItemProject = projects.get(0);
    				
    				// Fix this issue in the storage
    				mStorage.setDefaultProject(mItemProject.id);
    			}
    		}
    	}
    	
        if (mItemProject == null) {
        	mItemProjectName.setText("<None>");
        	mItemProjectName.setTextColor(Color.BLACK);
        	mOrderSpinner.setEnabled(false);
        	mIndentSpinner.setEnabled(false);
        	mMaxOrder = 1;
        } else {
        	mItemProjectName.setText(TodoistTextFormatter.formatText(mItemProject.getName()));
        	mItemProjectName.setTextColor((0xFF << 24) | mItemProject.getColor());
        	mOrderSpinner.setEnabled(true);
        	mIndentSpinner.setEnabled(true);
        	
        	
        	// Since items in a project do not necessarily have consecutive itemOrder (this happens
        	// when deleting an item from the middle of the list in the Todoist website) - thus
        	// we must use the item with the largest itemOrder and set it as the maximum possible order
        	int itemCount = mStorage.getMaxItemOrderForProject(mItemProject);
        	
        	if ((mItem.id == 0) || (mItem.projectId != mItemProject.id)) {
        		// +1 in case we want to place the item at the end of the list (happens when the item
        		// is new or when it has been moved to a new project)
	        	mMaxOrder = itemCount + 1;
        	} else {
	        	mMaxOrder = itemCount;
        	}
        }
        
        Integer[] orderLevels = new Integer[mMaxOrder];
        for (int i = 0; i < mMaxOrder; i++) { orderLevels[i] = i + 1; }
        ArrayAdapter<Integer> orderAdapter = new ArrayAdapter<Integer>(
        		(Context)this,
        		android.R.layout.simple_spinner_item,
        		orderLevels);
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mOrderSpinner.setAdapter(orderAdapter);       
        
    	if ((mItem.id == 0) || ((mItemProject != null) && (mItem.projectId != mItemProject.id))) {
    		// Default item order is at the end of the list
	        mOrderSpinner.setSelection(mMaxOrder - 1); // item order is one-based (while spinner is zero-based)
    	} else {
	        mOrderSpinner.setSelection(mItem.itemOrder - 1); // item order is one-based (while spinner is zero-based)
    	}
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mStorage = mClient.getStorage();
        
        Bundle extras = getIntent().getExtras();
        
        mItem = (Item)extras.get(KEY__ITEM);
        mMaxOrder = extras.getInt(KEY__MAX_ORDER);
        setContentView(R.layout.edit_item);
        
        mOkButton = (Button)findViewById(R.id.item_ok_button);
        mOkButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mItem.setContent(mItemContent.getText().toString(), null, true);
				mItem.itemOrder = mOrderSpinner.getSelectedItemPosition() + 1;
				mItem.indentLevel = mIndentSpinner.getSelectedItemPosition() + 1;
				mItem.dateString = mItemDueString.getText().toString().trim();
				mItem.projectId = mItemProject.id;
				
				if (mPriority1.isChecked())
					mItem.priority = Item.PRIORITY_1_HIGHEST;
				else if (mPriority2.isChecked())
					mItem.priority = Item.PRIORITY_2;
				else if (mPriority3.isChecked())
					mItem.priority = Item.PRIORITY_3;
				else if (mPriority4.isChecked())
					mItem.priority = Item.PRIORITY_4_LOWEST;

				Intent intent = new Intent();
				intent.putExtra(KEY__ITEM, mItem);
				setResult(RESULT_OK, intent);
				finish();
			}
		});
        
        mCancelButton = (Button)findViewById(R.id.item_cancel_button);
        mCancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
        
        mDateHelp = (TextView)findViewById(R.id.item_date_help);
        mDateHelp.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Show a dialog with help on date formatting (as give from the URL
				// http://todoist.com/Help/timeInsert)
		        Intent intent = new Intent(getBaseContext(), DateFormatHelpView.class);
	            startActivity(intent);
			}
        });

        
        mItemContent = (EditText)findViewById(R.id.item_content);
        mItemContent.addTextChangedListener(this);
        
        mOrderSpinner = (Spinner)findViewById(R.id.item_order_selector);
        mOrderSpinner.setOnItemSelectedListener(this);

        mIndentSpinner = (Spinner)findViewById(R.id.item_indent_selector);
        ArrayAdapter<Integer> indentAdapter = new ArrayAdapter<Integer>(
        		(Context)this,
        		android.R.layout.simple_spinner_item,
        		INDENT_LEVELS);
        indentAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mIndentSpinner.setAdapter(indentAdapter);
        mIndentSpinner.setOnItemSelectedListener(this);
        
        mPriority1 = (CheckBox)findViewById(R.id.item_priority_1);
        mPriority2 = (CheckBox)findViewById(R.id.item_priority_2);
        mPriority3 = (CheckBox)findViewById(R.id.item_priority_3);
        mPriority4 = (CheckBox)findViewById(R.id.item_priority_4);
        
        OnClickListener onClick = new OnClickListener() {
			@Override
			public void onClick(View v) {
				mPriority1.setChecked(false);
				mPriority2.setChecked(false);
				mPriority3.setChecked(false);
				mPriority4.setChecked(false);
				
				((CheckBox)v).setChecked(true);
			}
		};
       
        mPriority1.setOnClickListener(onClick);
        mPriority2.setOnClickListener(onClick);
        mPriority3.setOnClickListener(onClick);
        mPriority4.setOnClickListener(onClick);
       
        if (mItem == null) {
        	// Add a new item
        	mItem = new Item();
        	mItem.indentLevel = 1;
        	mItem.itemOrder = mMaxOrder;
        	mItem.priority = Item.PRIORITY_4_LOWEST;
        	
        	this.setTitle("Add Item");
        } else {
        	this.setTitle("Edit Item");
        }
        
        mItemProjectName = (TextView)findViewById(R.id.item_project_name);
        
        
        mItemProject = (Project)extras.get(KEY__PROJECT);
        
        // In case initial project wasn't passed from outside
        if (mItemProject == null)
	        mItemProject = mClient.getProjectById(mItem.projectId);
        
        refreshProject();
       
        mSelectProjectButton = (ImageButton)findViewById(R.id.item_choose_project_button);
        mSelectProjectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Show a project list
		        Intent intent = new Intent(getBaseContext(), ProjectListView.class);
		        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectListView.ProjectViewMode.MOVE_TO_PROJECT.toString());
	            startActivityForResult(intent, Bootloader.REQUEST_CODE__MOVE_TO_PROJECT);
			}
		});
        
        mItemDueString = (EditText)findViewById(R.id.item_due_string);
        
        mSelectLabelsButton = (Button)findViewById(R.id.item_select_labels);
        mSelectLabelsButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Show a checked label list
		        Intent intent = new Intent(getBaseContext(), LabelListView.class);
		        intent.putExtra(LabelListView.KEY__VIEW_MODE, LabelListView.LabelViewMode.SELECT_LABELS.toString());
		        intent.putExtra(LabelListView.KEY__ITEM, (Serializable)mItem);
	            startActivityForResult(intent, Bootloader.REQUEST_CODE__SELECT_ITEM_LABELS);
			}
		});
        
        mSelectDueDateButton = (ImageButton)findViewById(R.id.item_choose_date_button);
        mSelectDueDateButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				// Let the user choose a due date
				
				if ((mYear == 0) || (mMonth == 0) || (mDay == 0)) {
					// Date has never been selected before - init the dialog with a default date
					Calendar c = Calendar.getInstance();
					
					if ((mItem.dueDate == null) || (mItem.dueDate.getTime() == 0)) {
						// No due date - set as today's date (which means we don't have to set the
						// calendar at - it defaults to today's date anyhow)
					} else {
						// Set as current item's due date
						c.setTime(mItem.dueDate);
					}
					
					mYear = c.get(Calendar.YEAR);
					mMonth = c.get(Calendar.MONTH);
					mDay = c.get(Calendar.DAY_OF_MONTH);
				}
				
				showDialog(DATE_DIALOG_ID);
			}
		});
        
        // Populate the form fields
        String itemContent = mItem.getContent();
        if (itemContent == null) {
        	mItemContent.setText("");
        } else {
        	mItemContent.setText((mItem.canBeCompleted() ? "" : "*") + itemContent);
        }
        mIndentSpinner.setSelection(mItem.indentLevel - 1);
        mItemDueString.setText(mItem.hasDueDateString() ? mItem.dateString : "");
        
        if (mItem.priority == Item.PRIORITY_1_HIGHEST)
        	mPriority1.setChecked(true);
        else if (mItem.priority == Item.PRIORITY_2)
        	mPriority2.setChecked(true);
        else if (mItem.priority == Item.PRIORITY_3)
        	mPriority3.setChecked(true);
        else if (mItem.priority == Item.PRIORITY_4_LOWEST)
        	mPriority4.setChecked(true);
        
    	mOkButton.setEnabled(checkForm());
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
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DATE_DIALOG_ID:
		    DatePickerDialog dateDialog;
		    
		    try {
		        // Initialize a date picker dialog with the last remembered date
    		    dateDialog = new DatePickerDialog(this,
    					mDateSetListener,
    					mYear, mMonth, mDay);
		    } catch (Exception exc) {
		        // Invalid date - default back to current date
		        
		        Calendar c = Calendar.getInstance();
		        mYear = c.get(Calendar.YEAR);
		        mMonth = c.get(Calendar.MONTH);
		        mDay = c.get(Calendar.DAY_OF_MONTH);
		        
		        dateDialog = new DatePickerDialog(this,
		                mDateSetListener,
		                mYear, mMonth, mDay);
		    }
		    
		    return dateDialog;
		}
		    
		return null;
	}	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Bootloader.REQUEST_CODE__SELECT_ITEM_LABELS) {
			// Called when returning from "Select Labels"
			if (resultCode == RESULT_OK) {
				Item item = (Item)data.getExtras().get(LabelListView.KEY__ITEM);
				
				// Update item with new labels
				mItem = item;
			}
		} else if (requestCode == Bootloader.REQUEST_CODE__MOVE_TO_PROJECT) {
			// Called when returning from "Select Project"
			if (resultCode == RESULT_OK) {
				Project project = (Project)data.getExtras().get(ProjectListView.KEY__PROJECT);
				
				// Remember currently selected project
				mItemProject = project;
				
				refreshProject();
				mOkButton.setEnabled(checkForm());
			}
		}
    }	
	
	/**
	 * Returns whether or not the edit item form is valid (thus, affecting whether or not
	 * to disable the OK button)
	 * @return
	 */
	private boolean checkForm() {
		if (mItemContent.getText().length() == 0) {
			// No item content
			return false;
		} else if ((mOrderSpinner.getSelectedItemPosition() == 0) &&
			(mIndentSpinner.getSelectedItemPosition() > 0)){
			// The item in the first position cannot have a bigger-than-one indent level
			return false;
		} else if (mItemProject == null) {
			// No project selected for item
			return false;
		} else {
			// All OK
			return true;
		}
	}
}
