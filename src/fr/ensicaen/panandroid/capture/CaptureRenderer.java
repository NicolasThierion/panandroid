package fr.ensicaen.panandroid.capture;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.insideview.InsideRenderer;
import fr.ensicaen.panandroid.meshs.Cube;
import fr.ensicaen.panandroid.meshs.Snapshot3D;
import fr.ensicaen.panandroid.meshs.TexturedPlane;
import fr.ensicaen.panandroid.tools.BitmapDecoder;

/**
 * CaptureRenderer is basically an Inside3dRenderer, with a cube as preffered surrounding mesh.
 * The surrounding mesh is drawn as a "skybox".
 * The CaptureRenderer use a CameraManager to draw camera preview in foreground. 
 * By default, the renderer starts the cameraManager, and route the preview to a TexturedPlane.
 * @author Nicolas
 * 
 * @bug : cannot take snapshots near -180° yaw
 * @bug : snapshot3D are square
 * TODO : add roll
 */
public class CaptureRenderer extends InsideRenderer implements SnapshotEventListener
{
	
	/* *******
	 * DEBUG PARAMS
	 * ******/
    public final static String TAG = CaptureRenderer.class.getSimpleName();

    public static final boolean USE_UNLOAD_TEXTURE = false;
    
    
    /* ********
	 * CONSTANTS PARAMETERS
	 * ********/
    
    /** memory usage parameter **/
    private static final float AUTO_UNLOADTEXTURE_ANGLE = 80.0f;
    private static final float AUTO_LOADTEXTURE_ANGLE = 70.0f;
    
    
    
    /** Size & distance of the snapshots **/
	private static final float SNAPSHOTS_SIZE = 2.2f;
	private static final float SNAPSHOTS_DISTANCE = 5.0f;
	private static final int DEFAULT_SNAPSHOTS_SAMPLING_RATE= 4;

	/** Size & distance of the camera preview **/
	private static final float CAMERA_SIZE = 1.0f;
	private static final float CAMERA_DISTANCE = 4.5f;
	
	/** Size & distance of the viewFinder**/
	private static final float VIEWFINDER_SIZE = 0.08f;
	private static final float VIEWFINDER_DISTANCE = 3.0f;
	private static final float VIEWFINDER_ATTENUATION_ALPHA = 1.0f; 	
	
	/** Size & distance of the markers **/
	private static final float MARKERS_SIZE = 0.05f;
	private static final float MARKERS_DISTANCE = 3.0f;
	private static final float DEFAULT_MARKERS_ATTENUATION_FACTOR = 15.0f; 		//[ around 1]
		
	/** Ratio of snapshot surfaces when in portrait/landscape mode **/
	
	private static final float CAMERA_RATIO34 = 3.0f/4.0f;	//portait
	private static final float CAMERA_RATIO43 = 4.0f/3.0f;	//landscape
	
	/** default textures **/
	private static final int MARKER_RESSOURCE_ID = R.drawable.ic_picsphere_marker;
	private static final int VIEWFINDER_RESSOURCE_ID = R.drawable.ic_picsphere_viewfinder;
	private static final boolean USE_SCREEN_RATIO = false;
	
	/* ********
	 * ATTRIBUTES
	 * ********/
	/** current context of the application **/
	private Context mContext;
	
	/** surrounding skybox, given to parent Inside3dRenderer **/
	private Cube mSkybox;
	
	/** 
	 * ModelViewMatrix where the scene is drawn. 
	 * Equals identity, as the scene don't move, and it is the parent surrounding skybox that rotates by its own modelMatrix 
	 */
	private final float[] mViewMatrix = new float[16];

	/** Whether the captureRenderer should draw a skyBox **/
	private boolean mUseSkybox;
	
	/**... and some markers **/
	private boolean mUseMarkers;
	
	/** sizes of camera, markers and snapshots **/
	private float mCameraSize = CAMERA_SIZE;
	private float mSnapshotsSize = SNAPSHOTS_SIZE;
	private float mMarkersSize = MARKERS_SIZE;
	private float mViewFinderSize = VIEWFINDER_SIZE;
	
	

	/* ***
	 * camera
	 * ***/
	/** Camera manager in charge of the capture **/
	private final CameraManager mCameraManager;
	
