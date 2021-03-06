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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

import junit.framework.Assert;
import fr.ensicaen.panandroid.snapshot.Snapshot;
import fr.ensicaen.panandroid.snapshot.SnapshotEventListener;
import fr.ensicaen.panandroid.stitcher.StitcherWrapper;
import fr.ensicaen.panandroid.tools.EulerAngles;
import fr.ensicaen.panandroid.tools.SensorFusionManager;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
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
 * @author Nicolas THIERION.
 * @author Saloua BENSEDDIK.
 *
 * @bug : raw callback stores 0 bytes files (IOException, NullPointerException)
 *
 * TODO : remove automatic exposure, zoom, etc..
 *
 */
public class CameraManager /* implements SnapshotObserver */
{
	public static final String TAG = CameraManager.class.getSimpleName();

	/* *************
	 * GLOBAL PARAMETERS
	 * *************/
	public static final int DEFAULT_PREVIEW_FORMAT = ImageFormat.NV21;

	private static final int JPEG_COMPRESSION = 90;

	/** filename prefixes for stored images **/
	public static final String DEFAULT_FILE_PREFIX = "img";

	/** accepted distance between targeted shoot and current one **/
	public static final float DEFAULT_AUTOSHOOT_THRESHOLD = 3.0f;

	/** vibration tolerance for autoShoot **/
	public static final float DEFAULT_AUTOSHOOT_PRECISION = 0.3f;

	/** raw callback enabled by default?? ie : if we want to save raw picture **/
	public static final boolean DEFAULT_SAVE_RAW = false;	//TODO : debug raw callback

	/** jpeg callback enabled by default?? **/
	public static final boolean DEFAULT_SAVE_JPEG = true;

	//tricky workaround to ensure that camera opening has finished before using it
	public static final int CAMERA_INIT_DELAY = 10000;
	private boolean mCameraIsBusy = true;
	//private int mTempSnapshotOrientation;

	/* *************
	 * ATTRIBUTES
	 * *************/

	/** Unique instance of CameraManager **/
	private static CameraManager mInstance = null;

	/** context f the application **/
	private Context mContext;


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
	private final AutoFocusCallback mAutoFocusCallback = new OnFocusCallback();
	
	private LinkedList<SnapshotEventListener> mListeners ;
	private static ReentrantLock mListenersLock;




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
	private ReentrantLock mTargetsLock;

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

	private int mJpegCompression = JPEG_COMPRESSION;

	//TODO : remove
	//private int mTempExifOrientation;





	private CameraManager()
	{
		mSensorListener = new SensorListener();
		mListenersLock = new ReentrantLock();
		mTargetsLock = new ReentrantLock();
		mListeners = new LinkedList<SnapshotEventListener>();

	}



	 /* **************
	  * PUBLIC METHODS
	  * *************/
	/**
	 * get the unique instance of CameraManager
	 * @return the CameraManager instance
	 */
	public synchronized static final CameraManager getInstance(Context context)
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
		mInstance.mContext = context;
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
		if(isOpen())
		{
			return true;
		}
		int nbCam = Camera.getNumberOfCameras();

		if(nbCam<=0)
		{
			Log.e(TAG, "opening camera "+camId+" failed");
			return false;
		}

		mCameraId = camId;
		mCamera = Camera.open(camId);

		mCameraParameters = mCamera.getParameters();
		mCameraParameters.set("orientation", "portrait");
		mCameraParameters.setJpegQuality(mJpegCompression);
		mCamera.setParameters(mCameraParameters);
		setPreviewFormat(DEFAULT_PREVIEW_FORMAT);

		Log.i(TAG, "opening camera "+camId);

		mCameraIsBusy = false;
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
		{
			if(isOpen())
				close();
			res = open(mCameraId);
			if(!res)
				return res;
		}

		if(mCameraParameters!=null)
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

	/**
	 * saves raw image on SD.
	 * @param enabled
	 */
	public void setRawEnabled(boolean enabled)
	{
		mRawCallbackEnabled = enabled;
	}

	/**
	 * saves jpeg image on SD.
	 * @param enabled
	 */
	public void setJpegEnabled(boolean enabled)
	{
		mJpegCallbackEnabled = enabled;
	}

