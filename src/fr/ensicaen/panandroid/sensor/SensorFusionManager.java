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

package fr.ensicaen.panandroid.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.util.LinkedList;
import java.util.List;

import fr.ensicaen.panandroid.capture.EulerAngles;
import junit.framework.Assert;

/**
 * 
 * @author Nicolas THIERION.
 * @author Saloua BENSEDDIK.
 * 
 * SensorFusionManager is basically the same as native SensorManager that register a sensor of type TYPE_VECTOR_ROTATION.
 * Because TYPE_VECTOR_ROTATION uses the gyroscope, not all devices are supported.
 * 
 * SensorFusionManager falls back to a simulated gyroscope with compass and accelerometer in this case,
 * and fire sensorEvent like TYPE_VECTOR_ROTATION would normally have done.
 * 
 * SensorFusionManager gives real-time information about the rotation of the phone (pitch, yaw, rotation matrix, orientation vector).
 * Reference pitch is 0 degrees (looking at horizon), and reference yaw is startup yaw.
 * 
 * Based on picSphere's SensorFusion (Guillaume Lesniak, Paul Lawitzki) for the manager and
 * PanoramaGL library for simulated accelerometer.
 * 
 *
 */
public class SensorFusionManager implements SensorEventListener, EulerAngles
{

	/* *********
	 * GLOBAL CONSTANTS
	 * ********/
	private static final String TAG = SensorFusionManager.class.getSimpleName();
	private static final float RAD_TO_DEG =  (float) (180.0f / Math.PI);
	private static final float POLE_TRESHOLD = 2.0f;
	
	/** interval to listen to sensors, in 'us' */
	private static final int SENSOR_LISTENING_RATE = 20000; //20ms

	private static final float THRESHOLD_ACCELERATION = .05f;
	private static final float THRESHOLD_ROTATION = .05f;
	
	/** magic numbers to compute phone stability **/
	private static final float ALPHA_ACCELERATION = 0.7f;	//sum weight	[apla + beta <lambda]
	private static final float ALPHA_ROTATION = 1.0f;		
	
	private static final float BETA_ACCELERATION = 0.7f;	//sample weight
	private static final float BETA_ROTATION = 1.0f;		
	private static final float LAMBDA = 1.1f;		//>1
	
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
	private float[] mOrientation = new float[3];
	
	/** corresponding rotation matrix **/
	private float[] mRotationMatrix = new float[16];
	
	/**current pitch and yaw **/
	private float mPitch=-1500.0f;
	private float mYaw=-1500.0f;
	private float mRoll=0.0f;

	
	/** reference pitch and yaw **/
	float oPitch = 0.0f, oYaw = 0.0f, oRoll=0.0f;
	boolean mHasToResetYaw = true, mHasToResetPitch = false, mHasToResetRoll=false;

	private boolean mIsStarted;
	
	/** current and computed acceleration values **/
	private float mCurrAccelerometerValues[] = new float[3];
	private float mAccelerationValues[] = new float[3];
	
	
	/** current and computed rotation values **/
	private float[] mRotationValues = new float[3];
	private float[] mCurrRotationValues = new float[3];
	private Context mContext;

	
	/* *********
	 * CONSTRUCTOR
	 * ********/
	
	
	public SensorFusionManager(Context context)
	{
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
		mContext = context;
		
	    // get sensorManager and initialise sensor listeners
	    mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
	    registerListener(useGyroscope);
	    
	    //fill acceleration & rotation with invalid values
	    mRotationValues[0] = 15000;
	    mAccelerationValues[0] = 15000;
	}
	
	/* *********
	 * EVENTLISTENER STUFF
	 * ********/
	@Override
	public void onSensorChanged(SensorEvent event) 
	{

		if(mIsGyroscopeSupported)
		{
			if(event.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR)
				this.updateRotationMatrix(event);
		}
		else 
		{
			if(event.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD || event.sensor.getType()==Sensor.TYPE_ACCELEROMETER)
				mSimulatedRotationVector.updateRotationMatrix(event);
		}
		
		if(mPitch<-361 || mYaw<-361)
		{	
			Log.e(TAG, "sensorFusion not correctly started");
			return;
		}
		
		
		
		if(mHasToResetPitch)
		{
			oPitch = mPitch;
			mPitch = 0.0f;
			mHasToResetPitch = false;
		}
		if(mHasToResetYaw)
		{
			oYaw = mYaw;
			mYaw = 0.0f;
			mHasToResetYaw = false;
		}	
		if(mHasToResetRoll)
		{
			oRoll = mRoll;
			mRoll = 0.0f;
			mHasToResetRoll = false;
		}	
		
		if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER)
			computeAcceleration(event);
		
