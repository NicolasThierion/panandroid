package fr.ensicaen.panandroid.sensor;

import java.util.LinkedList;

import fr.ensicaen.panandroid.capture.EulerAngles;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Surface;
import android.view.WindowManager;

public class SensorFusionManager2 implements SensorEventListener, EulerAngles
{

	/* *********
	 * GLOBAL CONSTANTS
	 * ********/
	private static final String TAG = SensorFusionManager2.class.getSimpleName();
	private static final float RAD_TO_DEG =  (float) (180.0f / Math.PI);
	private static final float POLE_TRESHOLD = 2.0f;
	
	private static final int REFRESH_RATE = 100;	//10ms update

	/* *******
	 * ATTRIBUTES
	 * *******/
	/** Sensor manager **/
	private SensorManager mSensorManager;
	
	/** current context of sensor **/
	private Context mContext;

	
	private Sensor mRotationVectorSensor;

	/** corresponding rotation matrix **/
	private float[] mRotationMatrix = new float[16];	
	
	/** vector that represents orientation of the device **/
	private float[] mOrientation = new float[3];
	
	/**current pitch and yaw **/
	private float mPitch=-1500.0f;
	private float mYaw=-1500.0f;
	private float mRoll=0.0f;

	/** List of all listeners that listen to this**/
	LinkedList<SensorEventListener> mListeners = new LinkedList<SensorEventListener>();
	
	
	/* *******
	 * CONSTRUCTOR
	 * *******/
	public SensorFusionManager2(Context context)
	{
		mContext = context;
		
		// Get an instance of the SensorManager
		mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
		
		// initialize the rotation matrix to identity
        mRotationMatrix[ 0] = 1;
        mRotationMatrix[ 4] = 1;
        mRotationMatrix[ 8] = 1;
        mRotationMatrix[12] = 1;
	
	}
	
	

	/* *******
	 * METHODS
	 * *******/
	public boolean start()
	{
		// find the rotation-vector sensor
        mRotationVectorSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if(mRotationVectorSensor!=null)
        {
        	// enable our sensor when the activity is resumed, ask for
            // 10 ms updates.
            mSensorManager.registerListener(this, mRotationVectorSensor, REFRESH_RATE);
        }
        
        return mRotationVectorSensor!=null;
		
	}
	
	public void stop()
	{
		// make sure to turn our sensor off when the activity is paused
		mSensorManager.unregisterListener(this);
	}
	
	
	/* *******
	 * ACCESSORS
	 * *******/

	@Override
	public float getPitch() {
		return mPitch;
	}

	@Override
	public float getYaw() 
	{
		return mYaw;
	}
	
	/**
	 * get the absolute roll the device, regardless screen's orientation.
	 * See getRelativeRoll() to adapt roll to screen orientation.
	 * @return
	 */
	@Override
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

	public float[] getRotationMatrix()
	{
		return mRotationMatrix;
	}

	public boolean isStable(float mAutoShootPrecision)
	{
		return false;
	}
	
	
	
	/* *******
	 * SENSORLISTENER STUFF
	 * *******/
	
	@Override
	public void onAccuracyChanged(Sensor sensor, int arg1) {}

	@Override
	public void onSensorChanged(SensorEvent event) 
	{
		// check that we received the proper event
		if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) 
		{
		    // convert the rotation-vector to a 4x4 matrix.
		    SensorManager.getRotationMatrixFromVector(mRotationMatrix , event.values);
		    
		    // Remap the axes
		    SensorManager.remapCoordinateSystem(mRotationMatrix, SensorManager.AXIS_MINUS_Z,
		            SensorManager.AXIS_X, mRotationMatrix);
		
		    // save remapped orientation vector
		    SensorManager.getOrientation(mRotationMatrix, mOrientation);
		    
		
		}		
		
		// save pitch and yaw.
	    if(Math.abs(mPitch)<90-POLE_TRESHOLD)
		{
		    mYaw = mOrientation[0] * RAD_TO_DEG;
			mRoll = mOrientation[2] * RAD_TO_DEG;

		}

		mPitch = mOrientation[1] * RAD_TO_DEG;
		
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
}
