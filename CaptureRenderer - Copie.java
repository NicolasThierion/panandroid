package fr.ensicaen.panandroid.capture;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;





import junit.framework.Assert;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera.Size;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.Matrix;
import android.opengl.GLSurfaceView.Renderer;
import android.util.Log;
import fr.ensicaen.panandroid.sphere.Sphere;
import fr.ensicaen.panandroid.sphere.SphereRenderer;

public class CaptureRenderer extends SphereRenderer 
{
	private static final String TAG = CaptureRenderer.class.getSimpleName();
	
	
	/* **********
	 * ATTRIBUTES
	 * *********/
	/** Camera preview surface, that will be filled in by camera manager **/
	private SurfaceTexture mCameraSurfaceTexture;	
	private int mCameraTextureId; 
	private Snapshot3D mCameraPreviewSnapshot;

	/** Camera manager **/
	private CameraManager mCameraManager;

	
	/* **********
	 * CONSTRUCTOR
	 * *********/
	
	public CaptureRenderer(Context context, Sphere sphere, CameraManager cameraManager) throws IOException
	{
		super(context, sphere);
		mCameraManager = cameraManager;

		
	}


	
	

	/* **********
	 * RENDERER OVERRIDES
	 * *********/
	@Override
	public void onSurfaceCreated(final GL10 gl, final EGLConfig config)
	{
		initCameraPreview(gl, config);

	}
	
	

	/* **********
	 * PRIVATE METHODS
	 * *********/
	
	
	/**
	 * Must be called in onSurfaceCreated or another openGL thread.
	 * Creates and bind camera texture, and redirect camera preview to mCameraPreview surface.
	 * 
	 * @param gl
	 * @param config
	 */
	private void initCameraPreview(GL10 gl, EGLConfig config)
	{
	
		super.onSurfaceCreated(gl, config);
		
		//generate a new OpenGL texture to hold camera preview
		int[] textureId = new int[1];
		GLES10.glGenTextures(1, textureId, 0);
        mCameraTextureId = textureId[0];
        Assert.assertTrue(mCameraTextureId!=0);
	
		//create surfaceTexture with given texture.
        mCameraSurfaceTexture = new SurfaceTexture( mCameraTextureId);
		
		//and bind it to external OES texture, that will be automatically applied to our surfaceTexture
		GLES10.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureId);
		 
		 
        // Can't do mipmapping with camera source
        GLES10.glTexParameterf(	GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        						GLES10.GL_TEXTURE_MIN_FILTER,
        						GLES10.GL_LINEAR);
        GLES10.glTexParameterf(	GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        						GLES10.GL_TEXTURE_MAG_FILTER,
        						GLES10.GL_LINEAR);
        
        // Clamp to edge is the only option
        GLES10.glTexParameterx(	GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        						GLES10.GL_TEXTURE_WRAP_S,
        						GLES10.GL_CLAMP_TO_EDGE);
        GLES10.glTexParameterx(	GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
        						GLES10.GL_TEXTURE_WRAP_T,
        						GLES10.GL_CLAMP_TO_EDGE);
	
        // redirect camera preview to this SurfaceTexture.
        Size s;
		try 
		{
			s = mCameraManager.getPreviewSize();
			int w=s.width, h=s.height;	
			mCameraSurfaceTexture.setDefaultBufferSize(w, h);
	 		mCameraManager.setPreviewSurface(mCameraSurfaceTexture);
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		//create camera preview window
		mCameraPreviewSnapshot = new Snapshot3D();
		mCameraPreviewSnapshot.setTextureId(mCameraTextureId);
		
		 // Update camera view matrix
		/*
		mCameraPreviewSnapshot
		mCameraPreviewSnapshot.mModelMatrix = mCameraQuat.getMatrix();

        Matrix.invertM(mCameraBillboard.mModelMatrix, 0, mCameraBillboard.mModelMatrix, 0);
        Matrix.translateM(mCameraBillboard.mModelMatrix, 0, 0.0f, 0.0f, -DISTANCE);
        Matrix.rotateM(mCameraBillboard.mModelMatrix, 0, -90, 0, 0, 1);
		 */
		
		
		//TODO
		//mViewFinder = new Snapshot3D();
		mCameraPreviewSnapshot.draw();
		
		
	}
}
