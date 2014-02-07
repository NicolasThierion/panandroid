package fr.ensicaen.panandroid.capture;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.SurfaceTexture;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.insideview.InsideRenderer;
import fr.ensicaen.panandroid.meshs.Cube;
import fr.ensicaen.panandroid.meshs.TexturedPlane;
import fr.ensicaen.panandroid.tools.BitmapDecoder;

/**
 * CaptureRenderer is basically an Inside3dRenderer, with a cube as preffered surrounding mesh.
 * The surrounding mesh is drawn as a "skybox".
 * The CaptureRenderer use a CameraManager to draw camera preview in foreground. 
 * By default, the renderer starts the cameraManager, and route the preview to a TexturedPlane.
 * @author Nicolas
 * @bug : camera stops when screen rotates.
 */
public class CaptureRenderer extends InsideRenderer
{
    public final static String TAG = CaptureRenderer.class.getSimpleName();

    /* ********
	 * CONSTANTS PARAMETERS
	 * ********/
    
    /** Size & distance of the snapshots **/
	private static final float SNAPSHOTS_SIZE = 65.5f;
	private static final float SNAPSHOTS_DISTANCE = 135.0f;

	/** Size & distance of the camera preview **/
	private static final float CAMERA_SIZE = 10.0f;
	private static final float CAMERA_DISTANCE = 55.0f;
	
	/** Size & distance of the markers **/
	private static final float MARKERS_SIZE = 1.0f;
	private static final float MARKERS_DISTANCE = 45.0f;
		
	/** Ratio of snapshot surfaces when in portrait/landscape mode **/
	
	private static final float CAMERA_RATIO34 = 3.0f/4.0f;	//portait
	private static final float CAMERA_RATIO43 = 4.0f/3.0f;	//landscape
	
	
	/** angle interval between dots **/
	private static final float PITCH_STEP = 360.0f/12.0f;;
	private static final float YAW_STEP = 360.0f/12.0f;
	
	/** default textures **/
	private static final int MARKER_RESSOURCE_ID = R.drawable.ic_picsphere_marker;

	private static final boolean USE_SCREEN_RATIO = false;
	
	/* ********
	 * ATTRIBUTES
	 * ********/
	
	/** Camera manager in charge of the capture **/
	private final CameraManager mCamManager;
	
	//TODO : implement
	/** list of snapshot already taken **/
	private List<Snapshot3D> mSnapshots;
	
	//TODO : implement
	/** list of dots **/
	private List<Snapshot3D> mDots;
	   
	/** surrounding skybox, given to parent Inside3dRenderer **/
	private Cube mSkybox;
	
	/** current context of the application **/
	private Context mContext;
	
	/** surface texture where is the camera preview is redirected **/
	private SurfaceTexture mCameraSurfaceTex;
	
	/** ... and associated openGL texture ID **/
	private int mCameraTextureId;
	
	/** 3d plane holding this texture **/
	private TexturedPlane mCameraSurface;
	
	//TODO : implement
	/** current ratio of TexturedPlanes, determined by screen orientation **/
	private float mCameraRatio;
	
	//TODO : implement
	private TexturedPlane mViewfinderBillboard;
	
	
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


	//TODO : what for?
	//private ReentrantLock mListBusy;

	
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
		mCamManager = cameraManager;
		mContext = context;
		mCameraRatio = 0;
		
	    Matrix.setIdentityM(mViewMatrix, 0);
	
		//create dots and snapshot lists
		mSnapshots = new ArrayList<Snapshot3D>();
		mDots = new ArrayList<Snapshot3D>();
		
		for(float pitch = 0; pitch < 360; pitch+=PITCH_STEP)
		{
			for(float yaw = 0; yaw < 360; yaw+=YAW_STEP)
			{
				mDots.add(createDot(pitch, yaw));
			}
		}
		

		
		//TODO : trash this?
		//mListBusy = new ReentrantLock();
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
		mCamManager.reOpen();
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
	
		//TODO : draw snapshots
	    /*
	    mListBusy.lock();
	    for (TexturedPlane snap : mSnapshots) {
	        snap.draw(gl, mViewMatrix);
	    }
	    mListBusy.unlock();
		*/
		
