package fr.ensicaen.panandroid.capture;
import junit.framework.Assert;
import android.content.Context;
import android.opengl.GLSurfaceView;


/**
 * SphereView that use custom CaptureSphereRenderer instead of simple SphereRenderer.
 * This sphereView disable inertia scroll, and touch events, it puts a SurfaceTexture holding camera preview,
 * and put snapshots all around the sphere.
 * @author Nicolas
 *
 */
public class CaptureView extends GLSurfaceView
{

	private static final String TAG = CaptureView.class.getSimpleName();
	/* **********
	 * ATTRIBUTES
	 * *********/
	
	/** Camera manager, **/
	private CameraManager mCameraManager;
	
	/** Sphere renderer **/
	private CaptureRenderer mRenderer;

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
		mRenderer = new CaptureRenderer(context, mCameraManager) ;
		
        // Make a new GL view using the provided renderer
        super.setEGLContextClientVersion(2);
        super.setRenderer(mRenderer);
	
	}
	
	
	
	/* **********
	 * PRIVATE METHODS
	 * **********/
	
	
	
	
	
	

}
