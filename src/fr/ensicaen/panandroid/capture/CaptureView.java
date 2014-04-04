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
import java.util.LinkedList;






import android.app.Activity;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.insideview.Inside3dView;
import fr.ensicaen.panandroid.meshs.Cube;
import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.snapshot.SnapshotEventListener;
import fr.ensicaen.panandroid.tools.BitmapDecoder;
import fr.ensicaen.panandroid.tools.EulerAngles;
import fr.ensicaen.panandroid.tools.SensorFusionManager;
import junit.framework.Assert;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;



/**
 * Inside3dView that use custom CaptureSphereRenderer instead of simple SphereRenderer. The renderer is automatically set.
 * This Inside3dView disable inertia scroll, and touch events, it puts a SurfaceTexture holding camera preview,
 * and put snapshots all around the sphere.
 * @author Nicolas
 *
 */
@SuppressLint("ViewConstructor")
public class CaptureView extends Inside3dView implements SensorEventListener, SnapshotEventListener
{
	
	private static final String TAG = CaptureView.class.getSimpleName();

	/* *********
	 * CONSTANTS
	 * *********/
	/** Size of the skybox **/
	private static final float SKYBOX_SIZE 				= 400f;
	private static final int DEFAULT_SKYBOX_SAMPLE_SIZE = 4;		//[1 - 8] pow of 2.
	
	private static final float DEFAULT_PITCH_STEP 		= 360.0f/16.0f;;
	private static final float DEFAULT_YAW_STEP 		= 360.0f/16.0f;

	/** angle difference before considering a picture has to be captured **/
	private static final float DEFAULT_AUTOSHOOT_THREASHOLD 			= 3.0f; //[deg]
	private static final float DEFAULT_AUTOSHOOT_PRECISION				= 0.3f;  //[~deg/s]

	/** starting parameters **/
	private static final int START_DELAY = 5000 ; //[ms]
	private static int startIn=START_DELAY;
	private static int currentTime=0;

	/* **********
	 * ATTRIBUTES
	 * *********/

	/** Camera manager, **/
	private CameraManager mCameraManager;
	
	/** Sphere renderer **/
	private CaptureRenderer mRenderer;
	
	/** SensorFusion used to guide capture **/
	private SensorFusionManager mSensorManager;
	
	private float mPitchStep = DEFAULT_PITCH_STEP;
	private float mYawStep = DEFAULT_YAW_STEP;
	
	private boolean mCaptureIsStared = false;
	private ProgressBar mStartingProgressSpinner;


	/* **********
	 * CONSTRUCTORS
	 * *********/
	
	/**
	 * Draws the given sphere in the center of the view, plus the camera preview given by CameraManager.
	 * @param context - context of application.
	 * @param mCameraManager - Camera manager, to redirect camera preview.
	 */
	public CaptureView(Context context, CameraManager cameraManager) 
	{
		super(context);
		
		//add the starting spinner at the center of the view.
		ViewGroup layout = (ViewGroup) ((Activity)context).findViewById(android.R.id.content).getRootView();
		mStartingProgressSpinner = new ProgressBar((Activity)context,null,android.R.attr.progressBarStyleLarge);
		mStartingProgressSpinner.setIndeterminate(true);
		mStartingProgressSpinner.setVisibility(View.INVISIBLE);

		RelativeLayout.LayoutParams params = new 
		        RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,RelativeLayout.LayoutParams.MATCH_PARENT);

		RelativeLayout rl = new RelativeLayout(context);

		rl.setGravity(Gravity.CENTER);
		rl.addView(mStartingProgressSpinner);

		layout.addView(rl,params);
		
		//setup sensors	
		mSensorManager = SensorFusionManager.getInstance(getContext());	
		if(!mSensorManager.start())
		{
			//TODO : error toast
			Log.e(TAG, "Rotation not supported");
		}
		
		//setup cameraManager
		mCameraManager = cameraManager;	
		mCameraManager.setSensorialCaptureEnabled(true);
		
		//adapt precision according to sensorFusion type.
		mCameraManager.setAutoShootThreshold(DEFAULT_AUTOSHOOT_THREASHOLD);
		mCameraManager.setAutoShootPrecision(DEFAULT_AUTOSHOOT_PRECISION);
		
		//TODO : implement or remove
		/*
		if(mSensorManager.isGyroscopeSupported())
		{
			mCameraManager.setAutoShootThreshold(DEFAULT_AUTOSHOOT_GRYO_THREASHOLD);
		}
		else
		{
			mCameraManager.setAutoShootThreshold(DEFAULT_AUTOSHOOT_ACCELEROMETER_THREASHOLD);
		}
		 */
		
		//init the skybox
		Cube skybox = null;
		Resources res = super.getResources();	
		int sampleSize=DEFAULT_SKYBOX_SAMPLE_SIZE;
		boolean done = false;
		
		//try to load the best texture resolution
		while(!done && sampleSize<32)
		{
			try
			{
				Bitmap texFront = BitmapDecoder.safeDecodeBitmap(res, R.raw.skybox_ft, sampleSize);
				Bitmap texBack = BitmapDecoder.safeDecodeBitmap(res, R.raw.skybox_bk, sampleSize);
				Bitmap texLeft = BitmapDecoder.safeDecodeBitmap(res, R.raw.skybox_lt, sampleSize);
				Bitmap texRight = BitmapDecoder.safeDecodeBitmap(res, R.raw.skybox_rt, sampleSize);
				Bitmap texBottom = BitmapDecoder.safeDecodeBitmap(res, R.raw.skybox_bt, sampleSize);
				Bitmap texTop = BitmapDecoder.safeDecodeBitmap(res, R.raw.skybox_tp, sampleSize);
				
				skybox = new Cube(SKYBOX_SIZE,texFront,texBack, texLeft, texRight, texBottom, texTop);
				done = true;
			}
			catch (OutOfMemoryError e)
			{
				sampleSize*=2;
			}
		}
		
