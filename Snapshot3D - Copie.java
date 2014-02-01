package fr.ensicaen.panandroid.capture;

import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;


/**
 * Stores the information about each snapshot displayed in the sphere.
 * A snapshot3D is a snapshot with a modelMatrix, to be drawable in a 3d opengl context.
 */
public class Snapshot3D
{
	private static final String TAG = Snapshot3D.class.getSimpleName();
	
	/* ********
	 * 	ATTRIBUTES
	 * *******/
	
	/** Model matrix of the snapshot **/
	private float[]mModelMatrix;
	
	/** texture id of the snapshot **/
	private int mTextureData;
	
	/** bitmap data **/
	private Bitmap mBitmapToLoad;
	
	private boolean mVisible;
	
	/* **********
	 * 	CONSTRUCTOR
	 * *********/
	
	public Snapshot3D()
	{	
		mVisible = false;
	}
	
	
	/* *********
	 * ACCESSORS
	 * *********/
	
	
	public void setVisible(boolean  visible)
	{
		mVisible = visible;
	}
	
	public boolean isVisible()
	{
		return mVisible;
	}
	
	public void setTexture(Bitmap tex)
	{
		mBitmapToLoad = tex;
	}

    public void setTextureId(int id)
	{
		mTextureData = id;
	}
	
    /* **********
     * PRIVATE METHODS.
     * *********/
	
    
    /**
     * Load bitmap texture into the openGL texture ID
     */
    private void loadTexture()
    {
        // Load the snapshot bitmap as a texture to bind to our GLES20 program
        int texture[] = new int[1];

        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);

        GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLUtils.texImage2D(GLES10.GL_TEXTURE_2D, 0, mBitmapToLoad, 0);
        //tex.recycle();

        if(texture[0] == 0)
         {
            Log.e(TAG, "Unable to attribute texture to quad");
        }

        mTextureData = texture[0];
        mBitmapToLoad = null;
    }
    
    
	/*
        private boolean mIsFourToThree;
        private int mMode;
        private boolean mIsVisible = true;
        private float mAlpha = 1.0f;
        private float mAutoAlphaX;
        private float mAutoAlphaY;

        public Snapshot() {
            mIsFourToThree = true;
            mMode = SNAPSHOT;
        }

        public Snapshot(boolean isFourToThree) {
            mIsFourToThree = isFourToThree;
            mMode = SNAPSHOT;
        }

        public void setVisible(boolean visible) {
            mIsVisible = visible;
        }

        /**
         * Sets whether to use the CAMERA shaders or the SNAPSHOT shaders
         * @param mode CAMERA or SNAPSHOT
         */
	/*
        public void setMode(int mode) {
            mMode = mode;
        }

        public void setTexture(Bitmap tex) {
            mBitmapToLoad = tex;
        }

        public void setTextureId(int id) {
            mTextureData = id;
        }

        public void setAlpha(float alpha) {
            mAlpha = alpha;
        }

        public void setAutoAlphaAngle(float x, float y) {
            mAutoAlphaX = x;
            mAutoAlphaY = y;
        }

        public float getAutoAlphaX() {
            return mAutoAlphaX;
        }

        public float getAutoAlphaY() {
            return mAutoAlphaY;
        }

       

        */
    }


  