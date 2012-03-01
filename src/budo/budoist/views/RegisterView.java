package budo.budoist.views;

import budo.budoist.R;
import budo.budoist.TodoistApplication;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistServerException;
import budo.budoist.views.ProjectListView.ProjectViewMode;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Displays the register screen
 * @author Yaron
 *
 */
public class RegisterView extends Activity implements TextWatcher, OnClickListener {
    private static final String TAG = RegisterView.class.getSimpleName();

    private TodoistApplication mApplication;
    private TodoistClient mClient;
    
    private Button mRegisterButton;
    private EditText mFullName;
    private EditText mEmail;
    private EditText mPassword;
    
     // A "Registering.." dialog used
	private ProgressDialog mRegisterDialog;
	
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        
        setContentView(R.layout.register);
        
        mRegisterButton = (Button)findViewById(R.id.register_button);
        mRegisterButton.setOnClickListener(this);
        
        mFullName = (EditText)findViewById(R.id.register_full_name);
        mFullName.setText("");
        mFullName.addTextChangedListener(this);
        
        mEmail = (EditText)findViewById(R.id.register_email);
        mEmail.setText("");
        mEmail.addTextChangedListener(this);
        
        mPassword = (EditText)findViewById(R.id.register_password);
        mPassword.setText("");
        mPassword.addTextChangedListener(this);
        
        mRegisterButton.setEnabled(checkForm());
        
        setTitle("Register");
   }
    
 	@Override
	public void afterTextChanged(Editable arg0) {
		mRegisterButton.setEnabled(checkForm());
	}

	@Override
	public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
			int arg3) {
	}

	@Override
	public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
	}

	
	/**
	 * Returns whether or not the register form is valid (thus, affecting whether or not
	 * to disable the Register button)
	 * @return
	 */
	private boolean checkForm() {
		if (mFullName.getText().toString().trim().length() == 0) {
			// No full name
			return false;
		} else if (mEmail.getText().toString().trim().length() == 0) {
			// No email
			return false;
		} else if (mPassword.getText().toString().trim().length() == 0) {
			// No password
			return false;
		} else {
			// All OK
			return true;
		}
	}

	@Override
	public void onClick(View arg0) {
		// Register button has been clicked
		
		mRegisterDialog = ProgressDialog.show(this, "", "Registering...");
		
		// Run this logic on a separate thread in order for the dialog to actually show
		(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					mClient.register(mEmail.getText().toString(), mFullName.getText().toString(), mPassword.getText().toString());
				} catch (final TodoistServerException e) {
					// Registration failed
					runOnUiThread(new Runnable() {
						public void run() {	
							if (mRegisterDialog.isShowing())
								mRegisterDialog.dismiss();
							
							String errorText;
							switch (e.getErrorCode()) {
							case ERROR_PASSWORD_TOO_SHORT:
							case TOO_SHORT_PASSWORD: // Yes, Todoist servers can return two error codes for the same thing
								errorText = "Password is too short - need at least 6 characters";
								break;
							case ALREADY_REGISTRED:
								errorText = "Someone with that email address is already registered";
								break;
							default:
								errorText = "Registration failed - connection problems";
								break;
							}
							
							Toast.makeText(RegisterView.this, errorText, Toast.LENGTH_SHORT).show();
						}
					});
					
					return;
				}

				runOnUiThread(new Runnable() {
					public void run() {	
						if (mRegisterDialog.isShowing())
							mRegisterDialog.dismiss();
					}
				});
				
				runOnUiThread(new Runnable() {
					public void run() {	
						Toast.makeText(RegisterView.this, "Registration successful", Toast.LENGTH_SHORT).show();
						
						// Since this is the first time the user logins, simply switch to filter by project view
				        Intent intent = new Intent(getBaseContext(), ProjectListView.class);
				        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.FILTER_BY_PROJECTS.toString());
				        startActivity(intent);
					
						RegisterView.this.setResult(RESULT_OK);
						RegisterView.this.finish();
					}
				});
			}
		})).start();
	
	}

}
