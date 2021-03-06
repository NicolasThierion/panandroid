/*
 * Copyright (C) 2013 Nicolas THIERION, Saloua BENSEDDIK, Jean Marguerite.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package fr.ensicaen.panandroid.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
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
import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.snapshot.SnapshotEventListener;
import fr.ensicaen.panandroid.tools.BitmapDecoder;
import fr.ensicaen.panandroid.tools.EulerAngles;


/**
 * CaptureRenderer is basically an Inside3dRenderer, with a cube as preffered surrounding mesh.
 * The surrounding mesh is drawn as a "skybox".
 * The CaptureRenderer use a CameraManager to draw camera preview in foreground. 
 * By default, the renderer starts the cameraManager, and route the preview to a TexturedPlane.
 * @author Nicolas THIERION.
 * @bug : few snapshot cannot be taken randomly.
 * @bug : view sometimes not correctly reload the textures
 * @bug : textures are not reloaded after onResume() 
 */
public class CaptureRenderer extends InsideRenderer implements SnapshotEventListener, OnFrameAvailableListener
{

	/* *******
	 * DEBUG PARAMS
	 * ******/
    public final static String TAG = CaptureRenderer.class.getSimpleName();
    public static final boolean USE_MARKERS = true;
    public static final boolean USE_CONTOUR = true;
    
    public static final int MEMORY_CLEANUP_THRESHOLD = 1000 ;//[mB]
    
    
    /* ********
	 * CONSTANTS PARAMETERS
	 * ********/
    
    /** memory usage parameter **/
    private static final float AUTO_UNLOADTEXTURE_ANGLE = 150.0f;		//[deg]
    private static final float AUTO_LOADTEXTURE_ANGLE = 90.0f;			//[deg]
    
    /** field of vision of the scene **/
    private static final float DEFAULT_FOV_DEG = 110;
    
    /** Size & distance of the snapshots **/
	private static final float SNAPSHOTS_SIZE = 0.20f;
	private static final float SNAPSHOTS_DISTANCE = 0.50f;
	private static final float DEFAULT_SNAPSHOT_ZOOM = 140.0f;
	private static final int DEFAULT_SNAPSHOTS_SAMPLING_RATE= 0;	//[0 for automatic sample rate]
	private static final float SNAPSHOT_AUTO_SAMPLE_RATIO = 4.0f;	 

	/** Size & distance of the camera preview **/
	private static final float CAMERA_SIZE = 0.10f;
	private static final float CAMERA_DISTANCE = 0.45f;
	private static final float CAMERA_SURFACE_ALPHA = 0.65f;		//[0 - 1] : 1;

	/** Size & distance of the viewFinder**/
	private static final float VIEWFINDER_SIZE = 0.010f;
	private static final float VIEWFINDER_DISTANCE = 0.25f;
	private static final float VIEWFINDER_ATTENUATION_ALPHA = 1.0f; 	

	/** Size & distance of the markers **/
	private static final float MARKERS_SIZE = 0.008f;
	private static final float MARKERS_DISTANCE = 0.30f;
	private float CONTOUR_ZOOM_PERCENT = 115.0f;

	private static final float DEFAULT_MARKERS_ATTENUATION_FACTOR = 15.0f; 		//[ in percent]

	private static final float CAMERA_RATIO = 4.0f/3.0f;


	/** default textures **/
	private static final int MARKER_RESSOURCE_ID = R.drawable.capture_snapshot_marker;
	private static final int CONTOUR_RESSOURCE_ID = R.drawable.capture_snapshot_contour;
	private static final int VIEWFINDER_RESSOURCE_ID = R.drawable.capture_viewfinder;


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
	private final float[] mViewMatrix;

	/** Whether the captureRenderer should draw a skyBox **/
	private boolean mUseSkybox = true;

	/**... and some markers **/
	private boolean mUseMarkers = USE_MARKERS;
	private boolean mUseContours = USE_CONTOUR;

	private final Bitmap mMarkerBitmap;
	private final Bitmap mContourBitmap;



	/** sizes of camera, markers and snapshots **/
	private float mCameraSize = CAMERA_SIZE;
	private float mSnapshotsSize = SNAPSHOTS_SIZE;
	private float mSnapshotZoom = DEFAULT_SNAPSHOT_ZOOM;
	private float mMarkersSize = MARKERS_SIZE;
	private float mViewFinderSize = VIEWFINDER_SIZE;

