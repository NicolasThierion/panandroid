package fr.ensicaen.panandroid.meshs;

import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Bitmap.Config;

/**
 * Create a cube from 6 TexturedPlane objects
 * @author Nicolas
 *
 */
public class Cube extends Mesh
{
	/* ********
	 * DEFAULT PARAMETERS
	 * *******/
	/** default cude's size **/
	private static final float DEFAULT_SIZE = 65.0f;
	
	/** face-array index **/
	public static enum Face
	{
		FRONT(0),
		BACK(1), 
		LEFT(2), 
		RIGHT(3), 
		UP(4), 
		DOWN(5);
		
		int mValue;
		Face(int value)
		{
			mValue = value;
		}
		
		public int value()
		{
			return mValue;
		}
	}
	

	/* *******
	 * ATTRIBUTES
	 * *******/
	
	/** cube's size**/
	private float mSize;
	
	/** cube's faces **/
	private TexturedPlane[] mFaces = new TexturedPlane[6];
	
    
	
	public Cube()
	{
		this(DEFAULT_SIZE, Bitmap.createBitmap(new int[]{Color.GREEN}, 1, 1, Config.RGB_565),
				Bitmap.createBitmap(new int[]{Color.CYAN}, 1, 1, Config.RGB_565),
				Bitmap.createBitmap(new int[]{Color.RED}, 1, 1, Config.RGB_565), 
				Bitmap.createBitmap(new int[]{Color.BLUE}, 1, 1, Config.RGB_565), 
				Bitmap.createBitmap(new int[]{Color.WHITE}, 1, 1, Config.RGB_565), 
				Bitmap.createBitmap(new int[]{Color.YELLOW}, 1, 1, Config.RGB_565) );
		

	}
	/* *********
	 * CONSTRUCTORS
	 * *********/
    /**
     * 
     * @param texFront - texture of the North face
     * @param texBack - texture of the South face
     * @param texLeft - texture of the East face
     * @param texRight - texture of the West face
     * @param texUp - texture of the Up face
     * @param texDown - texture of the Down face
     */
    public Cube(	Bitmap texFront,
					Bitmap texBack,
					Bitmap texLeft, 
					Bitmap texRight, 
					Bitmap texUp, 
					Bitmap texDown)
    {
    	this(DEFAULT_SIZE, texFront,
					texBack,
					texLeft, 
					texRight, 
					texUp, 
					texDown );
    }
    
    /**
     * 
     * @param size - size of the skybox
     * @param texNorth - texture of the North face
     * @param texSouth - texture of the South face
     * @param texEast - texture of the East face
     * @param texWest - texture of the West face
     * @param texUp - texture of the Up face
     * @param texDown - texture of the Down face
     */
    public Cube(float size,
    		Bitmap texFront,
			Bitmap texBack,
			Bitmap texLeft, 
			Bitmap texRight, 
			Bitmap texUp, 
			Bitmap texDown)
    {
    	
    	mSize = size/2.0f;
    	TexturedPlane currentFace;
    	
    	//front face
    	currentFace = new TexturedPlane(mSize, 1.0f);
        mFaces[Face.FRONT.value()] = currentFace;
        currentFace.rotate(0.0f, 0.0f, 0.0f);
        currentFace.translate(0, 0, mSize);
        currentFace.setTexture(texFront);


    	//back face
    	currentFace = new TexturedPlane(mSize, 1.0f);
        mFaces[Face.BACK.value()] = currentFace;
        currentFace.rotate(0.0f, 180.0f, 0.0f);
        currentFace.translate(0, 0, mSize);
        currentFace.setTexture(texBack);

        //left face
    	currentFace = new TexturedPlane(mSize, 1.0f);
        mFaces[Face.LEFT.value()] = currentFace; 
        currentFace.rotate(0.0f, 90.0f, 0.0f);
        currentFace.translate(0, 0, mSize);
        currentFace.setTexture(texLeft);

        //right face
    	currentFace = new TexturedPlane(mSize, 1.0f);
        mFaces[Face.RIGHT.value()] = currentFace;
        currentFace.rotate(0.0f, 270.0f, 0.0f);
        currentFace.translate(0, 0, mSize);
        currentFace.setTexture(texRight);

        //up face
    	currentFace = new TexturedPlane(mSize, 1.0f);
        mFaces[Face.UP.value()] = currentFace;
        currentFace.rotate(270.0f, 0.0f, 0.0f);
        currentFace.translate(0, 0, mSize);
        currentFace.setTexture(texUp);

        //down face
    	currentFace = new TexturedPlane(mSize, 1.0f);
        mFaces[Face.DOWN.value()] = currentFace;
        currentFace.rotate(90.0f, 0.0f, 0.0f);
        currentFace.translate(0, 0, mSize);
        currentFace.setTexture(texDown);
        
    }

    

	/* *********
	 * ACCESSORS
	 * *********/
    public void setSize(float size)
    {
    	mSize = size;
    }
    
    public float getSize()
    {
    	return mSize;
    }
    
    public void setTexture(Face face, Bitmap texture)
    {
    	mFaces[face.value()].setTexture(texture);
    }
    
    
	/* *********
	 * RENDERER OVERRIDE
	 * *********/
	@Override
	public void draw(GL10 gl, float[] modelViewMatrix)
	{
		for (int i = 0; i < mFaces.length; i++)
		{
			mFaces[i].draw(gl, modelViewMatrix);
		}		
	}
	
	@Override
	public void loadGLTexture(GL10 gl)
	{
		for(int i=0; i<6; ++i)
		{
			mFaces[i].loadGLTexture(gl);
		}
	}
}