		if(mUseMarkers)
		{
		    for (TexturedPlane dot : mDots)
		    {
		        /*
		    	// Set alpha based on camera distance to the point
		        float dX = dot.getAutoAlphaX() - (rx + 180.0f);
		        float dY = dot.getAutoAlphaY() - ry;
		        dX = (dX + 180.0f) % 360.0f - 180.0f;
		        
		        dot.setAlpha(1.0f - Math.abs(dX)/180.0f * 8.0f);
				*/
		        dot.draw(gl, super.getRotationMatrix());
		    }
		}
	
		//TODO : 
	    //mViewfinderBillboard.draw(gl, mViewMatrix);*/
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
		mCamManager.setPreviewSurface(mCameraSurfaceTex);
		
		
		
		//TODO
		//Setup viewfinder	
		//mViewfinderBillboard = new TexturedPlane(2.0f);
		//mViewfinderBillboard.setTexture(BitmapFactory.decodeResource(mContext.getResources(),
		//        R.drawable.ic_picsphere_viewfinder));
	}
	
	private void reinitCameraSurface() throws IOException
	{
		//for an unknown reason, the camera preview is not in correct direction by default. Need to rotate it
		final int screenRotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();	
		float rotation = 270;
		switch (screenRotation)
		{
			case Surface.ROTATION_0:
				rotation += 0.0f;
				break;
			case Surface.ROTATION_90:
				rotation += 90.0f;
				break;
			case Surface.ROTATION_180:
				rotation += 180.0f;
				break;
			default:
				rotation += 270.0f;
				break;
		};
		
		rotation%=360;

		
		//create a new TexturedPlane, that holds the camera texture.
		mCameraSurface = new TexturedPlane(mCameraSize , mCameraRatio );
		mCameraSurface.setTexture(mCameraTextureId);
		
		//for unknown reason, the preview is not in correct orientation
		mCameraSurface.rotate(0, 0, rotation);
		mCameraSurface.translate(0, 0, CAMERA_DISTANCE);
		
	}
    
    
	private Snapshot3D createDot(float pitch, float yaw)
	{
		Snapshot3D dot = new Snapshot3D(mMarkersSize, pitch, yaw);
		
		dot.setTexture(BitmapDecoder.safeDecodeBitmap(mContext.getResources(), MARKER_RESSOURCE_ID));
		dot.translate(0.0f, 0.0f, MARKERS_DISTANCE);
		
		//TODO : implement
		//dot.setAutoAlphaAngle(pitch, yaw);
		
		return dot;
    }
	
	/* **********
     * TRASH METHODS
     * *********/
	
	/*
    public void setCameraOrientation(float rX, float rY, float rZ) {
        // Convert angles to degrees
        rX = (float) (rX * 180.0f/Math.PI);
        rY = (float) (rY * 180.0f/Math.PI);

        // Update quaternion from euler angles out of orientation and set it as view matrix
        mCameraQuat.fromEuler(rY, 0.0f, rX);
        
        //mViewMatrix = mCameraQuat.getConjugate().getMatrix();
    }
	*/
    
    /*
    public Vector3 getAngleAsVector() {
        float[] orientation = mSensorFusion.getFusedOrientation();

        // Convert angles to degrees
        float rX = (float) (orientation[0] * 180.0f/Math.PI);
        float rY = (float) (orientation[1] * 180.0f/Math.PI);
        float rZ = (float) (orientation[2] * 180.0f/Math.PI);

        return new Vector3(rX, rY, rZ);
    }*/

    /**
     * Helper function to compile a shader.
     *
     * @param shaderType The shader type.
     * @param shaderSource The shader source code.
     * @return An OpenGL handle to the shader.
     */
    /*
    public static int compileShader(final int shaderType, final String shaderSource) {
        int shaderHandle = GLES20.glCreateShader(shaderType);

        if (shaderHandle != 0) {
            // Pass in the shader source.
            GLES20.glShaderSource(shaderHandle, shaderSource);

            // Compile the shader.
            GLES20.glCompileShader(shaderHandle);

            // Get the compilation status.
            final int[] compileStatus = new int[1];
            GLES20.glGetShaderiv(shaderHandle, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

            // If the compilation failed, delete the shader.
            if (compileStatus[0] == 0) {
                Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shaderHandle));
                GLES20.glDeleteShader(shaderHandle);
                shaderHandle = 0;
            }
        }

        if (shaderHandle == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shaderHandle;
    }*/

    /**
     * Adds a snapshot to the sphere
     */
    /*
    public void addSnapshot(final Bitmap image) {
        TexturedPlane snap = new TexturedPlane();
        
        ///snap.setGlProgram(mProgram[SNAPSHOT], mPositionHandler[SNAPSHOT], mTexCoordHandler[SNAPSHOT], mMVPMatrixHandler[SNAPSHOT], mTextureHandler[SNAPSHOT], mAlphaHandler[SNAPSHOT]);
        
        snap.mModelMatrix = Arrays.copyOf(mViewMatrix, mViewMatrix.length);
        Assert.assertTrue(mViewMatrix!=null);
        
        Matrix.invertM(snap.mModelMatrix, 0, snap.mModelMatrix, 0);
        Matrix.translateM(snap.mModelMatrix, 0, 0.0f, 0.0f, -DISTANCE);
        Matrix.rotateM(snap.mModelMatrix, 0, -90, 0, 0, 1);

        snap.setTexture(image);

        mListBusy.lock();
        mSnapshots.add(snap);
        mListBusy.unlock();
    }
     */
    /**
     * Removes the last taken snapshot
     */
    /*
    public void removeLastPicture() {
        mListBusy.lock();
        if (mSnapshots.size() > 0) {
            mSnapshots.remove(mSnapshots.size()-1);
        }
        mListBusy.unlock();
    }
     */
    /**
     * Clear sphere's snapshots
     */
    /*
    public void clearSnapshots() {
        mListBusy.lock();
        mSnapshots.clear();
        mListBusy.unlock();
    }
    */
	
	/*
	 @Override
    public void onDrawFrame(GL10 gl) {
        
    	super.onDrawFrame(gl);
    	
    	mCameraSurfaceTex.updateTexImage();
    	
    	//GLES11.glTexParameteriv(GLES10.GL_TEXTURE_2D, GLES11Ext.GL_TEXTURE_CROP_RECT_OES, mCrop, 0);
		//GLES11Ext.glDrawTexiOES(mRect[0], mRect[1], 0, mRect[2], mRect[3]);

        /*
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_BLEND);

        // Update camera view matrix
        float[] orientation = mSensorFusion.getFusedOrientation();

        // Convert angles to degrees
        float rX = (float) (orientation[1] * 180.0f/Math.PI);
        float rY = (float) (orientation[0] * 180.0f/Math.PI);
        float rZ = (float) (orientation[2] * 180.0f/Math.PI);
         */
    	
    	/*
    	float rx = super.getPitch();
    	float ry = super.getYaw();
    	
        // Update quaternion from euler angles out of orientation
        mCameraQuat.fromEuler( rx, ry, 0.0f);
        mCameraQuat = mCameraQuat.getConjugate();
        mCameraQuat.normalise();
        */
       
    	//mViewMatrix = mCameraQuat.getMatrix();
		
    	
    	/*
        // Update camera billboard
        mCameraBillboard.mModelMatrix = mCameraQuat.getMatrix();
        Matrix.invertM(mCameraBillboard.mModelMatrix, 0, mCameraBillboard.mModelMatrix, 0);
        Matrix.translateM(mCameraBillboard.mModelMatrix, 0, 0.0f, 0.0f, -DISTANCE);
        Matrix.rotateM(mCameraBillboard.mModelMatrix, 0, -90, 0, 0, 1);

        mViewfinderBillboard.mModelMatrix = Arrays.copyOf(mCameraBillboard.mModelMatrix,
                mCameraBillboard.mModelMatrix.length);
        Matrix.scaleM(mViewfinderBillboard.mModelMatrix, 0, 0.25f, 0.25f, 0.25f);

        // Draw all teh things
        // First the skybox, then the marker dots, then the snapshots
        
        //mSkyBox.draw(gl);
		*/
	/*
  		gl.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);

        mCameraSurface.draw(gl, mViewMatrix);
  		gl.glDisable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
*/
        /*
        mListBusy.lock();
        for (TexturedPlane snap : mSnapshots) {
            snap.draw(gl, mViewMatrix);
        }
        mListBusy.unlock();

        for (TexturedPlane dot : mDots) {
            // Set alpha based on camera distance to the point
            float dX = dot.getAutoAlphaX() - (rx + 180.0f);
            float dY = dot.getAutoAlphaY() - ry;
            dX = (dX + 180.0f) % 360.0f - 180.0f;
            
            ///dot.setAlpha(1.0f - Math.abs(dX)/180.0f * 8.0f);

            dot.draw(gl, mViewMatrix);
        }

        mViewfinderBillboard.draw(gl, mViewMatrix);
    }
	 */
	
	/*
    @Override
    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        // Set the OpenGL viewport to the same size as the surface.
        GLES20.glViewport(0, 0, width, height);

        // We use here a field of view of 40, which is mostly fine for a camera app representation
        final float hfov = 90f;

        // Create a new perspective projection matrix. The height will stay the same
        // while the width will vary as per aspect ratio.
        final float ratio = 640.0f / 480.0f;
        final float near = 0.1f;
        final float far = 1500.0f;
        final float left = (float) Math.tan(hfov * Math.PI / 360.0f) * near;
        final float right = -left;
        final float bottom = ratio * right / 1.0f;
        final float top = ratio * left / 1.0f;

        Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
    }
	
     */	

	/*
	 private float[] matrixFromEuler(float rx, float ry, float rz, float tx, float ty, float tz) {
        Quaternion quat = new Quaternion();
        quat.fromEuler(rx,ry,rz);
        float[] matrix = quat.getMatrix();

        Matrix.translateM(matrix, 0, tx, ty, tz);

        return matrix;
    }


	 */
	
	/*
	 * @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        super.onSurfaceCreated(gl, config);

        
        /*
        // Simple GLSL vertex/fragment, as GLES2 doesn't have the classical fixed pipeline
        final String vertexShader =
                "uniform mat4 u_MVPMatrix; \n"
                        + "attribute vec4 a_Position;     \n"
                        + "attribute vec2 a_TexCoordinate;\n"
                        + "varying vec2 v_TexCoordinate;  \n"
                        + "void main()                    \n"
                        + "{                              \n"
                        + "   v_TexCoordinate = a_TexCoordinate;\n"
                        + "   gl_Position = u_MVPMatrix * a_Position;   \n"
                        + "}                              \n";

        final String fragmentShader =
                        "precision mediump float;       \n"
                        + "uniform sampler2D u_Texture;   \n"
                        + "varying vec2 v_TexCoordinate;  \n"
                        + "uniform float f_Alpha;\n"
                        + "void main()                    \n"
                        + "{                              \n"
                        + "   gl_FragColor = texture2D(u_Texture, v_TexCoordinate);\n"
                        + "   gl_FragColor.a = gl_FragColor.a * f_Alpha;"
                        + "}                              \n";

        // As the camera preview is stored in the OES external slot, we need a different shader
        final String camPreviewShader = "#extension GL_OES_EGL_image_external : require\n"
                + "precision mediump float;       \n"
                + "uniform samplerExternalOES u_Texture;   \n"
                + "varying vec2 v_TexCoordinate;  \n"
                + "uniform float f_Alpha;\n"
                + "void main()                    \n"
                + "{                              \n"
                + "   gl_FragColor = texture2D(u_Texture, v_TexCoordinate);\n"
                + "   gl_FragColor.a = gl_FragColor.a * f_Alpha;"
                + "}                              \n";


        mVertexShader[CAMERA] = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        mFragmentShader[CAMERA] = compileShader(GLES20.GL_FRAGMENT_SHADER, camPreviewShader);

        mVertexShader[SNAPSHOT] = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        mFragmentShader[SNAPSHOT] = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

        // create the program and bind the shader attributes
        for (int i = 0; i < 2; i++) {
            mProgram[i] = GLES20.glCreateProgram();
            GLES20.glAttachShader(mProgram[i], mFragmentShader[i]);
            GLES20.glAttachShader(mProgram[i], mVertexShader[i]);
            GLES20.glLinkProgram(mProgram[i]);

            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(mProgram[i], GLES20.GL_LINK_STATUS, linkStatus, 0);

            if (linkStatus[0] == 0) {
                throw new RuntimeException("Error linking shaders");
            }
            mPositionHandler[i]     = GLES20.glGetAttribLocation(mProgram[i], "a_Position");
            mTexCoordHandler[i]     = GLES20.glGetAttribLocation(mProgram[i], "a_TexCoordinate");
            mMVPMatrixHandler[i]    = GLES20.glGetUniformLocation(mProgram[i], "u_MVPMatrix");
            mTextureHandler[i]      = GLES20.glGetUniformLocation(mProgram[i], "u_Texture");
            mAlphaHandler[i]      = GLES20.glGetUniformLocation(mProgram[i], "f_Alpha");
        }
         */
        
        /*
        initCameraSurface();
    }
*/
	 
}


