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
package fr.ensicaen.panandroid.insideview;

import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import fr.ensicaen.panandroid.meshs.Mesh;
import fr.ensicaen.panandroid.meshs.NullMesh;
import fr.ensicaen.panandroid.tools.EulerAngles;
import android.content.Context;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLU;
import android.opengl.Matrix;

/**
 * OpenGL renderer that display a shape from inside, at the center of the view.
 * @author Nicolas THIERION.
 *
 */
public class InsideRenderer implements Renderer, EulerAngles
{		
	@SuppressWarnings("unused")
	private static final String TAG = InsideRenderer.class.getSimpleName();

	/* *************
	 * GLOBAL RENDERING PARAMS
	 * *************/
	/** Identity matrix **/
	private static final float[] IDENTITY = {1, 0, 0, 0,
										   0, 1, 0, 0,
										   0, 0, 1, 0, 
										   0, 0, 0, 1};
	
	
	/** Clear color */
	private static final float CLEAR_RED = 0.0f;
	private static final float CLEAR_GREEN = 0.0f;
	private static final float CLEAR_BLUE = 0.0f;
	private static final float CLEAR_ALPHA = 2.5f;
		
	/** Perspective setup, field of view component. */
	private static final float DEFAULT_FOV = 60.0f;
	
	/** Perspective setup, near component. */
	private static final float Z_NEAR = 0.1f;
	
	/** Perspective setup, far component. */
	private static final float Z_FAR = 500.0f;

	/* *************
	 * ATTRIBUTES
	 * *************/
	
	/** The mesh. */
	protected Mesh mMesh;
	
	/** Diagonal field of view **/
	private float mFovDeg; 
	
	/** The rotation angle of the mesh */
	private float mYaw; 	/* Rotation around y axis */
	private float mPitch; 	/* Rotation around x axis */
	private float mRoll; 	/* Rotation around z axis */
	
	float mPitchLimits[];
	float mYawLimits[];

	/** Rotation matrix = modelview matrix **/
	private float[] mRotationMatrix = new float[16]; 		// While accessing this matrix, the renderer object has to be locked.
	
	/** drawing surface dimension **/
	private int mSurfaceWidth, mSurfaceHeight;
	
	/** rotation speed decreasing coef **/
	private float mRotationFriction = 200; 					// [deg/s^2]
	
	/** inertia rotation active **/
	private boolean inertiaEnabled = false;
	
	/** inertia rotation speed **/
	private float mRotationYawSpeed, mRotationPitchSpeed; 	// [deg/s]
	private float mYaw0, mPitch0;							//[deg]
	private long mT0; 										// [ms]

	private boolean mHasToResetFov = false;

	private float mOPitch;

	private float mOYaw;

	
	/* *************
	 * CONSTRUCTOR
	 * *************/
	
	/**
	* Constructor with argument
	* @param context - Application's context.
	* @param Mesh - Mesh to draw at the center of the view.
	*/
	public InsideRenderer(final Context context, Mesh mesh) 
	{
		mYaw = mPitch = mRoll = 0.0f;
		mMesh = mesh;
	  	setRotation(mYaw, mPitch);
	  	mFovDeg = DEFAULT_FOV;	
	  	
	  	mPitchLimits  = new float[2];
	  	mYawLimits  = new float[2];
		mPitchLimits[0] = -1000;
		mPitchLimits[1] = 1000;
		mYawLimits[0] = -1000;
		mYawLimits[1] = 1000;
	  	
	  	mRotationMatrix = Arrays.copyOf(IDENTITY, 16);
	  	
	}

	/**
	 * Constructor with no mesh. Must explicitly call "setSurroundingMesh() later, or nothing will be drawn.
	 * @param context - Application's context.
	 */
	public InsideRenderer(Context context)
	{
		this(context, new NullMesh());
	}


	/* *************
	 * RENDERER OVERRIDES
	 * *************/
	
	@Override
	public void onDrawFrame(final GL10 gl) 
	{		
		if(mHasToResetFov)
		{
			mHasToResetFov=false;
			setProjection(gl);
		}
		
		//clear the whole frame
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
	  	
		//reset referential
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		//compute an eventual new matrix rotation according to inertia
	  	doInertiaRotation();

	  	//load the rotation matrix
		synchronized(mRotationMatrix){
	  		gl.glLoadMatrixf(mRotationMatrix, 0);
	  		//draw the mesh in this new referential.
			this.mMesh.draw(gl, mRotationMatrix);
	  	} 	
	}
	
	@Override
	public void onSurfaceChanged(final GL10 gl, final int width, final int height) 
	{
		mSurfaceWidth = width;
		mSurfaceHeight = height;
		setProjection(gl);		
		gl.glViewport(0, 0, width, height);
		
	}

