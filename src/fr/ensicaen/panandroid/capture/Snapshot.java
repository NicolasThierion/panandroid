package fr.ensicaen.panandroid.capture;

/**
 *
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

	private String mFileName;
	

	private int mId;
	


	public Snapshot(float pitch, float yaw)
	{
		mYaw = yaw%360.0f;
		mPitch = pitch%180.0f;

		if(mYaw>180.0f)
		{
			mYaw-=360.0f;
		}
		
		if(mPitch>90.0f)
		{
			mPitch-=180.0f;
		}
		mId = GLOBAL_ID++;

	}
	
	
	
	
	

	@Override
	public float getYaw() {
		return mYaw;
	}
	

	@Override
	public float getPitch() {
		return mPitch;
	}
	
	public float getRoll()
	{
		return mRoll;
	}
	
	

	public int getId() {
		return mId;
	}
	

	public String getFileName() {
		return mFileName;
	}
	
	public void setFileName(String mFileName) {
		this.mFileName = mFileName;
	}
	
	

	
}