	/**
	 * Set the directory where to store the captured pictures.
	 * @param dir
	 * @throws IOException - if the given directory is invalid or don't have write access.
	 */
	public void setTargetDirectory(String dir) throws IOException
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
		return mCameraParameters.getPreviewSize();
	}

	/**
	 *
	 * @return HFov in degrees.
	 */
	public float getHorizontalViewAngle()
	{
		return mCamera.getParameters().getHorizontalViewAngle();
	}

	/**
	 *
	 * @return VFov in degrees.
	 */
	public float getVerticalViewAngle()
	{
		return mCamera.getParameters().getVerticalViewAngle();
	}


	public boolean isReady()
	{
		return !mCameraIsBusy;
	}

	public boolean setSensorialCaptureEnabled(boolean enable)
	{

		if(enable)
		{
			mSensorFusionManager = SensorFusionManager.getInstance(mContext);
			boolean res = mSensorFusionManager.start();
			if (!res)
				mSensorFusionManager=null;
			return res;
		}
		if(mSensorFusionManager!=null)
		{
			mSensorFusionManager.stop();
		}
		return true;

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
	public void setAutoShootTargetList(LinkedList<EulerAngles> targets )
	{
		mTargetsLock.lock();

		mAutoShootTargets = new LinkedList<Snapshot>();
		for(EulerAngles a : targets)
			mAutoShootTargets.add(new Snapshot(a.getPitch(), a.getYaw()));

		mTargetsLock.unlock();

		if(isAutoShootEnabled())
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

	public void setJpegCompression(int compression)
	{
		mJpegCompression = compression;
	}

	 /* **************
	  * ACTIVITY-RELATED METHODS
	  * *************/
	public void onResume()
	{
		reOpen();
		this.startPreview();
		mSensorFusionManager.onResume();
	}

	public void onPause()
	{
		mCameraIsBusy = true;
		if(mCamera!=null)
		{
			mCamera.release();
			mCamera = null;
		}
		mSensorFusionManager.onPauseOrStop();
	}

	public void onClose()
	{
		close();
	}

	public void close()
	{
		if(mCamera!=null)
			mCamera.release();
		mCamera = null;
		mCameraIsBusy = false;
	}

	public void startPreview()
	{
		try
		{
			mCameraIsBusy=true;
			//start preview in a separate thread to avoid random pause of main activity
			new Thread(new Runnable(){

				@Override
				public void run()
				{
						try
						{
							mCamera.startPreview();
							mPreviewStarted = true;
							mCameraIsBusy=false;

						}
						catch(Exception e)
						{}
			    }
			}).start();
		}
		catch(RuntimeException e)
		{
			e.printStackTrace();
		}


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

		if(mCameraIsBusy)
		{
			Log.e(TAG, "trying to take a picture while camera is busy");
			return null;
		}

		if(!isOpen())
		{
			Log.e(TAG, "trying to take a picture while camera is closed");
			return null;
		}

		mCameraIsBusy = true;

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

		//TODO : remove
		//take a picture in separated thread
	/*	try
		{
			new Thread(new Runnable(){

				@Override
				public void run() {
					// Call to garbage collector to avoid bug http://code.opencv.org/issues/2961
				    //System.gc();

				    }


			}).start();
		}
		catch(RuntimeException e)
		{
			e.printStackTrace();
		}*/
        mCamera.autoFocus(mAutoFocusCallback);


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
		takePicture(filename);
		return mTempSnapshot;
	}


	/* *************
	 * LISTENER STUFFS
	 * ************/


	/**
	 * Methods triggered when a snapshot will be taken.
	 * @param listener - listener
	 * @return true if the listener correctly added.
	 */
	public boolean addSnapshotEventListener(SnapshotEventListener listener)
	{
		mListenersLock.lock();
			if(listener != null && !mListeners.contains(listener))
				return mListeners.add(listener);
		mListenersLock.unlock();
		return false;
	}

	/**
	 *
	 * @param listener
	 * @return
	 */
	public boolean removeSnapshotEventListener(SnapshotEventListener listener)
	{
		mListenersLock.lock();
		boolean res = mListeners.remove(listener);
		mListenersLock.unlock();
		return res;

	}

	/* *************
	 * PRIVATE CALLBACK CLASSES
	 * ************/

	private class OnFocusCallback implements AutoFocusCallback
	{

		@Override
        public void onAutoFocus(boolean success, Camera camera) {
			mCamera.takePicture(mShutterCallback, mRawCallback, mJpegCallback);
        }

	}
	/**
	 * If capture is sensorial, get current pitch and current yaw, and fill mTempSnapshot with it.
	 * @author Nicolas THIERION.
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
            float roll =  mSensorFusionManager.getRoll();

			// create the Snapshot Object corresponding
            mTempSnapshot = new Snapshot(pitch, yaw, roll);
        }
	}

	/**
	 * if raw callback enabled, save raw to sd.
	 * @author Nicolas THIERION.
	 * TODO : bug : save 0 byte raw
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
			Snapshot takenSnapshot = null;

			if(mJpegCallbackEnabled)
			{


				try
				{
					//save to sd card
					final String jpegFile = mTempFilename+".jpg";

					try
					{
		        		Log.i(TAG, "Saving file at "+jpegFile);
		        		Log.i(TAG, "setting file orientation at " +mTempSnapshot.getOrientation() + " degrees");


		        		FileOutputStream fos = new FileOutputStream(jpegFile);
						fos.write(data);
						fos.close();

						//perform image rotation in a separate thread
						//since cameraParams.setRotation don't work the same on all device, we can only rely on ourselves and implement
						//snapshot rotation by rotating Jpeg file.
						//exif won't work with openCV, the only solution is to rotate raw data.
						//Matrix rotation with java makes the heap to overflow, we hate to pass through JNI to rotate image.
						//Java matrix cannot be converted to OpenCV matrix, we need to transit through a file.
						//Matrix rotaion freeze the UI, need to launch processing on separate thread.... BUT
						//What a bad day!!
						final int orientation = mTempSnapshot.getOrientation();
							new Thread(new Runnable()
							{
								public void run()
								{
									try{
									int res = StitcherWrapper.rotateImage(jpegFile, orientation);
									Assert.assertTrue(res==0);
									}catch (Exception e)
									{
										e.printStackTrace();
									}
								}
							}).start();


						//StitcherWrapper.rotateImage(jpegFile, mTempSnapshot.getOrientation());

						mTempSnapshot.setFileName(jpegFile);
						takenSnapshot = mTempSnapshot;
						mTempSnapshot = null;
						mTempFilename = null;



						//put exifs
						//TODO : remove
						/*
						ExifInterface exif = new ExifInterface(jpegFile);
		        		String exifOrientation = ""+mTempExifOrientation;
		        		exif.setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation);
		        		exif.saveAttributes();
		        		exif = new ExifInterface(jpegFile);
						*/


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

			//Reset camera preview : on some devices, camera preview sometimes stops.
			startPreview();


			//tell camera is ready now
			mCameraIsBusy = false;

			if(takenSnapshot!=null)
			{
				mListenersLock.lock();
				for (SnapshotEventListener listener : new LinkedList<SnapshotEventListener>(mListeners))
					listener.onSnapshotTaken(data, takenSnapshot);
				mListenersLock.unlock();

			}
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
			if(!isOpen() || mCameraIsBusy)
				return;

			Assert.assertTrue(isAutoShootEnabled());
			Assert.assertTrue(mCamera!=null);

			float oPitch = mSensorFusionManager.getPitch();
			float oYaw = mSensorFusionManager.getYaw();
			float oRoll = mSensorFusionManager.getRelativeRoll();

			float distance;
			Snapshot os = new Snapshot(oPitch, oYaw, oRoll);
			//seek targets of a near one
			for (Snapshot snap: mAutoShootTargets)
			{

				distance = os.getDistanceRoll(snap);

		        if(distance<mAutoShootThreshold)
		        {
		        	if(mSensorFusionManager.isStable(mAutoShootPrecision))
		        	{
		        		Log.i(TAG, "taking snapshot at angle ("+oPitch+", "+oYaw+")");

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
	private String genAbsoluteFilename(String prefix)
	{

		final String path=mDirectory.getAbsolutePath()+File.separator;
		int id = 0;

		if(prefix.equals(mPrefix))
			id = mFileId;


		File fJpeg, fRaw;
		String absoluteFilename = path+prefix;
		/*
		fJpeg = new File(absoluteFilename+".jpg");
		fRaw = new File(absoluteFilename+".raw");
		if(fJpeg.exists() || fRaw.exists())*/
		{
			do
			{
				absoluteFilename = path+prefix+(id++);
				fJpeg = new File(absoluteFilename+".jpg");
				fRaw = new File(absoluteFilename+".raw");

			}while(fJpeg.exists() || fRaw.exists());
		}

		mFileId = id;
		return absoluteFilename;
	}


	public double getCameraResolution() {
		Camera.Size size = mCameraParameters.getPictureSize();

		double mpx = (double)(size.height * size.width) /1024000.0 ;
		return mpx;
	}



	public int getCameraResX() {
		Camera.Size size = mCameraParameters.getPictureSize();

		return size.width;
	}

	public int getCameraResY() {
		Camera.Size size = mCameraParameters.getPictureSize();

		return size.height;
	}

/*
	private int getCameraRotation()
	{
		int screenRot = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
		int rotation=90;
		switch(screenRot){
		case Surface.ROTATION_0:
			rotation+=0;
			break;
		case Surface.ROTATION_90:
			rotation-=90;
			break;
		case Surface.ROTATION_180:
			rotation-=180;
			break;
		case Surface.ROTATION_270:
			rotation-=270;
			break;
		}
		rotation+=360;
		return rotation%360;
	}
	private int getExifOrientation()
	{
		int screenRot = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
		int exifOrientation = 0;
		switch(screenRot){
		case Surface.ROTATION_0:
			exifOrientation=6;
			break;
		case Surface.ROTATION_90:
			exifOrientation=1;
			break;
		case Surface.ROTATION_180:
			exifOrientation=0;
			break;
		case Surface.ROTATION_270:
			exifOrientation=3;
			break;
		}
		return exifOrientation;
	}
*/
}
