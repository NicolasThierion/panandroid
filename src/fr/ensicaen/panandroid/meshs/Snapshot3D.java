package fr.ensicaen.panandroid.meshs;

import javax.microedition.khronos.opengles.GL10;

import fr.ensicaen.panandroid.capture.EulerAngles;
import fr.ensicaen.panandroid.capture.Snapshot;


/**
 * Like snapshot, but drawable as a TexturedPlane.
 */
public class Snapshot3D extends TexturedPlane implements EulerAngles
{
	/* ********
	 * ATTRIBUTES
	 * *******/
	private Snapshot mSnapshot;
	
	private boolean mVisible = true;
	
	private GL10 mGl;
	
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
		super.rotate(-pitch, -yaw, roll);
		mSnapshot = new Snapshot(-pitch, -yaw);
		
	}
	
	
	public Snapshot3D(float scale, float ratio, Snapshot snapshot)
	{
		super(scale, ratio);
		
		super.rotate(-snapshot.getPitch(), -snapshot.getYaw(), snapshot.getRoll());
		mSnapshot = snapshot;
		
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
	
	@Override
	public void draw(GL10 gl, float[] modelViewMatrix)
	{
		mGl = gl;
		super.draw(gl, modelViewMatrix);
	}
	
	
	
	@Override
	public void setVisible(boolean visible)
	{
		
		super.setVisible(visible);
		
		if(mVisible == visible)
			return;
		mVisible = visible;
	
		//unload texture
		if(!mVisible)
		{
			new Thread(new Runnable()
			{
				public void run()
				{
					Snapshot3D.super.unloadGLTexture(mGl);

				}
			}).start();
		}
		else
		{
			super.setTexture(mSnapshot.getFileName());
			super.loadGLTexture(mGl);
		}
	}
	
}


  