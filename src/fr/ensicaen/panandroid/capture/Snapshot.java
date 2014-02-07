package fr.ensicaen.panandroid.capture;

import android.graphics.Bitmap;

public class Snapshot
{
	private Bitmap mBitmap;
	
	private float mPitch;
	private float mYaw;
	private float mRoll;

	private String mFileName;
	
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
	}
	
	public Snapshot()
	{
	}
	
	
	
	public Bitmap getBitmap() {
		return mBitmap;
	}
	public void setBitmap(Bitmap mBitmap) {
		this.mBitmap = mBitmap;
	}
	public float getYaw()
	{
		return mYaw;
	}
	
	public float getPitch()
	{
		return mPitch;
	}
	
	public float getRoll()
	{
		return mRoll;
	}
	
	/*
	public void setPitch(float pitch)
	{
		mPitch = pitch;
	}
	
	public void setYaw(float yaw)
	{
		mYaw = yaw;
	}
	
	public void setRoll(float roll)
	{
		mRoll = roll;
	}
	*/
	public String getFileName() {
		return mFileName;
	}
	
	public void setFileName(String mFileName) {
		this.mFileName = mFileName;
	}
	
	


	public int getId() {
		//TODO
		return 0;
	}

	
	
}