		//if(event.sensor.getType()==Sensor.TYPE_GYROSCOPE)
		computeRotation(event);
		
		
		//throw the event to all listeners
	    for(SensorEventListener l : mListeners)
	    {
	    	l.onSensorChanged(event);
	    }
	    
	}
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int i) {
	
	}
	
	
	public void addSensorEventListener(SensorEventListener listener)
	{
		mListeners.add(listener);
	}
	
	
	public boolean removeSensorEventListener(SensorEventListener listener)
	{
		return mListeners.remove(listener);
	}
	
	
	
	public boolean registerListener()
	{
		if(!mIsRotationSupported)
			return this.registerListener(true);
		return true;
	}
	
	public void unregisterListener()
	{
    	mSensorManager.unregisterListener(this);
    	mIsStarted = false;

	}
	
	/* *********
	 * PUBLIC METHODS
	 * ********/	   
	
	/**
	 * Same effect as registerListener, plus set reference yaw to current orientation.
	 * @return
	 */
	public boolean start()
	{
		boolean res =  this.registerListener(mIsGyroscopeSupported);
		this.setReferenceYaw();
	
		return res;
	}
	
	/**
	 * same effect as unregisterListener, but reset pitch and yaw to 0.
	 */
	public void stop()
	{
    	unregisterListener();
    	resetPitch();
    	resetYaw();
	} 
	
	
	/**
	 * Unregister all the used sensors. same as unregisterListener().
	 */
	public void onPauseOrStop()
	{
	    unregisterListener();
	}
	
	/**
	 * Re register the used sensors. Same as registerListener().
	 */
	public void onResume()
	{
	    // restore the sensor listeners when user resumes the application.
	    registerListener(mIsGyroscopeSupported);
	}
	
	/**
	 * Return true if the devce has a gyroscope. SensorFusionManager have to have been started at least once to know if gyroscope is supported.
	 * @return True if gyroscope is supported.
	 */
	public boolean isGyroscopeSupported()
	{
		return mIsGyroscopeSupported;
	}
	
	/**
	 * Set reference pitch to current pitch.
	 */
	public void setReferencePitch()
	{
		mHasToResetPitch = true;
	
	}
	
	public void setReferenceYaw()
	{
		mHasToResetYaw = true;
	}
	
	public void resetPitch()
	{
		oPitch = 0.0f;
	}
	
	public void resetYaw()
	{
		oYaw = 0.0f;
	}
	
	public void resetRoll()
	{
		oRoll = 0.0f;
	}
	
	/* **********
	 * ACCESSORS
	 * *********/
	
	public float getPitch()
	{
		return mPitch;
	}
	
	public float getYaw()
	{
		return mYaw;
	}
	
	/**
	 * get the absolute roll the device, regardless screen's orientation.
	 * See getRelativeRoll() to adapt roll to screen orientation.
	 * @return
	 */
	public float getRoll() 
	{
		return mRoll;
	}
	
	/**
	 * get roll relative to device's current orientation( landscape or portrait).
	 * 
	 */
	public float getRelativeRoll()
	{
		final int screenRotation = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();	
		float relativeRoll = 90;
		switch (screenRotation)
		{
			case Surface.ROTATION_0:
				relativeRoll += 0.0f;
				break;
			case Surface.ROTATION_90:
				relativeRoll += 90.0f;
				break;
			case Surface.ROTATION_180:
				relativeRoll += 180.0f;
				break;
			default:
				relativeRoll += 270.0f;
				break;
		};
		
		relativeRoll %=360;
		relativeRoll+=mRoll;
		if(relativeRoll>180.01f)
		{
			relativeRoll-=360.0f;
		}
		return relativeRoll;
	}
	
	
	public float[] getFusedOrientation()
	{
	    return mOrientation;
	}
	
	public float[] getRotationMatrix()
	{
	    return mRotationMatrix;
	}
	
	
	public boolean isStable()
	{
		return this.isStable(THRESHOLD_ACCELERATION, THRESHOLD_ROTATION);
		
	}
	public boolean isStable(float threshold)
	{
		return this.isStable(threshold, threshold);
	}
	
	public boolean isStable(float accelerationThreshold, float rotationThreshold)
	{
		return (isAccelerometerStable(accelerationThreshold) && isGyroStable(rotationThreshold));
	}
	
	private boolean isAccelerometerStable(float threshold)
	{
		float x = mAccelerationValues[0];
		float y = mAccelerationValues[1];
		float z = mAccelerationValues[2];
		
		return ( x+y+z < 3*threshold);
	}
	
	private boolean isGyroStable(float threshold)
	{
		float pitch = mRotationValues[0];
		float yaw = mRotationValues[1];
		float roll = mRotationValues[2];

		
		return (pitch + yaw + roll < 3*threshold );
		
	}
	
	public boolean isStarted()
	{
		return mIsStarted;
	}
	
	/* *********
	 * PRIVATE METHODS
	 * ********/
	
	// This function registers sensor listeners for the accelerometer, magnetometer and gyroscope.
	private boolean registerListener(boolean useGyroscope)
	{
		if(mIsStarted)
		{
			return true;
		}
		
		//mIsGyroscopeSupported = false;
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
		
		boolean accelerationSupported = mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SENSOR_LISTENING_RATE);
		mIsStarted = mIsRotationSupported && accelerationSupported;
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
	    if(Math.abs(mPitch)<90-POLE_TRESHOLD)
		{
		    mYaw = mOrientation[0] * RAD_TO_DEG - oYaw;
			mRoll = mOrientation[2] * RAD_TO_DEG - oRoll;

		}

		mPitch = mOrientation[1] * RAD_TO_DEG - oPitch;
		
		this.normalize();
		
	}
	
	private void normalize()
	{
		
		mYaw %= 360.0f;
		mPitch %= 180.0f;

		if(mYaw>180.01f)
		{
			mYaw-=360.0f;
		}
		
		if(mRoll>180.01f)
		{
			mRoll-=360.0f;
		}
		
		if(mPitch>90.01f)
		{
			mPitch-=180.0f;
		}
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
			mFirstAccelerometerPitch = mLastAccelerometerPitch = mAccelerometerPitch = -1500.0f;
			mFirstMagneticHeading = mLastMagneticHeading = mMagneticHeading = -1500.0f;			
			
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
						
						//translate rotation matrix to represent phone's orientation
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
			
		}
	
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) 
		{}
	
		
		@Override
		public void onSensorChanged(SensorEvent event)
		{
			mListener.onSensorChanged(event);
		}
	}
	
	/**
	 *
	 */
	private void computeRotation(SensorEvent event)
	{
		float p, y, r, P, Y, R, lp, ly, lr;
		float alpha = ALPHA_ROTATION;
		float beta = BETA_ROTATION;
		float lambda = LAMBDA;
		//move the current values of rotation into last values
		lp = mCurrRotationValues[0];
		ly = mCurrRotationValues[1];
		lr = mCurrRotationValues[2];
		
		// get the actual values of angles
		p = getPitch();
		y = getYaw();
		r = getRoll();
		
		//update the current values
		mCurrRotationValues[0] = p;
		mCurrRotationValues[1] = y;
		mCurrRotationValues[2] = r;
		
		
		P = mRotationValues[0];
		Y = mRotationValues[1];
		R = mRotationValues[2];
		
		//compute smooth angles values
		P = (alpha*P + (beta)*Math.abs(p-lp))/lambda;
		Y = (alpha*Y + (beta)*Math.abs(y-ly))/lambda;
		R = (alpha*R + (beta)*Math.abs(r-lr))/lambda;
		
		mRotationValues[0] = P;
		mRotationValues[1] = Y;
		mRotationValues[2] = R;
		
		
	}
	


	private void computeAcceleration(SensorEvent event)
	{
		Assert.assertTrue(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER);
		
		float x, y, z, X, Y, Z, lx, ly, lz, alpha=ALPHA_ACCELERATION, beta=BETA_ACCELERATION;
		float lambda = LAMBDA;

		
		//move current values into last values
		//
		lx = mCurrAccelerometerValues[0];
		ly = mCurrAccelerometerValues[1];
		lz = mCurrAccelerometerValues[2];
	
		
		// get the actual acceleration values from event on x, y and z
		x = event.values[0];
		y = event.values[1];
		z = event.values[2];		
		
		//update current values
		mCurrAccelerometerValues[0] = x;
		mCurrAccelerometerValues[1] = y;
		mCurrAccelerometerValues[2] = z;
		
		
		// compute difference
		X = mAccelerationValues[0];
		Y = mAccelerationValues[1];
		Z = mAccelerationValues[2];
		
		X = (X*alpha + Math.abs((x-lx))*beta)/lambda;
		Y = (Y*alpha + Math.abs((y-ly))*beta)/lambda;
		Z = (Z*alpha + Math.abs((z-lz))*beta)/lambda;

		mAccelerationValues[0] = X;
		mAccelerationValues[1] = Y;
		mAccelerationValues[2] = Z;
	}

	@SuppressWarnings("unused")
	private void debugMonitor()
	{
		System.out.println("startingh debug");
		new Thread(new Runnable(){

			
			@Override
			public void run() {
				
				while(true){
				float x = mAccelerationValues[0];
				float y = mAccelerationValues[1];
				float z = mAccelerationValues[2];
				
				float pitch = mRotationValues[0];
				float yaw = mRotationValues[1];
				float roll = mRotationValues[2];
				
				System.out.println("=======GYRO============");
				System.out.println("pitch="+pitch);
				System.out.println("yaw="+yaw);
				System.out.println("roll="+roll);
				System.out.println("Stable : "+ isGyroStable(THRESHOLD_ROTATION));
				
				System.out.println("=======ACC============");
				System.out.println("x="+x);
				System.out.println("y="+y);
				System.out.println("z="+z);
				System.out.println("Stable : "+ isAccelerometerStable(THRESHOLD_ACCELERATION));
				
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				}
			}
			
			
		}).start();
		
	}
	

	

}
