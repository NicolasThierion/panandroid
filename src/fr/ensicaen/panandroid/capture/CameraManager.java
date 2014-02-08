package fr.ensicaen.panandroid.capture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;

import junit.framework.Assert;
import fr.ensicaen.panandroid.sensor.SensorFusionManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;


/**
 * Helper to instantiate Camera, initialize parameter and redirect camera preview to a GL-Compatible texture.
 * Capable of tagging pictures (formally Snapshots objects) with their pitch and yaw).
 * Can automate capture with autoShoot(), given a list of targets.
 * 
 * @author Nicolas
 * @author Saloua
 *
 */
public class CameraManager
{
	public static final String TAG = CameraManager.class.getSimpleName();

	/* *************
	 * GLOBAL PARAMETERS
	 * *************/
	public static final int DEFAULT_PREVIEW_FORMAT = ImageFormat.NV21;
	
	/** filename prefixes for stored images **/
	public static final String DEFAULT_FILE_PREFIX = "img";

	/** accepted distance between targeted shoot and current one **/
	public static final float DEFAULT_AUTOSHOOT_THRESHOLD = 1.0f;

	/** vibration tolerance for autoShoot **/
	public static final float DEFAULT_AUTOSHOOT_PRECISION = 0.1f;
	
	/** raw callback enabled by default?? **/
	public static final boolean DEFAULT_SAVE_RAW = true;
	
	/** jpeg callback enabled by default?? **/
	public static final boolean DEFAULT_SAVE_JPEG = true;
	
	//tricky workaround to ensure that camera opening has finished before using it
	public static final int CAMERA_INIT_DELAY = 3000;
	boolean mCameraLoaded = false;
	/* *************
	 * ATTRIBUTES
	 * *************/
	
	/** Unique instance of CameraManager **/
	private static CameraManager mInstance = null;
	
	/* ***
	 * camera
	 * ***/
	/** Camera device, id and parameters**/
	private Camera mCamera;
	private int mCameraId;
	private Camera.Parameters mCameraParameters;

	/* ***
	 * preview
	 * ***/
	/** SurfaceTexture where preview is redirected **/
	private SurfaceTexture mSurfaceTexturePreview;
	
	/** if preview is started **/
	private boolean mPreviewStarted;
	
	/* ***
	 * callbacks
	 * ***/
	/** callback enabled **/
	private boolean mRawCallbackEnabled = DEFAULT_SAVE_RAW;
	private boolean mJpegCallbackEnabled = DEFAULT_SAVE_JPEG;
	
	/**callbacks **/
	private final ShutterCallback mShutterCallback = new OnShutterCallback();
	private final PictureCallback mRawCallback = new RawCallback();
	private final PictureCallback mJpegCallback = new JpegCallback();
	
	/* ***
	 * auto shoot
	 * ***/
	/** Sensor manager used if sensorial capture is enabled **/
	private SensorFusionManager mSensorFusionManager = null;
		
	/** autoshoot precision **/
	private float mAutoShootPrecision = DEFAULT_AUTOSHOOT_PRECISION;
	
	/** autoshoot tolerance **/
	private float mAutoShootThreshold = DEFAULT_AUTOSHOOT_THRESHOLD;
	
	/** targeted points by auto shoot **/
	private LinkedList<Snapshot> mAutoShootTargets;
	
	private final SensorListener mSensorListener;

	/* ***
	 * file system
	 * ***/
	/** current Filename **/
	private volatile String mTempFilename;
	
	/** directory where to store captured pictures. Must be set through setDirectory**/
	private File mDirectory = null;
	
	/** current snapshot if sensorial capture is enabled **/
	private Snapshot mTempSnapshot;
	
	/** prefix of stored image files **/
	private String mPrefix = DEFAULT_FILE_PREFIX;
	
	/** current picture file id **/
	private int mFileId = 0;
	
	
	
	

	private CameraManager()
	{
		mSensorListener = new SensorListener();
	}
	

	
	 /* **************
	  * PUBLIC METHODS
	  * *************/
	/**
	 * get the unique instance of CameraManager
	 * @return the CameraManager instance
	 */
	public synchronized static final CameraManager getInstance()
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
	public synchronized boolean open(int camId)
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
		
		
		new Thread(new Runnable(){
			public void run()
			{
				try {
					Thread.sleep(CAMERA_INIT_DELAY);
				} catch (InterruptedException e) {}
				mCameraLoaded = true;
			}
		}).start();
		
