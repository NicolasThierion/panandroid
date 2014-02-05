/*
 * Copyright (C) 2013 Guillaume Lesniak
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
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;










import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import junit.framework.Assert;
import fr.ensicaen.panandroid.R;
import fr.ensicaen.panandroid.meshs.Cube;
import fr.ensicaen.panandroid.meshs.TexturedPlane;
import fr.ensicaen.panandroid.renderer.InsideRenderer;
import fr.ensicaen.panandroid.sensor.SensorFusionManager;

/**
 * Manages the 3D rendering of the sphere capture mode, using gyroscope to
 * orientate the camera and displays the preview at the center.
 *
 * TODO: Fallback for non-GLES2 devices?
 */
public class CaptureRenderer extends InsideRenderer
{
    public final static String TAG = CaptureRenderer.class.getSimpleName();

    
    /* ********
     * ATTRIBUTES
     * ********/
    
    /** Camera manager in charge of the capture **/
    private final CameraManager mCamManager;

    /** list of snapshot already taken **/
    private List<TexturedPlane> mSnapshots;
    
    /** list of dots **/
    private List<TexturedPlane> mDots;
    
    //TODO : what for?
    private ReentrantLock mListBusy;
    
    //TODO : export
    private SensorFusionManager mSensorFusion;
    
    //TODO : refactor to matrix.
    private Quaternion mCameraQuat;
    private Cube mSkyBox;
    
    private float[] mViewMatrix = new float[16];
    private float[] mProjectionMatrix = new float[16];

    
    private final static float SNAPSHOT_SCALE = 65.5f;
    private final static float RATIO = 4.0f/3.0f;
    private final static float DISTANCE = 135.0f;

   
    private final static int CAMERA = 0;
    private final static int SNAPSHOT = 1;

    private int[] mProgram = new int[2];
    private int[] mVertexShader = new int[2];
    private int[] mFragmentShader = new int[2];
    private int[] mPositionHandler = new int[2];
    private int[] mTexCoordHandler = new int[2];
    private int[] mTextureHandler = new int[2];
    private int[] mAlphaHandler = new int[2];
    private int[] mMVPMatrixHandler = new int[2];

    private SurfaceTexture mCameraSurfaceTex;
    private int mCameraTextureId;
    private TexturedPlane mCameraBillboard;
    private TexturedPlane mViewfinderBillboard;
    private Context mContext;
    private Quaternion mTempQuaternion;
    private float[] mMVPMatrix = new float[16];

    

    /**
     * Stores the information about each snapshot displayed in the sphere
     */
    

    /**
     * Initialize the model data.
     */
    public CaptureRenderer(Context context, CameraManager cameraManager)
    {
    	//based on InsideRenderer. We are inside a skybox.
    	super(context);
    	
    	//build the skyBox
    	Bitmap texFront = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_fr);
        Bitmap texBack = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_bk);
        Bitmap texLeft = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_lt);
        Bitmap texRight = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_rt);
        Bitmap texUp = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_up);
        Bitmap texDown = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_dn);
        
        //Add the shyBox to the parent renderer.
        mSkyBox = new Cube(SNAPSHOT_SCALE, texFront, texBack, texLeft, texRight, texUp, texDown);
    	super.setSurroundingMesh(mSkyBox);
    	
    	

        mCamManager = cameraManager;
        
        /*
        mSnapshots = new ArrayList<TexturedPlane>();
        mDots = new ArrayList<TexturedPlane>();
        mListBusy = new ReentrantLock();
        mSensorFusion = new SensorFusionManager(context);
        mCameraQuat = new Quaternion();
        mContext = context;
        mTempQuaternion = new Quaternion();

        // Position the dots every 40°
        for (int x = 0; x < 360; x += 360/12) {
            for (int y = 0; y < 360; y += 360/12) {
                createDot(x, y);
            }
        }
        */

    }
