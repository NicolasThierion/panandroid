package fr.ensicaen.panandroid.capture;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.insideview.Inside3dView;
import fr.ensicaen.panandroid.insideview.InsideRenderer;
import fr.ensicaen.panandroid.meshs.Cube;
import fr.ensicaen.panandroid.tools.BitmapDecoder;
import junit.framework.Assert;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.Log;


/**
 * SphereView that use custom CaptureSphereRenderer instead of simple SphereRenderer.
 * This sphereView disable inertia scroll, and touch events, it puts a SurfaceTexture holding camera preview,
 * and put snapshots all around the sphere.
 * @author Nicolas
 *
 */
public class CaptureView extends Inside3dView
{	
	
	private static final String TAG = CaptureView.class.getSimpleName();

	/* *********
	 * CONSTANTS
	 * *********/
	/** Size of the skybox **/
	private static final float SKYBOX_SIZE = 400f;
	private static final int DEFAULT_SKYBOX_SAMPLE_SIZE = 8;		//[1 - 8]
	

	/* **********
	 * ATTRIBUTES
	 * *********/
	
	/** Camera manager, **/
	private CameraManager mCameraManager;
	
	/** Sphere renderer **/
	private InsideRenderer mRenderer;

	/* **********
	 * CONSTRUCTORS
	 * *********/
	
	/**
	 * Draws the given sphere in the center of the view, plus the camera preview given by CameraManager.
	 * @param context - context of application.
	 * @param sphere - Sphere to draw.
	 * @param mCameraManager - Camera manager, to redirect camera preview.
	 */
	public CaptureView(Context context, CameraManager cameraManager)
	{
		super(context);
	
		mCameraManager = cameraManager;	
				
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
        super.setEGLContextClientVersion(1);
        super.setRenderer(mRenderer);
        
        //set view rotation parameters
        //super.enableSensorialRotation(true);
        super.enableTouchRotation(true);
        super.enableInertialRotation(true);
	}
	

}
