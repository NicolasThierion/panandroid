package fr.ensicaen.panandroid.stitcher;
 
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.highgui.Highgui;

import fr.ensicaen.panandroid.R;
import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
 
public class StitcherActivity extends Activity implements CvCameraViewListener {
    private static final String TAG = "OCVSample::Activity";
 
    public static final int VIEW_MODE_RGBA = 0;
    public static final int SAVE_IMAGE_MAT = 1;
    public static final int CAPT_STILL_IM = 2;
    private static int viewMode = VIEW_MODE_RGBA;
//  public static int image_count = 0;
    private MenuItem mStitch;
    private MenuItem mItemCaptureImage;
    private Mat mRgba;
    private Mat mGrayMat;
    private Mat panorama;
    private Mat mtemp;
    private List < Mat > images_to_be_stitched = new ArrayList < Mat >();
    private CameraBridgeViewBase mOpenCvCameraView;
    private long mPrevTime = new Date().getTime();
    private static final int FRAME2GRAB = 10;
    private int mframeNum = 0;
    private static final File tempImageDir = new File(Environment.getExternalStorageDirectory() + File.separator + "panoTmpImage");
    private static final File StitchImageDir = new File(Environment.getExternalStorageDirectory()+ File.separator  + "panoStitchIm");
    private static final String mImageName = "im";
    private static final String mImageExt = ".jpeg";
    private long recordStart = new Date().getTime();
    private static final long MAX_VIDEO_INTERVAL_IN_SECONDS = 3 * 1000; // Convert milliseconds to seconds
    public final Handler mHandler = new Handler();
 
    // Create runnable for posting
    final Runnable mUpdateResults = new Runnable() {
        public void run() {
            updateResultsInUi();
        }
    };
 
