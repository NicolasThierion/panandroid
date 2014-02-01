/*
 * Copyright (C) 2013 Guillaume Lesniak
 * Copyright (C) 2012 Paul Lawitzki
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

package fr.ensicaen.panandroid.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import java.util.LinkedList;
import java.util.List;
import junit.framework.Assert;

/**
 *  * @author Nicolas

 * SensorFusionManager is basically the same as native SensorManager that register a sensor of type TYPE_VECTOR_ROTATION.
 * Because TYPE_VECTOR_ROTATION uses the gyroscope, not all devices are supported.
 * 
 * SensorFusionManager falls back to a simulated gyroscope with compass and accelerometer in this case,
 * and fire sensorEvent like TYPE_VECTOR_ROTATION would normally have done.
 * 
 * SensorFusionManager gives information about the rotation of the phone (pitch, yaw, rotation matrix, orientation vector). * 
 * 
 * Based on picSphere's SensorFusion (Guillaume Lesniak, Paul Lawitzki) for the manager and
 * PanoramaGL library for simulated accelerometer.
 * 
 *
 */
public class SensorFusionManager implements SensorEventListener
{

	/* *********
	 * GLOBAL CONSTANTS
	 * ********/
	private static final String TAG = SensorFusionManager.class.getSimpleName();
	public static final float RAD_TO_DEG =  (float) (180.0f / Math.PI);
	
	
	/** interval to listen to sensors, in 'us' */
	private static final int SENSOR_LISTENING_RATE = 20000;
	
	
	/* *********
	 * ATTRIBUTES
	 * ********/
	
	/** if device has gyroscope or have to use our simulated one **/
	private boolean mIsGyroscopeSupported = false;
	
	/** if either gyroscope or fallback mode is supported **/
	private boolean mIsRotationSupported = false;
	
	/** simulated gyroscope if fallback mode **/
	private SimulatedRotationVector mSimulatedRotationVector;
	
	/** List of all listeners **/
	List<SensorEventListener> mListeners = new LinkedList<SensorEventListener>();
	
	/** Internal sensor manager **/
	private SensorManager mSensorManager = null;

	/** vector that represents orientation of the device **/
    float[] mOrientation = new float[3];
    
    /** corresponding rotation matrix **/
    float[] mRotationMatrix = new float[16];

    float mPitch, mYaw;

	
	/* *********
	 * CONSTRUCTOR
	 * ********/
	
	/**
	 * Init a sensorManager and listen to TYPE_ROTATION_VECTOR events.
	 * 
	 * @param context
	 */
    public SensorFusionManager(Context context)
    {
        // get sensorManager and initialise sensor listeners
        this(context, true);
    }
    
    /**
     * allow to force fallback mode.
     * for debug purpose only.
     * @param context
     * @param useGyroscope
     */
    private SensorFusionManager(Context context, boolean useGyroscope)
    {
        // get sensorManager and initialise sensor listeners
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        registerListener(useGyroscope);
    }
    
    /* *********
	 * SENSOREVENTLISTENER OVERRIDES
	 * ********/
    @Override
    public void onSensorChanged(SensorEvent event) 
    {
    	
    	//don't know why this test.
    	/*
        if (mRotationMatrix == null) {
            mRotationMatrix = new float[16];
        }
        */
    	
    	
    	if(mIsGyroscopeSupported)
    	{
        	this.updateRotationMatrix(event);

    	}
    	else
    	{
    		Assert.assertTrue(mSimulatedRotationVector!=null);
    		mSimulatedRotationVector.updateRotationMatrix(event);
    	}
    	
       
    	//throw the event to all listeners
        for(SensorEventListener l : mListeners)
        {
        	l.onSensorChanged(event);
        }
    }

    
    public void addSensorEventListener(SensorEventListener listener)
    {
    	mListeners.add(listener);
    }

    
    public boolean removeSensorEventListener(SensorEventListener listener)
    {
    	return mListeners.remove(listener);
    }

	@Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    
    /* *********
	 * PUBLIC METHODS
	 * ********/	   

    public float[] getFusedOrientation()
    {
        return mOrientation;
    }

    public float[] getRotationMatrix() {
        return mRotationMatrix;
    }

    public void onPauseOrStop() {
        mSensorManager.unregisterListener(this);
    }

    public void onResume() {
        // restore the sensor listeners when user resumes the application.
        registerListener(mIsGyroscopeSupported);
    }

    public float getPitch()
    {
    	return this.mPitch;
    }
    
    public float getYaw()
    {
    	return this.mYaw;
    }
    public boolean registerListener()
    {
    	if(!mIsRotationSupported)
    		return this.registerListener(true);
    	return true;
    }

    
    /* *********
	 * PRIVATE METHODS
	 * ********/