/*
    private void createDot(float rx, float ry) {
        TexturedPlane dot = new TexturedPlane(false);
        dot.setGlProgram(mProgram[SNAPSHOT],
        		mPositionHandler[SNAPSHOT],
        		mTexCoordHandler[SNAPSHOT],
        		mMVPMatrixHandler[SNAPSHOT],
        		mTextureHandler[SNAPSHOT],
        		mAlphaHandler[SNAPSHOT]);
        dot.setTexture(BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.ic_picsphere_marker));
        dot.mModelMatrix = matrixFromEuler(rx, 0, ry, 0, 0, 100);
        Assert.assertTrue(mViewMatrix!=null);

        dot.setViewMatrix(mViewMatrix);
        
        Matrix.scaleM(dot.mModelMatrix, 0, 0.1f, 0.1f, 0.1f);
        dot.setAutoAlphaAngle(rx, ry);
        mDots.add(dot);
    }

    private float[] matrixFromEuler(float rx, float ry, float rz, float tx, float ty, float tz) {
        Quaternion quat = new Quaternion();
        quat.fromEuler(rx,ry,rz);
        float[] matrix = quat.getMatrix();

        Matrix.translateM(matrix, 0, tx, ty, tz);

        return matrix;
    }
*/

    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config)
    {
        // Set the background clear color to gray.
        GLES20.glClearColor(0.5f, 0.5f, 0.5f, 0.5f);

        

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

        Bitmap texFront = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_fr);
        Bitmap texBack = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_bk);
        Bitmap texLeft = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_lt);
        Bitmap texRight = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_rt);
        Bitmap texUp = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_up);
        Bitmap texDown = BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.picsphere_sky_dn);
        
        mSkyBox = new Cube(SNAPSHOT_SCALE, texFront, texBack, texLeft, texRight, texUp, texDown);

        initCameraBillboard();
    }

    private void initCameraBillboard() {
        int texture[] = new int[1];

        GLES20.glGenTextures(1, texture, 0);
        mCameraTextureId = texture[0];

        if (mCameraTextureId == 0) {
            throw new RuntimeException("CAMERA TEXTURE ID == 0");
        }

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureId);
        // Can't do mipmapping with camera source
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        // Clamp to edge is the only option
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);

        mCameraSurfaceTex = new SurfaceTexture(mCameraTextureId);
        mCameraSurfaceTex.setDefaultBufferSize(640, 480);

        mCameraBillboard = new TexturedPlane();
        /*
        mCameraBillboard.setGlProgram(mProgram[CAMERA], mPositionHandler[CAMERA],
        							mTexCoordHandler[CAMERA], mMVPMatrixHandler[CAMERA],
        							mTextureHandler[CAMERA], mAlphaHandler[CAMERA]);
        */
        Assert.assertTrue(mViewMatrix!=null);
        mCameraBillboard.setTextureId(mCameraTextureId);
        try {
			mCamManager.setPreviewSurface(mCameraSurfaceTex);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        // Setup viewfinder billboard
        //TODO
        //mViewfinderBillboard = new TexturedPlane( false);
        
        /*
        mViewfinderBillboard.setGlProgram(mProgram[SNAPSHOT], mPositionHandler[SNAPSHOT],
				mTexCoordHandler[SNAPSHOT], mMVPMatrixHandler[SNAPSHOT],
				mTextureHandler[SNAPSHOT], mAlphaHandler[SNAPSHOT]);
        */
        mViewfinderBillboard.setTexture(BitmapFactory.decodeResource(mContext.getResources(),
                R.drawable.ic_picsphere_viewfinder));
    }

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

    @Override
    public void onDrawFrame(GL10 gl) {
        mCameraSurfaceTex.updateTexImage();

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable(GLES20.GL_BLEND);

        // Update camera view matrix
        float[] orientation = mSensorFusion.getFusedOrientation();

        // Convert angles to degrees
        float rX = (float) (orientation[1] * 180.0f/Math.PI);
        float rY = (float) (orientation[0] * 180.0f/Math.PI);
        float rZ = (float) (orientation[2] * 180.0f/Math.PI);

        // Update quaternion from euler angles out of orientation
        mCameraQuat.fromEuler( rX, 180.0f-rZ, rY);
        mCameraQuat = mCameraQuat.getConjugate();
        mCameraQuat.normalise();
        mViewMatrix = mCameraQuat.getMatrix();

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
        
        //TODO : re-enable
        //mSkyBox.draw(gl);

        mCameraBillboard.draw(gl, mViewMatrix);

        mListBusy.lock();
        for (TexturedPlane snap : mSnapshots) {
            snap.draw(gl, mViewMatrix);
        }
        mListBusy.unlock();

        for (TexturedPlane dot : mDots) {
            // Set alpha based on camera distance to the point
            float dX = dot.getAutoAlphaX() - (rX + 180.0f);
            float dY = dot.getAutoAlphaY() - rY;
            dX = (dX + 180.0f) % 360.0f - 180.0f;
            
            ///dot.setAlpha(1.0f - Math.abs(dX)/180.0f * 8.0f);

            dot.draw(gl, mViewMatrix);
        }

        mViewfinderBillboard.draw(gl, mViewMatrix);
    }

    public void setCamPreviewVisible(boolean visible) {
        mCameraBillboard.setVisible(visible);
    }

    public void onPause() {
        if (mSensorFusion != null) {
            mSensorFusion.onPauseOrStop();
        }
    }

    public void onResume() {
        if (mSensorFusion != null) {
            mSensorFusion.onResume();
        }
    }

    public void setCameraOrientation(float rX, float rY, float rZ) {
        // Convert angles to degrees
        rX = (float) (rX * 180.0f/Math.PI);
        rY = (float) (rY * 180.0f/Math.PI);

        // Update quaternion from euler angles out of orientation and set it as view matrix
        mCameraQuat.fromEuler(rY, 0.0f, rX);
        mViewMatrix = mCameraQuat.getConjugate().getMatrix();
    }

    public Vector3 getAngleAsVector() {
        float[] orientation = mSensorFusion.getFusedOrientation();

        // Convert angles to degrees
        float rX = (float) (orientation[0] * 180.0f/Math.PI);
        float rY = (float) (orientation[1] * 180.0f/Math.PI);
        float rZ = (float) (orientation[2] * 180.0f/Math.PI);

        return new Vector3(rX, rY, rZ);
    }

    /**
     * Helper function to compile a shader.
     *
     * @param shaderType The shader type.
     * @param shaderSource The shader source code.
     * @return An OpenGL handle to the shader.
     */
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
    }

    /**
     * Adds a snapshot to the sphere
     */
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

    /**
     * Removes the last taken snapshot
     */
    public void removeLastPicture() {
        mListBusy.lock();
        if (mSnapshots.size() > 0) {
            mSnapshots.remove(mSnapshots.size()-1);
        }
        mListBusy.unlock();
    }

    /**
     * Clear sphere's snapshots
     */
    public void clearSnapshots() {
        mListBusy.lock();
        mSnapshots.clear();
        mListBusy.unlock();
    }
}
