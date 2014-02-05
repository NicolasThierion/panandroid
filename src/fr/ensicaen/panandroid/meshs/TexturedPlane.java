package fr.ensicaen.panandroid.meshs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.opengles.GL10;

import fr.ensicaen.panandroid.capture.Quaternion;
import junit.framework.Assert;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Bitmap.Config;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

public class TexturedPlane extends Mesh
{
	
	private static final String TAG = TexturedPlane.class.getSimpleName();

	
	private static final float DEFAULT_SCALE = 1.0f;
	
	private static final float DEFAULT_RATIO = 1.0f;
	
	private static final float VERTEX_ARRAY_PATTERN[] =  
						{
					        -DEFAULT_RATIO,  -1.0f, 0,
					        -DEFAULT_RATIO, 1.0f, 0,
					        DEFAULT_RATIO, 1.0f, 0,
					        DEFAULT_RATIO, -1.0f, 0
						};
	
	/* *********
	 * ATTRIBUTES
	 * ********/
    public float[]mModelMatrix;
    private int imTextureId;
    private Bitmap mBitmapTexture;
    
    //private boolean mIsFourToThree;
    
    private boolean mIsVisible = true;
    
    ///private float mAlpha = 1.0f;
    
    private float mAutoAlphaX;
    private float mAutoAlphaY;
    
    
    //private int mPositionHandler ;
    //private int mTexCoordHandler ;
    
    private FloatBuffer mVertexBuffer;
    
    //private FloatBuffer m43VertexBuffer;
    
    private FloatBuffer mTexCoordBuffer;
    
    //private float[] mViewMatrix;
    //private float[] mProjectionMatrix = new float[16];
    
    
    private float[] mMVPMatrix = new float[16];
    
    
    
    
 // x, y, z
    private float mVertices[];

    /*
    private final float m43VertexData[] =
            {
                    -SNAPSHOT_SCALE *RATIO,  -SNAPSHOT_SCALE,
                    -SNAPSHOT_SCALE *RATIO, SNAPSHOT_SCALE,
                    SNAPSHOT_SCALE *RATIO, SNAPSHOT_SCALE,
                    SNAPSHOT_SCALE *RATIO, -SNAPSHOT_SCALE
            };
*/
    // u,v
    private static final float mTexCoordData[] =
            {
                    0.0f, 1.0f,
                    0.0f, 0.0f,
                    1.0f, 0.0f,
                    1.0f, 1.0f
            };


    
    private int mProgram;


	private int mMVPMatrixHandler;


	private int mTextureHandler;


	private int mAlphaHandler;
    
    

	/* *********
	 * CONSTRUCTORS
	 * ********/

    public TexturedPlane()
    {
    	this(DEFAULT_SCALE, DEFAULT_RATIO);
    }

    public TexturedPlane(float sizeX)
    {
    	this(sizeX, DEFAULT_RATIO);
    }

    
    public TexturedPlane(float sizeX, float ratio)
    {
       // mIsFourToThree = isFourToThree;    
    	
    	mVertices = Arrays.copyOf(VERTEX_ARRAY_PATTERN, VERTEX_ARRAY_PATTERN.length);
    	
    	for(int i=0; i< mVertices.length; i+=3)
    	{
    		mVertices[i]*=sizeX;
    		mVertices[i+1]*=sizeX*ratio;
    	}
    	
    	
    	
		// Initialize plane vertex data 
		ByteBuffer byteBuffer = ByteBuffer.allocateDirect(mVertices.length * Float.SIZE);
		byteBuffer.order(ByteOrder.nativeOrder());
		
		//ByteBuffer bb_43data = ByteBuffer.allocateDirect(m43VertexData.length * 4);
		//bb_43data.order(ByteOrder.nativeOrder());
		
		mVertexBuffer = byteBuffer.asFloatBuffer();
		mVertexBuffer.put(mVertices);
		mVertexBuffer.position(0);
		
		//initialize texcoords data
		byteBuffer = ByteBuffer.allocateDirect(mTexCoordData.length * Float.SIZE);
		byteBuffer.order(ByteOrder.nativeOrder());
		mTexCoordBuffer = byteBuffer.asFloatBuffer();
		mTexCoordBuffer.put(mTexCoordData);
		mTexCoordBuffer.position(0);
		
		
		///m43VertexBuffer = bb_43data.asFloatBuffer();
		///m43VertexBuffer.put(m43VertexData);
		///m43VertexBuffer.position(0);
		
		this.setTexture(Bitmap.createBitmap(new int[]{Color.CYAN}, 1, 1, Config.RGB_565));
		mModelMatrix = new float[16];
		Matrix.setIdentityM(mModelMatrix, 0);

    }

    

	/* *********
	 * ACCESSORS
	 * ********/
    /*
    public void setGlProgram(int program,
    						int positionHandler, 
    						int texCoordHandler, 
    						int MVPMatrixHandler, 
    						int textureHandler, 
    						int alphaHandler)
    {
    	mProgram = program;
		mPositionHandler= positionHandler;
		mTexCoordHandler = texCoordHandler;
		mMVPMatrixHandler = MVPMatrixHandler;
		mTextureHandler = textureHandler;
		mAlphaHandler = alphaHandler;
		
		
    }
    */
   
    
    public void setVisible(boolean visible) {
        mIsVisible = visible;
    }


