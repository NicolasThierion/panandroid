package fr.ensicaen.panandroid.capture;

import junit.framework.Assert;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.meshs.Cube;
import fr.ensicaen.panandroid.meshs.Inside3dView;
import fr.ensicaen.panandroid.meshs.Sphere;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;


/**
 * Activity that display a blank 3D sphere, and allow to take snapshots
 * @todo Take snaptshots
 * @todo put dots all around shpere
 * @todo use sensor to activate camera
 * @todo set snapshots as texture of the sphere
 * @todo build a JSON file with position info of each shapshots
 *  
 * @author Nicolas
 *
 */
public class CaptureActivity extends Activity
{
	/* *********
	 * CONSTANTS
	 * *********/
	/** Size of the skybox **/
	private static final float SKYBOX_SIZE = 15f;
		
	/* *********
	 * ATTRIBUTES
	 * *********/
			
	/** The OpenGL view where to draw the sphere. */
	private Inside3dView mCaptureView;
	
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
	    
	    //view in fullscreen
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		//init the skybox
		Cube skybox = null;
		Resources res = super.getResources();
		
		int sampleSize=1;
		boolean done = false;
		while(!done && sampleSize<32)
		{
			try{
			
				Bitmap texFront = safeDecodeBitmap(res, R.raw.skybox_ft, sampleSize);
				Bitmap texBack = safeDecodeBitmap(res, R.raw.skybox_bk, sampleSize);
				Bitmap texLeft = safeDecodeBitmap(res, R.raw.skybox_lt, sampleSize);
				Bitmap texRight = safeDecodeBitmap(res, R.raw.skybox_rt, sampleSize);
				Bitmap texBottom = safeDecodeBitmap(res, R.raw.skybox_bt, sampleSize);
				Bitmap texTop = safeDecodeBitmap(res, R.raw.skybox_tp, sampleSize);
				
				skybox = new Cube(SKYBOX_SIZE,texFront,texBack, texLeft, texRight, texBottom, texTop);
				done = true;
			}
			catch (OutOfMemoryError e)
			{
				
				sampleSize*=2;
			}
			
		}
		Assert.assertTrue(skybox!=null);
		
		//get camera manager
		mCameraManager = CameraManager.getInstance();
		
		//set GL view & its renderer
		this.mCaptureView = new Inside3dView(this, skybox );// new CaptureView(this,  mCameraManager);
		this.setContentView(this.mCaptureView);	
		
		mCaptureView.enableSensorialRotation(true);
		//mCaptureView.enableInertialRotation(true);
		//mCaptureView.enableTouchRotation(true);
		
	}
	
	  
	/**
	 * Remember to resume the glSurface.
	 */
	@Override
	protected void onResume()
	{
		super.onResume();
		this.mCaptureView.onResume();
	}
	
	/**
	 * Also pause the glSurface.
	 */
	@Override
	protected void onPause()
	{
		this.mCaptureView.onPause();
		super.onPause();
	}
	
	private Bitmap safeDecodeBitmap(Resources res, int resId, int sampleSize)
	{
		Bitmap bmp;
		BitmapFactory.Options bitmapOptions = new BitmapFactory.Options(); 
		bitmapOptions.inSampleSize = sampleSize;
		try
		{
			 bmp = BitmapFactory.decodeResource(res, resId, bitmapOptions);
			 return bmp;
		}
		catch(OutOfMemoryError e)
		{
			//out of memory => launch garbage collector
			System.gc();
		}
		
		
		while (bitmapOptions.inSampleSize<32)
		{
			try
			{
				bmp = BitmapFactory.decodeResource(res, resId, bitmapOptions);
				return bmp;
			}
			catch (OutOfMemoryError e)
			{
				bitmapOptions.inSampleSize *= 2;
			}
		}
		throw new OutOfMemoryError();
		
	}
	private Bitmap safeDecodeBitmap(Resources res, int resId)
	{
		return safeDecodeBitmap(res, resId, 1);
	}
}
