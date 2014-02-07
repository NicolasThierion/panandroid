package fr.ensicaen.panandroid.meshs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

import fr.ensicaen.panandroid.meshs.TexturedPlane.Axis;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLUtils;
import android.opengl.Matrix;

/**
 * Based on Jim Cornmell's Sphere.
 * @author Nicolas
 *
 */
public abstract class Mesh
{

	/** Master rotation axis around which one the mesh rotates first **/
	private Axis mAxis = Axis.VERTICAL; 
	
	/** model matrix of the mesh. Define rotation/transformation of the mesh applied in the view matrix **/
	protected float[] mModelMatrix = {1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
	
	/**
	 * Load the texture for the square.
	 *
	 * @param gl Handle.
	 */
	public abstract void loadGLTexture(final GL10 gl);
  
  

	/**
	 * The draw method for the square with the GL context.
	 *
	 * @param gl Graphics handle.
	 * @param modelViewMatrix 
	 */
	public abstract void draw(final GL10 gl, float[] modelViewMatrix);
	  
	  

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
	
	public void setAxis(Axis axis)
	{
		mAxis = axis;
	}
}
