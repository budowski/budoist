package budo.budoist.views;

import budo.budoist.R;
import budo.budoist.TodoistApplication;
import budo.budoist.models.Item;
import budo.budoist.models.TodoistTextFormatter;
import budo.budoist.models.User;
import budo.budoist.services.InvalidDateStringException;
import budo.budoist.services.TodoistClient;
import budo.budoist.services.TodoistServerException;
import budo.budoist.services.TodoistClient.ISyncProgress;
import budo.budoist.services.TodoistServer.ErrorCode;
import budo.budoist.views.ProjectListView.ProjectViewMode;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Displays the login screen
 * @author Yaron Budowski
 *
 */
public class LoginView extends Activity implements TextWatcher, OnClickListener {
    private static final String TAG = LoginView.class.getSimpleName();

    private TodoistApplication mApplication;
    private TodoistClient mClient;
    private User mUser;
    
    private Button mLoginButton;
    private EditText mEmail;
    private EditText mPassword;
    
	private final static String SYNC_INITIAL_MESSAGE = "Syncing for the first time. This might take a while...";
	
	private static final int MAX_ITEM_NAME_IN_SYNC_ERROR = 20;
	private static final int SYNCING_FAILED_DUE_DATE_ERROR_DURATION = 7000;
	
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mApplication = (TodoistApplication)getApplication();
        mClient = mApplication.getClient();
        mUser = mClient.getUser();
        
        setContentView(R.layout.login);
        
        mLoginButton = (Button)findViewById(R.id.login_button);
        mLoginButton.setOnClickListener(this);
        
        mEmail = (EditText)findViewById(R.id.login_email);
        mEmail.setText(mUser.email);
        mEmail.addTextChangedListener(this);
        
        mPassword = (EditText)findViewById(R.id.login_password);
        mPassword.setText(mUser.password);
        mPassword.addTextChangedListener(this);
        
        mLoginButton.setEnabled(checkForm());
        
