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

import java.util.Stack;

import fr.ensicaen.panandroid.meshs.Mesh;
import fr.ensicaen.panandroid.tools.SensorFusionManager;
import junit.framework.Assert;


import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * Surface view drawing a 3D mesh at the middle of the scene.
 * The view creates and starts its own openGL renderer.
 * 
 * @author Nicolas
 *
 */
public class Inside3dView extends GLSurfaceView implements SensorEventListener 
{
	
	private static final String TAG = Inside3dView.class.getSimpleName();
    
	/* *********
	 * VIEW PARAMETERS
	 * ********/
	private static final long INERTIA_TIME_INTERVAL = 200; // [ms]
	private static final int REST_THRESHOLD = 5; // [pixel]

	public static final float MIN_FOV = 20;

	public static final float MAX_FOV = 120;
	
	/* *********
	 * ATTRIBUTES
	 * ********/
	/** GL renderer **/
	protected InsideRenderer mRenderer;
	
	/** list of all touch events captured **/
    private Stack<EventInfo> mMotionEvents;
    
    /** if touch event are enabled **/
    private boolean mTouchScrollEnable = false;
    
    /** if inertia is enabled **/
    private boolean mInertiaEnable = false;

    /** if sensorial rotation is enabled **/
	private boolean mSensorialRotationEnable;
	private boolean mRollEnable = true;
	private boolean mPitchEnable = true;
	private boolean mYawEnable = true;
    
	/** The sensorFusion manager for sensorial rotation **/
	private SensorFusionManager mSensorFusionManager = null;


	/** Mesh that is drawed, given to the renderer **/
	
	/****/
	float mPitchLimits[] = new float[2];
	float mYawLimits[] =  new float[2];

	
	private ScaleGestureDetector mScaleDetector;

	private boolean mPinchZoomEnable = false;

	
	
	//protected Mesh mMesh;
	
    /* *********
	 * CONSTRUCTORS
	 * *********/


