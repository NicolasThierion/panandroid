package org.panandroid.capture;

import org.panandroid.sensor.SensorFusionManager;
import org.panandroid.sphere.Sphere;
import org.panandroid.sphere.SphereView;

import android.app.Activity;
import android.opengl.GLSurfaceView;
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
public class CaptureActivity extends Activity {

	/** Size of the sphere **/
	private static final float SPHERE_RADIUS = 0.15f;
	
	/** Resolution of the sphere **/
	private static final int SPHERE_RESOLUTION = 4;
			
	/** The OpenGL view. */
	private SphereView mSphereView;
	
	
	
  /**
   * Called when the activity is first created.
   * @param savedInstanceState The instance state.
   */
  @Override
  public void onCreate(final Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    //view in fullscreen
    this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    
    //init the sphere
    Sphere sphere = new Sphere(SPHERE_RESOLUTION, SPHERE_RADIUS);
    
    //set GL view & its renderer
    this.mSphereView = new SphereView(this, sphere);
    this.setContentView(this.mSphereView);
    
    
    mSphereView.enableInertialRotation(false);
    mSphereView.enableTouchRotation(false);
    mSphereView.setInertiaFriction(0.0f);
    
    mSphereView.enableSensorialRotation(true);
  }

  
  /**
   * Remember to resume the glSurface.
   */
  @Override
  protected void onResume() {
    super.onResume();
    this.mSphereView.onResume();
  }

  /**
   * Also pause the glSurface.
   */
  @Override
  protected void onPause() {
    this.mSphereView.onPause();
    super.onPause();
  }
}
