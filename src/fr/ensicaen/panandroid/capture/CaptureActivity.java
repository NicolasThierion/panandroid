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

import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.stitcher.StitcherActivity;
import junit.framework.Assert;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
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

	private static final boolean ALTERNATIVE_MARKERS_PLACEMENT = false;
	private static final float DEFAULT_PITCH_STEP = 360.0f/12.0f;;
	private static final float DEFAULT_YAW_STEP = 360.0f/12.0f;

	/* *********
	 * PARAMETERS
	 * *********/

	/** Directory where pictures will be saved **/
	private static final String APP_DIRECTORY = Environment.getExternalStorageDirectory() + File.separator + "Panandroid";
	private static final String TEMP_PREFIX = "temp";
	/** immersive mode will hide status bar and navigation bar **/
	private static boolean IMMERSIVE_ENABLE = true;

	/** delay for hiding bars **/
	private static final int IMERSIVE_DELAY = 4000; //[s]
	private static final int NAVIGATION_HIDE_TYPE = View.SYSTEM_UI_FLAG_LOW_PROFILE;

	public final static String FOLDER = "fr.ensicaen.panandroid.FOLDER";

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
	private String mWorkingDir = APP_DIRECTORY;
	private String mPanoName;

	/**
	 * Called when the activity is first created.
	 * @param savedInstanceState - The instance state.
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		//view in fullscreen, and don't turn screen off
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		//bind activity to its layout
		setContentView(R.layout.capture_activity);

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
			mCameraManager.setTargetDir(mWorkingDir);
			mCameraManager.open();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return;
		}
		//setup JSON and snapshot manager
		mSnapshotManager = new SnapshotManager();
		mCameraManager.addSnapshotEventListener(mSnapshotManager);

		//setup GL view & its renderer
		mCaptureView = new CaptureView(this, mCameraManager);

		//and populate the layout with it.
		ViewGroup parent = (ViewGroup) (mCaptureView.getParent());
		if(parent !=null)
			parent.removeView(mCaptureView);

		ViewGroup container = ((ViewGroup) findViewById(R.id.gl_renderer_container));
		container.addView(mCaptureView);


		if(ALTERNATIVE_MARKERS_PLACEMENT)
		{
			mCaptureView.setMarkersDistance(DEFAULT_PITCH_STEP);
		}
		else
		{
			mCaptureView.setPitchStep(DEFAULT_PITCH_STEP);
			mCaptureView.setYawStep(DEFAULT_YAW_STEP);
		}

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

	/*void generateMarks(float s)
	{
		float radius = 1;
		float phi = s/radius;
		LinkedList<Mark> marks;


		double theta = Math.PI; //equateur
		phi = 0;
		Mark mark;

		mark.mPitch = theta;
		mark.mYaw = phi;

		marks.add(mark);

		while(   ) { //test d'arret pour recouvrir tte la sphere
			phi += phi;
			mark.mPitch = theta;
			mark.mYaw = phi;

			marks.add(mark);

		}

	}*/


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
		super.onResume();

	}

	/**
	 * Also pause the glSurface.
	 */
	@Override
	protected void onPause()
	{
		Log.i(TAG, "onPause()");
		
		Assert.assertTrue(mCaptureView!=null);
		//pause camera, GL context, etc.
		mCaptureView.onPause();
		mCameraManager.onPause();
		//save project to json
		String res = mSnapshotManager.toJSON(mWorkingDir);
		Log.i(TAG,  "saving project to "+res);

		//call parent
		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		mCaptureView.onDestroy();
		super.onDestroy();
	}

	@Override
	public void onBackPressed()
	{
		AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
		alertDialog.setMessage(getString(R.string.exit_confrm)).setCancelable(false);

		alertDialog.setPositiveButton(getString(R.string.exit_yes), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
			    Intent intent = new Intent(CaptureActivity.this,
			            StitcherActivity.class);
			    intent.putExtra(FOLDER, mWorkingDir);
			    startActivity(intent);
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
			    	 //getActionBar().hide();
			     }
			 }, IMERSIVE_DELAY);
		}
	}

	/* ******
	 * PRIVATE METHODS
	 * ******/

	private String genWorkingDir(String panoName)
	{

		File dir = new File(APP_DIRECTORY+File.separator+panoName);
		dir.mkdirs();
		return APP_DIRECTORY+File.separator+panoName;
	}


	private String genPanoName(String prefix)
	{

		int id = 1;

		String path = APP_DIRECTORY+File.separator+prefix;

		File dir = new File(path);
		if(dir.exists())
		{
			do
			{
				path = APP_DIRECTORY+File.separator+prefix+(id++);
				dir = new File(path);

			}while(dir.exists());
			return prefix+(--id);
		}

		return prefix;
	}


}
