/*
 * Copyright (C) 2013 Nicolas THIERION, Saloua BENSEDDIK, Jean Marguerite.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package fr.ensicaen.panandroid.capture;

import java.io.File;
import java.io.IOException;

import fr.ensicaen.panandroid.PanandroidApplication;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.snapshot.SnapshotManager;
import fr.ensicaen.panandroid.stitcher.StitcherActivity;
import junit.framework.Assert;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

/**
 *
 * Activity through what the user can capture snapshots in order to make his panorama.
 *
 * The activity opens a 3D and draws a cubic skybox seen from inside.
 * The view rotates with the device orientation. Dots are put all around the view, and a snapshot is taken automatically when viewfinder and dot are alligned
 *
 * @author Nicolas THIERION.
 *
 */
public class CaptureActivity extends Activity implements OnSystemUiVisibilityChangeListener
{
	/* ******
	 * DEBUG PARAMETERS
	 * ******/
	public static final String TAG = CaptureActivity.class.getSimpleName();

	private static final float DEFAULT_PITCH_STEP = 360.0f/16.0f;;
	private static final float DEFAULT_YAW_STEP = 360.0f/16.0f;

	/* *********
	 * PARAMETERS
	 * *********/

	/** Directory where pictures will be saved **/
	private static final String TEMP_PREFIX = "temp";
	/** immersive mode will hide status bar and navigation bar **/
	private static boolean IMMERSIVE_ENABLE = true;

	/** delay for hiding bars **/
	private static final int IMERSIVE_DELAY = 4000; //[s]
	private static final int NAVIGATION_HIDE_TYPE = View.SYSTEM_UI_FLAG_LOW_PROFILE;

	/** Some devices don't return good FOV values. In these cases, DEFAULT_FOV will be used **/
	private static final float DEFAULT_HFOV = 59.62f;	//values for my nexus 5
	private static final float DEFAULT_VFOV = 46.6f;	//TODO : manually set fov of known devices.

	/* *********
	 * ATTRIBUTES
	 * *********/

	/** The OpenGL view where to draw the sphere. */
	private CaptureView mCaptureView;

	/** The Camera manager **/
	private CameraManager mCameraManager;

	/** Instance of SnapshotManager, observer of onSnapshotTaken()**/
	private SnapshotManager mSnapshotManager;

	/** directory where images and JSon file are stored **/
	private String mWorkingDir ;
	private String mPanoName;

	private PowerManager mPowerManager;

	private WakeLock mWakeLock;

