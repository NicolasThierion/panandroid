package fr.ensicaen.panandroid.meshs;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLUtils;

/**
 * Based on Jim Cornmell's Sphere.
 * @author Nicolas
 *
 */
public abstract class Mesh
{
	 /** Maximum allowed depth. */
	  protected static final int MAXIMUM_ALLOWED_DEPTH = 5;

	 


	 

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
}