	/**
	 * Creates a new MeshView to put in the given context.
	 * @param context - Android context owning this view.
	 * @param mesh - mesh to draw in the view.
	 * @param renderer - renderer to use to render this scene.
	 * If mesh is null, create a default mesh.
	 * If renderer is null, you must use setRenderer later.
	 */
	protected Inside3dView(Context context, Mesh mesh, InsideRenderer renderer)
	{
		super(context);
		super.setPreserveEGLContextOnPause(true);
		mPitchLimits[0] = -1000;
		mPitchLimits[1] = 1000;
		mYawLimits[0] = -1000;
		mYawLimits[1] = 1000;
		//if renderer is set, assign the view to it.
		if(renderer != null)
		{        
			super.setEGLContextClientVersion(1);
			renderer.setRotationLimits(mPitchLimits, mYawLimits );
			setRenderer(renderer);
		}
		
		//scale gesture detector for pinch-n-zoom
	    mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());

	}
	
	/**
	 * Creates a new MeshView to put in the given context.
	 * Draws the mesh given in parameter.
	 * @param context - Android context owning this view.
	 * @param mesh - Mesh to draw.
	 */
	public Inside3dView(Context context, Mesh mesh)
	{
        this(context, mesh, new InsideRenderer(context, mesh) );	 
	}
    
	public Inside3dView(Context context)
	{
        this(context, null, null);	 
	}


	/* *********
	 * GLVIEW OVERRIDES
	 * ********/
	/**
	 * Called when the view receives a touch event.
	 */
	@Override
	public boolean onTouchEvent(final MotionEvent event)
	{
		
		if(isPinchZommEnabled())
		{
		    mScaleDetector.onTouchEvent(event);

		}
		if(!isTouchScrollEnabled())
		{
			return false;
		}
		
		
		switch (event.getAction()) 
		{
		
	    	case MotionEvent.ACTION_DOWN :
	    		stopInertialRotation();
	    		
	    		mMotionEvents = new Stack<EventInfo>();
	    		mMotionEvents.push(new EventInfo(event.getX(), event.getY(), event.getEventTime()));
	   
	    		return true;
	    	case MotionEvent.ACTION_MOVE :
	    		Assert.assertTrue(mMotionEvents != null);
	    		
	    		if (mMotionEvents.size() > 0) {
	    			EventInfo lastEvent = mMotionEvents.lastElement(); 
	        		
	        		float distX = event.getX()-lastEvent.x;
	        		float distY = event.getY()-lastEvent.y;
	        		
	        		rotate(distX, distY);
	    		}
	    		
	    		mMotionEvents.push(new EventInfo(event.getX(), event.getY(), event.getEventTime()));
	    		
	    		return true;
	    	case MotionEvent.ACTION_UP :
	    		Assert.assertTrue(mMotionEvents != null);
	    		
	    		mMotionEvents.push(new EventInfo(event.getX(), event.getY(), event.getEventTime()));
	
	    		startInertialRotation();
	
	    		return true;
	    	case MotionEvent.ACTION_CANCEL :
	    		mMotionEvents = null;
	    		return true;
    	}
   
    	return false;
	}
	
	

	@Override
	public void onResume()
	{
		if(this.isSensorialRotationEnabled())
		{
			mSensorFusionManager.addSensorEventListener(this);
			this.mSensorFusionManager.onResume();
		}
		super.onResume();
	}
	@Override 
	public void onPause()
	{
		super.onPause();
		if(this.isSensorialRotationEnabled())
		{
			mSensorFusionManager.removeSensorEventListener(this);
			mSensorFusionManager.onPauseOrStop();
		}
	}

	/* *********
	 * SENSORLISTENER OVERRIDES
	 * ********/
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	
	}


	@Override
	public void onSensorChanged(SensorEvent event)
	{
		float roll = 0.0f;
		float pitch = 0.0f;
		float yaw = 0;
		
		if(mPitchEnable)
			pitch = mSensorFusionManager.getPitch();
		
		if(mYawEnable)
			yaw += mSensorFusionManager.getYaw();
		
		if(mRollEnable)
			roll = mSensorFusionManager.getRelativeRoll();		

		
		pitch = (pitch>mPitchLimits[1]?mPitchLimits[1]:pitch);
		pitch = (pitch<mPitchLimits[0]?mPitchLimits[0]:pitch);
		yaw = (yaw>mYawLimits[1]?mYawLimits[1]:yaw);
		yaw = (yaw<mYawLimits[0]?mYawLimits[0]:yaw);
		mRenderer.setRotation(pitch, yaw, roll);
		
		//mRenderer.setRotationMatrix(mSensorFusionManager.getRotationMatrix());
			
	}
	/* *********
	 * PUBLIC METHODS
	 * ********/
	
	/**
	 * Set the mesh rotation
	 * @param deltaYaw - How many degree to rotate horizontally
	 * @param deltaPitch - How many degrees to rotate vertically
	 */
	public void rotate(float deltaYaw, float deltaPitch) {
		
		int surfaceWidth = mRenderer.getSurfaceWidth();
		int surfaceHeight = mRenderer.getSurfaceHeight();
		Assert.assertTrue(surfaceWidth>0);
		Assert.assertTrue(surfaceHeight>0);
		
		float aspect = (float) surfaceWidth/(float) surfaceHeight;
		float pitch = mRenderer.getPitch();
		float yaw = mRenderer.getYaw();
		float hFovDeg = mRenderer.getHFovDeg();
		
		float deltaLongitute = deltaYaw/surfaceWidth*hFovDeg;
		yaw -= deltaLongitute;	
		
		float fovYDeg = hFovDeg/aspect;
		float deltaLatitude = deltaPitch/surfaceHeight*fovYDeg;
		pitch -= deltaLatitude;
	
		
		pitch = (pitch>mPitchLimits[1]?mPitchLimits[1]:pitch);
		pitch = (pitch<mPitchLimits[0]?mPitchLimits[0]:pitch);
		yaw = (yaw>mYawLimits[1]?mYawLimits[1]:yaw);
		yaw = (yaw<mYawLimits[0]?mYawLimits[0]:yaw);
		

		mRenderer.setRotation(pitch, yaw);
	}

	/* *********
	 * ACCESSORS
	 * ********/
	/**
	 * Enable inertia rotation. Only works when touch scroll is enabled.
	 * @param enabled
	 */
	public void setEnableInertialRotation(boolean enabled)
	{
		mInertiaEnable = enabled;		
	}
	
	public boolean isInertialRotationEnabled()
	{
		return mInertiaEnable;
	}
	
	public void setEnablePinchZoom(boolean enabled)
	{
		mPinchZoomEnable  = enabled;
	}
	public boolean isPinchZommEnabled() {
		
		return mPinchZoomEnable;
	}
	
	/**
	 * Set friction value for the inartial rotation.
	 * @param coef
	 */
	public void setInertiaFriction(float coef)
	{
		mRenderer.setRotationFriction(coef);
	}
	
	/**
	 * Enable touch rotation.
	 * @param enable
	 */
	public void setEnableTouchRotation(boolean enable)
	{
		this.mTouchScrollEnable = enable;
	}
	
	public boolean isTouchScrollEnabled()
	{
		return this.mTouchScrollEnable;
	}
	
	/**
	 * Must override this method if want to use a custom renderer.
	 * Don't forget to call super.setRenderer() with the new renderer.
	 */
	protected void setRenderer(InsideRenderer renderer)
	{	
        mRenderer = renderer;
        super.setRenderer(mRenderer);
		mRenderer.setRotationLimits(mPitchLimits, mYawLimits );
		
	}
	
	
	/**
	 * Enable the sensorial rotation. The 3d scene will rotates around X, Y and Z axis according to device's rotation.
	 * @param enable
	 * @return
	 */
	public boolean setEnableSensorialRotation(boolean enable) 
	{	
		if(enable)
		{
			//create a new sensor manager
			mSensorFusionManager = SensorFusionManager.getInstance(getContext());
			
			boolean initialized = true;
			if(!mSensorFusionManager.isStarted())
			{
				//and register it to the system
				initialized = mSensorFusionManager.start();
			}
			
			if(!initialized)
				mSensorFusionManager = null;
			else 
				mSensorFusionManager.addSensorEventListener(this);
			
			mSensorialRotationEnable = initialized;

			return initialized;
		}
		//disable sensorial rotation
		else if (mSensorFusionManager!=null)
		{
			mSensorialRotationEnable = false;
			mSensorFusionManager.removeSensorEventListener(this);
			mSensorFusionManager.stop();;
			mSensorFusionManager = null;
		}
		return true;

	}
	
	/**
	 * if sensorialRotation should rotate around Z axis.
	 * @param enable
	 */
	public void setEnableRollRotation(boolean enable)
	{
		mRollEnable = enable;
	}
	
	/**
	 * if sensorialRotation should rotate around X axis.
	 * @param enable
	 */
	public void setEnablePitchRotation(boolean enable)
	{
		mPitchEnable = enable;
	}
	
	/**
	 * if sensorialRotation should rotate around Y axis.
	 * @param enable
	 */
	public void setEnableYawRotation(boolean enable)
	{
		mYawEnable = enable;
	}
	
	public boolean isSensorialRotationEnabled()
	{
		return mSensorialRotationEnable;
	}
	
	public float[] getmPitchLimits() {
		return mPitchLimits;
	}

	public void setPitchLimits(float[] pitchLimits)
	{
		mPitchLimits = pitchLimits;
		if(mRenderer!=null)
		{
			mRenderer.setRotationLimits(mPitchLimits, mYawLimits );
		}
	}

	public float[] getmYawLimits()
	{
		return mYawLimits;
	}

	public void setYawLimits(float[] yawLimits)
	{
		mYawLimits = yawLimits;
		if(mRenderer!=null)
		{
			mRenderer.setRotationLimits(mPitchLimits, mYawLimits );
		}

	}
	
	/* *********
	 * PRIVATE METHODS
	 * ********/
	
	private void stopInertialRotation() {
		mRenderer.stopInertialRotation();
	}
	
	private void startInertialRotation() 
	{
		if(!isInertialRotationEnabled())
		{
			Log.w(TAG, "abording inertial scrolling cause it is disabled"  );
			return;
		}
		
		
		
		Assert.assertTrue(mMotionEvents != null);
		
		if (mMotionEvents.size() < 2) {
			return;
		}
		
		EventInfo event1 = mMotionEvents.pop();
				
		long tEnd = event1.time;
		float directionX = 0.0f;
		float directionY = 0.0f;
		EventInfo event2 = mMotionEvents.pop();
		long tStart = tEnd;
		
		while (event2 != null && tEnd-event2.time < INERTIA_TIME_INTERVAL) {
			tStart = event2.time;
			directionX += event1.x-event2.x;
			directionY += event1.y-event2.y;
			
			event1 = event2;
			if (mMotionEvents.size() > 0) {
				event2 = mMotionEvents.pop();
			} else {
				event2 = null;
			}
		}
		
		float dist = (float) Math.sqrt(directionX*directionX + directionY*directionY);
		if (dist <= REST_THRESHOLD) {
			return;
		}
		
		// The pointer was moved by more than REST_THRESHOLD pixels in the last
		// INERTIA_TIME_INTERVAL seconds (or less). --> We have a inertial scroll event.


		float deltaT = (tEnd-tStart)/1000.0f;
		if (deltaT == 0.0f) {
			return;
		}
		
		int surfaceWidth = mRenderer.getSurfaceWidth();
		int surfaceHeight = mRenderer.getSurfaceHeight();
		
		float hFovDeg = mRenderer.getHFovDeg();
		float vFovDeg = mRenderer.getVFovDeg();
		
		float deltaYaw = directionX/surfaceWidth*hFovDeg;
		float scrollSpeedX = deltaYaw/deltaT;
		
		float deltaPitch = directionY/surfaceHeight*vFovDeg;
		float scrollSpeedY = deltaPitch/deltaT;
		
		
		float pitch = mRenderer.getPitch();
		float yaw = mRenderer.getYaw();
		
		if (pitch>=mPitchLimits[1] || pitch <=mPitchLimits[0])
			scrollSpeedY = 0;
		if (yaw>=mYawLimits[1] || yaw <=mYawLimits[0])
			scrollSpeedX = 0;
		
		if (scrollSpeedX == 0.0f && scrollSpeedY == 0.0f) {
			return;
		}
		mRenderer.startInertiaRotation(-1.0f*scrollSpeedY, -1.0f*scrollSpeedX);
	}

	
	/* *********
	 * PRIVATE CLASS
	 * ********/
    private class EventInfo 
    {
    	public float x;
    	public float y;
    	public long time;
    	
    	public EventInfo(float x, float y, long time) {
    		this.x = x;
    		this.y = y;
    		this.time = time;
    	}
    }


	public void setReferenceRotation(float pitch, float yaw)
	{
		mRenderer.setReferenceRotation(pitch, yaw);
	}


	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
	    @Override
	    public boolean onScale(ScaleGestureDetector detector) {

	        float scaleFactor = detector.getScaleFactor();
	        float fov = mRenderer.getFov();
	        fov/=scaleFactor;
	        // Don't let the object get too small or too large.
	        fov = (float) Math.max(MIN_FOV, Math.min(fov, MAX_FOV));

	        mRenderer.setFovDeg(fov);
	        return true;
	    }
	    
	   
	    @Override
	    public boolean onScaleBegin(ScaleGestureDetector detector) {
	    	startInertialRotation();
	    return true;
	    }
	    @Override
	    public void onScaleEnd(ScaleGestureDetector detector) {
	    	startInertialRotation();
	    }
	   
	}



}