    // This function registers sensor listeners for the accelerometer, magnetometer and gyroscope.
    private boolean registerListener(boolean useGyroscope)
    {
        mIsGyroscopeSupported =false;
        //try to init classic ROTATION_VECTOR sensor ,with gyroscope. 
        if(useGyroscope)
        {
        	mIsGyroscopeSupported= mSensorManager.registerListener(
        									this,
        									mSensorManager.getDefaultSensor(
        											Sensor.TYPE_ROTATION_VECTOR),
        									SENSOR_LISTENING_RATE );
        	Log.i(TAG, "sensor fusion avaible on this device");
        }
        
        if(mIsGyroscopeSupported)
        {
        	mIsRotationSupported = true;
        }
        //no gyro avaibe.. simulates try to one
        else
        {
        	if(useGyroscope)
        		Log.w(TAG, "Device has no gyroscope... trying fallback mode");

        	
        	mSimulatedRotationVector = new SimulatedRotationVector(mSensorManager, this);
        	mIsRotationSupported = mSimulatedRotationVector.registerListeners(SENSOR_LISTENING_RATE);
        	
        	if(!mIsRotationSupported)
        	{
        		Log.e(TAG, "device has no devices capable of handling rotation");
        	}
        	
        }
        return mIsRotationSupported;
    }
    
 
    /**
     * Updates rotation matrix given by rotation event, coming from gyroscope.
     * @param event
     */
	private void updateRotationMatrix(SensorEvent event) 
	{
		// Get rotation matrix from angles
        SensorManager.getRotationMatrixFromVector(mRotationMatrix, event.values);

        // Remap the axes
        SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_Z,
                SensorManager.AXIS_X, mRotationMatrix);

        // save remapped orientation vector
        SensorManager.getOrientation(mRotationMatrix, mOrientation);
        