	@Override
	public void onSurfaceCreated(final GL10 gl, final EGLConfig config)
	{
		
		
		this.mMesh.loadGLTexture(gl);
		
		//texture mode
		gl.glEnable(GL10.GL_TEXTURE_2D);
		
		// smooth shader
		gl.glShadeModel(GL10.GL_SMOOTH);
					//gl.glEnable(GL10.GL_BLEND);

		
		// Don't draw back sides.
					/*
				    gl.glEnable(GL10.GL_CULL_FACE);
				    gl.glFrontFace(GL10.GL_CCW);
				    gl.glCullFace(GL10.GL_BACK);*/
	    
	    gl.glEnable(GL10.GL_DEPTH_TEST);
	    
		gl.glClearColor(CLEAR_RED, CLEAR_GREEN, CLEAR_BLUE, CLEAR_ALPHA);
		gl.glClearDepthf(1.0f);
		
					//gl.glDepthFunc(GL10.GL_LEQUAL);
		gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
		
	    
				    /*//done in drawMesh
					gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
					gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
					
					gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_NICEST);
					*/
		
					//setupTextures(gl);
		
		gl.glDisable(GL10.GL_TEXTURE_2D);
		
	}
	

	
	/* *************
	 * PUBLIC METHODS
	 * *************/
	/**
	 * Rotate the mesh given euler's angles.
	 * @param pitch - rotation around y axis.
	 * @param yaw - rotation around x axis.
	 */
	public void setRotation(float pitch, float yaw)
	{
		this.setRotation(pitch, yaw, mRoll);
	}
	
	public void setRotation(float pitch, float yaw, float roll)
	{
		mYaw = yaw;
		mPitch = pitch;
		mRoll = roll;
		
		normalizeRotation();
		computeRotationMatrix();		
	}
	/**
	 * 
	 * @param pitchSpeed
	 * @param yawSpeed
	 */
	public synchronized void startInertiaRotation(float pitchSpeed, float yawSpeed)
	{
		inertiaEnabled = true;
		mRotationYawSpeed = yawSpeed;
		mRotationPitchSpeed = pitchSpeed;
		mYaw0 = mYaw;
		mPitch0 = mPitch;
		mT0 = System.currentTimeMillis();
	}
	
	
	public synchronized void stopInertialRotation() 
	{
	  	inertiaEnabled = false;
	}

	public void setRotationFriction(float coef)
	{
		this.mRotationFriction = coef;
		
	}
		  
	/* ************
	 * ACCESSORS
	 * ************/
	/**
	 * 
	 * @return pitch of the mesh, between -90 and 90.
	 */
	public float getPitch() 
	{
	  	return mPitch;
	}
	
	/**
	 *   
	 * @return yaw of the mesh, between -180 and 180.
	 */
	public float getYaw() 
	{
		return mYaw;
	}
	
	/**
	 *   
	 * @return yaw of the mesh, between -180 and 180.
	 */
	public float getRoll() 
	{
		return mRoll;
	}
		
	public int getSurfaceWidth()
	{
		return mSurfaceWidth;
	}
	  
	public int getSurfaceHeight()
	{
		return mSurfaceHeight;
	}
	  
	public float getFov() 
	{
		return mFovDeg;
	}
	  
	public float getHFovDeg() 
	{
		float viewDiagonal = (float) Math.sqrt(mSurfaceHeight*mSurfaceHeight + mSurfaceWidth*mSurfaceWidth);
		float hFovDeg = (float) mSurfaceWidth/viewDiagonal * mFovDeg;  	
		return hFovDeg;
	}

	public float getVFovDeg() {
		float viewDiagonal = (float) Math.sqrt(mSurfaceHeight*mSurfaceHeight + mSurfaceWidth*mSurfaceWidth);	
		float vFovDeg = (float) mSurfaceHeight/viewDiagonal * mFovDeg;
		return vFovDeg;
	}

	/**
	 * Set mesh to draw in the view.
	 * @param mesh
	 */
	public void setSurroundingMesh(Mesh mesh)
	{
		mMesh = mesh;
	}
	
	/**
	 * get mesh associated with this renderer.
	 * @return
	 */
	public Mesh getSurroundingMesh()
	{
		return mMesh;
	}
	
	protected float[] getRotationMatrix()
	{
		synchronized(mRotationMatrix){
			return mRotationMatrix;
		}
	}
	
	public void setFovDeg(float degree)
	{
		mFovDeg = degree;
		mHasToResetFov=true;
	
	}
	
	
	