		Assert.assertTrue(skybox!=null);
		if(sampleSize!=DEFAULT_SKYBOX_SAMPLE_SIZE)
		{
			Log.w(TAG, "not enough memory to load skybox texture.. Forced to downscale texture by "+sampleSize);
		}		
		
		
		
		// set glview to use a capture renderer with the provided skybox.
		mRenderer = new CaptureRenderer(context, skybox, mCameraManager) ;
		super.setRenderer(mRenderer);
		  
		//set first dot only. Other targers will be placed after first shoot
		LinkedList<EulerAngles> initialTarget = new LinkedList<EulerAngles>();
		initialTarget.add(new Snapshot(0.0f, 0.0f) );
		mRenderer.setMarkerList(initialTarget);
		
		
		//set view rotation parameters
		super.setEnableSensorialRotation(true);
		super.setEnableTouchRotation(false);
		super.setEnableInertialRotation(false);
		
		//disable yaw. Will be enabled back after first shoot    
		super.setEnablePitchRotation(true);
		super.setEnableRollRotation(true);
		super.setEnableYawRotation(false);
		
	}
	
	/**
	 * Set pitch interval between markers and updates camera autoShoot targets, if the capture is started.
	 * @param step - pitch interval.
	 */
	public void setPitchStep(float step)
	{
		mPitchStep = step;
		if(mCaptureIsStared)
			setTargets();

	}
	
	/**
	 * Set yaw interval between markers and updates camera autoShoot targets, if the capture is started.
	 * @param step - yaw interval.
	 */
	public void setYawStep(float step)
	{
		mYawStep = step;
		if(mCaptureIsStared)
			setTargets();	
	}

	
	
	private LinkedList<EulerAngles> setTargets()
	{
		//updates camera autoshoot targets
		LinkedList<EulerAngles> targets = new LinkedList<EulerAngles>();
		
		double currentPitch, currentYaw;
		double pitchStep = 180.0 / ((int)(180.0f / mPitchStep));
		double yawStep = 360.0 / ((int)(360.0f / mYawStep));
		double s = Math.toRadians(yawStep);
		
		
		for(currentPitch=0; currentPitch< 90.1f - pitchStep; currentPitch+=pitchStep)
		{
			double phi = Math.toRadians(currentPitch);
			double sinPhi = Math.sin(phi);
			double cosPhi = Math.cos(phi);
			double lambda = Math.acos((Math.cos(s)-sinPhi*sinPhi)/(cosPhi*cosPhi));
			yawStep = Math.toDegrees(lambda);
			for(currentYaw = -180.0; currentYaw < 180.1 - yawStep; currentYaw+=yawStep)
			{
				float p = (float)currentPitch;
				float y = ((float)(currentYaw))%360;
				targets.add(new Snapshot(p, y));
				if(p!=0)
					targets.add(new Snapshot(-p, y));
			}
		}
		
		//add poles
		targets.add(new Snapshot(90.0f, 0.0f));
		targets.add(new Snapshot(-90.0f, 0.0f));
		
		mCameraManager.setAutoShootTargetList(targets);
		mRenderer.setMarkerList(targets);

		return targets;
	}
	
	

	/**
	 * Watch pitch for starting initial shoot.
	 */
	@Override
	public void onSensorChanged(SensorEvent e)
	{
		super.onSensorChanged(e);
	
		//if capture already started, continue
		if(mCaptureIsStared)
			return;
		
		//else, first call?
		if(currentTime==0)
		{
			currentTime = (int) (System.currentTimeMillis());
			return;
		}
		
		//else, wait viewFinder to be on target for enough time.
		int time = (int) (System.currentTimeMillis());
		int elapsed = time - currentTime;
		currentTime = time;

		//viewFinder on target?
		if(Math.abs(mSensorManager.getPitch())<DEFAULT_AUTOSHOOT_THREASHOLD
				&& Math.abs(mSensorManager.getRelativeRoll())<DEFAULT_AUTOSHOOT_THREASHOLD)
		{
			mStartingProgressSpinner.setVisibility(View.VISIBLE);
			mStartingProgressSpinner.requestLayout();
			startIn-=elapsed;
			
			if(startIn<=0)
			{
				Log.i(TAG, "Starting capture");

				//set orientation's origin to current
				mSensorManager.setReferenceYaw();	
				
				mCameraManager.addSnapshotEventListener(this);
				mCaptureIsStared  = true;
				
				//set all targets
				setTargets();
			}
		}
		//viewFinder not on target => reset delay.
		else
		{
			startIn=START_DELAY;
			mStartingProgressSpinner.setVisibility(View.INVISIBLE);
			mStartingProgressSpinner.requestLayout();
		}		
	}
	/* **********
	 * VIEW OVERRIDES
	 * *********/
	
	
	@Override
	public void onPause()
	{
		super.onPause();
		mCameraManager.onPause();
		mSensorManager.onResume();
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		mCameraManager.onResume();
		mSensorManager.onResume();	
	}
	
	public void onDestroy()
	{
		mCameraManager.onClose();
	}
	
	
	@Override
	public void onSnapshotTaken(byte[] pictureData, Snapshot snapshot)
	{
		mCameraManager.removeSnapshotEventListener(this);
		mStartingProgressSpinner.setVisibility(View.INVISIBLE);
		mStartingProgressSpinner.requestLayout();
		super.setEnableYawRotation(true);
	}
	
	
}
	
