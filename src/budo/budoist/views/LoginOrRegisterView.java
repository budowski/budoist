package budo.budoist.views;

import budo.budoist.R;
import budo.budoist.Bootloader;
import budo.budoist.TodoistApplication;
import budo.budoist.models.User;
import budo.budoist.services.TodoistClient;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * Displays an initial screen with two options - login or register
 * @author Yaron Budowski
 *
 */
public class LoginOrRegisterView extends Activity {
    private static final String TAG = LoginOrRegisterView.class.getSimpleName();

    private TodoistApplication mApplication;
    private TodoistClient mClient;
    private User mUser;
    
    private Button mRegisterButton;
    private Button mLoginButton;
    
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mUser = mClient.getUser();
        
        setContentView(R.layout.login_or_register);
        
        mRegisterButton = (Button)findViewById(R.id.init_register_button);
        mRegisterButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
		        Intent intent = new Intent(getBaseContext(), RegisterView.class);
		        startActivityForResult(intent, Bootloader.REQUEST_CODE__REGISTER);
			}
		});
        
        mLoginButton = (Button)findViewById(R.id.init_login_button);
        mLoginButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
		        Intent intent = new Intent(getBaseContext(), LoginView.class);
		        startActivityForResult(intent, Bootloader.REQUEST_CODE__LOGIN);
			}
		});
        
        setTitle("Welcome");
   }
    
 	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if ((requestCode == Bootloader.REQUEST_CODE__REGISTER) || (requestCode == Bootloader.REQUEST_CODE__LOGIN)) {
			// Login/registration was successful - we can close this activity (so the user can't
			// press "back" and get shown this view)
			if (resultCode == RESULT_OK) {
				finish();
			}
		}
 	}
   
}