		return true;
	}
	
	public synchronized boolean isOpen()
	{
		return mCamera!=null;
	
	}
	
	/**
	 * reopen the camera.
	 * @return
	 */
	public synchronized boolean reOpen()
	{
		boolean res;
		//synchronized(mCamera)
		{
			if(isOpen())
				close();
			res = open(mCameraId);
			if(!res)
				return res;
		}
		
		
		if(this.mCameraParameters!=null)
		{
			mCamera.setParameters(mCameraParameters);
		}
		if(mSurfaceTexturePreview!=null)
		{
			try {
				mCamera.setPreviewTexture(mSurfaceTexturePreview);
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		if(mPreviewStarted)
			startPreview();
		return res;
	}
	
	
	 /* **************
	  * ACCESSORS
	  * *************/
	public void setRawEnabled(boolean enabled)
	{
		mRawCallbackEnabled = enabled;
	}
	
	public void setJpegEnabled(boolean enabled)
	{
		mJpegCallbackEnabled = enabled;
	}
	
	/**
	 * Set the directory where to store the captured pictures.
	 * @param dir
	 * @throws IOException - if the given directory is invalid or don't have write access.
	 */
	public void setTargetDir(String dir) throws IOException
	{
		mDirectory = new File(dir);
		if(mDirectory == null)
			throw new IOException("the directory "+dir+"is not valid");
		

		if(!mDirectory.exists())
			mDirectory.mkdirs();
		
		if( !mDirectory.isDirectory())
			throw new IOException("the file "+dir+"is not a directory");
		
		if(!mDirectory.canWrite())
		{
			throw new IOException("unable to write in the directory "+dir);
		}
		
		Log.d(TAG, "Setting camera dir to "+mDirectory.getAbsolutePath());
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
	 * Camera has to be opened to set the preview texture. If it isn't opened yet, the method will try to open it.
	 * @param texture
	 * @throws IOException if camera cannot be opened.
	 */
	public synchronized void setPreviewSurface(SurfaceTexture texture) throws IOException
	{
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

      
		
		startPreview();
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
	
	public void setSensorFusionManager(SensorFusionManager sensorFusionManager )
	{
		mSensorFusionManager = sensorFusionManager ;
	}
	
	/**
	 * Set prefix for the filename of stored pictures.
	 * @param prefix
	 */
	public void setFilePrefix(String prefix)
	{
		mFileId = 0;
		mPrefix = prefix;
		
		if(prefix == null)
		{
			mPrefix = DEFAULT_FILE_PREFIX;
		}
	}

	/**
	 * Capture is sensorial when a sensorFusionManager has been set.
	 * @return
	 */
	public boolean isSensorialCaptureEnabled()
	{
		return mSensorFusionManager!=null;
	}
	
	/**
	 * Tell if autoShoot is enabled (if sensorial capture enabled and valid target list provided)
	 * @return true if autoshoot is enabled
	 */
	public boolean isAutoShootEnabled()
	{
		return (isSensorialCaptureEnabled() && mAutoShootTargets!=null && mAutoShootTargets.size() !=0);
	}
	
	/**
	 * Provide a list of target that cameraManager will shoot automatically when the device will be correctly oriented.
	 * Must have called setSensorFusionManager once first. Setting target to null disable autoShoot.
	 * @param targets
	 */
	public void setAutoShootTargetList(LinkedList<Snapshot> targets )
	{
		mAutoShootTargets = targets;
		if(this.isAutoShootEnabled())
			mSensorFusionManager.addSensorEventListener(mSensorListener);
		else
			mSensorFusionManager.removeSensorEventListener(mSensorListener);

	}
	
	/**
	 * Configure threshold for autoShoot (Distance tolerance between target and current orientation).
	 * @param threshold
	 */
	public void setAutoShootThreshold(float threshold)
	{
		mAutoShootThreshold = threshold;
	}
	
	/**
	 * Configure autoShoot precision (movement tolerance at capture time).
	 * @param precision
	 */
	public void setAutoShootPrecision(float precision)
	{
		mAutoShootPrecision = precision;
	}
	
	public void setPreviewOrientation(int degrees)
	{
		if(!isOpen())
			open();
		mCamera.setDisplayOrientation(degrees);

	}
	
	 /* **************
	  * ACTIVITY-RELATED METHODS
	  * *************/
	public void onResume()
	{
		reOpen();
	}
	
	public void onPause()
	{
		mCameraLoaded = false;
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
		mCameraLoaded = false;
	}
	
	public void startPreview()
	{
		mPreviewStarted = true;
		mCamera.startPreview();

	}

	public void stopPreview()
	{
		mPreviewStarted = false;
		mCamera.stopPreview();

	}
	
	/**
	 * take a picture, and store it to the current target directory.
	 * setTargetDir() must have been called once first.
	 * @return path to the created file.
	 */
	public String takePicture()
	{
		return this.takePicture(mPrefix);
	}
	
	/**
	 * take a picture, and store it to the current target directory.
	 * setTargetDir() must have been called once first.
	 * @param filename - Name of the picture file to write on storage.
	 * @return path to the created file.
	 */
	public String takePicture(String filename)
	{
		if(mDirectory == null)
		{
			Log.e(TAG, "Cannot save picture : No directory has been set");
		}
		Assert.assertTrue(mCamera!=null);
		Assert.assertTrue(mShutterCallback!=null);
		Assert.assertTrue(mRawCallback!=null);
		Assert.assertTrue(mJpegCallback!=null);
		
		mTempFilename = genAbsoluteFilename(filename);
		filename = mTempFilename;
		mCamera.takePicture(mShutterCallback, mRawCallback, mJpegCallback);
		
		
		return filename;
	}
	
	
	/**
	 * Take a picture, and store current pitch and yaw side by the picture into a Snapshot object.
	 * setSensorFusionManager() must have been called once first, as setTargetDir().
	 * @return the taken snapshot.
	 */
	public Snapshot takeSnapshot()
	{
		return this.takeSnapshot(mPrefix);
	}
	
	/**
	 * Take a picture, and store current pitch and yaw side by the picture into a Snapshot object.
	 * setSensorFusionManager() must have been called once first, as setTargetDir().
	 * @param filename - Name of the picture file to write on storage.
	 * @return the taken snapshot.
	 */
	public Snapshot takeSnapshot(String filename)
	{
		Assert.assertTrue(mDirectory!=null);
		if(mSensorFusionManager == null)
		{
			Log.w(TAG, "Trying to take a 'sensored snapshot' while no sensorFusionanager has been provided. Forget to call setSensorFusionManager() ?");
		}
		this.takePicture(filename);
		return mTempSnapshot;
	}


	
	
	
	
	
	/* *************
	 * PRIVATE CALLBACK CLASSES
	 * ************/
	
	/**
	 * If capture is sensorial, get current pitch and current yaw, and fill mTempSnapshot woth it
	 * @author Nicolas
	 *
	 */
	private class OnShutterCallback implements ShutterCallback
	{

		@Override
		public void onShutter() 
		{
			if(!isSensorialCaptureEnabled())
				return;
			
			// get snapshot's pitch and yaw
			float pitch = mSensorFusionManager.getPitch();
            float yaw =  mSensorFusionManager.getYaw();
			
			// create the Snapshot Object corresponding
            mTempSnapshot = new Snapshot(pitch, yaw);		
        }
	}
	
	/**
	 * if raw callback enabled, save raw to sd.
	 * @author Nicolas
	 *
	 */
	private class RawCallback implements PictureCallback
	{

		@Override
		public void onPictureTaken(byte[] data, Camera camera)
		{
			if(!mRawCallbackEnabled)
				return;
			
			Assert.assertTrue(mTempFilename!=null);;
			//synchronized(mTempFilename)
			{
				try
				{
					Log.d(TAG, "OnPictureTaken()");
										
					//save to sd card
					String rawFile = mTempFilename+".raw";
					
					try 
					{
		        		Log.i(TAG, "Saving file at "+rawFile);
	
				        FileOutputStream fos = new FileOutputStream(rawFile);
				        fos.write(data);
				        fos.close();
	
				    } 
					catch (FileNotFoundException e) 
					{
				        Log.d(TAG, "File not found: " + e.getMessage());
				    }
					catch (IOException e) 
					{
				        Log.d(TAG, "Error accessing file: " + e.getMessage());
				    }
			
				} 
				catch(Exception e) 
				{
		                e.getMessage();
		                e.printStackTrace();       
				}
			}
		}
		
	}
	
	/**
	 * if jpeg callback enabled, save jpeg to sd.
	 * @author Nicolas
	 *
	 */
	private class JpegCallback implements PictureCallback
	{
		@Override
		public void onPictureTaken(byte[] data, Camera camera)
		{
			Assert.assertTrue(mTempFilename!=null);
			//synchronized(mTempFilename)
			{
				if(!mJpegCallbackEnabled)
					return;
				try 
				{
					//save to sd card
					String jpegFile = mTempFilename+".jpg";
					
					try 
					{
		        		Log.i(TAG, "Saving file at "+jpegFile);
	
				        FileOutputStream fos = new FileOutputStream(jpegFile);
				        fos.write(data);
				        fos.close();
				        mTempSnapshot.setFileName(mTempFilename);
						mTempFilename = null;

	
				    } 
					catch (FileNotFoundException e) 
					{
				        Log.d(TAG, "File not found: " + e.getMessage());
				    } 
					catch (IOException e) 
					{
				        Log.d(TAG, "Error accessing file: " + e.getMessage());
				    }
				}
				catch(Exception e) 
				{
		                e.getMessage();
		                e.printStackTrace();       
				}
			}
			
			//for unknown reason, taking picture sometimes stops preview. Ensure that preview will keep started.
			//reOpen();
			mCamera.startPreview();
		}
		
	}
	
	/* *************
	 * PRIVATE SENSORLISTENER CLASS
	 * ************/
	private class SensorListener implements SensorEventListener
	{
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy){/*NOP*/}



		@Override
		public void onSensorChanged(SensorEvent event)
		{
			if(!isOpen() || !mCameraLoaded)
				return;
			
			Assert.assertTrue(isAutoShootEnabled());
			Assert.assertTrue(mCamera!=null);
			
			float oPitch = mSensorFusionManager.getPitch();
			float oYaw = mSensorFusionManager.getYaw();
			
			float sPitch , sYaw, dPitch, dYaw, distance;
			
			//seek targets of a near one
			for (Snapshot snap: mAutoShootTargets)
			{
				sPitch = snap.getPitch();
		        sYaw = snap.getYaw();
		        
		        dPitch = oPitch - sPitch;
		        dYaw = oYaw - sYaw;
		        
		        distance = (float) Math.sqrt(Math.pow(dPitch, 2)+Math.pow(dYaw, 2));
		        if(distance<mAutoShootThreshold)
		        {
		        	if(mSensorFusionManager.getShake()<mAutoShootPrecision)
		        	{
		        		Log.i(TAG, "taking snapshot at angle ("+oPitch+", "+oYaw+")");
		        		//TODO : save snapshot
		        		mInstance.takeSnapshot();
		        		mAutoShootTargets.remove(snap);
		        		return;
		        	}
		        }
			}
		}
	}
	
	

	
	/* *************
	 * PRIVATE FUNCTIONS
	 * ************/
	
	/**
	 * Generate a complete filename given the provided prefix.
	 */
	private String genAbsoluteFilename(String filename)
	{
		
		final String path=mDirectory.getAbsolutePath()+File.separator;
		int id = 0;
		
		if(filename.equals(mPrefix))
			id = mFileId;
		
		String absoluteFilename = path+filename;
		
		File fJpeg = new File(absoluteFilename+".jpg");
		File fRaw = new File(absoluteFilename+".raw");
		if(fJpeg.exists() || fRaw.exists())
		{
			do
			{
				absoluteFilename = path+filename+(id++);
				fJpeg = new File(absoluteFilename+".jpg");
				fRaw = new File(absoluteFilename+".raw");

			}while(fJpeg.exists() || fRaw.exists());
		}
		
		mFileId = id;
		return absoluteFilename;
	}

	
	
	
	
	
	
}