	/**
	 * Called when the activity is first created.
	 * @param savedInstanceState - The instance state.
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		//view in fullscreen, and don't turn screen off
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");

		//bind activity to its layout
		setContentView(R.layout.activity_capture);

		// Hide both the navigation bar and the status bar.
		View decorView = getWindow().getDecorView();
		decorView.setOnSystemUiVisibilityChangeListener(this);

		if(IMMERSIVE_ENABLE)
		{
        	getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
			toggleImmersive();
		}

		//set up pano name and working dir
		mPanoName = genPanoName(TEMP_PREFIX);
		mWorkingDir = genWorkingDir(mPanoName);

		//setup camera manager
		mCameraManager = CameraManager.getInstance(this);

		try
		{
			mCameraManager.setTargetDirectory(mWorkingDir);
			mCameraManager.open();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return;
		}
		//setup JSON and snapshot manager
		int resX = mCameraManager.getCameraResX();
		int resY = mCameraManager.getCameraResY();
		float hfov = mCameraManager.getHorizontalViewAngle();
		float vfov = mCameraManager.getVerticalViewAngle();

		if(hfov<1)
		{
			Log.e(TAG, "Invalid hfov : "+hfov+"\nSetting hfov to "+DEFAULT_HFOV);
			hfov = DEFAULT_HFOV;
		}
		if(vfov<1)
		{
			Log.e(TAG, "Invalid vfov : "+vfov+"\nSetting hfov to "+DEFAULT_VFOV);
			hfov = DEFAULT_VFOV;
		}
		mSnapshotManager = new SnapshotManager(SnapshotManager.DEFAULT_JSON_FILENAME,
												resX, resY ,hfov, vfov,
												DEFAULT_PITCH_STEP, DEFAULT_YAW_STEP);

		mCameraManager.addSnapshotEventListener(mSnapshotManager);

		//setup GL view & its renderer
		mCaptureView = new CaptureView(this, mCameraManager);

		//and populate the layout with it.
		ViewGroup parent = (ViewGroup) (mCaptureView.getParent());
		if(parent !=null)
			parent.removeView(mCaptureView);

		ViewGroup container = ((ViewGroup) findViewById(R.id.gl_renderer_container));
		container.addView(mCaptureView);


		mCaptureView.setPitchStep(DEFAULT_PITCH_STEP);
		mCaptureView.setYawStep(DEFAULT_YAW_STEP);


		//do not set the view as content cause it is bind to layout.
		//this.setContentView(this.mCaptureView);
		//mCameraManager.startPreview();

	}



	@SuppressLint("InlinedApi")
	public void toggleImmersive()
	{
		 // Navigation bar hiding:  Backwards compatible to ICS.
        if (Build.VERSION.SDK_INT >= 14)
        {
	        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
	        int newUiOptions = uiOptions;
	        boolean isImmersiveModeEnabled = ((uiOptions  | NAVIGATION_HIDE_TYPE) == uiOptions);
	        if (isImmersiveModeEnabled)
	        {
	            Log.i(TAG, "Turning immersive mode mode off. ");
	        }
	        else
	        {
	            Log.i(TAG, "Turning immersive mode mode on.");
	        }


	        newUiOptions ^= NAVIGATION_HIDE_TYPE;

	/*
	        // Status bar hiding: Backwards compatible to Jellybean
	        if (Build.VERSION.SDK_INT >= 16)
	        {
	            newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
	        }
	*/


	        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
        }
        else
        {
        	getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 0);
        }
    }


	/* ********
	 * OVERRIDES
	 * ********/
	/**
	 * Remember to resume the glSurface.
	 */
	@Override
	protected void onResume()
	{
		Assert.assertTrue(mCaptureView!=null);
		mCaptureView.onResume();
		mWakeLock.acquire();
		super.onResume();

	}

	/**
	 * Also pause the glSurface.
	 */
	@Override
	protected void onPause()
	{
		Log.i(TAG, "onPause()");
		mWakeLock.release();
		Assert.assertTrue(mCaptureView!=null);
		//pause camera, GL context, etc.
		mCaptureView.onPause();
		mCameraManager.onPause();
		//save project to json
		if(mSnapshotManager.getSnapshotsList().size()>0)
		{
			String res = mSnapshotManager.toJSON(SnapshotManager.DEFAULT_JSON_FILENAME);
			Log.i(TAG,  "saving project to "+res);
		}
		//call parent
		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		mCaptureView.onDestroy();
		super.onDestroy();
	}

	/**
	 * Show a confirmation dialog before exit. Exit to stitcher activity.
	 */
	@Override
	public void onBackPressed()
	{
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
		alertDialog.setMessage(getString(R.string.exit_capture)).setCancelable(false);

		alertDialog.setPositiveButton(getString(R.string.exit_yes), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				//if we have at least 2 snapshots to stitch, switch to stitcher activity
				if(mSnapshotManager.getSnapshotsList().size()>1)
				{
				    Intent intent = new Intent(CaptureActivity.this,
				            StitcherActivity.class);
				    intent.putExtra("projectFile",mWorkingDir+File.separator+SnapshotManager.DEFAULT_JSON_FILENAME);
				    intent.putExtra("commingFrom",CaptureActivity.class.getSimpleName());
				    //intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
				    startActivity(intent);
				    //finish this activity in order to free maximum of memory
				    finish();
				}
				else
				{

					CaptureActivity.this._onBackPressed();
				}
			}
		});
		alertDialog.setNegativeButton(R.string.exit_no, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.cancel();
			}
		});

		AlertDialog alert = alertDialog.create();
		alert.show();


	}
	private void _onBackPressed()
	{

		super.onBackPressed();
	}

	@Override
	public void onSystemUiVisibilityChange(int visibility)
	{
		if ((visibility & NAVIGATION_HIDE_TYPE) == 0)
		{

			Handler h = new Handler();

			h.postDelayed(new Runnable() {

			     @Override
			     public void run() {
			    	 toggleImmersive();
			     }
			 }, IMERSIVE_DELAY);
		}
	}

	/* ******
	 * PRIVATE METHODS
	 * ******/

	private String genWorkingDir(String panoName)
	{

		File dir = new File(PanandroidApplication.APP_DIRECTORY+File.separator+panoName);
		dir.mkdirs();
		return dir.getAbsolutePath();
	}


	private String genPanoName(String prefix)
	{

		int id = 1;

		String path = PanandroidApplication.APP_DIRECTORY+File.separator+prefix;

		File dir = new File(path);
		if(dir.exists())
		{
			do
			{
				path = PanandroidApplication.APP_DIRECTORY+File.separator+prefix+(id++);
				dir = new File(path);

			}while(dir.exists());
			return prefix+(--id);
		}

		return prefix;
	}


}
