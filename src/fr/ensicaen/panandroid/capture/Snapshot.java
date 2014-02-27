package fr.ensicaen.panandroid.capture;

/**
 *
 * A snapshot represents an image, tagged by its position. 
 * The position corresponds to the pitch and the yaw of the device when the picture has been captured.
 * The image is identified by its filename.
 * @author Saloua
 * @author Nicolas
 *
 */
public class Snapshot implements EulerAngles
{
	private static int GLOBAL_ID = 0;
	
	
	private float mPitch;
	private float mYaw;
	private float mRoll;

	private String mFileName = null;
	

	private int mId;
	

	/**
	 * Create a new snapshot, marked with the position given by pitch and yaw.
	 * The pitch get normalized between -90 and 90.
	 * The yaw get normalized between -180 and 180.
	 * @param pitch - pitch of the snapshot.
	 * @param yaw - yaw of the snapshot.
	 */
	public Snapshot(float pitch, float yaw)
	{
		this(pitch, yaw, 0.0f);

	}
	
	/**
	 * Create a new snapshot, marked with the position given by pitch and yaw.
	 * The pitch get normalized between -90 and 90.
	 * The yaw get normalized between -180 and 180.
	 * The roll get normalized between -180 and 180.
	 * @param pitch - pitch of the snapshot.
	 * @param yaw - yaw of the snapshot.
	 * @param roll - roll of the snapshot.
	 */
	public Snapshot(float pitch, float yaw, float roll)
	{
		mYaw = yaw%360.0f;
		mPitch = pitch%180.0f;
		mRoll = roll%360.0f;

		//normalize angles
		if(mYaw>180.0001f)
			mYaw-=360.0f;
		
		if(mPitch>90.0001f)
			mPitch-=180.0f;
		
		if(mRoll>180.0001f)
			mRoll-=360.0f;
		
		mId = GLOBAL_ID++;	
	}




	@Override
	public float getYaw()
	{
		return mYaw;
	}
	

	@Override
	public float getPitch() {
		return mPitch;
	}
	
	@Override
	public float getRoll()
	{
		return mRoll;
	}

	public int getId() {
		return mId;
	}
	

	public String getFileName()
	{
		return mFileName;
	}
	
	public void setFileName(String mFileName) {
		this.mFileName = mFileName;
	}
	
	

	
}
