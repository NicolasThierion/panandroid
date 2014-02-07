package fr.ensicaen.panandroid.capture;

import android.graphics.Bitmap;

public class Snapshot implements EulerAngles
{
	
	private static int GLOBAL_ID = 0;
	private Bitmap mBitmap;
	private float mYaw;
	private float mPitch;
	//final float mRoll;
	private String mFileName;
	private int mId;
	


	public Snapshot(float pitch, float yaw)
	{
		mYaw = yaw;
		mPitch = pitch;
		mId = GLOBAL_ID++;
	}
	
	public Snapshot()
	{
		mId = GLOBAL_ID++;
	}
	
	
	
	public Bitmap getBitmap() {
		return mBitmap;
	}
	public void setBitmap(Bitmap mBitmap) {
		this.mBitmap = mBitmap;
	}
	
	@Override
	public float getYaw() {
		return mYaw;
	}
	
	@Override
	public float getPitch() {
		return mPitch;
	}

	public String getFileName() {
		return mFileName;
	}
	
	public void setFileName(String mFileName) {
		this.mFileName = mFileName;
	}
	
	
	public void setPitch(float pitch)
	{
		mPitch = pitch;
	}
	
	public void setYaw(float yaw)
	{
		mYaw = yaw;
	}

	public int getId() {
		return mId;
	}
	
	
}
