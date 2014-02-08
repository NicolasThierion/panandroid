package fr.ensicaen.panandroid.capture;

import java.io.File;
import java.io.IOException;

import junit.framework.Assert;
import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.Window;
import android.view.WindowManager;


/**
 * 
 * Activity through what the user can capture snapshots in order to make his panorama.
 * 
 * The activity opens a 3D and draws a cubic skybox seen from inside.
 * The view rotates with the device orientation. Dots are put all around the view, and a snapshot is taken automatically when viewfinder and dot are alligned
 * 
 * 
 * @todo Take snaptshots
 * @todo put dots all around
 * @todo use sensor to activate camera
 * @todo put snapshots
 * @todo build a JSON file with position info of each shapshots
 *  
 * @author Nicolas
 *
 */
public class CaptureActivity extends Activity
{


	/* *********
	 * PARAMETERS
	 * *********/

	private static final float DEFAULT_PITCH_STEP = 360.0f/12.0f;;
	private static final float DEFAULT_YAW_STEP = 360.0f/12.0f;
	private static final String PICTURE_DIRECTORY = Environment.getExternalStorageDirectory() + File.separator + "Panandroid";

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
	    
		//view in fullscreen
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		
		//setup camera manager
		mCameraManager = CameraManager.getInstance();
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
		
		mCaptureView.setPitchStep(DEFAULT_PITCH_STEP);
		mCaptureView.setYawStep(DEFAULT_YAW_STEP);
		
		
		this.setContentView(this.mCaptureView);	
		
		
		//mCameraManager.startPreview();
	
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
	
}