	/** toggle to true when memory is running low **/
    public boolean mHasToFreeMemory = false;



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
	private boolean mCameraFrameAvaible = false;

	private float mCameraRoll;


	/* ***
	 * snapshots
	 * ***/
	/** list of snapshot already taken **/
	private List<Snapshot3D> mSnapshots;
	private ReentrantLock mSnapshotsLock;

	/** snapshot quality **/
	private int mSampleRate = DEFAULT_SNAPSHOTS_SAMPLING_RATE;

	/* ***
	 * markers
	 * ***/
	/** list of dots **/
	private List<Snapshot3D> mDots;
	private ReentrantLock mTargetsLock;
	private List<Snapshot3D> mContours;
	private List<Snapshot3D> mContours43;
	private List<Snapshot3D> mContours34;

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
		super.setSurroundingMesh(mSkybox);
		super.setFovDeg(DEFAULT_FOV_DEG);

		//init attributes
		mCameraManager = cameraManager;
		mCameraManager.addSnapshotEventListener(this);
		mContext = context;

		mMarkerBitmap = BitmapDecoder.safeDecodeBitmap(mContext.getResources(), MARKER_RESSOURCE_ID);
		mContourBitmap = BitmapDecoder.safeDecodeBitmap(mContext.getResources(), CONTOUR_RESSOURCE_ID);


		//if auto samplig enabled
		if(mSampleRate == 0)
		{
			mSampleRate=(int) mCameraManager.getCameraResolution();
			mSampleRate = (int) (ceilPowOf2(mSampleRate)/SNAPSHOT_AUTO_SAMPLE_RATIO);
		}

		mViewMatrix = new float[16];
	    Matrix.setIdentityM(mViewMatrix, 0);

		//create dots and snapshot lists
		mSnapshots = new ArrayList<Snapshot3D>();
		mDots = new LinkedList<Snapshot3D>();
		mContours43 = new LinkedList<Snapshot3D>();
		mContours34 = new LinkedList<Snapshot3D>();
		mContours = mContours43;
		mTargetsLock = new ReentrantLock();
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

	public void setContourEnabled(boolean enabled)
	{
		mUseContours = enabled;
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
		mSampleRate = rate;
	}