	/** surface texture where is the camera preview is redirected **/
	private SurfaceTexture mCameraSurfaceTex;
	
	/** ... and associated openGL texture ID **/
	private int mCameraTextureId;
	
	/** 3d plane holding this texture **/
	private TexturedPlane mCameraSurface;
	
	//TODO : implement
	/** current ratio of TexturedPlanes, determined by screen orientation **/
	private float mCameraRatio;
	private float mCameraRoll;
	
	
	/* ***
	 * snapshots
	 * ***/
	/** list of snapshot already taken **/
	private List<Snapshot3D> mSnapshots;
	private ReentrantLock mSnapshotsLock;
	
	/** snapshot quality **/
	private int mSamplingRate = DEFAULT_SNAPSHOTS_SAMPLING_RATE;

	/* ***
	 * markers
	 * ***/
	/** list of dots **/
	private List<Snapshot3D> mDots;
	
	/** plane holding viewFinder at the center of the view **/
	private TexturedPlane mViewFinder;
	
	/** marker attenuation factor **/
	private float mMarkersAttenuationFactor = DEFAULT_MARKERS_ATTENUATION_FACTOR;


	
	/* ********
	 * CONSTRUCTOR
	 * ********/
	/**
	 * Creates a new CaptureRenderer, based on an Inside3dRenderer with the given mesh as Skybox.
	 * @param context - Context of the application.
	 * @param skybox
	 * @param cameraManager 
	 */
	public CaptureRenderer(Context context, Cube skybox, CameraManager cameraManager)
	{
		//based on Inside3dRenderer. We are inside a skybox.
		super(context);
		mSkybox = skybox;	
		mUseSkybox = true;
		mUseMarkers = true;
		super.setSurroundingMesh(mSkybox);
		
		
		//init attributes
		mCameraManager = cameraManager;
		mCameraManager.addSnapshotEventListener(this);
		mContext = context;
		mCameraRatio = 0;
		
	    Matrix.setIdentityM(mViewMatrix, 0);
	
		//create dots and snapshot lists
		mSnapshots = new ArrayList<Snapshot3D>();
		mDots = new LinkedList<Snapshot3D>();
		
		
		mSnapshotsLock = new ReentrantLock();
	}
    
	/**
	 * @param context
	 * @param cameraManager
	 */
    public CaptureRenderer(Context context, CameraManager cameraManager)
    {	
		this(context, null, cameraManager );
		mUseSkybox = false;
	}

    
    
    /* ********
	 * ACCESSORS
	 * ********/
	public void setCamPreviewVisible(boolean visible)
	{
        mCameraSurface.setVisible(visible);
    }
	
	public void setSkyboxEnabled(boolean enabled)
	{
		mUseSkybox = enabled;
		
		//if no skybox is set, create a dummy one
		if(mSkybox==null)
		{
			mSkybox = new Cube();
			mSkybox.setSize(SNAPSHOTS_DISTANCE*2.0f);
		}
	}
	
	public void setMarkersEnabled(boolean enabled)
	{
		mUseMarkers = enabled;
	}
	
	public void setCameraSize(float scale)
	{
		mCameraSize = scale;
	}
	
	public void setSnapshotsSize(float scale)
	{
		mSnapshotsSize = scale;
	}
	
	public void setMarkersSize(float scale)
	{
		mMarkersSize = scale;
	}
	
	public void setViewFinderSize(float size)
	{
		mViewFinderSize = size;
	}
	
	public void setSnapshotSamplingRate(int rate)
	{
		mSamplingRate = rate;
	}
	

