package fr.ensicaen.panandroid.capture;

import junit.framework.Assert;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.insideview.Inside3dView;
import fr.ensicaen.panandroid.meshs.Cube;
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
		
		//get camera manager
		mCameraManager = CameraManager.getInstance();
		
		//set GL view & its renderer
		this.mCaptureView = new CaptureView(this, mCameraManager);
		this.setContentView(this.mCaptureView);	
		
		
		//mCameraManager.startPreview();
	
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
	
	
}