	/**
	 * set how fast markers disappears when going far from them.
	 * @param factor - factor. A high value set markers to disappear quickly. Setting to zero will make them to never dissapear.
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
		mTargetsLock.lock();
		mDots = new LinkedList<Snapshot3D>();
		boolean is43=(mContours == mContours43?true:false);

		mContours34 = new LinkedList<Snapshot3D>();
		mContours43 = new LinkedList<Snapshot3D>();
		for(EulerAngles a : marks)
		{	
			putMarker(a.getPitch(), a.getYaw());
			putContour(a.getPitch(), a.getYaw());
		}	
		mContours = (is43?mContours43:mContours34);
		mTargetsLock.unlock();

	}

	public void setSnapshotZoom(float zoom)
	{
		mSnapshotZoom = zoom;
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
		try
		{
			reinitCameraSurface();
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}

	}

	@Override
	public void onDrawFrame(GL10 gl)
	{    		
		//draws the skybox
		if(mUseSkybox)
			super.onDrawFrame(gl);

		//refresh camera texture
		if(mCameraFrameAvaible)
		{
			mCameraSurfaceTex.updateTexImage();
			mCameraFrameAvaible = false;
		}
		mCameraSurfaceTex.setOnFrameAvailableListener(this);
		//draw camera surface
		gl.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
	    mCameraSurface.draw(gl, mViewMatrix);
		gl.glDisable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);

		//draw the viewFinder
		mViewFinder.draw(gl, mViewMatrix);

		//launch memory cleanup??
		Runtime info = Runtime.getRuntime();
		long freeMem = info.freeMemory()/1048576L;
        if( freeMem<MEMORY_CLEANUP_THRESHOLD)
        	mHasToFreeMemory = true;
        else
        	mHasToFreeMemory= false;


		//the snapshots that are in FOV
		mSnapshotsLock.lock();
		for (Snapshot3D snap : mSnapshots)
		{
			float distance = this.getSnapshotDisnance(snap);

			if(mHasToFreeMemory && distance>AUTO_UNLOADTEXTURE_ANGLE)
			{
				snap.unloadGLTexture(gl);
			}
			else if(this.getSnapshotDisnance(snap)<AUTO_LOADTEXTURE_ANGLE)
			{
				snap.loadGLTexture(gl);
			}

			if(distance > 120.0f)
				snap.setVisible(false);
			else
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
			mTargetsLock.lock();
			for (Snapshot3D dot : mDots)
			{		
				d = getSnapshotDisnance(dot);
				if(d>60.0f)
				{
					dot.setVisible(false);
				}
				else
				{
					dot.setVisible(true);
					// Set alpha based on camera distance to the point
					d = d *mMarkersAttenuationFactor/360.0f;
					d = (d>1.0f?1.0f:d);
					dot.setAlpha(1.0f - d);    
					dot.draw(gl, super.getRotationMatrix());
				}
			}
			mTargetsLock.unlock();
		}
		if(mUseContours)
		{
			mTargetsLock.lock();
			for (Snapshot3D contour : mContours)
			{		
				d = getSnapshotDisnance(contour);

				if(d>60.0f)
				{
					contour.setVisible(false);
				}
				else
				{
					contour.setVisible(true);
					// Set alpha based on camera distance to the point
					d = d*mMarkersAttenuationFactor/360.0f;
					d = (d>1.0f?1.0f:d);
					contour.setAlpha(1.0f - d);    
					contour.draw(gl, super.getRotationMatrix());
				}

			}
			mTargetsLock.unlock();
		}

	}

	@Override
	public void onSnapshotTaken(byte[] pictureData, Snapshot snapshot)
	{
		//a snapshot has just been taken :

		float pitch = snapshot.getPitch();
		float yaw = snapshot.getYaw();
		if(!removeMarker(pitch, yaw) || !removeContour(pitch, yaw))
		{
			Log.e(TAG, "cannot remove mark for snapshot (p="+pitch+", y="+yaw+")");
		}

		//put a new textureSurface with the snapshot in it.
		putSnapshot(pictureData, snapshot);

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
		mViewFinder.recycleTexture();
		mViewFinder.translate(0, 0, VIEWFINDER_DISTANCE);
		mViewFinder.setAlpha(VIEWFINDER_ATTENUATION_ALPHA);


	}

	private void reinitCameraSurface() throws IOException
	{
		//for an unknown reason, the camera preview is not in correct direction by default. Need to rotate it
		final int screenRotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();	
		mCameraRoll = 270;
		mTargetsLock.lock();
		switch (screenRotation)
		{
			case Surface.ROTATION_0:
				mCameraRoll += 0.0f;
				mContours = mContours34;
				break;
			case Surface.ROTATION_90:
				mCameraRoll += 90.0f;
				mContours = mContours43;
				break;
			case Surface.ROTATION_180:
				mCameraRoll += 180.0f;
				mContours = mContours34;
				break;
			default:
				mCameraRoll += 270.0f;
				mContours = mContours43;
				break;
		};
		mTargetsLock.unlock();

		mCameraRoll%=360;

		//create a new TexturedPlane, that holds the camera texture.
		mCameraSurface = new TexturedPlane(mCameraSize , CAMERA_RATIO );
		mCameraSurface.setTexture(mCameraTextureId);
		mCameraSurface.setAlpha(CAMERA_SURFACE_ALPHA);
		//for unknown reason, the preview is not in correct orientation
		mCameraSurface.rotate(0, 0, mCameraRoll);
		mCameraSurface.translate(0, 0, CAMERA_DISTANCE);

	}
    
    /**
     * put a dot in the dot list, at the given pitch and yaw
     * @param pitch - pitch where to put the dot.
     * @param yaw - yaw where to put the dot.
     * @return the created Snapshot3D representing the dot.
     */
	private Snapshot3D putMarker(float pitch, float yaw)
	{

		Snapshot3D dot = new Snapshot3D(mMarkersSize, pitch, yaw);
		dot.setTexture(mMarkerBitmap);
		dot.translate(0.0f, 0.0f, MARKERS_DISTANCE);

		mDots.add(dot);
		return dot;
    }