    public void setTexture(Bitmap tex) {
        mBitmapTexture = tex;
    }

    public void setTextureId(int id) {
        imTextureId = id;
    }

    /*
    public void setAlpha(float alpha) {
        mAlpha = alpha;
    }
	*/
    
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

  

    
    
    
    
    
    public void draw(GL10 gl, float[] modelViewMatrix)
    {
    	
    	
        if (!mIsVisible) return;

        if (mBitmapTexture != null) {
            loadGLTexture(gl);
        }

        /*
        GLES20.glUseProgram(mProgram);
        if (mIsFourToThree) {
            m43VertexBuffer.position(0);
        } else {
            mVertexBuffer.position(0);
        }
        mTexCoordBuffer.position(0);
		*/
        
        // bind the previously generated texture.
        gl.glBindTexture(GL10.GL_TEXTURE_2D, imTextureId);
        
        // Point to our buffers.
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        
     // Set the face rotation, clockwise in this case.
        //gl.glFrontFace(GL10.GL_CCW);
        
        /*
        GLES20.glEnableVertexAttribArray(mTexCoordHandler);
        GLES20.glEnableVertexAttribArray(mPositionHandler);
         */
        //if (mIsFourToThree)
        {
        	/*
            GLES20.glVertexAttribPointer(mPositionHandler,
                    2, GLES20.GL_FLOAT, false, 8, m43VertexBuffer);*/
        	gl.glVertexPointer(3, GL10.GL_FLOAT, 0, this.mVertexBuffer);
            
        	
        	//gl.glTexCoordPointer(AMOUNT_OF_NUMBERS_PER_TEXTURE_POINT, GL10.GL_FLOAT, 0, this.mTextureBuffer.get(i));

        
        } 
        /*
        else 
        {
            GLES20.glVertexAttribPointer(mPositionHandler,
                    2, GLES20.GL_FLOAT, false, 8, mVertexBuffer);
        }
        */
        GLES10.glTexCoordPointer(2,GLES10.GL_FLOAT, 0, mTexCoordBuffer);

        // This multiplies the view matrix by the model matrix, and stores the
        // result in the MVP matrix (which currently contains model * view).
        //TODO : remove
        Assert.assertTrue(mMVPMatrix!=null);
        Assert.assertTrue(mModelMatrix!=null);
        

        Matrix.multiplyMM(mMVPMatrix, 0, modelViewMatrix, 0, mModelMatrix, 0);
  		gl.glLoadMatrixf(mMVPMatrix, 0);

        // This multiplies the modelview matrix by the projection matrix, and stores
        // the result in the MVP matrix (which now contains model * view * projection).
        //Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

        // Pass in the combined matrix.
        //GLES20.glUniformMatrix4fv(mMVPMatrixHandler, 1, false, mMVPMatrix, 0);

        //GLES20.glUniform1f(mAlphaHandler, mAlpha);

        GLES10.glActiveTexture(GLES10.GL_TEXTURE0);
        GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, imTextureId);

        //GLES20.glUniform1i(mTextureHandler, 0);

        GLES10.glDrawArrays(GLES10.GL_TRIANGLE_FAN, 0, 4);
  		gl.glLoadMatrixf(modelViewMatrix, 0);

    }

	@Override
	public void loadGLTexture(GL10 gl) {
		// Load the snapshot bitmap as a texture to bind to our GLES20 program
        int texture[] = new int[1];

        GLES10.glGenTextures(1, texture, 0);
        GLES10.glBindTexture(GLES10.GL_TEXTURE_2D, texture[0]);

        GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MIN_FILTER, GLES10.GL_NEAREST);
        GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_MAG_FILTER, GLES10.GL_NEAREST);
        
        //GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_WRAP_S, GLES10.GL_CLAMP_TO_EDGE);
        //GLES10.glTexParameterf(GLES10.GL_TEXTURE_2D, GLES10.GL_TEXTURE_WRAP_T, GLES10.GL_CLAMP_TO_EDGE);

        GLUtils.texImage2D(GLES10.GL_TEXTURE_2D, 0, mBitmapTexture, 0);
        mBitmapTexture.recycle();

        if(texture[0] == 0){
            Log.e(TAG, "Unable to attribute texture to quad");
        }

        imTextureId = texture[0];
        mBitmapTexture = null;		
	}
	

	public void rotate(float rx, float ry, float rz)
	{
		Matrix.rotateM(mModelMatrix, 0, rx, 1, 0, 0);
		Matrix.rotateM(mModelMatrix, 0, ry, 0, 1, 0);
		Matrix.rotateM(mModelMatrix, 0, rz, 0, 0, 1);
		

		
	}

	public void translate(float tx,float ty,float tz)
	{
        Matrix.translateM(mModelMatrix, 0, -tx, -ty, -tz);
		
	}
}