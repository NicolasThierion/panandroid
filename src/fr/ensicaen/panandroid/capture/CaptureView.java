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
 * SphereView that use custom CaptureSphereRenderer instead of simple SphereRenderer. The renderer is automatically set.
 * This sphereView disable inertia scroll, and touch events, it puts a SurfaceTexture holding camera preview,
 * and put snapshots all around the sphere.
 * @author Nicolas
 *
 */
@SuppressLint("ViewConstructor")
public class CaptureView extends Inside3dView
{	
	
	private static final String TAG = CaptureView.class.getSimpleName();

	/* *********
	 * CONSTANTS
	 * *********/
	/** Size of the skybox **/
	private static final float SKYBOX_SIZE = 400f;
	private static final int DEFAULT_SKYBOX_SAMPLE_SIZE = 4;		//[1 - 8] pow of 2.
	
	private static final float DEFAULT_PITCH_STEP = 360.0f/12.0f;;
	private static final float DEFAULT_YAW_STEP = 360.0f/12.0f;

	/* **********
	 * ATTRIBUTES
	 * *********/
	
	/** Camera manager, **/
	private CameraManager mCameraManager;
	
	/** Sphere renderer **/
	private CaptureRenderer mRenderer;

	
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
	
		//setup cameraManager
		mCameraManager = cameraManager;	
		mCameraManager.setSensorFusionManager(SensorFusionManager.getInstance(context));
		
		
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
		mRenderer.setPitchStep(mPitchStep);
		mRenderer.setYawStep(mYawStep);
        super.setRenderer(mRenderer);
        
        setPitchStep(mPitchStep);
        setYawStep(mYawStep);
        
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
		mRenderer.setPitchStep(step);

		//updates camera autoshoot targets
		LinkedList<Snapshot> targets = new LinkedList<Snapshot>();
		for(float pitch = 0; pitch < 180; pitch+=mPitchStep)
		{
			for(float yaw = 0; yaw < 360; yaw+=mYawStep)
			{
				targets.add(new Snapshot(pitch, yaw));
			}
		}
		mCameraManager.setAutoShootTargetList(targets);
	}
	
	/**
	 * Set yaw interval between markers and updates camera autoShoot targets.
	 * @param step - yaw interval.
	 */
	public void setYawStep(float step)
	{
		mRenderer.setPitchStep(step);
		mRenderer.setYawStep(step);

		//updates camera autoshoot targets
		LinkedList<Snapshot> targets = new LinkedList<Snapshot>();
		for(float pitch = 0; pitch < 180; pitch+=mPitchStep)
		{
			for(float yaw = 0; yaw < 360; yaw+=mYawStep)
			{
				targets.add(new Snapshot(pitch, yaw));
			}
		}
		mCameraManager.setAutoShootTargetList(targets);
	}

	
	/* **********
	 * VIEW OVERRIDES
	 * *********/
	
	@Override
	public void onPause()
	{
		super.onPause();
		mCameraManager.onPause();
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		mCameraManager.onResume();
	}
	
	
	public void onDestroy()
	{
		mCameraManager.onClose();
	}

}