	/**
	 * set how fast markers disappears when going far from them.
	 * @param factor - factor. A high value set markers to disappear quickly.
	 */
	public void setMarkersAttenuationFactor(float factor)
	{
		mMarkersAttenuationFactor = factor;
	}
	
	
	/**
	 * Set the list of marks to display all around the 3d scene.
	 * @param marks linkedlist of marks to display.
	 */
	public void setMarkerList(LinkedList<EulerAngles> marks)
	{
		for(EulerAngles a : marks)
		{	
			putMarker(a.getPitch(), a.getYaw());
	
		}
	}
	
    
    /* ********
	 * RENDERER OVERRIDES
	 * ********/
	
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config)
	{
		super.onSurfaceCreated(gl, config);
		try
		{
			initCameraSurface();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	/**
	 * Update camera ratio according to new screen orientation
	 */
	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height)
	{
		super.onSurfaceChanged(gl, width, height);
		
		
		if(USE_SCREEN_RATIO)
		{
			if(width>height)
				mCameraRatio = (float)((float)width/(float)height);
			else
				mCameraRatio = (float)((float)height/(float)width);
				
		}
		else
		{
			//ratio of the camera, not the screen		
			if(width>height)
				mCameraRatio=CAMERA_RATIO43;
			else
				mCameraRatio=CAMERA_RATIO34;
			mCameraRatio=CAMERA_RATIO34;

		}
		Log.i(TAG, "surface changed : width="+width+", height="+height+"(ratio:"+mCameraRatio+")");
		try
		{
			reinitCameraSurface();
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		//reset camera to avoid random "error 100 : server died"
		//mCamManager.reOpen();
		//fixed by added configChanged to manifest to avoid camera reopen on screen changed.
	}
	
	@Override
	public void onDrawFrame(GL10 gl)
	{    
		//draws the skybox
		if(mUseSkybox)
			super.onDrawFrame(gl);
		
		//refresh camera texture
		mCameraSurfaceTex.updateTexImage();
		
		//draw camera surface
		gl.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
	    mCameraSurface.draw(gl, mViewMatrix);
		gl.glDisable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
		
		//draw the viewFinder
		mViewFinder.draw(gl, mViewMatrix);

		//the snapshots that are in FOV
		mSnapshotsLock.lock();
		for (Snapshot3D snap : mSnapshots)
		{

			if(USE_UNLOAD_TEXTURE && this.getSnapshotDistance(snap)>AUTO_UNLOADTEXTURE_ANGLE)
				snap.setVisible(false);
			else if(this.getSnapshotDistance(snap)<AUTO_LOADTEXTURE_ANGLE)
			{
				snap.setVisible(true);
				snap.draw(gl, super.getRotationMatrix());
			}
				
		}
		mSnapshotsLock.unlock();
		
		
		//... and then all markers with newly computed alpha
		float d;
		
		//draw markers
		if(mUseMarkers)
		{
			for (Snapshot3D dot : mDots)
			{
				
				// Set alpha based on camera distance to the point
				d = getSnapshotDistance(dot)*mMarkersAttenuationFactor/360.0f;
				d = (d>1.0f?1.0f:d);
				dot.setAlpha(1.0f - d);    
				dot.draw(gl, super.getRotationMatrix());
			}
		}
	
	}


	/* **********
	 * PRIVATE METHODS
	 * *********/
	
	/**
	 * Init CameraManager gl texture id, camera SurfaceTexture, bind to EXTERNAL_OES, and redirect camera preview to the surfaceTexture.
	 * @throws IOException when camera cannot be open
	 */
	private void initCameraSurface() throws IOException
	{
	
		//Gen openGL texture id
		int texture[] = new int[1];
		GLES10.glGenTextures(1, texture, 0);
		mCameraTextureId = texture[0];
		
		if (mCameraTextureId == 0)
		{
		    throw new RuntimeException("Cannot create openGL texture (initCameraSurface())");
		}
		
		//Camera preview is redirected to SurfaceTexture.
		//SurfaceTexture works with TEXTURE_EXTERNAL_OES, so we bind this textureId so that camera
		//will automatically fill it with its video.
		GLES10.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureId);
		
		// Can't do mipmapping with camera source
		GLES10.glTexParameterf(	GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
								GLES10.GL_TEXTURE_MIN_FILTER,
								GLES10.GL_LINEAR);
		GLES10.glTexParameterf(	GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
								GLES10.GL_TEXTURE_MAG_FILTER,
								GLES10.GL_LINEAR);
		
		// Clamp to edge is the only option
		GLES10.glTexParameterf(	GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
								GLES10.GL_TEXTURE_WRAP_S,
								GLES10.GL_CLAMP_TO_EDGE);
		GLES10.glTexParameterf(	GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
								GLES10.GL_TEXTURE_WRAP_T,
								GLES10.GL_CLAMP_TO_EDGE);
				
		//create a SurfaceTexture associated to this openGL texture...
		mCameraSurfaceTex = new SurfaceTexture(mCameraTextureId);
		mCameraSurfaceTex.setDefaultBufferSize(640, 480);
		
		//... and redirect camera preview to it 		
		mCameraManager.setPreviewSurface(mCameraSurfaceTex);
		
		//Setup viewfinder	
		mViewFinder = new TexturedPlane(mViewFinderSize);
		mViewFinder.setTexture(BitmapDecoder.safeDecodeBitmap(mContext.getResources(), VIEWFINDER_RESSOURCE_ID));
		mViewFinder.translate(0, 0, VIEWFINDER_DISTANCE);
		mViewFinder.setAlpha(VIEWFINDER_ATTENUATION_ALPHA);

	}
	
	private void reinitCameraSurface() throws IOException
	{
		//for an unknown reason, the camera preview is not in correct direction by default. Need to rotate it
		final int screenRotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();	
		mCameraRoll = 270;
		switch (screenRotation)
		{
			case Surface.ROTATION_0:
				mCameraRoll += 0.0f;
				break;
			case Surface.ROTATION_90:
				mCameraRoll += 90.0f;
				break;
			case Surface.ROTATION_180:
				mCameraRoll += 180.0f;
				break;
			default:
				mCameraRoll += 270.0f;
				break;
		};
		
		mCameraRoll%=360;

		
		//create a new TexturedPlane, that holds the camera texture.
		mCameraSurface = new TexturedPlane(mCameraSize , mCameraRatio );
		mCameraSurface.setTexture(mCameraTextureId);
		
		//for unknown reason, the preview is not in correct orientation
		mCameraSurface.rotate(0, 0, mCameraRoll);
		mCameraSurface.translate(0, 0, CAMERA_DISTANCE);
		
	}
    
    
	private Snapshot3D putMarker(float pitch, float yaw)
	{
		Snapshot3D dot = new Snapshot3D(mMarkersSize, pitch, yaw);
		dot.setTexture(BitmapDecoder.safeDecodeBitmap(mContext.getResources(), MARKER_RESSOURCE_ID));
		dot.translate(0.0f, 0.0f, MARKERS_DISTANCE);
		
		mDots.add(dot);
		return dot;
    }
	
	
	private Snapshot3D putSnapshot(byte[] pictureData, Snapshot snapshot)
	{
		Snapshot3D snap = new Snapshot3D(mSnapshotsSize, mCameraRatio, snapshot);
		
		//snap.setTexture(BitmapDecoder.safeDecodeBitmap(pictureData, mSamplingRate));
		//mSamplingRate = BitmapDecoder.getSampleRate();
		//snap.setTexture(BitmapDecoder.safeDecodeBitmap(mContext.getResources(), VIEWFINDER_RESSOURCE_ID));

		snap.translate(0.0f, 0.0f, SNAPSHOTS_DISTANCE);
		snap.rotate(0, 0, mCameraRoll);
		snap.setVisible(true);
		mSnapshotsLock.lock();
		mSnapshots.add(snap);
		mSnapshotsLock.unlock();
		

		return snap;
    }
	/**
	 * get distance etween current orientation and gven snapshot
	 * @param snapshot
	 * @return
	 */
	private float getSnapshotDistance(EulerAngles snapshot)
	{
		float oPitch = super.getPitch();
		float oYaw = super.getYaw();
		float sPitch , sYaw, dPitch, dYaw, d;	
			
        sPitch = snapshot.getPitch();
        sYaw = snapshot.getYaw();
        
        dPitch = Math.abs(Math.abs(sPitch) - Math.abs(oPitch));
        dYaw = Math.abs(Math.abs(sYaw) - Math.abs(oYaw));
        d = (dPitch+dYaw);
        
        //neutralize yaw if it is a pole
        if(Math.abs(sPitch)>89.0f)
        	d = dPitch;
        
        return d;
				
	}
	
	@Override
	public void onSnapshotTaken(byte[] pictureData, Snapshot snapshot)
	{
		//a snapshot has just been taken :
		
		//TODO
		//find corresponding dot and remove it.

		//put a new textureSurface with the snapshot in it.
		putSnapshot(pictureData, snapshot);
		
	}

	
	 
}




