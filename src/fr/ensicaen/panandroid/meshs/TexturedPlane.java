package fr.ensicaen.panandroid.meshs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Bitmap.Config;
import android.opengl.GLES10;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;


/**
 * A TexturedPlane is a Mesh composed of 4 vertices. A bitmap texture or an openGL textureID can be applied to it. 
 * @author Nicolas
 *
 */
public class TexturedPlane extends Mesh
{
	
	private static final String TAG = TexturedPlane.class.getSimpleName();

	/* *********
	 * DEFAULT PARAMETERS
	 * ********/
	/** size by default of the plane **/
	private static final float DEFAULT_SCALE = 1.0f;
	
	/** ratio by default of the plane (ie : WIDTH/HEIGHT) **/
	private static final float DEFAULT_RATIO = 1.0f;
	
	/** standard vertices coord array... **/
	private static final float VERTEX_ARRAY_PATTERN[] =  		// {x, y, z}
						{
							-DEFAULT_RATIO,  -1.0f, 0,
							-DEFAULT_RATIO, 1.0f, 0,
							DEFAULT_RATIO, 1.0f, 0,
							DEFAULT_RATIO, -1.0f, 0
						};
	
	/** ...and associated texture coordinates **/ 
	private static final float mTexCoordData[] =
        {
                0.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f,
                1.0f, 1.0f
        };
	
	/* *********
	 * ATTRIBUTES
	 * ********/
	
	
	/** if the mesh should be drawn **/
	private boolean mIsVisible = true;
	
	/** openGL texture ID applied to this plane **/
	private int imTextureId;
	
	/** Optional bitmap texture of the plane **/
	private Bitmap mBitmapTexture;
	
	/** buffer of vertices that will be drawn **/
	private FloatBuffer mVertexBuffer;
	
	/** texture coordinates buffer **/
	private FloatBuffer mTexCoordBuffer;
	
	/** modelView matrix, computed from this model matrix and given view matrix on drawing time **/
	private float[] mMVMatrix = new float[16];
	
	/** effective vertices array **/
	private float mVertices[]; 			// {x, y, z}
	
	/** if there is a new Bitmap texture to load **/
	private boolean mTextureToLoad = false;

	//TODO : implement or remove
	
	/*
	private float mAlpha = 1.0f;
	private float mAutoAlphaX;
	private float mAutoAlphaY;
	private int mPositionHandler ;
	private int mTexCoordHandler ;
	private FloatBuffer m43VertexBuffer;
	private float[] mViewMatrix;
	private float[] mProjectionMatrix = new float[16];
	

	*/
	

    

	public enum Axis{
		VERTICAL, HORIZONTAL
	}
	

	/* *********
	 * CONSTRUCTORS
	 * ********/

	/**
	 * Construct a new plane of size 1*1 (ratio = 1).
	 */
	public TexturedPlane()
	{
		this(DEFAULT_SCALE, DEFAULT_RATIO);
	}
	
	/**
	 * Construct a new plane of size "size*size"
	 * @param size - size of the plane.
	 */
	public TexturedPlane(float size)
	{
		this(size, DEFAULT_RATIO);
	}
	
	/**
	 * Construct a new pane with given size and ratio
	 * @param sizeX - width of the plane.
	 * @param ratio - height of the plane.
	 */
	public TexturedPlane(float sizeX, float ratio)
	{
		mVertices = Arrays.copyOf(VERTEX_ARRAY_PATTERN, VERTEX_ARRAY_PATTERN.length);
		
		//creates vertice data array from given size and ratio
		for(int i=0; i< mVertices.length; i+=3)
		{
			mVertices[i]*=sizeX;
			mVertices[i+1]*=sizeX*ratio;
		}
		
		// Initialize plane vertices into a vertex buffer 
		ByteBuffer byteBuffer = ByteBuffer.allocateDirect(mVertices.length * Float.SIZE);
		byteBuffer.order(ByteOrder.nativeOrder());
		mVertexBuffer = byteBuffer.asFloatBuffer();
		mVertexBuffer.put(mVertices);
		mVertexBuffer.position(0);
		
		//initialize texcoords data
		byteBuffer = ByteBuffer.allocateDirect(mTexCoordData.length * Float.SIZE);
		byteBuffer.order(ByteOrder.nativeOrder());
		mTexCoordBuffer = byteBuffer.asFloatBuffer();
		mTexCoordBuffer.put(mTexCoordData);
		mTexCoordBuffer.position(0);
	
		this.setTexture(Bitmap.createBitmap(new int[]{Color.CYAN}, 1, 1, Config.RGB_565));
		mModelMatrix = new float[16];
		Matrix.setIdentityM(mModelMatrix, 0);
	}
	
	
	/* *********
	 * ACCESSORS
	 * ********/
	 
