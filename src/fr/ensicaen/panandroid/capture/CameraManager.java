package fr.ensicaen.panandroid.capture;

import java.io.IOException;
import junit.framework.Assert;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.util.Log;


/**
 * Helper to instantiate Camera, init parameter and redirect camera preview to a GL-Compatible texture.
 * 
 * @author Nicolas
 *
 */
public class CameraManager
{
	
	/* *************
	 * GLOBAL PARAMETERS
	 * *************/
	public static final int DEFAULT_PREVIEW_FORMAT = ImageFormat.NV21;
	public static final String TAG = CameraManager.class.getSimpleName();
	
	/* *************
	 * ATTRIBUTES
	 * *************/
	
	/** Unique instance of CameraManager **/
	private static CameraManager mInstance = null;
	
	/** Camera device **/
	private Camera mCamera;
	private int mCameraId;

	/** Camera parameters **/
	private Camera.Parameters mCameraParameters;
		
	/** Camera preview callback to process frames **/
	//TODO : to remove?
	///private CameraPreviewCallback mCameraCallback;

	private SurfaceTexture mSurfaceTexturePreview;
	


	private CameraManager(){}
	

	
	 /* **************
	  * PUBLIC STATIC METHODS
	  * *************/
	
	public static CameraManager getInstance()
	{
		if(mInstance==null)
		{
			synchronized (CameraManager.class)
			{
				if(mInstance==null)
				{
					mInstance = new CameraManager();
				}
			}
		}
		
		return mInstance;

	}
	
	/**
	 * Try to open default (facing back) camera.
	 * 
	 * @return true if open succeed.
	 */
	public boolean open()
	{
		return mInstance.open(Camera.CameraInfo.CAMERA_FACING_BACK);
	}
	
	/**
	 * Try to open the given camera.
	 * 
	 * @return true if open succeed.
	 */
	public boolean open(int camId)
	{
		int nbCam = Camera.getNumberOfCameras();
		
		if(nbCam<=0)
		{
			Log.e(TAG, "opening camera "+camId+" failed");
			return false;
		}
				
		mCameraId = camId;
		mCamera = Camera.open(camId);
		mCameraParameters = mCamera.getParameters();
		
		setPreviewFormat(DEFAULT_PREVIEW_FORMAT);

		Log.i(TAG, "opening camera "+camId);
		return true;
	}
	
	public boolean isOpen()
	{
		return mCamera!=null;
	}
	
	public boolean reOpen()
	{
		return open(mCameraId);
	}
	
	public void onResume()
	{
		Camera.open(mCameraId);
	}
	
	public void onPause()
	{
		mCamera.release();
	}
	
	public void onClose()
	{
		close();
	}
	
	public void close()
	{
		mCamera.release();
		mCamera = null;
	}
	
	public void startPreview()
	{
		mCamera.startPreview();

	}

	public void stopPreview()
	{
		mCamera.stopPreview();

	}
	
	/**
	 * Set the image format of the Camera's preview.
	 * If given format not supported, default to DEFAULT_PREVIEW_FORMAT.
	 * @param imageFormat
	 * @return true if format is supported.
	 */
	public boolean setPreviewFormat(int imageFormat)
	{
		boolean res;
		if(mCameraParameters.getSupportedPreviewFormats().contains(imageFormat))
		{
			res = true;
			mCameraParameters.setPreviewFormat(imageFormat);		
		}
		else
		{
			res = false;
			mCameraParameters.setPreviewFormat(DEFAULT_PREVIEW_FORMAT);
			
		}	

		return res;
	}
	

	/**
	 * Camera render to display by default. Redirect the preview to a custom surtfaceTexture.
	 * Camera has to be opened to set the preview texture. If it isn't opeed yet, the method will try to open it.
	 * @param texture
	 * @throws IOException if camera cannot be opened.
	 */
	public void setPreviewSurface(SurfaceTexture texture) throws IOException
	{
		Log.i(TAG, "redirectig preview to Texture");
		if(!isOpen())
		{
			this.open();
		}
		if(!isOpen())
			throw new IOException("Cannot open camera.");

		//restart preview 
		mCamera.stopPreview();
		
		//reset params
        mCamera.setParameters(mCameraParameters);

        //redirect preview
        mSurfaceTexturePreview = texture;
        mCamera.setPreviewTexture(mSurfaceTexturePreview);

        //TODO : to remove?
		///mCameraCallback = new CameraPreviewCallback(mCamera);
		///mCameraCallback.disable();
		
		mCamera.startPreview();
		
	}

	/**
	 * Camera has to be opened to set the preview texture. If it isn't opened yet, the method will try to open it.
	 * @param texture
	 * @throws IOException if camera cannot be opened.
	 */
	public Size getPreviewSize() throws IOException
	{
		if(!isOpen())
		{
			this.open();
		}
		if(!isOpen())
			throw new IOException("Cannot open camera.");
		return mCamera.getParameters().getPreviewSize();
	}

	
	
	
	
	/* *************
	 * PRIVATE METHODS
	 * ************/

	
	/* *************
	 * PRIVATE CLASS
	 * ************/
	
	//TODO : to remove?
	private class CameraPreviewCallback implements Camera.PreviewCallback
	{
		
		
		/** byte buffer where is stored camera frame preview **/
		private byte[] mFrameBytes ;
		private int mFrameBufferSize;
		
		/** camera that send the preview **/
		private Camera mCamera;
		
		/** if callback is enabled **/
		private boolean mEnabled;
		
		/**
		 * Creates a new camera callback and bind it to the given camera.
		 * @param camera
		 */
		public CameraPreviewCallback(Camera camera)
		{
			
			Camera.Parameters params = camera.getParameters();
			mCamera = camera;
			
			//computes frameBuffer's size according to chosen format
			Size size = params.getPreviewSize();
			int w = size.width, h = size.height;
			int prevFormat = params.getPreviewFormat();
			int bpp = ImageFormat.getBitsPerPixel(prevFormat);
			mFrameBufferSize = bpp*w*h;
			
			//adds this callback to the camera
			mFrameBytes = new byte[mFrameBufferSize];	
			Assert.assertTrue(mFrameBytes!=null);
			mCamera.addCallbackBuffer(mFrameBytes);		
			mCamera.setPreviewCallback(this);
		}
		
		public void enable()
		{
			mEnabled = true;
		}

		public void disable()
		{
			mEnabled = false;
		}
		
		@Override
		public void onPreviewFrame(byte[] frameBytes, Camera camera)
		{
			if(!mEnabled)
				return;
			
			
			Log.i("Preview Callback", "Receveid one frame");
			
			//re set a new buffer
			camera.setPreviewCallbackWithBuffer(this);	
			Assert.assertTrue(frameBytes!=null);
			camera.addCallbackBuffer(frameBytes);
		}
	}
	
	
}