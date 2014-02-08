package fr.ensicaen.panandroid.trash;

import fr.ensicaen.panandroid.capture.EulerAngles;

public class Mark implements EulerAngles {

	
	float mPitch;
	float mYaw;
	
	public Mark(float pitch, float yaw)
	{
		mPitch = pitch;
		mYaw = yaw;
	}

	@Override
	public float getYaw() {
		return mYaw;
	}

	@Override
	public float getPitch() {
		return mPitch;
	}
	
	float distance(EulerAngles m2)
	{
		return (float) Math.sqrt(Math.pow((mPitch - m2.getPitch()), 2) + Math.pow((mYaw - m2.getYaw()), 2)); 
	}

}
