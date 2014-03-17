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

	/**
	 * get the distance between this snapshot and the given eulerAngle, regardless of the roll.
	 * See "getDistanceRoll()" for taking roll in account.
	 * @param eulerAngle
	 * @return
	 */
	public float getDistance(EulerAngles eulerAngle) {
		float d;
		
		Snapshot s = new Snapshot(eulerAngle.getPitch(), eulerAngle.getYaw(), eulerAngle.getRoll());
		float dPitch, dYaw;
		
		
		dPitch = Math.abs(mPitch - s.mPitch);
		dYaw =  Math.abs(mYaw-s.mYaw);
		if(dYaw>180.0f)
		{
			dYaw = 360.0f - dYaw;
		}
		

		
		float pitchCoef = (float) Math.cos(Math.toRadians(Math.max(Math.abs(mPitch),  Math.abs(s.mPitch))));
		d = (dYaw * pitchCoef + dPitch);
		
		return d;
	}

	/**
	 * get the distance between this snapshot and the given eulerAngle, taking the roll in account.
	 * @param eulerAngle
	 * @return
	 */
	public float getDistanceRoll(EulerAngles eulerAngle)
	{
		float d;
		
		Snapshot s = new Snapshot(eulerAngle.getPitch(), eulerAngle.getYaw(), eulerAngle.getRoll());
		float dPitch, dYaw, dRoll;
		
		
		dPitch = Math.abs(mPitch - s.mPitch);
		dYaw =  Math.abs(mYaw-s.mYaw);
		if(dYaw>180.0f)
		{
			dYaw = 360.0f - dYaw;
		}
		
		dRoll = Math.abs(mRoll - s.mRoll);
		if(dRoll>180.0f)
		{
			dRoll = 360.0f - dRoll;;
		}
		
		float pitchCoef = (float) Math.cos(Math.toRadians(Math.max(Math.abs(mPitch),  Math.abs(s.mPitch))));
		d = (dRoll*pitchCoef + dYaw * pitchCoef + dPitch);
		
		return d;
	}
}
