package budo.budoist.views;

import budo.budoist.R;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Label;
import budo.budoist.views.adapters.ColorSpinnerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

/**
 * Edit/Create Label view
 * @author Yaron
 *
 */
public class EditLabelView extends Activity implements TextWatcher {
    private static final String TAG = EditLabelView.class.getSimpleName();

    private TodoistApplication mApplication;
    
    private Spinner mColorSpinner;
    private EditText mLabelName;
    private Button mOkButton;
    private Button mCancelButton;
    
    private Label mLabel;
    
    public static final String KEY__LABEL = "LABEL";
    
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        
        Bundle extras = getIntent().getExtras();
        
        mLabel = (Label)extras.get(KEY__LABEL);
        setContentView(R.layout.edit_label);
        
        mOkButton = (Button)findViewById(R.id.label_ok_button);
        mOkButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mLabel.name = mLabelName.getText().toString();
				mLabel.colorIndex = mColorSpinner.getSelectedItemPosition();

				Intent intent = new Intent();
				intent.putExtra(KEY__LABEL, mLabel);
				setResult(RESULT_OK, intent);
				finish();
			}
		});
        
        mCancelButton = (Button)findViewById(R.id.label_cancel_button);
        mCancelButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				setResult(RESULT_CANCELED);
				finish();
			}
		});
        
        mLabelName = (EditText)findViewById(R.id.label_name);
        mLabelName.addTextChangedListener(this);
        
        mColorSpinner = (Spinner)findViewById(R.id.label_color_selector);
        ColorSpinnerAdapter colorAdapter = new ColorSpinnerAdapter(
        		(Context)this,
        		android.R.layout.simple_spinner_dropdown_item,
        		Label.SUPPORTED_COLORS);
        mColorSpinner.setAdapter(colorAdapter);

        if (mLabel == null) {
        	// Add a new label
        	mLabel = new Label();
        	this.setTitle("Add Label");
        	mOkButton.setEnabled(false);
        } else {
        	this.setTitle("Edit Label");
        }
        
        // Populate the form fields
        mLabelName.setText(mLabel.name);
        mColorSpinner.setSelection(mLabel.colorIndex);
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
	 * Returns whether or not the edit label form is valid (thus, affecting whether or not
	 * to disable the OK button)
	 * @return
	 */
	private boolean checkForm() {
		if (mLabelName.getText().length() == 0) {
			// No label name
			return false;
		} else {
			// All OK
			return true;
		}
	}
}
