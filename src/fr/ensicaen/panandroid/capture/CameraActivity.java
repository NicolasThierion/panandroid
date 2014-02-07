package fr.ensicaen.panandroid.capture;

import java.io.File;
import java.io.IOException;
import java.util.List;

import fr.ensicaen.panandroid.sensor.SensorFusionManager;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

public class CameraActivity extends Activity
{
	
	private static final String TAG = CameraActivity.class.getSimpleName();
	private static final String DEFAULT_TARGET_DIRECTORY = new File(Environment.getExternalStorageDirectory()
            + File.separator + "Panandroid").getName();
	private String mDirectory = DEFAULT_TARGET_DIRECTORY;
	private CameraManager mCameraManager;
	private Preview mPreview;
	Camera mCamera;
	PictureCallback jpeg;
	PictureCallback raw = new Camera.PictureCallback() {
		
		private SensorFusionManager mSensorFusionManager;

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			Bitmap bitmap = null;
			try {

				Log.d(TAG, "OnPictureTaken()");
				
				// convert data array to bitmap image
				int dataLength = data.length;
				bitmap = BitmapFactory.decodeByteArray(data, 0, dataLength);
				
				// get snapshot's pitch and yaw
				float pitch = mSensorFusionManager.getPitch();
                float yaw =  mSensorFusionManager.getYaw();
				
				// create the Snapshot Object corresponding
                Snapshot snapshot = new Snapshot(pitch, yaw);
				
								
				//save to sd card
				Log.d(TAG, "save bitmap to sdcard");
				String snapshotFile = mDirectory + File.separator + "snap" + snapshot.getId()
		                + ".bmp";
				
			} catch(Exception exc) {
	                exc.getMessage();
	                exc.printStackTrace();       
			}
		}
	};
	

	private boolean safeCameraOpen(int id) {
	    boolean qOpened = false;
	  
	    try {
	        releaseCameraAndPreview();
	        mCamera = Camera.open(id);
	        qOpened = (mCamera != null);
	    } catch (Exception e) {
	        e.printStackTrace();
	    }	    

	    return qOpened;    
	}
	
	

	private void releaseCameraAndPreview() {
		mPreview.setCamera(null);
	    if (mCamera != null) {
	        mCamera.release();
	        mCamera = null;
	    }
	}
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
	
	
	class Preview extends ViewGroup implements SurfaceHolder.Callback {

	    SurfaceView mSurfaceView;
	    SurfaceHolder mHolder;
		private Size mPreviewSize;
		private List<Size> mSupportedPreviewSizes;

	    Preview(Context context) {
	        super(context);

	        mSurfaceView = new SurfaceView(context);
	        addView(mSurfaceView);

	        // Install a SurfaceHolder.Callback so we get notified when the
	        // underlying surface is created and destroyed.
	        mHolder = mSurfaceView.getHolder();
	        mHolder.addCallback(this);
	        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	    }
	    
	    public void setCamera(Camera camera) {
	        if (mCamera == camera) { return; }
	        
	        stopPreviewAndFreeCamera();
	        
	        mCamera = camera;
	        
	        if (mCamera != null) {
	            List<Size> localSizes = mCamera.getParameters().getSupportedPreviewSizes();
	            mSupportedPreviewSizes = localSizes;
	            requestLayout();
	          
	            try {
	                mCamera.setPreviewDisplay(mHolder);
	            } catch (IOException e) {
	                e.printStackTrace();
	            }
	          
	            // Important: Call startPreview() to start updating the preview
	            // surface. Preview must be started before you can take a picture.
	            mCamera.startPreview();
	        }
	    }

		private void stopPreviewAndFreeCamera() {
			if (mCamera != null) {
		        // Call stopPreview() to stop updating the preview surface.
		        mCamera.stopPreview();
		    
		        // Important: Call release() to release the camera for use by other
		        // applications. Applications should release the camera immediately
		        // during onPause() and re-open() it during onResume()).
		        mCamera.release();
		    
		        mCamera = null;
		    }

			
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
		{
			// Now that the size is known, set up the camera parameters and begin
		    // the preview.
		    Camera.Parameters parameters = mCamera.getParameters();
		    parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
		    requestLayout();
		    mCamera.setParameters(parameters);

		    // Important: Call startPreview() to start updating the preview surface.
		    // Preview must be started before you can take a picture.
		    mCamera.startPreview();

			
		}

		@Override
		public void surfaceCreated(SurfaceHolder arg0) {
			
			
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder arg0) {
			// Surface will be destroyed when we return, so stop the preview.
		    if (mCamera != null) {
		        // Call stopPreview() to stop updating the preview surface.
		        mCamera.stopPreview();
		    }

			
		}

		@Override
		protected void onLayout(boolean arg0, int arg1, int arg2, int arg3,
				int arg4) {
			// TODO Auto-generated method stub
			
		}
	
	}
	
	public void takePicture() {
		mCamera.takePicture(null, raw, jpeg);
	}

}
