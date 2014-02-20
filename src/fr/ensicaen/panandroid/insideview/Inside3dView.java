/*
 * ENSICAEN
 * 6 Boulevard Marechal Juin
 * F-14050 Caen Cedex
 *
 * This file is owned by ENSICAEN students.
 * No portion of this code may be reproduced, copied
 * or revised without written permission of the authors.
 */

package fr.ensicaen.panandroid.insideview;

import java.util.Stack;

import fr.ensicaen.panandroid.meshs.Mesh;
import fr.ensicaen.panandroid.sensor.SensorFusionManager;
import junit.framework.Assert;


import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;

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
	
	/* *********
	 * ATTRIBUTES
	 * ********/
	/** GL renderer **/
	protected InsideRenderer mRenderer;
	
	/** list of all touch events captured **/
    private Stack<EventInfo> mMotionEvents;
    
    /** if touch event are enabled **/
    private boolean mIsTouchScrollEnabled = false;
    
    /** if inertia is enabled **/
    private boolean mIsInertiaEnabled = false;

    /** if sensorial rotation is enabled **/
	private boolean mIsSensorialRotationEnabled;
    
	/** The sensorFusion manager for sensorial rotation **/
	private SensorFusionManager mSensorFusionManager = null;

	/** Mesh that is drawed, given to the renderer **/
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
			
		//if renderer is set, assign the view to it.
		if(renderer != null)
		{        
			super.setEGLContextClientVersion(1);

			setRenderer(renderer);
		}

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
			this.mSensorFusionManager.onResume();
		super.onResume();
	}
	@Override 
	public void onPause()
	{
		super.onPause();
		if(this.isSensorialRotationEnabled())
			this.mSensorFusionManager.onPauseOrStop();
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
		float pitch = this.mSensorFusionManager.getPitch();
		float yaw = this.mSensorFusionManager.getYaw();
		
		this.mRenderer.setRotation(pitch, yaw);
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
		float aspect = (float) surfaceWidth/(float) surfaceHeight;
		float rotationLatitudeDeg = mRenderer.getPitch();
		float rotationLongitudeDeg = mRenderer.getYaw();
		float hFovDeg = mRenderer.getHFovDeg();
		
		float deltaLongitute = deltaYaw/surfaceWidth*hFovDeg;
		rotationLongitudeDeg -= deltaLongitute;	
		
		float fovYDeg = hFovDeg/aspect;
		float deltaLatitude = deltaPitch/surfaceHeight*fovYDeg;
		rotationLatitudeDeg -= deltaLatitude;
	
		mRenderer.setRotation(rotationLatitudeDeg, rotationLongitudeDeg);
	}

	/* *********
	 * ACCESSORS
	 * ********/
	/**
	 * Enable inertia rotation. Only works when touch scroll is enabled.
	 * @param enabled
	 */
	public void enableInertialRotation(boolean enabled)
	{
		this.mIsInertiaEnabled = enabled;		
	}
	
	public boolean isInertialRotationEnabled()
	{
		return this.mIsInertiaEnabled;
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
	public void enableTouchRotation(boolean enable)
	{
		this.mIsTouchScrollEnabled = enable;
	}
	
	public boolean isTouchScrollEnabled()
	{
		return this.mIsTouchScrollEnabled;
	}
	
	/**
	 * Must override this method if want to use a custom renderer.
	 * Don't forget to call super.setRenderer() with the new renderer.
	 */
	protected void setRenderer(InsideRenderer renderer)
	{	
        mRenderer = renderer;
        super.setRenderer(mRenderer);
	}
	
	
	/**
	 * 
	 * @param enable
	 * @return
	 */
	public boolean enableSensorialRotation(boolean enable) {
		
		
		if(enable)
		{
			//create a new sensor manager
			this.mSensorFusionManager = SensorFusionManager.getInstance(this.getContext());
			
			//and register it to the system
			boolean initialized = mSensorFusionManager.start();
			
			if(!initialized)
				mSensorFusionManager = null;
			else 
				mSensorFusionManager.addSensorEventListener(this);
			
			mIsSensorialRotationEnabled = initialized;

			return initialized;
		}
		
		else if (mSensorFusionManager!=null)
		{
			mIsSensorialRotationEnabled = false;
			mSensorFusionManager.removeSensorEventListener(this);
			this.mSensorFusionManager.onPauseOrStop();
			mSensorFusionManager = null;
		}
		return true;

	}
	
	public boolean isSensorialRotationEnabled()
	{
		return this.mIsSensorialRotationEnabled;
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



}
