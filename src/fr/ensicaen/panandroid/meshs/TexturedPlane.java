package fr.ensicaen.panandroid.meshs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.opengles.GL10;

import fr.ensicaen.panandroid.tools.BitmapDecoder;
import junit.framework.Assert;
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
	
	/** dummy texture to load when bitmap not yet loaded **/
	private static final Bitmap mDummyBitmapTexture = Bitmap.createBitmap(new int[]{Color.CYAN}, 1, 1, Config.RGB_565);

	/** if persistent texture should be loaded incrementally through mipmap to avoid system jam **/
	private static final boolean USE_MIPMAP_LOADING = true;

	/** delay between mipmap loadings **/
	private static final int MIPMAP_LOADING_DELAY = 1500;		//[ms]
	
	/* *********
	 * ATTRIBUTES
	 * ********/
	/** Master rotation axis around which one the mesh rotates first **/
	private Axis mAxis = Axis.VERTICAL; 
	
	/** model matrix of the mesh. Define rotation/transformation of the mesh applied in the view matrix **/
	public float[]mModelMatrix;
	
	/** if the mesh should be drawn **/
	private boolean mIsVisible = true;
	
	/** alpha (transparency) component**/
	private float mAlpha = 1.0f;
	
	/** openGL texture ID applied to this plane **/
	private int mImameTextureId;
	
	/** Optional bitmap texture of the plane **/
	private Bitmap mBitmapTexture;
	
	/** sample rate used to load the texture through setTexture(String resId) **/
	private int mSampleRate = 1;
	
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
	private boolean mHasToRecycle = false;
	private String mPersistentTexturePath = null;
	
	


    

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
		float ratioX=1, ratioY=1;
		if(ratio>1)
			ratioX = ratio;
		else
			ratioY=ratio;
		
		for(int i=0; i< mVertices.length; i+=3)
		{
			mVertices[i]*=sizeX*ratioX;
			mVertices[i+1]*=sizeX*ratioY;
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
	
		this.setTexture(mDummyBitmapTexture);
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
	 * @param tex - The new plane's texture path.
	 */
	public void setTexture(String imgPath)
	{    
		this.setTexture(imgPath, mSampleRate);
	}
	
	/**
	 * Give the plane a new texture.
	 * @param tex - The new plane's texture path.
	 */
	public void setTexture(final String imgPath, final int sampleRate)
	{	
		mPersistentTexturePath = imgPath;
		mSampleRate = sampleRate;
		mTextureToLoad = true;
		
	}
	
	public void setSampleRate(int sampleRate)
	{
		mSampleRate = sampleRate;
	}
	/**
	 * 
	 * @param bmp
	 */
	public void setTexture(Bitmap bmp)
	{
		mBitmapTexture = bmp;
		mTextureToLoad = true;  

	}

	/**
	 * Give the plane a new texture.
	 * @param textureId - openGL texture id from glGenTexture.
	 */
	public void setTexture(int textureId) 
	{
		mImameTextureId = textureId;
	}
	
	
	/**
	 * Set the axis around wich one the mesh rotate first.
	 * @param axis
	 */
	public void setAxis(Axis axis)
	{
		mAxis = axis;
	}
	
	/**
	 * Set the alpha component (transparency) of the texture.
	 * Only works with bitmap textures
	 */
	public void setAlpha(float alpha)
	{
		mAlpha = alpha;
	}
	
	



	

	public void rotate(float rx, float ry, float rz)
	{
		switch(mAxis)
		{
		//rotate yaw first
		case VERTICAL :
			Matrix.rotateM(mModelMatrix, 0, ry, 0, 1, 0);
			Matrix.rotateM(mModelMatrix, 0, rx, 1, 0, 0);
			break;
		default:
			Matrix.rotateM(mModelMatrix, 0, rx, 1, 0, 0);
			Matrix.rotateM(mModelMatrix, 0, ry, 0, 1, 0);
			
		}
		Matrix.rotateM(mModelMatrix, 0, rz, 0, 0, 1);
		
	}
	
	public void translate(float tx,float ty,float tz)
	{
        Matrix.translateM(mModelMatrix, 0, -tx, -ty, -tz);
		
	}
	

	public void recycleTexture()
	{
		if(mBitmapTexture!=null && mBitmapTexture!=mDummyBitmapTexture)
			mHasToRecycle = true;
	}
	

	/* *******
	 * OVERRIDES
	 * ******/
	

	@Override
	public void draw(GL10 gl, float[] modelViewMatrix)
	{
		//enter 2d texture mode
		gl.glEnable(GL10.GL_TEXTURE_2D);
		
		//enable depth test
		gl.glEnable(GL10.GL_DEPTH_TEST);
		gl.glDepthMask(true);
		
		//enable alpha
		gl.glAlphaFunc( GLES10.GL_GREATER, 0 );
		gl.glEnable( GLES10.GL_ALPHA_TEST );
		
		//apply additionnal transparency
		gl.glColor4f(1.0f,1.0f,1.0f,mAlpha);
		gl.glTexEnvf(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE);
		
		//enable blending
//		gl.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE_MINUS_SRC_ALPHA);
		gl.glBlendFunc(GL10.GL_SRC_ALPHA, GL10.GL_ONE_MINUS_SRC_ALPHA);
//		gl.glBlendFunc(GL10.GL_ONE, GL10.GL_ONE);
		gl.glEnable(GL10.GL_BLEND);
		
		
		if (!mIsVisible) return;
		
		if (mTextureToLoad)
		{
		    loadGLTexture(gl);
		}
		
		// bind the previously generated texture.
		gl.glBindTexture(GL10.GL_TEXTURE_2D, mImameTextureId);
		
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

	/**
	 * !!! Will recycle the given bitmap texture if any
	 */
	@Override
	public void loadGLTexture(GL10 gl) 
	{
		//texture already loaded => nothing to do
		if(!mTextureToLoad)
			return;
		
		// if mBitmapTexture, will try to load persistent texture.
		if(mBitmapTexture == null || mBitmapTexture == mDummyBitmapTexture )
		{
			if(this.mPersistentTexturePath != null)
			{
				//load texture from file on storage
				this.loadBitmapTexture(mPersistentTexturePath, mSampleRate);
			}
			else
			{
				if(mBitmapTexture == null)
					Log.e(TAG, "loadGLtexture with bitmap texture = null");
				
				this.setTexture(mDummyBitmapTexture);
			}
		}
		
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
		// Tidy up.
		
		if(mHasToRecycle)
		{
			mBitmapTexture.recycle();
			mBitmapTexture = mDummyBitmapTexture;
			mHasToRecycle=false;
		}
		
		mImameTextureId = texture[0];
		mTextureToLoad = false;
		
	}
	
	
	@Override
	public void unloadGLTexture(GL10 gl)
	{
		new Thread(new Runnable(){

			@Override
			public void run() 
			{
				int texture[] = new int[1];
				texture[0] = mImameTextureId;
				GLES10.glDeleteTextures(1, texture, 0);
				
				//if texture jpg has been given, will tryy to load it next time
				if(mPersistentTexturePath!=null)
					mTextureToLoad = true;
				
			}
			
		}).start();
		
	
	}
	
	/* *******
	 * PRIVATE FUNCTIONS
	 * ******/
	
	/**
	 * Set the given image as current texture, with given sample rate.
	 * @param imgPath
	 * @param sampleRate
	 */
	private void loadBitmapTexture(final String imgPath, final int sampleRate)
	{
		
		new Thread(new Runnable(){

			public void run()
			{
				int iSample = 32, iSampled;
				Bitmap oldTex;
				if(!USE_MIPMAP_LOADING)
					iSample = sampleRate;
				
				while(iSample >= sampleRate && mIsVisible)
				{
					Assert.assertTrue(iSample>=1);
					
					oldTex = mBitmapTexture;
					mBitmapTexture = BitmapDecoder.safeDecodeBitmap(imgPath, iSample);
					iSampled = BitmapDecoder.getSampleRate();
					mTextureToLoad = true; 
					
					if(oldTex!=mDummyBitmapTexture)
						oldTex.recycle();
					
					if(iSample != iSampled)
					{
						try 
						{
							Thread.sleep(3*MIPMAP_LOADING_DELAY);
						} 
						catch (InterruptedException e) 
						{
							e.printStackTrace();
						}
						mBitmapTexture = BitmapDecoder.safeDecodeBitmap(imgPath, iSample);
						if(iSample != iSampled)
						{
							return;
						}
					}
					
					try 
					{
						Thread.sleep(MIPMAP_LOADING_DELAY);
					} 
					catch (InterruptedException e) 
					{
						e.printStackTrace();
					}
				
					iSample/=2;
					
				}
				
				
			}
		}).start();
		
	}
	
	
    
}