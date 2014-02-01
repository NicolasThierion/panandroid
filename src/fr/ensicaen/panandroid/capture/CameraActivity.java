package fr.ensicaen.panandroid.capture;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;

public class CameraActivity extends Activity
{
	
	private static final String TAG = CameraActivity.class.getSimpleName();
	
	/* ************
	 * ATTRIBUTES
	 * ************/
	
	
	/** Camera manager **/
	CameraManager mCameraManager;
	
	@Override
	public void onCreate(Bundle savedInstance)
	{
		mCameraManager = CameraManager.getInstance();
		if(!mCameraManager.open())
		{
			Log.e(TAG,  "Device has no camera");
			//TODO : error msg
			return;
		}
		
		
	}
	
	@Override 
	public void onDestroy()
	{
		mCameraManager.onClose();
	}
	
	@Override
	public void onPause()
	{
		
	}

}