        setTitle("Login");
   }
    
 	@Override
	public void afterTextChanged(Editable arg0) {
		mLoginButton.setEnabled(checkForm());
	}

	@Override
	public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
			int arg3) {
	}

	@Override
	public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
	}

	
	/**
	 * Returns whether or not the login form is valid (thus, affecting whether or not
	 * to disable the Login button)
	 * @return
	 */
	private boolean checkForm() {
		if (mEmail.getText().toString().trim().length() == 0) {
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

	/**
	 * Performs an immediate sync with GUI indications (shows a sync dialog)
	 * 
	 * @param activity the activity on which to display the GUI notifications
	 * @param client
	 * @param email
	 * @param password
	 * @param runOnCompletion a callback function to be run when sync is completed successfully (optional)
	 */
    public static void syncNow(final Activity activity, final TodoistClient client, final String email, final String password, final Runnable runOnCompletion) {
		if (client.isCurrentlySyncing()) {
			 Toast.makeText(activity, "Sync is already running...", Toast.LENGTH_SHORT).show();
			 return;
		}

  		// Don't let the user switch between screen orientations (causes the login process to restart)
		activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
		
		final ProgressDialog loginDialog = ProgressDialog.show(activity, "", "Logging in...");
		final PowerManager pm = (PowerManager)activity.getSystemService(Context.POWER_SERVICE);
		final WakeLock wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK, TAG);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		LayoutInflater inflater = (LayoutInflater)activity.getSystemService(LAYOUT_INFLATER_SERVICE);
		final View dialogLayout = inflater.inflate(R.layout.sync_dialog,
               (ViewGroup)activity.findViewById(R.id.sync_dialog_root));
		Display display = activity.getWindowManager().getDefaultDisplay();
		dialogLayout.setMinimumWidth(display.getWidth());
		builder.setView(dialogLayout);
		
		final AlertDialog syncDialog = builder.create();
		final ProgressBar syncProgress = (ProgressBar)dialogLayout.findViewById(R.id.syncing_progress);;
		final Button syncHide = (Button)dialogLayout.findViewById(R.id.sync_hide);
		final TextView syncPercentage = (TextView)dialogLayout.findViewById(R.id.sync_percentage);
		final TextView syncMessage = (TextView)dialogLayout.findViewById(R.id.sync_message);

		
		// Run this logic on a separate thread in order for the dialog to actually show
		(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					client.login(email, password);
				} catch (final TodoistServerException e) {
					// Login failed
					activity.runOnUiThread(new Runnable() {
						public void run() {	
							activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
							try {
    							if (loginDialog.isShowing())
    								loginDialog.dismiss();
							} catch (Exception exc) {
							    // Sometimes an IllegalArgumentException occurrs (View not attached to window manager).
							    // This could happens sometimes when the activity finishes before the dialog successfully
							    // dismisses - so there's nothing to do here.
							    // See: http://stackoverflow.com/a/5102572/1233767
							}
							
							if (e.getErrorCode() == ErrorCode.LOGIN_ERROR) {
								 Toast.makeText(activity, "Login failed - invalid email or password", Toast.LENGTH_SHORT).show();
							} else {
								 Toast.makeText(activity, "Login failed - connection problems", Toast.LENGTH_SHORT).show();
							}
						}
					});
					
					return;
				}

				activity.runOnUiThread(new Runnable() {
					public void run() {	
						if (loginDialog.isShowing()) {
							try {
								loginDialog.dismiss();
							} catch (Exception exc) {
								// This *sometimes* happens - and the only known way to handle it
								// is to simply catch the exception:
								// See http://stackoverflow.com/a/5102572/1233767
							}
						}
					}
				});
		
				// If we've reached this far - this means login was successful
				
				// See if the user has never sync'd before
				
				if ((activity instanceof LoginView) && (!client.hasNeverSynced())) {
					// User has sync'd before (happens when calling "Change login details") -
					// Simply close the login dialog
					activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
					activity.runOnUiThread(new Runnable() {
						public void run() {	
							Toast.makeText(activity, "Login successful", Toast.LENGTH_SHORT).show();
							activity.setResult(RESULT_OK);
							activity.finish();
						}
					});
					
					return;
				}
			
				
				// Start syncing while displaying a progress dialog
				
				activity.runOnUiThread(new Runnable() {
					public void run() {	
						if (activity instanceof LoginView) {
							// First time syncing - can't allow any cancelations
							syncDialog.setCancelable(false);
							syncHide.setVisibility(View.GONE);
						} else {
							syncDialog.setCancelable(true);
							
							OnCancelListener onCancel = new OnCancelListener() {
								@Override
								public void onCancel(DialogInterface dialog) {
									// User has hidden the sync dialog
									wakeLock.release();
									activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
								}
							};
							syncDialog.setOnCancelListener(onCancel);
							
							syncHide.setOnClickListener(new OnClickListener() {
								@Override
								public void onClick(View v) {
									syncDialog.cancel();
								}
							});
						}
						
						syncMessage.setText("");
						
						if (syncDialog.isShowing())
							syncDialog.hide();
						
						syncDialog.show();
					}
				});
				
				// This makes sure the screen will stay on while syncing
				wakeLock.acquire();
							
				try {
					client.syncAll(new ISyncProgress() {
						@Override
						public void onSyncProgress(final String message, final int progress) {
							activity.runOnUiThread(new Runnable() {
								public void run() {
									if (activity instanceof LoginView) {
										// Show a "first time syncing.." text as well
										syncMessage.setText(SYNC_INITIAL_MESSAGE + "\n\n" + message.trim());
									} else {
										syncMessage.setText(message.trim());
									}
									
									syncProgress.setProgress(progress);
									syncPercentage.setText(progress + "%");
									
									if (progress == 100) {
										if (wakeLock.isHeld())
											wakeLock.release();
										activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
										syncDialog.hide();
										Toast.makeText(activity, "Syncing complete", Toast.LENGTH_SHORT).show();
										
										if (runOnCompletion != null) {
											// Callback routine to be called when syncing is complete
											runOnCompletion.run();
										}
									}
								}
							});
						}
					});
				} catch (final InvalidDateStringException exc) {
					if (wakeLock.isHeld())
						wakeLock.release();
					
					Log.e("Budoist", String.format("Sync Exception: Invalid date: %s", exc.getItem().toString()));
					
					activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
					activity.runOnUiThread(new Runnable() {
					    public void run() {	
					        if (syncDialog.isShowing())
					            syncDialog.hide();

					        Item item = exc.getItem();
					        String shortContent = TodoistTextFormatter.formatText(item.getContent()).toString();
					        if (shortContent.length() > MAX_ITEM_NAME_IN_SYNC_ERROR) shortContent = shortContent.subSequence(0, MAX_ITEM_NAME_IN_SYNC_ERROR) + "...";
					        
					        final Toast toast = Toast.makeText(activity,
					                String.format(
					                        "Syncing failed - The item '%s' has an invalid due date string: '%s'",
					                        shortContent, item.dateString
					                        ),
					                        Toast.LENGTH_LONG
				                        );
					        toast.show();
					        
					        // A hack to show the toast for a longer period
					        new CountDownTimer(SYNCING_FAILED_DUE_DATE_ERROR_DURATION, 1000) {
					            public void onTick(long millisUntilFinished) { toast.show(); }
					            public void onFinish() { toast.show(); }
					        }.start(); 
					    }
					});

					
				} catch (TodoistServerException e) {
					if (wakeLock.isHeld())
						wakeLock.release();
					
					Log.e("Budoist", String.format("Sync Exception: %s", e.toString()));
					Log.e("Budoist", String.format("Sync Exception: %s", e.getMessage()));
					
					StackTraceElement[] trace = e.getStackTrace();
					for (int i = 0; i < trace.length; i++) {
					    Log.e("Budoist", String.format("%s: %s: %d",
					            trace[i].getClassName(),
					            trace[i].getMethodName(),
					            trace[i].getLineNumber()
					            ));
					            
					}
					activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
					activity.runOnUiThread(new Runnable() {
						public void run() {	
							if (syncDialog.isShowing())
								syncDialog.hide();
							
							Toast.makeText(activity, "Syncing failed - probably a connection error. Try syncing again later.", Toast.LENGTH_LONG).show();
						}
					});
				}
				
			}
		})).start();
  	
    }
 	
  
	@Override
	public void onClick(View arg0) {
		// Login button has been clicked
		
		syncNow(this, mClient, mEmail.getText().toString(), mPassword.getText().toString(), new Runnable() {
			@Override
			public void run() {
				// Syncing is complete
				
				// Since this is the first time the user logins, simply switch to filter by project view
		        Intent intent = new Intent(LoginView.this.getBaseContext(), ProjectListView.class);
		        intent.putExtra(ProjectListView.KEY__VIEW_MODE, ProjectViewMode.FILTER_BY_PROJECTS.toString());
		        LoginView.this.startActivity(intent);
		        
				LoginView.this.setResult(RESULT_OK);
		        LoginView.this.finish();
			}
		});
	}
	
}
