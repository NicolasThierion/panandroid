package fr.ensicaen.panandroid.viewer;

import fr.ensicaen.panandroid.sphere.Sphere;
import fr.ensicaen.panandroid.sphere.SphereView;
import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;



public class SphereViewerActivity extends Activity {

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
    
    
    mSphereView.enableInertialRotation(true);
    mSphereView.enableTouchRotation(true);
    mSphereView.setInertiaFriction(50.0f);
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