    private void updateResultsInUi()
    {
 
    }
 
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
            case LoaderCallbackInterface.SUCCESS: {
                Log.i(TAG, "OpenCV loaded successfully");
 
                // Load native library after(!) OpenCV initialization
                System.loadLibrary("ocvstitcher");
 
                mOpenCvCameraView.enableView();
            }
                break;
            default: {
                super.onManagerConnected(status);
            }
                break;
            }
        }
    };
 
    public StitcherActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }
 
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
 
        setContentView(R.layout.stitcherview);
 
        final Button btnVidCapt = (Button) findViewById(R.id.btnVidCapt);
        btnVidCapt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startVidCap();
            }
        });
 
        final Button btnStitch = (Button) findViewById(R.id.btnStitch);
        btnStitch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stitchImages();
            }
        });
 
        final Button btnViewStitchedIm = (Button) findViewById(R.id.btnViewStitchedIm);
        btnViewStitchedIm.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                viewStitchImages();
            }
        });
 
        final Button btnCapStil = (Button) findViewById(R.id.btnCapStil);
        btnCapStil.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                captStillImage();
            }
        });
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial4_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }
 
    @Override
    public void onPause() {
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
        super.onPause();
    }
 
    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
                mLoaderCallback);
    }
 
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }
 
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC3);
        mGrayMat = new Mat(height, width, CvType.CV_8UC1);
        mtemp = new Mat(height, width, CvType.CV_8UC3);
        panorama = new Mat(height, width, CvType.CV_8UC3);
    }
 
    public void onCameraViewStopped() {
        mRgba.release();
        mGrayMat.release();
        mtemp.release();
        panorama.release();
    }
 
    public Mat onCameraFrame(Mat inputFrame) {
        inputFrame.copyTo(mRgba);
        switch (StitcherActivity.viewMode) {
        case StitcherActivity.VIEW_MODE_RGBA: {
            Core.putText(mRgba, "Video Mode", new Point(10, 50), 3, 1, new Scalar(255, 0, 0, 255), 2);
            // Update start recordtime until starting recording
        }break;
        case StitcherActivity.SAVE_IMAGE_MAT: {
            long curTime = new Date().getTime();
            Core.putText(mRgba, "Record Mode", new Point(10, 50), 3, 1, new Scalar(255, 0, 0, 255), 2);
            long timeDiff = curTime - recordStart;
            Log.i("timeDiff", Long.toString(timeDiff));
 
            if ( timeDiff < MAX_VIDEO_INTERVAL_IN_SECONDS) {
                if ((mframeNum % FRAME2GRAB) == 0) {
                    saveImageToArray(inputFrame);
                    mframeNum++;
                }
                else
                    mframeNum++;
            }
            else
            {
                mframeNum = 0;
                turnOffCapture();
            }
        }break;
        case StitcherActivity.CAPT_STILL_IM :
        {
            saveImageToArray(inputFrame);
            StitcherActivity.viewMode = StitcherActivity.VIEW_MODE_RGBA;
        }
        }
        return mRgba;
    }
 
    public void startVidCap() {
        if (StitcherActivity.viewMode == StitcherActivity.VIEW_MODE_RGBA)
        {
            turnOnCapture();
        }
        else if (StitcherActivity.viewMode == StitcherActivity.SAVE_IMAGE_MAT)
        {
            turnOffCapture();
        }
    }
 
    private void turnOffCapture()
    {
 
        StitcherActivity.viewMode = StitcherActivity.VIEW_MODE_RGBA;
    }
 
    private void turnOnCapture()
    {
 
        StitcherActivity.viewMode = StitcherActivity.SAVE_IMAGE_MAT;
//      startVidCapture.setText("Stop Video Capture");
        images_to_be_stitched.clear();
        recordStart = new Date().getTime();
 
    }
 
    public void stitchImages() {
        if(!images_to_be_stitched.isEmpty())
        {
            for (int j = 0; j < images_to_be_stitched.size(); j++) {
                writeImage(images_to_be_stitched.get(j), j);
            }
        Log.i("stitchImages", "Done writing to disk. Starting stitching " + images_to_be_stitched.size() + " images");
            FindFeatures(images_to_be_stitched.get(0).getNativeObjAddr(),
                    images_to_be_stitched.get(0).getNativeObjAddr(),
                    panorama.getNativeObjAddr(), images_to_be_stitched.size());
        Log.i("stitchImages", "Done stitching. Writing panarama");
            writePano(panorama);
 
        Log.i("stitchImages", "deleting temp files");
 
            deleteTmpIm();
        }
    }
 
    public void captStillImage()
    {
        StitcherActivity.viewMode = StitcherActivity.CAPT_STILL_IM;
 
    }
 
    private String getFullFileName( int num)
    {
        return mImageName + num + mImageExt;
    }
 
    private void writeImage(Mat image, int imNum)
    {
        writeImage(image, getFullFileName(imNum));
    }
 
    private void writeImage(Mat image, String fileName) {

    	File createDir = tempImageDir;
        
        if(!createDir.exists())
        {
        	Log.i("TEST DIR", "creating dir \"" + createDir+"\"");
            createDir.mkdirs();
        }
        else
        {
        	Log.i("TEST DIR", "dir \"" + createDir+"\" already exists");
        }
        Highgui.imwrite(tempImageDir+File.separator + fileName, image);

    }
 
    private void writePano(Mat image)
    {
        Date dateNow = new  Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        if(!StitchImageDir.exists())
        {
        	Log.i(TAG, "creating dir \"" + StitchImageDir+"\"");
            StitchImageDir.mkdir();
        }
        Highgui.imwrite(StitchImageDir.getPath()+ File.separator + "panoStich"+dateFormat.format(dateNow) +mImageExt, image);
 
    }
 
    private void deleteTmpIm()
    {
        File curFile;
        for (int j = 0; j < images_to_be_stitched.size(); j++) {
            curFile = new File(getFullFileName(j));
            curFile.delete();
        }
        images_to_be_stitched.clear();
    }
 
    public void viewStitchImages()
    {
    	/*
        Intent intent = new Intent(this, GalleryActivity.class);
 
        startActivity(intent);*/
    }
 
    private void saveImageToArray(Mat inputFrame) {
        images_to_be_stitched.add(inputFrame.clone());
    }
 
    private int FPS() {
        long curTime = new Date().getTime();
        int FPS = (int) (1000 / (curTime - mPrevTime));
        mPrevTime = curTime;
        return FPS;
    }
 
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
 
    }
 
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    return true;
    }
 
    // public native void FindFeatures(List pano_images, Long stitch );
    public native void FindFeatures(long image1, long image2, long image3,
            int count);
}