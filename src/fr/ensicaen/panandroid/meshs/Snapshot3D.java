/*
 * Copyright (C) 2013 Nicolas THIERION, Saloua BENSEDDIK, Jean Marguerite.
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

package fr.ensicaen.panandroid.meshs;

import javax.microedition.khronos.opengles.GL10;

import android.graphics.Bitmap;
import android.util.Log;
import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.tools.EulerAngles;


/**
 * Like snapshot, but drawable as a TexturedPlane.
 * @author Nicolas THIERION.
 */
public class Snapshot3D extends TexturedPlane implements EulerAngles
{
	private static final String TAG = Snapshot3D.class.getSimpleName();
	/* ********
	 * ATTRIBUTES
	 * *******/
	private Snapshot mSnapshot;
	
	private boolean mVisible = true;

	
	private int mPostRotation = 0;
	private boolean mUsePersistentTexture = false;
	
	
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
		super(scale, ratio);
		super.rotate(-pitch, -yaw, 180.0f-roll);
		mSnapshot = new Snapshot(pitch, yaw);
		
	}
	
	
	public Snapshot3D(float scale, float ratio, Snapshot snapshot)
	{
		super(scale, ratio);
		super.rotate(-snapshot.getPitch(), -snapshot.getYaw(), 180.0f-snapshot.getRoll());
		mSnapshot = snapshot;
		super.setTexture(mSnapshot.getFileName());

		
	}

	public float getPitch()
	{
		return mSnapshot.getPitch();
	}
	
	public float getYaw()
	{
		return mSnapshot.getYaw();
	}
	
	public float getRoll()
	{
		return mSnapshot.getRoll();
	}
	
	@Override
	public void draw(GL10 gl, float[] modelViewMatrix)
	{
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
		/*
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
		}*/
	}
	
	@Override
	public void setTexture(Bitmap bmp)
	{
		super.setTexture(bmp);
		mPostRotation *=-1;
		super.rotate(0, 0, mPostRotation);
		mUsePersistentTexture = false;
	}
	
	
	@Override
	public void unloadGLTexture(GL10 gl)
	{
		super.unloadGLTexture(gl);
		if(mUsePersistentTexture)
			return;
		
		mPostRotation = mSnapshot.getOrientation();
		mUsePersistentTexture = true;
	

	}
	
	@Override
	public void loadGLTexture(GL10 gl)
	{
		super.loadGLTexture(gl);
		
		if(!mUsePersistentTexture)
			return;
		//has to load texture from jpeg => perform pre rotation
		switch(mPostRotation)
		{
			case 0:
				break;
			
			case 90:
			case -270 :
			case 270 :
			case -90 :
				super.setRatio(1/super.getRatio()); 
				break;
		
		}
		super.rotate(0, 0, mPostRotation);
		mPostRotation=0;

	}


	public float getOrientation() {
		return mSnapshot.getOrientation();
	}
	
	
}


  