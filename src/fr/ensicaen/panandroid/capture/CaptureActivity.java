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
import junit.framework.Assert;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
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
	private static final boolean ALTERNATIVE_MARKERS_PLACEMENT = false;
	public static final String TAG = CaptureActivity.class.getSimpleName();

	/* *********
	 * PARAMETERS
	 * *********/

	private static final float DEFAULT_PITCH_STEP = 360.0f/12.0f;;
	private static final float DEFAULT_YAW_STEP = 360.0f/12.0f;
	private static final String PICTURE_DIRECTORY = Environment.getExternalStorageDirectory() + File.separator + "Panandroid";
	
	private static boolean IMMERSIVE_ENABLE = true;
	private static final int IMERSIVE_DELAY = 4000; //[s]
	private static final int NAVIGATION_HIDE_TYPE = View.SYSTEM_UI_FLAG_LOW_PROFILE;

	/* *********
	 * ATTRIBUTES
	 * *********/
			
	/** The OpenGL view where to draw the sphere. */
	private CaptureView mCaptureView;
	
	/** shutter button **/
	ShutterButton mShutterButton;
	
	/** The Camera manager **/
	private CameraManager mCameraManager;
	
	/** Instance of SnapshotManager, observer of onSnapshotTaken()**/
	public SnapshotManager mSnapshotManager;
	
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
		
		//add shutter button
        mShutterButton = (ShutterButton) findViewById(R.id.btn_shutter);
		
		//setup camera manager
		mCameraManager = CameraManager.getInstance(this);
		try
		{
			mCameraManager.setTargetDir(PICTURE_DIRECTORY);
			mCameraManager.open();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return;
		}
		
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
	
	/**
	 * Remember to resume the glSurface.
	 */
	@Override
	protected void onResume()
	{
		Assert.assertTrue(mCaptureView!=null);
		this.mCaptureView.onResume();
		super.onResume();

	}
	
	/**
	 * Also pause the glSurface.
	 */
	@Override
	protected void onPause()
	{
		Assert.assertTrue(mCaptureView!=null);
		this.mCaptureView.onPause();
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
			    	 //getActionBar().hide();
			     }
			 }, IMERSIVE_DELAY);
		} 	
	}
}
