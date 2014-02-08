package fr.ensicaen.panandroid.capture;

import javax.microedition.khronos.opengles.GL10;

import fr.ensicaen.panandroid.meshs.TexturedPlane;
import android.graphics.Bitmap;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;


/**
 * Like snapshot, but drawable as a TexturedPlane.
 */
public class Snapshot3D extends TexturedPlane
{
	/* ********
	 * ATTRIBUTES
	 * *******/
	private Snapshot mSnapshot;
	
	

	/* ********
	 * CONSTRUCTOR
	 * *******/
	
	
	public Snapshot3D(float scale, float pitch, float yaw) 
	{
		this(scale, 1.0f, pitch, yaw, 0.0f);
	}
	
	public Snapshot3D(float scale, float ratio, float pitch, float yaw)
	{
		this(scale, ratio, pitch, yaw, 0.0f);
	}
	
	
	public Snapshot3D(float scale, float ratio, float pitch, float yaw, float roll)
	{
		//TODO : roll??
		
		super(scale, ratio);
		super.rotate(pitch, yaw, roll);
		mSnapshot = new Snapshot(pitch, yaw);
		
	}

	
	public float getPitch()
	{
		return mSnapshot.getPitch();
	}
	
	public float getYaw()
	{
		return mSnapshot.getYaw();
	}
	
	//TODO;
	public float getRoll()
	{
		return mSnapshot.getRoll();
	}
	
}


  