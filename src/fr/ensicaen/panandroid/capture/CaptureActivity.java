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

import junit.framework.Assert;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
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
	
	private static final int IMERSIVE_DELAY = 4000; //[s]

	/* *********
	 * ATTRIBUTES
	 * *********/
			
	/** The OpenGL view where to draw the sphere. */
	private CaptureView mCaptureView;
	
	/** The Camera manager **/
	private CameraManager mCameraManager;
	
	
	/**
	 * Called when the activity is first created.
	 * @param savedInstanceState - The instance state.
	 */
	@Override
	public void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
	    
		//view in fullscreen, and don't turn screen off
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Hide both the navigation bar and the status bar.
		View decorView = getWindow().getDecorView();
		decorView.setOnSystemUiVisibilityChangeListener(this);
		
		toggleImmersive();
		
		
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
		this.mCaptureView = new CaptureView(this, mCameraManager);
		
		
		if(ALTERNATIVE_MARKERS_PLACEMENT)
		{
			mCaptureView.setMarkersDistance(DEFAULT_PITCH_STEP);
		}
		else
		{
			mCaptureView.setPitchStep(DEFAULT_PITCH_STEP);
			mCaptureView.setYawStep(DEFAULT_YAW_STEP);
		}
		
		
		
		this.setContentView(this.mCaptureView);	
		
		
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
	        boolean isImmersiveModeEnabled = ((uiOptions  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == uiOptions);
	        if (isImmersiveModeEnabled) 
	        {
	            Log.i(TAG, "Turning immersive mode mode off. ");
	        } 
	        else 
	        {
	            Log.i(TAG, "Turning immersive mode mode on.");
	        }
	
	      
	        newUiOptions ^= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
	
	
	        // Status bar hiding: Backwards compatible to Jellybean
	        if (Build.VERSION.SDK_INT >= 16) 
	        {
	            newUiOptions ^= View.SYSTEM_UI_FLAG_FULLSCREEN;
	        }
	
	        
	
	        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
        }
        else
        {
        	getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 0);
        }

        //END_INCLUDE (set_ui_flags)
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
	public void onSystemUiVisibilityChange(int visibility)
	{
		
	
		if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0)
		{
			/*
			visibilityFlags = View.SYSTEM_UI_FLAG_VISIBLE;
			int uiOptions = visibilityFlags;	
			decorView.setSystemUiVisibility(uiOptions);*/
			
			
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