    /**
     * put a contour in the contour list, at the given pitch and yaw.
     * @param pitch - pitch where to put the contour.
     * @param yaw - yaw where to put the contour.
     * @return the created Snapshot3D representing the contour.
     */
	private void putContour(float pitch, float yaw)
	{
		Snapshot3D contour43 = new Snapshot3D(CAMERA_SIZE, CAMERA_RATIO, pitch, yaw);
		Snapshot3D contour34 = new Snapshot3D(CAMERA_SIZE, CAMERA_RATIO, pitch, yaw);
		contour34.rotate(0, 0, 90.0f);

		contour43.setTexture(mContourBitmap);
		contour43.translate(0.0f, 0.0f, CAMERA_DISTANCE - CAMERA_DISTANCE/(CONTOUR_ZOOM_PERCENT -100.0f));
		contour43.setVisible(false);

		contour34.setTexture(mContourBitmap);
		contour34.translate(0.0f, 0.0f, CAMERA_DISTANCE - CAMERA_DISTANCE/(CONTOUR_ZOOM_PERCENT-100.0f));
		contour34.setVisible(false);

		mContours43.add(contour43);
		mContours34.add(contour34);
	}

	/**
	 * Build a Snapshot3D from the given snapshot, and put it in the 3D view at its pithc, yaw and roll.
	 * @param pictureData - the picture byteArray to fill the snapshot3D with.
	 * @param snapshot
	 * @return
	 */
	private Snapshot3D putSnapshot(byte[] pictureData, Snapshot snapshot)
	{

		//build a snapshot3d from the snapshot2d
		Snapshot3D snap = new Snapshot3D(mSnapshotsSize, CAMERA_RATIO, snapshot);
		//fill the snapshot with the byteArray, faster than reading its data from SD.
		snap.setSampleRate(mSampleRate);
		snap.setTexture(BitmapDecoder.safeDecodeBitmap(pictureData, mSampleRate));
		snap.setZoom(mSnapshotZoom);
		snap.recycleTexture();

		//put the snapshot at its place.
		snap.translate(0.0f, 0.0f, SNAPSHOTS_DISTANCE);
		snap.setVisible(true);
		mSnapshotsLock.lock();
		mSnapshots.add(snap);
		mSnapshotsLock.unlock();


		return snap;
    }

	/**
	 * get distance between current orientation and gven snapshot
	 * @param snapshot
	 * @return
	 */
	private float getSnapshotDisnance(EulerAngles a)
	{

		Snapshot s = new Snapshot(super.getPitch(), super.getYaw());
		return s.getDistance(a); 
	}

	/**
	 * Remove the dot near the given position.
	 * @param pitch
	 * @param yaw
	 */
	private boolean removeMarker(float pitch, float yaw)
	{
		final float TRESHOLD = 10.0f;
		Snapshot o = new Snapshot(pitch, yaw);
		mTargetsLock.lock();
		for(Snapshot3D dot : mDots)
		{
			if(o.getDistance(dot)<TRESHOLD)
			{
				mDots.remove(dot);
				mTargetsLock.unlock();
				return true;
			}
		}
		mTargetsLock.unlock();
		return false;	
	}

	/**
	 * Remove the dot near the given position.
	 * @param pitch
	 * @param yaw
	 */
	private boolean removeContour(float pitch, float yaw)
	{
		final float TRESHOLD = 10.0f;
		Snapshot o = new Snapshot(pitch, yaw);
		int i=0;
		mTargetsLock.lock();
		for(Snapshot3D contour : mContours)
		{
			if(o.getDistance(contour)<TRESHOLD)
			{
				mContours43.remove(i);
				mContours34.remove(i);
				mTargetsLock.unlock();
				return true;
			}
			++i;
		}
		mTargetsLock.unlock();

		return false;	
	}

	private int ceilPowOf2(int val)
	{
		int i = 1;

		while(i<val)
		{
			i=i<<1;
		}

		return i;
	}

	/**
	 * update camera board only if a new frame is avaible.
	 */
	@Override
	public void onFrameAvailable(SurfaceTexture unused)
	{
		mCameraFrameAvaible  = true;	
	}
}