        // save pitch and yaw.
        mYaw = mOrientation[0] * RAD_TO_DEG;
		mPitch = -mOrientation[1] * RAD_TO_DEG;
		
	}
	
	/* ********
	 * PRIVATE CLASS
	 * *******/
	/**
	 * Use a Simulated gyroscope from compass (for yaw) and accelerometer (for pitch) to compute a rotation vector.
	 * @author Nicolas
	 *
	 */
	private class SimulatedRotationVector implements SensorEventListener
	{
	
		/* **********
		 * CONSTANTS
		 * *********/
		
		private static final float PITCH_MAX = 50.0f;
		private static final float YAW_MAX = 100.0f;
		public static final int THRESHOLD =			150;
		public static final int PITCH_ERROR_MARGIN = 	5;
		public static final int YAW_ERROR_MARGIN = 		5;

		

		/* **********
		 * ATTRIBUTES
		 * *********/
		/** if sensors correctly registered **/
		private boolean mIsRotationSupported;
		
		/** sensor polling delay **/
		private int mSensorRate;
		
		/** Listener to contact when sensorEvent is fired **/
		private SensorEventListener mListener ;
			
		/** force value coming from accelerometer **/
		private float[] mAccelerometerData = new float[3];
		
		/** if pitch and yaw have been mesured**/
		private boolean mHasFirstAccelerometerPitch, mHasFirstMagneticHeading;
		
		/** mesured values of pitch and yaw**/
		private float mFirstAccelerometerPitch, mLastAccelerometerPitch, mAccelerometerPitch;
		private float mFirstMagneticHeading, mLastMagneticHeading, mMagneticHeading;

		
		/** ?? **/
		private long mThresholdTimestamp = 0;
		private boolean mThresholdFlag = false;
		
		



		

		/**
		 * 
		 * @param sensorManager
		 * @param listener
		 */
		public SimulatedRotationVector(SensorManager sensorManager, SensorEventListener listener )
		{
				
			mSensorManager = sensorManager;
			mListener= listener;
			
			mHasFirstAccelerometerPitch = mHasFirstMagneticHeading = false;
			mFirstAccelerometerPitch = mLastAccelerometerPitch = mAccelerometerPitch = 0.0f;
			mFirstMagneticHeading = mLastMagneticHeading = mMagneticHeading = 0.0f;			
			
		}
		
		public void updateRotationMatrix(SensorEvent event) 
		{
			float[] values = event.values;
			switch(event.sensor.getType())
			{
				//save accelerometer values.
				case Sensor.TYPE_ACCELEROMETER:						
					mAccelerometerData[0] = values[0];
					mAccelerometerData[1] = values[1];
					mAccelerometerData[2] = values[2];
						
					//don't know what is this code
					//this.accelerometer(event, mTempAcceleration.setValues(values));
					
					break;
				
				case Sensor.TYPE_MAGNETIC_FIELD:
					
					if(mThresholdFlag)
					{
						//computes rotation matrix from compass head and accelerometer's gravity
						SensorManager.getRotationMatrix(mRotationMatrix, null, mAccelerometerData, values);
						
						//translate rotation matrix to represent phone's orientation?
						SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, mRotationMatrix);
						
						//get device's orientation from rotation matrix
						SensorManager.getOrientation(mRotationMatrix, mOrientation);
						
						//translate pitch and yaw into degrees
						float yaw = mOrientation[0] * RAD_TO_DEG;
						float pitch = -mOrientation[1] * RAD_TO_DEG;
						
						//if compass sample previously recorded
						if(mHasFirstMagneticHeading)
						{
							if((pitch >= 0.0f && pitch < PITCH_MAX) || (pitch < 0.0f && pitch > -PITCH_MAX))
							{
								yaw -= mFirstMagneticHeading;
								float diff = yaw - mLastMagneticHeading;
								if(Math.abs(diff) > YAW_MAX)
								{
									mLastMagneticHeading = yaw;
									mMagneticHeading += (diff >= 0.0f ? 360.0f : -360.0f);
								}
								else if((yaw > mLastMagneticHeading && yaw - YAW_ERROR_MARGIN > mLastMagneticHeading) ||
										(yaw < mLastMagneticHeading && yaw + YAW_ERROR_MARGIN < mLastMagneticHeading))
									mLastMagneticHeading = yaw;
							}
						}
						else
						{
							mFirstMagneticHeading = yaw;
							mLastMagneticHeading = mMagneticHeading = 0;
							mHasFirstMagneticHeading = true;
						}
						
						if(mHasFirstAccelerometerPitch)
				        {
							pitch -= mFirstAccelerometerPitch;
				            if((pitch > mLastAccelerometerPitch && pitch - PITCH_ERROR_MARGIN > mLastAccelerometerPitch) ||
				               (pitch < mLastAccelerometerPitch && pitch + PITCH_ERROR_MARGIN < mLastAccelerometerPitch))
				            	mLastAccelerometerPitch = pitch;
				        }
				        else
				        {
				        	mFirstAccelerometerPitch = pitch ;
				        	mLastAccelerometerPitch = mAccelerometerPitch = 0;
				            mHasFirstAccelerometerPitch = true;
				        }
						this.doSimulatedGyroUpdate();
						//mListener.onSensorChanged(event);		

					}
					else
					{
						if(mThresholdTimestamp == 0)
							mThresholdTimestamp = System.currentTimeMillis();
						else if((System.currentTimeMillis() - mThresholdTimestamp) >= THRESHOLD)
							mThresholdFlag = true;
					}
		
					break;
			}
			
			
			
		}

		public boolean registerListeners(int rate)
		{	
			mSensorRate = rate;
			
			//Init accelerometer...
        	mIsRotationSupported = mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), mSensorRate);
    
        	//...compass
        	if(mIsRotationSupported)
        		mIsRotationSupported = mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), mSensorRate);
        	
			
        	return mIsRotationSupported;
		}
		
		public void doSimulatedGyroUpdate()
		{
			
			float step, offset = Math.abs(mLastAccelerometerPitch - mAccelerometerPitch);
			if(offset < 0.25f)
				mAccelerometerPitch = mLastAccelerometerPitch;
			else
			{
				step = (offset <= 10.0f ? 0.25f : 1.0f);
		        if(mLastAccelerometerPitch > mAccelerometerPitch)
		        	mAccelerometerPitch += step;
		        else if(mLastAccelerometerPitch < mAccelerometerPitch)
		        	mAccelerometerPitch -= step;
			}
		    offset = Math.abs(mLastMagneticHeading - mMagneticHeading);
		    if(offset < 0.25f)
		    	mMagneticHeading = mLastMagneticHeading;
		    else
		    {
		    	step = (offset <= 10.0f ? 0.25f : 1.0f);
		    	if(mLastMagneticHeading > mMagneticHeading)
		    		mMagneticHeading += step;
		        else if(mLastMagneticHeading < mMagneticHeading)
		        	mMagneticHeading -= step;
		    }
		    mPitch = -mAccelerometerPitch ;
		    mYaw = mMagneticHeading;
		    //mPanorama.getCamera().lookAt(this, mAccelerometerPitch, mMagneticHeading);

	    	
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) 
		{
			// TODO Auto-generated method stub
			
		}

		
		@Override
		public void onSensorChanged(SensorEvent event)
		{
	    	mListener.onSensorChanged(event);
		}

	};

	


}
