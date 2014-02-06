package fr.ensicaen.panandroid.capture;

import java.util.LinkedList;

import fr.ensicaen.panandroid.sphere.Sphere;
import android.app.Activity;
import android.os.Bundle;
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
	/** Size of the sphere **/
	private static final float SPHERE_RADIUS = 0.15f;
	/** difference **/
	private static final float DELTA_YAW = 10, DELTA_PITCH = 10; 
	/** Resolution of the sphere **/
	private static final int SPHERE_RESOLUTION = 4;
	
	/* *********
	 * ATTRIBUTES
	 * *********/
			
	/** The OpenGL view where to draw the sphere. */
	private CaptureView mSphereView;
	
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
		
		//init the sphere
		Sphere sphere = new Sphere(SPHERE_RESOLUTION, SPHERE_RADIUS);
		
		//get camera manager
		mCameraManager = CameraManager.getInstance();
		
		//set GL view & its renderer
		this.mSphereView = new CaptureView(this, mCameraManager);
		this.setContentView(this.mSphereView);		
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
		super.onResume();
		this.mSphereView.onResume();
	}
	
	/**
	 * Also pause the glSurface.
	 */
	@Override
	protected void onPause()
	{
		this.mSphereView.onPause();
		super.onPause();
	}
}
