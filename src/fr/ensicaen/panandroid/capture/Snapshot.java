package fr.ensicaen.panandroid.capture;

import android.graphics.Bitmap;

public class Snapshot
{
	Bitmap mBitmap;
	float mYaw;
	float mPitch;
	//final float mRoll;
	String mFileName;
	
	public Snapshot(float yaw, float pitch)
	{
		mYaw = yaw;
		mPitch = pitch;
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
	public float getYaw() {
		return mYaw;
	}
	
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
		//TODO
		return 0;
	}
	
}
