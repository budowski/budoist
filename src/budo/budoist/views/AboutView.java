package budo.budoist.views;

import budo.budoist.R;
import budo.budoist.Bootloader;
import budo.budoist.TodoistApplication;
import budo.budoist.services.TodoistClient;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

/**
 * Displays the about dialog
 * @author Yaron Budowski
 *
 */
public class AboutView extends Activity {
    private static final String TAG = AboutView.class.getSimpleName();

    private TodoistApplication mApplication;
    private TodoistClient mClient;
    
    private TextView mVersion;
    private TextView mContactEmail;
    private TextView mSourceCode;
    private TextView mTreeViewProject;
    
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        
        setContentView(R.layout.about);
        
        try {
	        mVersion = (TextView)findViewById(R.id.about_version);
			mVersion.setText("Version: " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		
        mContactEmail = (TextView)findViewById(R.id.about_contact);
        mContactEmail.setText(Html.fromHtml("Contact: <a href=\"mailto:budowski@gmail.com\">budowski@gmail.com</a>"));
        mContactEmail.setMovementMethod(LinkMovementMethod.getInstance());
        
        mSourceCode = (TextView)findViewById(R.id.about_source_code);
        mSourceCode.setText(Html.fromHtml("Download <a href=\"http://github.com/budowski/budoist/\">source code</a>"));
        mSourceCode.setMovementMethod(LinkMovementMethod.getInstance());
       
        mTreeViewProject = (TextView)findViewById(R.id.about_tree_view);
        mTreeViewProject.setText(Html.fromHtml("Uses parts from <a href=\"http://code.google.com/p/tree-view-list-android/\">tree-view-list-android</a> and <a href=\"http://code.google.com/p/android-file-dialog/\">android-file-dialog</a> projects"));
        mTreeViewProject.setMovementMethod(LinkMovementMethod.getInstance());
        
        setTitle("About Budoist");
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