	/* ************
	 * PRIVATE METHODS
	 * ************/

	/**
	 * Computes rotation matrix from euler's angles mYaw and mPitch.
	 */
	private synchronized void computeRotationMatrix()
	{
		
		float rotationMatrix[]  = Arrays.copyOf(IDENTITY, 16);
		Matrix.rotateM(rotationMatrix, 0, mRoll, 0, 0, 1);
		Matrix.rotateM(rotationMatrix, 0, mPitch, 1, 0, 0);
		Matrix.rotateM(rotationMatrix, 0, mYaw, 0, 1, 0);
		synchronized(mRotationMatrix){
			mRotationMatrix = rotationMatrix;
		}
  	}
	
	/**
	 * keep pitch and yaw between correct intervals : 
	 * pitch  in [-180 , 180]
	 * yaw in [-90, 90]
	 * 
	 */
	private void normalizeRotation() 
	{
		mYaw %= 360.0f;
		if (mYaw < -180.0f) 
			mYaw += 360.0f;
		else if (mYaw > 180.0f)
			mYaw -= 360.0f;
		
		
		if (mPitch < -90.0f) 
			mPitch = -90.0f;
		else if (mPitch > 90.0f)
			mPitch = 90.0f;
		
		mRoll %= 360.0f;
		if (mRoll < -180.0f) 
			mRoll += 360.0f;
		else if (mRoll > 180.0f)
			mRoll -= 360.0f;
		
	}
	
	/**
	 * Computes projection matrix.
	 * @param gl
	 */
	private void setProjection(GL10 gl)
	{
		final float aspectRatio = (float) mSurfaceWidth / (float) (mSurfaceHeight == 0 ? 1 : mSurfaceHeight);
		
		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();
		
		float fovYDeg = getVFovDeg();
		GLU.gluPerspective(gl, fovYDeg, aspectRatio, Z_NEAR, Z_FAR);
		
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
	}
		
	/**
	 * if inertia rotation is enabled, do the rotation.
	 */
	private synchronized void doInertiaRotation()
	{
		if (!inertiaEnabled)
			return;
  	
		long currentTime = System.currentTimeMillis();
		float deltaT = (currentTime-mT0)/1000.0f;
		if (deltaT == 0.0f)
			return;
		
  	
		float rPitch = deltaT*mRotationFriction;
		float rYaw = deltaT*mRotationFriction;
  	
		if (Math.abs(mRotationPitchSpeed) < rPitch && Math.abs(mRotationYawSpeed) < rYaw)
		{
			stopInertialRotation();
		}
  	
		float deltaPitch, deltaYaw;
  
		if (Math.abs(mRotationPitchSpeed) >= rPitch)
		{
			float rotSpeedLat_t = mRotationPitchSpeed - Math.signum(mRotationPitchSpeed)*rPitch;
			deltaPitch = (mRotationPitchSpeed+rotSpeedLat_t)/2.0f*deltaT;
		} 
		else 
		{
			float tMax = Math.abs(mRotationPitchSpeed)/mRotationFriction;
			deltaPitch = 0.5f*tMax*mRotationPitchSpeed;
		}
  	
		if (Math.abs(mRotationYawSpeed) >= rYaw)
		{
			float rotSpeedLon_t = mRotationYawSpeed - Math.signum(mRotationYawSpeed)*rYaw;
			deltaYaw = (mRotationYawSpeed+rotSpeedLon_t)/2.0f*deltaT;
		} 
		else 
		{
			float tMax = Math.abs(mRotationYawSpeed)/mRotationFriction;
			deltaYaw = 0.5f*tMax*mRotationYawSpeed;
		}
  	
		if(mPitch0+deltaPitch > mPitchLimits[1])
		{
			deltaPitch = mPitchLimits[1]-mPitch0;
		}
		else if(mPitch0+deltaPitch < mPitchLimits[0])
		{
			deltaPitch = mPitchLimits[0]-mPitch0;
		}

		if(mYaw0+deltaYaw > mYawLimits[1])
		{
			deltaYaw = mYawLimits[1]-mYaw0;
		}
		if(mYaw0+deltaYaw < mYawLimits[0])
		{
			deltaYaw = mYawLimits[0]-mYaw0;
		}
		
		setRotation(mPitch0+deltaPitch, mYaw0+deltaYaw);
	}

	public void setRotationMatrix(float[] rotationMatrix) {
		mRotationMatrix = rotationMatrix;
	}

	public void setRotationRange(float[] pitchLimits, float[] yawLimits)
	{
		mPitchLimits = pitchLimits;
		mYawLimits =  yawLimits;
	}


	

	



}
