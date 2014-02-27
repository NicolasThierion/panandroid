package fr.ensicaen.panandroid.capture;
import java.util.LinkedList;

import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.insideview.Inside3dView;
import fr.ensicaen.panandroid.meshs.Cube;
import fr.ensicaen.panandroid.sensor.SensorFusionManager;
import fr.ensicaen.panandroid.tools.BitmapDecoder;
import junit.framework.Assert;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.Log;


/**
 * Inside3dView that use custom CaptureSphereRenderer instead of simple SphereRenderer. The renderer is automatically set.
 * This Inside3dView disable inertia scroll, and touch events, it puts a SurfaceTexture holding camera preview,
 * and put snapshots all around the sphere.
 * @author Nicolas
 *
 */
@SuppressLint("ViewConstructor")
public class CaptureView extends Inside3dView
{	
	
	private static final String TAG = CaptureView.class.getSimpleName();

	
	/* ******
	 * DEBUG PARAMETERS
	 * ******/
	private static final boolean ALTERNATIVE_MARKERS_PLACEMENT = false;
	
	
	/* *********
	 * CONSTANTS
	 * *********/
	/** Size of the skybox **/
	private static final float SKYBOX_SIZE 				= 400f;
	private static final int DEFAULT_SKYBOX_SAMPLE_SIZE = 4;		//[1 - 8] pow of 2.
	
	private static final float DEFAULT_PITCH_STEP 		= 360.0f/12.0f;;
	private static final float DEFAULT_YAW_STEP 		= 360.0f/12.0f;

	/** angle difference before considering a picture has to be captured **/
	private static final float DEFAULT_AUTOSHOOT_GRYO_THREASHOLD 			= 3.0f; //[deg]
	private static final float DEFAULT_AUTOSHOOT_ACCELEROMETER_THREASHOLD 	= 8.0f; //[deg]

	
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
	
		//setup sensors
		mSensorManager = SensorFusionManager.getInstance(context);
		if(!mSensorManager.start())
		{
			//TODO : error toast
			Log.e(TAG, "Rotation not supported");
		}
		
		//setup cameraManager
		mCameraManager = cameraManager;	
		mCameraManager.setSensorFusionManager(mSensorManager);
		
		//adapt precision according to sensorFusion type.
		if(mSensorManager.isGyroscopeSupported())
		{
			mCameraManager.setAutoShootThreshold(DEFAULT_AUTOSHOOT_GRYO_THREASHOLD);
		}
		else
		{
			mCameraManager.setAutoShootThreshold(DEFAULT_AUTOSHOOT_ACCELEROMETER_THREASHOLD);
		}

		
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
		this.updateTargets();
        super.setRenderer(mRenderer);
        
     
        
        //set view rotation parameters
        super.enableSensorialRotation(true);
        super.enableTouchRotation(false);
        super.enableInertialRotation(false);
	}
	/**
	 * Set pitch interval between markers and updates camera autoShoot targets.
	 * @param step - pitch interval.
	 */
	public void setPitchStep(float step)
	{
		mPitchStep = step;
		updateTargets();
		
	
	}
	
	/**
	 * Set yaw interval between markers and updates camera autoShoot targets.
	 * @param step - yaw interval.
	 */
	public void setYawStep(float step)
	{
		mYawStep = step;
		updateTargets();
	}

	private LinkedList<EulerAngles> updateTargets()
	{
		//updates camera autoshoot targets
		
		
		LinkedList<EulerAngles> targets = new LinkedList<EulerAngles>();
		
		for(float pitch = -90.0f+mPitchStep; pitch < 90.1f-mPitchStep; pitch+=mPitchStep)
		{
			for(float yaw = -180.0f; yaw < 180.1f-mYawStep; yaw+=mYawStep)
			{
				targets.add(new Snapshot(pitch, yaw));
			}
		}
		
		
		//add poles
		targets.add(new Snapshot(90.0f, 0.0f));
		targets.add(new Snapshot(-90.0f, 0.0f));
		
		


		
		mRenderer.setMarkerList(targets);
		mCameraManager.setAutoShootTargetList(targets);

		return targets;
	}
	
	public void setPitchDistance(float s)
	{
		
		LinkedList<EulerAngles> targets = new LinkedList<EulerAngles>();

		//on ne sait se deplacer qu'en termes de pitch et de yaw
		float yawStep;
		float r, theta;
		for( float pitch = -90+mPitchStep; pitch < 90-mPitchStep; pitch+=mPitchStep)
		{
			theta = 90.0f + pitch;
			
			r = (float) Math.sin(theta);
			
			yawStep = s/r;
			yawStep = Math.abs(yawStep);
			
			for(float yaw = 0; yaw < 360; yaw+=yawStep)
			{
				targets.add(new Snapshot(pitch, yaw));
			}
		}
		
		
		mCameraManager.setAutoShootTargetList(targets);
		mRenderer.setMarkerList(targets);

		
		//add poles
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

}