	/**
	 * Set the visibility property of the mesh.
	 * @param visible
	 */
	public void setVisible(boolean visible)
	{
	    mIsVisible = visible;
	}
	
	/**
	 * Give the plane a new texture.
	 * @param tex - The new plane texture.
	 */
	public void setTexture(Bitmap tex)
	{    
		mBitmapTexture = tex;
		mTextureToLoad = true;   
	}
	
	/**
	 * Give the plane a new texture.
	 * @param textureId - openGL texture id from glGenTexture.
	 */
	public void setTexture(int textureId) 
	{
		imTextureId = textureId;
		mTextureToLoad = true;
	}

  
	@Override
	public void draw(GL10 gl, float[] modelViewMatrix)
	{
		//enter 2d texture mode
		gl.glEnable(GL10.GL_TEXTURE_2D);
		
		//enable alpha
		gl.glAlphaFunc( GLES10.GL_GREATER, 0 );
		gl.glEnable( GLES10.GL_ALPHA_TEST );
		
		//enanle blending
		gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
		gl.glEnable(GL10.GL_BLEND);
		
		if (!mIsVisible) return;
		
		if (mTextureToLoad) {
		    loadGLTexture(gl);
		}
		
		// bind the previously generated texture.
		gl.glBindTexture(GL10.GL_TEXTURE_2D, imTextureId);
		
		// Point to our buffers.
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
		gl.glVertexPointer(3, GL10.GL_FLOAT, 0, this.mVertexBuffer);  
		gl.glTexCoordPointer(2,GLES10.GL_FLOAT, 0, mTexCoordBuffer);

		// This multiplies the view matrix by the model matrix, and stores the
		// result in the MVP matrix (which currently contains model * view).
		Matrix.multiplyMM(mMVMatrix, 0, modelViewMatrix, 0, mModelMatrix, 0);
		gl.glLoadMatrixf(mMVMatrix, 0);
		
		gl.glDrawArrays(GLES10.GL_TRIANGLE_FAN, 0, 4);
		gl.glLoadMatrixf(modelViewMatrix, 0);
			
		//leave
		gl.glDisable(GL10.GL_TEXTURE_2D);
		gl.glDisable( GLES10.GL_ALPHA_TEST );
		gl.glDisable(GL10.GL_BLEND);
		gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
		gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
	
	
	
	}

	@Override
	public void loadGLTexture(GL10 gl) 
	{
		// Load the snapshot bitmap as a texture to bind to our gl program
		int texture[] = new int[1];
		
		GLES10.glGenTextures(1, texture, 0);
		GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, texture[0]);
		
		GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MIN_FILTER, GLES10.GL_NEAREST);
		GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MAG_FILTER, GLES10.GL_NEAREST);
		
		//GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_WRAP_S, GLES10.GL_CLAMP_TO_EDGE);
		//GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_WRAP_T, GLES10.GL_CLAMP_TO_EDGE);
		
		GLUtils.texImage2D(GLES10.GL_TEXTURE_2D, 0, mBitmapTexture, 0);
		
		if(texture[0] == 0)
		{
			Log.e(TAG, "Unable to attribute texture to quad");
		}
		
		imTextureId = texture[0];
		mTextureToLoad = false;
		
	}
	

	
	//TODO : implement or remove
	/*
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